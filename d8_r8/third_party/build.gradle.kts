// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters

plugins {
  `java-library`
  id("dependencies-plugin")
}

abstract class DownloadLimitService : BuildService<BuildServiceParameters.None>

val downloadLimitService =
  gradle.sharedServices.registerIfAbsent("downloadLimit", DownloadLimitService::class.java) {
    maxParallelUsages.set(30)
  }

private fun registerDependency(dep: ThirdPartyDependency): TaskProvider<DownloadDependency> {
  // Task name must be unique and safe. packageNames should be unique.
  val taskName = "download_${dep.packageName}"
  return tasks.register(taskName, DownloadDependency::class) {
    sha1File.set(getRoot().resolve(dep.sha1File))
    outputDir.set(getRoot().resolve(dep.path))
    tarGzFile.set(getRoot().resolve(dep.tarGzFile))
    successFile.set(getRoot().resolve(dep.successFile))
    dependencyType.set(dep.type)
    usesService(downloadLimitService)
  }
}

val publicTasks = allPublicDependencies().map { registerDependency(it) }
val publicTestTasks = allPublicTestDependencies().map { registerDependency(it) }

private fun registerTestDepArtifact(configurationName: String, artifact: Any, propName: String) {
  configurations.consumable(configurationName) {
    attributes { attribute(TEST_DEP_PROP, propName) }
    outgoing.artifact(artifact)
  }
}

private fun registerTestDep(
  configurationName: String,
  dep: ThirdPartyDependency,
  propName: String,
) {
  val taskProvider = tasks.named<DownloadDependency>("download_${dep.packageName}")
  registerTestDepArtifact(configurationName, taskProvider.flatMap { it.outputDir }, propName)
}

private fun registerTestDep(configurationName: String, dir: File, propName: String) {
  registerTestDepArtifact(configurationName, dir, propName)
}

// 'dependencies' have to be treated differently since it contains files that Gradle uses to run.
// This should be called 'dependencies' but that is reserved in gradle, so dependenciesBucket.
registerTestDep("dependenciesBucket", getRoot().resolve("third_party/dependencies"), "DEPENDENCIES")

registerTestDep("coreLambdaStubs", ThirdPartyDeps.coreLambdaStubs, "CORE_LAMBDA_STUBS")

registerTestDep("gson", ThirdPartyDeps.gson, "GSON")

registerTestDep("jdwpTests", ThirdPartyDeps.jdwpTests, "JDWP_TESTS")

val internalTasks =
  if (!providers.gradleProperty("no_internal").isPresent) {
    allInternalDependencies().map { registerDependency(it) }
  } else {
    emptyList()
  }

val internalTestTasks =
  if (!providers.gradleProperty("no_internal").isPresent) {
    allInternalTestDependencies().map { registerDependency(it) }
  } else {
    emptyList()
  }

tasks.register("downloadDeps") { dependsOn(publicTasks) }

val sharedDepsFiles by
  configurations.consumable("sharedDepsFiles") {
    publicTasks.forEach { taskProvider -> outgoing.artifact(taskProvider.flatMap { it.outputDir }) }
  }

tasks.register("downloadTestDeps") { dependsOn(publicTestTasks) }

val sharedTestDepsFiles by
  configurations.consumable("sharedTestDepsFiles") {
    publicTestTasks.forEach { taskProvider ->
      outgoing.artifact(taskProvider.flatMap { it.outputDir })
    }
  }

tasks.register("downloadDepsInternal") { dependsOn(internalTasks) }

val sharedDepsInternalFiles by
  configurations.consumable("sharedDepsInternalFiles") {
    internalTasks.forEach { taskProvider ->
      outgoing.artifact(taskProvider.flatMap { it.outputDir })
    }
  }

tasks.register("downloadTestDepsInternal") { dependsOn(internalTestTasks) }

val sharedTestDepsInternalFiles by
  configurations.consumable("sharedTestDepsInternalFiles") {
    internalTestTasks.forEach { taskProvider ->
      outgoing.artifact(taskProvider.flatMap { it.outputDir })
    }
  }

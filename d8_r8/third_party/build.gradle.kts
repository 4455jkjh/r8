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

fun registerDependency(dep: ThirdPartyDependency): TaskProvider<DownloadDependency> {
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

val downloadDeps by tasks.registering { dependsOn(publicTasks) }

val sharedDepsFiles by
  configurations.consumable("sharedDepsFiles") {
    publicTasks.forEach { taskProvider -> outgoing.artifact(taskProvider.flatMap { it.outputDir }) }
  }

val downloadTestDeps by tasks.registering { dependsOn(publicTestTasks) }

val sharedTestDepsFiles by
  configurations.consumable("sharedTestDepsFiles") {
    publicTestTasks.forEach { taskProvider ->
      outgoing.artifact(taskProvider.flatMap { it.outputDir })
    }
  }

val downloadDepsInternal by tasks.registering { dependsOn(internalTasks) }

val sharedDepsInternalFiles by
  configurations.consumable("sharedDepsInternalFiles") {
    internalTasks.forEach { taskProvider ->
      outgoing.artifact(taskProvider.flatMap { it.outputDir })
    }
  }

val downloadTestDepsInternal by tasks.registering { dependsOn(internalTestTasks) }

val sharedTestDepsInternalFiles by
  configurations.consumable("sharedTestDepsInternalFiles") {
    internalTestTasks.forEach { taskProvider ->
      outgoing.artifact(taskProvider.flatMap { it.outputDir })
    }
  }

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

registerTestDep("aapt2", ThirdPartyDeps.aapt2, "AAPT2")

registerTestDep("bundletool", ThirdPartyDeps.bundletool, "BUNDLETOOL")

registerTestDep("compilerApi", ThirdPartyDeps.compilerApi, "COMPILER_API")

registerTestDep(
  "composeExamplesChangedBitwiseValuePropagation",
  ThirdPartyDeps.composeExamplesChangedBitwiseValuePropagation,
  "COMPOSE_EXAMPLES_CHANGED_BITWISE_VALUE_PROPAGATION",
)

registerTestDep("coreLambdaStubs", ThirdPartyDeps.coreLambdaStubs, "CORE_LAMBDA_STUBS")

registerTestDep("dagger", ThirdPartyDeps.dagger, "DAGGER")

registerTestDep(
  "desugarLibraryConversions",
  ThirdPartyDeps.desugarLibraryConversions,
  "DESUGAR_LIBRARY_CONVERSIONS",
)

registerTestDep("googleJavaFormat", ThirdPartyDeps.googleJavaFormat, "GOOGLE_JAVA_FORMAT")

registerTestDep("googleKotlinFormat", ThirdPartyDeps.googleKotlinFormat, "GOOGLE_KOTLIN_FORMAT")

registerTestDep("gson", ThirdPartyDeps.gson, "GSON")

registerTestDep("guavaJre", ThirdPartyDeps.guavaJre, "GUAVA_JRE")

registerTestDep("jacoco", ThirdPartyDeps.jacoco, "JACOCO")

registerTestDep("jdk21Float16Test", ThirdPartyDeps.jdk21Float16Test, "JDK21_FLOAT16_TEST")

registerTestDep("jdwpTests", ThirdPartyDeps.jdwpTests, "JDWP_TESTS")

registerTestDep("jsr223", ThirdPartyDeps.jsr223, "JSR223")

registerTestDep(
  "kotlinR8TestResources",
  ThirdPartyDeps.kotlinR8TestResources,
  "KOTLIN_R8_TEST_RESOURCES",
)

registerTestDep("kotlinxCoroutines", ThirdPartyDeps.kotlinxCoroutines, "KOTLINX_COROUTINES")

registerTestDep("multidex", ThirdPartyDeps.multidex, "MULTIDEX")

registerTestDep(
  "processKeepRulesBinaryCompatibility",
  ThirdPartyDeps.processKeepRulesBinaryCompatibility,
  "PROCESS_KEEP_RULES_BINARY_COMPATIBILITY",
)

registerTestDep("r8Mappings", ThirdPartyDeps.r8Mappings, "R8_MAPPINGS")

registerTestDep(
  "retraceBinaryCompatibility",
  ThirdPartyDeps.retraceBinaryCompatibility,
  "RETRACE_BINARY_COMPATIBILITY",
)

registerTestDep(
  "retracePartitionFormats",
  ThirdPartyDeps.retracePartitionFormats,
  "RETRACE_PARTITION_FORMATS",
)

registerTestDep("rhino", ThirdPartyDeps.rhino, "RHINO")

registerTestDep("rhinoAndroid", ThirdPartyDeps.rhinoAndroid, "RHINO_ANDROID")

registerTestDep("smali", ThirdPartyDeps.smali, "SMALI")

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

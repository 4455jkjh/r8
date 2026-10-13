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

registerTestDep("apiDatabase", ThirdPartyDeps.apiDatabase, "API_DATABASE")

registerTestDep("artTests", ThirdPartyDeps.artTests, "ART_TESTS")

registerTestDep("artTestsLegacy", ThirdPartyDeps.artTestsLegacy, "ART_TESTS_LEGACY")

registerTestDep("bookstory", ThirdPartyDeps.bookstory, "BOOKSTORY")

registerTestDep("bundletool", ThirdPartyDeps.bundletool, "BUNDLETOOL")

registerTestDep("chromeBenchmark", ThirdPartyDeps.chromeBenchmark, "CHROME_BENCHMARK")

registerTestDep("chromeHeadless", ThirdPartyDeps.chromeHeadless, "CHROME_HEADLESS")

registerTestDep("compilerApi", ThirdPartyDeps.compilerApi, "COMPILER_API")

registerTestDep(
  "composeExamplesChangedBitwiseValuePropagation",
  ThirdPartyDeps.composeExamplesChangedBitwiseValuePropagation,
  "COMPOSE_EXAMPLES_CHANGED_BITWISE_VALUE_PROPAGATION",
)

registerTestDep("composeSamplesCrane", ThirdPartyDeps.composeSamplesCrane, "COMPOSE_SAMPLES_CRANE")

registerTestDep(
  "composeSamplesJetCaster",
  ThirdPartyDeps.composeSamplesJetCaster,
  "COMPOSE_SAMPLES_JETCASTER",
)

registerTestDep(
  "composeSamplesJetChat",
  ThirdPartyDeps.composeSamplesJetChat,
  "COMPOSE_SAMPLES_JETCHAT",
)

registerTestDep(
  "composeSamplesJetLagged",
  ThirdPartyDeps.composeSamplesJetLagged,
  "COMPOSE_SAMPLES_JETLAGGED",
)

registerTestDep(
  "composeSamplesJetNews",
  ThirdPartyDeps.composeSamplesJetNews,
  "COMPOSE_SAMPLES_JETNEWS",
)

registerTestDep(
  "composeSamplesJetSnack",
  ThirdPartyDeps.composeSamplesJetSnack,
  "COMPOSE_SAMPLES_JETSNACK",
)

registerTestDep("composeSamplesOwl", ThirdPartyDeps.composeSamplesOwl, "COMPOSE_SAMPLES_OWL")

registerTestDep("composeSamplesReply", ThirdPartyDeps.composeSamplesReply, "COMPOSE_SAMPLES_REPLY")

registerTestDep("coreLambdaStubs", ThirdPartyDeps.coreLambdaStubs, "CORE_LAMBDA_STUBS")

registerTestDep("dagger", ThirdPartyDeps.dagger, "DAGGER")

registerTestDep("desugarJdkLibs11", ThirdPartyDeps.desugarJdkLibs11, "DESUGAR_JDK_LIBS_11")

registerTestDep("desugarJdkLibs8", ThirdPartyDeps.desugarJdkLibs8, "DESUGAR_JDK_LIBS_8")

registerTestDep(
  "desugarLibraryConversions",
  ThirdPartyDeps.desugarLibraryConversions,
  "DESUGAR_LIBRARY_CONVERSIONS",
)

registerTestDep(
  "desugarLibraryRelease1_0_9",
  ThirdPartyDeps.desugarLibraryRelease1_0_9,
  "DESUGAR_LIBRARY_RELEASE_1_0_9",
)

registerTestDep(
  "desugarLibraryRelease1_0_10",
  ThirdPartyDeps.desugarLibraryRelease1_0_10,
  "DESUGAR_LIBRARY_RELEASE_1_0_10",
)

registerTestDep(
  "desugarLibraryRelease1_1_0",
  ThirdPartyDeps.desugarLibraryRelease1_1_0,
  "DESUGAR_LIBRARY_RELEASE_1_1_0",
)

registerTestDep(
  "desugarLibraryRelease1_1_1",
  ThirdPartyDeps.desugarLibraryRelease1_1_1,
  "DESUGAR_LIBRARY_RELEASE_1_1_1",
)

registerTestDep(
  "desugarLibraryRelease1_1_5",
  ThirdPartyDeps.desugarLibraryRelease1_1_5,
  "DESUGAR_LIBRARY_RELEASE_1_1_5",
)

registerTestDep(
  "desugarLibraryRelease2_0_3",
  ThirdPartyDeps.desugarLibraryRelease2_0_3,
  "DESUGAR_LIBRARY_RELEASE_2_0_3",
)

registerTestDep("examples", ThirdPartyDeps.examples, "EXAMPLES")

registerTestDep("examplesAndroidN", ThirdPartyDeps.examplesAndroidN, "EXAMPLES_ANDROID_N")

registerTestDep("examplesAndroidO", ThirdPartyDeps.examplesAndroidO, "EXAMPLES_ANDROID_O")

registerTestDep(
  "examplesAndroidOGenerated",
  ThirdPartyDeps.examplesAndroidOGenerated,
  "EXAMPLES_ANDROID_O_GENERATED",
)

registerTestDep(
  "examplesAndroidOLegacy",
  ThirdPartyDeps.examplesAndroidOLegacy,
  "EXAMPLES_ANDROID_O_LEGACY",
)

registerTestDep("examplesAndroidP", ThirdPartyDeps.examplesAndroidP, "EXAMPLES_ANDROID_P")

registerTestDep("feeder", ThirdPartyDeps.feeder, "FEEDER")

registerTestDep("fossifyFileManager", ThirdPartyDeps.fossifyFileManager, "FOSSIFY_FILE_MANAGER")

registerTestDep("googleJavaFormat", ThirdPartyDeps.googleJavaFormat, "GOOGLE_JAVA_FORMAT")

registerTestDep("googleKotlinFormat", ThirdPartyDeps.googleKotlinFormat, "GOOGLE_KOTLIN_FORMAT")

registerTestDep("gson", ThirdPartyDeps.gson, "GSON")

registerTestDep("guavaJre", ThirdPartyDeps.guavaJre, "GUAVA_JRE")

registerTestDep("jacoco", ThirdPartyDeps.jacoco, "JACOCO")

registerTestDep("java8Runtime", ThirdPartyDeps.java8Runtime, "JAVA_8_RUNTIME")

registerTestDep("jdk11Test", ThirdPartyDeps.jdk11Test, "JDK11_TEST")

registerTestDep("jdk21Float16Test", ThirdPartyDeps.jdk21Float16Test, "JDK21_FLOAT16_TEST")

registerTestDep("jdwpTests", ThirdPartyDeps.jdwpTests, "JDWP_TESTS")

registerTestDep("jsr223", ThirdPartyDeps.jsr223, "JSR223")

registerTestDep("keepassdx", ThirdPartyDeps.keepassdx, "KEEPASSDX")

registerTestDep(
  "kotlinR8TestResources",
  ThirdPartyDeps.kotlinR8TestResources,
  "KOTLIN_R8_TEST_RESOURCES",
)

registerTestDep("kotlinxCoroutines", ThirdPartyDeps.kotlinxCoroutines, "KOTLINX_COROUTINES")

registerTestDep("multidex", ThirdPartyDeps.multidex, "MULTIDEX")

registerTestDep("newpipe", ThirdPartyDeps.newpipe, "NEWPIPE")

registerTestDep("nowinandroid", ThirdPartyDeps.nowinandroid, "NOWINANDROID")

registerTestDep("omninotes", ThirdPartyDeps.omninotes, "OMNINOTES")

registerTestDep("opentracks", ThirdPartyDeps.opentracks, "OPENTRACKS")

registerTestDep(
  "processKeepRulesBinaryCompatibility",
  ThirdPartyDeps.processKeepRulesBinaryCompatibility,
  "PROCESS_KEEP_RULES_BINARY_COMPATIBILITY",
)

registerTestDep("proguard7_0_0", ThirdPartyDeps.proguard7_0_0, "PROGUARD_7_0_0")

registerTestDep("proguard7_7_0", ThirdPartyDeps.proguard7_7_0, "PROGUARD_7_7_0")

registerTestDep("r8", ThirdPartyDeps.r8, "R8")

registerTestDep("r8Mappings", ThirdPartyDeps.r8Mappings, "R8_MAPPINGS")

registerTestDep("r8v2_0_74", ThirdPartyDeps.r8v2_0_74, "R8_V2_0_74")

registerTestDep("r8v3_2_54", ThirdPartyDeps.r8v3_2_54, "R8_V3_2_54")

registerTestDep("r8v8_0_46", ThirdPartyDeps.r8v8_0_46, "R8_V8_0_46")

registerTestDep("readyou", ThirdPartyDeps.readyou, "READYOU")

registerTestDep("retraceBenchmark", ThirdPartyDeps.retraceBenchmark, "RETRACE_BENCHMARK")

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

registerTestDep("tivi", ThirdPartyDeps.tivi, "TIVI")

registerTestDep("tusky", ThirdPartyDeps.tusky, "TUSKY")

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

configurations.consumable("sharedDepsFiles") {
  publicTasks.forEach { taskProvider -> outgoing.artifact(taskProvider.flatMap { it.outputDir }) }
}

tasks.register("downloadTestDeps") { dependsOn(publicTestTasks) }

configurations.consumable("sharedTestDepsFiles") {
  publicTestTasks.forEach { taskProvider ->
    outgoing.artifact(taskProvider.flatMap { it.outputDir })
  }
}

tasks.register("downloadDepsInternal") { dependsOn(internalTasks) }

configurations.consumable("sharedDepsInternalFiles") {
  internalTasks.forEach { taskProvider -> outgoing.artifact(taskProvider.flatMap { it.outputDir }) }
}

tasks.register("downloadTestDepsInternal") { dependsOn(internalTestTasks) }

configurations.consumable("sharedTestDepsInternalFiles") {
  internalTestTasks.forEach { taskProvider ->
    outgoing.artifact(taskProvider.flatMap { it.outputDir })
  }
}

// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

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
  val noInternal = providers.gradleProperty("no_internal")
  return tasks.register(taskName, DownloadDependency::class) {
    sha1File.set(getRoot().resolve(dep.sha1File))
    outputDir.set(getRoot().resolve(dep.path))
    tarGzFile.set(getRoot().resolve(dep.tarGzFile))
    successFile.set(getRoot().resolve(dep.successFile))
    dependencyType.set(dep.type)
    usesService(downloadLimitService)
    val isInternal = dep.type == DependencyType.X20
    val isMissingOnWindows = dep.missingOnWindows
    val isWindows = DefaultNativePlatform.getCurrentOperatingSystem().isWindows
    onlyIf { !(isInternal && noInternal.isPresent) && !(isMissingOnWindows && isWindows) }
  }
}

val publicTasks = allPublicDependencies().map { registerDependency(it) }
val publicTestTasks = allPublicTestDependencies().map { registerDependency(it) }
val internalTestTasks = allInternalTestDependencies().map { registerDependency(it) }

private fun registerTestDepArtifact(
  configurationName: String,
  artifact: Any,
  propName: String,
  isInternal: Boolean = false,
  missingOnWindows: Boolean = false,
) {
  configurations.consumable(configurationName) {
    attributes {
      attribute(TEST_DEP_PROP, propName)
      attribute(TEST_DEP_INTERNAL, isInternal)
      attribute(TEST_DEP_MISSING_ON_WINDOWS, missingOnWindows)
    }
    outgoing.artifact(artifact)
  }
}

private fun registerTestDep(
  configurationName: String,
  dep: ThirdPartyDependency,
  propName: String,
) {
  val taskProvider = tasks.named<DownloadDependency>("download_${dep.packageName}")
  registerTestDepArtifact(
    configurationName,
    taskProvider.flatMap { it.outputDir },
    propName,
    dep.type == DependencyType.X20,
    dep.missingOnWindows,
  )
}

private fun registerTestDep(configurationName: String, dir: File, propName: String) {
  registerTestDepArtifact(configurationName, dir, propName)
}

// 'dependencies' have to be treated differently since it contains files that Gradle uses to run.
// This should be called 'dependencies' but that is reserved in gradle, so dependenciesBucket.
registerTestDep("dependenciesBucket", getRoot().resolve("third_party/dependencies"), "DEPENDENCIES")

registerTestDep("aapt2", ThirdPartyDeps.aapt2, "AAPT2")

registerTestDep("agsa", ThirdPartyDeps.agsa, "AGSA")

registerTestDep("apiDatabase", ThirdPartyDeps.apiDatabase, "API_DATABASE")

registerTestDep("artTests", ThirdPartyDeps.artTests, "ART_TESTS")

registerTestDep("artTestsLegacy", ThirdPartyDeps.artTestsLegacy, "ART_TESTS_LEGACY")

registerTestDep("bookstory", ThirdPartyDeps.bookstory, "BOOKSTORY")

registerTestDep("bundletool", ThirdPartyDeps.bundletool, "BUNDLETOOL")

registerTestDep("chrome", ThirdPartyDeps.chrome, "CHROME")

registerTestDep("chromeBenchmark", ThirdPartyDeps.chromeBenchmark, "CHROME_BENCHMARK")

registerTestDep("chromeHeadless", ThirdPartyDeps.chromeHeadless, "CHROME_HEADLESS")

registerTestDep("clank", ThirdPartyDeps.clank, "CLANK")

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

registerTestDep("framework", ThirdPartyDeps.framework, "FRAMEWORK")

registerTestDep("gmscoreLatest", ThirdPartyDeps.gmscoreLatest, "GMSCORE_LATEST")

registerTestDep("gmscoreV10", ThirdPartyDeps.gmscoreV10, "GMSCORE_V10")

registerTestDep("googleJavaFormat", ThirdPartyDeps.googleJavaFormat, "GOOGLE_JAVA_FORMAT")

registerTestDep("googleKotlinFormat", ThirdPartyDeps.googleKotlinFormat, "GOOGLE_KOTLIN_FORMAT")

registerTestDep("gson", ThirdPartyDeps.gson, "GSON")

registerTestDep("guavaJre", ThirdPartyDeps.guavaJre, "GUAVA_JRE")

registerTestDep(
  "internalIssue127524985",
  ThirdPartyDeps.internalIssue127524985,
  "INTERNAL_ISSUE_127524985",
)

registerTestDep("jacoco", ThirdPartyDeps.jacoco, "JACOCO")

registerTestDep("java8Runtime", ThirdPartyDeps.java8Runtime, "JAVA_8_RUNTIME")

registerTestDep("jdk8", ThirdPartyDeps.jdk8, "JDK_8")

registerTestDep("jdk9", ThirdPartyDeps.jdk9, "JDK_9")

registerTestDep("jdk11", ThirdPartyDeps.jdk11, "JDK_11")

registerTestDep("jdk11Test", ThirdPartyDeps.jdk11Test, "JDK11_TEST")

registerTestDep("jdk17", ThirdPartyDeps.jdk17, "JDK_17")

registerTestDep("jdk21", ThirdPartyDeps.jdk21, "JDK_21")

registerTestDep("jdk21Float16Test", ThirdPartyDeps.jdk21Float16Test, "JDK21_FLOAT16_TEST")

registerTestDep("jdk25", ThirdPartyDeps.jdk25, "JDK_25")

registerTestDep("jdk27", ThirdPartyDeps.jdk27, "JDK_27")

registerTestDep("jdwpTests", ThirdPartyDeps.jdwpTests, "JDWP_TESTS")

registerTestDep("jsr223", ThirdPartyDeps.jsr223, "JSR223")

registerTestDep("keepassdx", ThirdPartyDeps.keepassdx, "KEEPASSDX")

registerTestDep(
  "kotlinCompiler1_3_72",
  ThirdPartyDeps.kotlinCompiler1_3_72,
  "KOTLIN_COMPILER_1_3_72",
)

registerTestDep(
  "kotlinCompiler1_4_20",
  ThirdPartyDeps.kotlinCompiler1_4_20,
  "KOTLIN_COMPILER_1_4_20",
)

registerTestDep("kotlinCompiler1_5_0", ThirdPartyDeps.kotlinCompiler1_5_0, "KOTLIN_COMPILER_1_5_0")

registerTestDep("kotlinCompiler1_6_0", ThirdPartyDeps.kotlinCompiler1_6_0, "KOTLIN_COMPILER_1_6_0")

registerTestDep("kotlinCompiler1_7_0", ThirdPartyDeps.kotlinCompiler1_7_0, "KOTLIN_COMPILER_1_7_0")

registerTestDep("kotlinCompiler1_8_0", ThirdPartyDeps.kotlinCompiler1_8_0, "KOTLIN_COMPILER_1_8_0")

registerTestDep(
  "kotlinCompiler1_9_21",
  ThirdPartyDeps.kotlinCompiler1_9_21,
  "KOTLIN_COMPILER_1_9_21",
)

registerTestDep(
  "kotlinCompiler2_0_20",
  ThirdPartyDeps.kotlinCompiler2_0_20,
  "KOTLIN_COMPILER_2_0_20",
)

registerTestDep(
  "kotlinCompiler2_1_10",
  ThirdPartyDeps.kotlinCompiler2_1_10,
  "KOTLIN_COMPILER_2_1_10",
)

registerTestDep("kotlinCompiler2_2_0", ThirdPartyDeps.kotlinCompiler2_2_0, "KOTLIN_COMPILER_2_2_0")

registerTestDep(
  "kotlinCompiler2_3_10",
  ThirdPartyDeps.kotlinCompiler2_3_10,
  "KOTLIN_COMPILER_2_3_10",
)

registerTestDep(
  "kotlinCompiler2_4_20",
  ThirdPartyDeps.kotlinCompiler2_4_20,
  "KOTLIN_COMPILER_2_4_20",
)

registerTestDep("kotlinCompilerDev", ThirdPartyDeps.kotlinCompilerDev, "KOTLIN_COMPILER_DEV")

registerTestDep(
  "kotlinR8TestResources",
  ThirdPartyDeps.kotlinR8TestResources,
  "KOTLIN_R8_TEST_RESOURCES",
)

registerTestDep("kotlinxCoroutines", ThirdPartyDeps.kotlinxCoroutines, "KOTLINX_COROUTINES")

registerTestDep("multidex", ThirdPartyDeps.multidex, "MULTIDEX")

registerTestDep("nest", ThirdPartyDeps.nest, "NEST")

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

registerTestDep("proguardsettings", ThirdPartyDeps.proguardsettings, "PROGUARD_SETTINGS")

registerTestDep(
  "protoRuntimeEdition2023",
  ThirdPartyDeps.protoRuntimeEdition2023,
  "PROTO_RUNTIME_EDITION2023",
)

registerTestDep("protoRuntimeLegacy", ThirdPartyDeps.protoRuntimeLegacy, "PROTO_RUNTIME_LEGACY")

registerTestDep(
  "protoTestEdition2023",
  ThirdPartyDeps.protoTestEdition2023,
  "PROTO_TEST_EDITION2023",
)

registerTestDep("protoTestProto2", ThirdPartyDeps.protoTestProto2, "PROTO_TEST_PROTO2")

registerTestDep("protoTestProto3", ThirdPartyDeps.protoTestProto3, "PROTO_TEST_PROTO3")

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

registerTestDep("retraceInternal", ThirdPartyDeps.retraceInternal, "RETRACE_INTERNAL")

registerTestDep(
  "retracePartitionFormats",
  ThirdPartyDeps.retracePartitionFormats,
  "RETRACE_PARTITION_FORMATS",
)

registerTestDep("rhino", ThirdPartyDeps.rhino, "RHINO")

registerTestDep("rhinoAndroid", ThirdPartyDeps.rhinoAndroid, "RHINO_ANDROID")

registerTestDep("smali", ThirdPartyDeps.smali, "SMALI")

registerTestDep("systemUI", ThirdPartyDeps.systemUI, "SYSTEM_UI")

registerTestDep("tivi", ThirdPartyDeps.tivi, "TIVI")

registerTestDep("tusky", ThirdPartyDeps.tusky, "TUSKY")

registerTestDep("youtube2140", ThirdPartyDeps.youtube2140, "YOUTUBE_21_40")

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

tasks.register("downloadTestDepsInternal") { dependsOn(internalTestTasks) }

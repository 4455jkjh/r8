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

private fun registerConfig(configurationName: String, dep: ThirdPartyDependency) {
  val taskProvider = tasks.named<DownloadDependency>("download_${dep.packageName}")
  configurations.consumable(configurationName) {
    outgoing.capability("com.android.tools.r8.third_party:$configurationName:0")
    attributes {
      if (dep.type == DependencyType.X20) {
        attribute(ARTIFACT_INTERNAL, true)
      }
      if (dep.missingOnWindows) {
        attribute(ARTIFACT_MISSING_ON_WINDOWS, true)
      }
    }
    outgoing.artifact(taskProvider.flatMap { it.outputDir })
  }
}

// 'dependencies' have to be treated differently since it contains files that Gradle uses to run.
// This should be called 'dependencies' but that is reserved in gradle, so dependenciesBucket.
configurations.consumable("dependenciesBucket") {
  outgoing.artifact(getRoot().resolve("third_party/dependencies"))
}

registerConfig("aapt2", ThirdPartyDeps.aapt2)

registerConfig("agsa", ThirdPartyDeps.agsa)

registerConfig("androidJar14", ThirdPartyDeps.androidJar14)

registerConfig("androidJar15", ThirdPartyDeps.androidJar15)

registerConfig("androidJar19", ThirdPartyDeps.androidJar19)

registerConfig("androidJar21", ThirdPartyDeps.androidJar21)

registerConfig("androidJar22", ThirdPartyDeps.androidJar22)

registerConfig("androidJar23", ThirdPartyDeps.androidJar23)

registerConfig("androidJar24", ThirdPartyDeps.androidJar24)

registerConfig("androidJar25", ThirdPartyDeps.androidJar25)

registerConfig("androidJar26", ThirdPartyDeps.androidJar26)

registerConfig("androidJar27", ThirdPartyDeps.androidJar27)

registerConfig("androidJar28", ThirdPartyDeps.androidJar28)

registerConfig("androidJar29", ThirdPartyDeps.androidJar29)

registerConfig("androidJar30", ThirdPartyDeps.androidJar30)

registerConfig("androidJar31", ThirdPartyDeps.androidJar31)

registerConfig("androidJar32", ThirdPartyDeps.androidJar32)

registerConfig("androidJar33", ThirdPartyDeps.androidJar33)

registerConfig("androidJar34", ThirdPartyDeps.androidJar34)

registerConfig("androidJar35", ThirdPartyDeps.androidJar35)

registerConfig("androidJar36", ThirdPartyDeps.androidJar36)

registerConfig("androidJar36_1", ThirdPartyDeps.androidJar36_1)

registerConfig("androidJar37", ThirdPartyDeps.androidJar37)

registerConfig("androidJar37_1", ThirdPartyDeps.androidJar37_1)

registerConfig("androidJar37_2", ThirdPartyDeps.androidJar37_2)

registerConfig("androidJarLibcoreLatest", ThirdPartyDeps.androidJarLibcoreLatest)

registerConfig("androidJarMain", ThirdPartyDeps.androidJarMain)

registerConfig("apiDatabase", ThirdPartyDeps.apiDatabase)

registerConfig("artDefault", ThirdPartyDeps.artDefault)

registerConfig("art5_1_1", ThirdPartyDeps.art5_1_1)

registerConfig("art6_0_1", ThirdPartyDeps.art6_0_1)

registerConfig("art7_0_0", ThirdPartyDeps.art7_0_0)

registerConfig("art8_1_0", ThirdPartyDeps.art8_1_0)

registerConfig("art9_0_0", ThirdPartyDeps.art9_0_0)

registerConfig("art10_0_0", ThirdPartyDeps.art10_0_0)

registerConfig("art12_0_0", ThirdPartyDeps.art12_0_0)

registerConfig("art13_0_0", ThirdPartyDeps.art13_0_0)

registerConfig("art14_0_0", ThirdPartyDeps.art14_0_0)

registerConfig("art15_0_0", ThirdPartyDeps.art15_0_0)

registerConfig("art16_0_0", ThirdPartyDeps.art16_0_0)

registerConfig("art16_1_0", ThirdPartyDeps.art16_1_0)

registerConfig("art17_0_0", ThirdPartyDeps.art17_0_0)

registerConfig("art17_1_0", ThirdPartyDeps.art17_1_0)

registerConfig("art17_2_0", ThirdPartyDeps.art17_2_0)

registerConfig("artMain", ThirdPartyDeps.artMain)

registerConfig("artTests", ThirdPartyDeps.artTests)

registerConfig("artTestsLegacy", ThirdPartyDeps.artTestsLegacy)

registerConfig("bookstory", ThirdPartyDeps.bookstory)

registerConfig("bundletool", ThirdPartyDeps.bundletool)

registerConfig("chrome", ThirdPartyDeps.chrome)

registerConfig("chromeBenchmark", ThirdPartyDeps.chromeBenchmark)

registerConfig("chromeHeadless", ThirdPartyDeps.chromeHeadless)

registerConfig("chromeInternal", ThirdPartyDeps.chromeInternal)

registerConfig("clank", ThirdPartyDeps.clank)

registerConfig("compilerApi", ThirdPartyDeps.compilerApi)

registerConfig(
  "composeExamplesChangedBitwiseValuePropagation",
  ThirdPartyDeps.composeExamplesChangedBitwiseValuePropagation,
)

registerConfig("composeSamplesCrane", ThirdPartyDeps.composeSamplesCrane)

registerConfig("composeSamplesJetCaster", ThirdPartyDeps.composeSamplesJetCaster)

registerConfig("composeSamplesJetChat", ThirdPartyDeps.composeSamplesJetChat)

registerConfig("composeSamplesJetLagged", ThirdPartyDeps.composeSamplesJetLagged)

registerConfig("composeSamplesJetNews", ThirdPartyDeps.composeSamplesJetNews)

registerConfig("composeSamplesJetSnack", ThirdPartyDeps.composeSamplesJetSnack)

registerConfig("composeSamplesOwl", ThirdPartyDeps.composeSamplesOwl)

registerConfig("composeSamplesReply", ThirdPartyDeps.composeSamplesReply)

registerConfig("coreLambdaStubs", ThirdPartyDeps.coreLambdaStubs)

registerConfig("dagger", ThirdPartyDeps.dagger)

registerConfig("dalvik4_0_4", ThirdPartyDeps.dalvik4_0_4)

registerConfig("dalvik4_4_4", ThirdPartyDeps.dalvik4_4_4)

registerConfig("desugarJdkLibs11", ThirdPartyDeps.desugarJdkLibs11)

registerConfig("desugarJdkLibs8", ThirdPartyDeps.desugarJdkLibs8)

registerConfig("desugarLibraryConversions", ThirdPartyDeps.desugarLibraryConversions)

registerConfig("desugarLibraryRelease1_0_9", ThirdPartyDeps.desugarLibraryRelease1_0_9)

registerConfig("desugarLibraryRelease1_0_10", ThirdPartyDeps.desugarLibraryRelease1_0_10)

registerConfig("desugarLibraryRelease1_1_0", ThirdPartyDeps.desugarLibraryRelease1_1_0)

registerConfig("desugarLibraryRelease1_1_1", ThirdPartyDeps.desugarLibraryRelease1_1_1)

registerConfig("desugarLibraryRelease1_1_5", ThirdPartyDeps.desugarLibraryRelease1_1_5)

registerConfig("desugarLibraryRelease2_0_3", ThirdPartyDeps.desugarLibraryRelease2_0_3)

registerConfig("dex2Oat33_10", ThirdPartyDeps.dex2Oat33_10)

registerConfig("dex2Oat35_14", ThirdPartyDeps.dex2Oat35_14)

registerConfig("dex2Oat36_0", ThirdPartyDeps.dex2Oat36_0)

registerConfig("dex2OatHead", ThirdPartyDeps.dex2OatHead)

registerConfig("examples", ThirdPartyDeps.examples)

registerConfig("examplesAndroidN", ThirdPartyDeps.examplesAndroidN)

registerConfig("examplesAndroidO", ThirdPartyDeps.examplesAndroidO)

registerConfig("examplesAndroidOGenerated", ThirdPartyDeps.examplesAndroidOGenerated)

registerConfig("examplesAndroidOLegacy", ThirdPartyDeps.examplesAndroidOLegacy)

registerConfig("examplesAndroidP", ThirdPartyDeps.examplesAndroidP)

registerConfig("feeder", ThirdPartyDeps.feeder)

registerConfig("fossifyFileManager", ThirdPartyDeps.fossifyFileManager)

registerConfig("framework", ThirdPartyDeps.framework)

registerConfig("gmscoreLatest", ThirdPartyDeps.gmscoreLatest)

registerConfig("gmscoreV10", ThirdPartyDeps.gmscoreV10)

registerConfig("googleJavaFormat", ThirdPartyDeps.googleJavaFormat)

registerConfig("googleKotlinFormat", ThirdPartyDeps.googleKotlinFormat)

registerConfig("gson", ThirdPartyDeps.gson)

registerConfig("guavaJre", ThirdPartyDeps.guavaJre)

registerConfig("internalIssue127524985", ThirdPartyDeps.internalIssue127524985)

registerConfig("jacoco", ThirdPartyDeps.jacoco)

registerConfig("java8Runtime", ThirdPartyDeps.java8Runtime)

registerConfig("jdk8", ThirdPartyDeps.jdk8)

registerConfig("jdk9", ThirdPartyDeps.jdk9)

registerConfig("jdk11", ThirdPartyDeps.jdk11)

registerConfig("jdk11Test", ThirdPartyDeps.jdk11Test)

registerConfig("jdk17", ThirdPartyDeps.jdk17)

registerConfig("jdk21", ThirdPartyDeps.jdk21)

registerConfig("jdk21Float16Test", ThirdPartyDeps.jdk21Float16Test)

registerConfig("jdk25", ThirdPartyDeps.jdk25)

registerConfig("jdk27", ThirdPartyDeps.jdk27)

registerConfig("jdwpTests", ThirdPartyDeps.jdwpTests)

registerConfig("jsr223", ThirdPartyDeps.jsr223)

registerConfig("keepassdx", ThirdPartyDeps.keepassdx)

registerConfig("kotlinCompiler1_3_72", ThirdPartyDeps.kotlinCompiler1_3_72)

registerConfig("kotlinCompiler1_4_20", ThirdPartyDeps.kotlinCompiler1_4_20)

registerConfig("kotlinCompiler1_5_0", ThirdPartyDeps.kotlinCompiler1_5_0)

registerConfig("kotlinCompiler1_6_0", ThirdPartyDeps.kotlinCompiler1_6_0)

registerConfig("kotlinCompiler1_7_0", ThirdPartyDeps.kotlinCompiler1_7_0)

registerConfig("kotlinCompiler1_8_0", ThirdPartyDeps.kotlinCompiler1_8_0)

registerConfig("kotlinCompiler1_9_21", ThirdPartyDeps.kotlinCompiler1_9_21)

registerConfig("kotlinCompiler2_0_20", ThirdPartyDeps.kotlinCompiler2_0_20)

registerConfig("kotlinCompiler2_1_10", ThirdPartyDeps.kotlinCompiler2_1_10)

registerConfig("kotlinCompiler2_2_0", ThirdPartyDeps.kotlinCompiler2_2_0)

registerConfig("kotlinCompiler2_3_10", ThirdPartyDeps.kotlinCompiler2_3_10)

registerConfig("kotlinCompiler2_4_20", ThirdPartyDeps.kotlinCompiler2_4_20)

registerConfig("kotlinCompilerDev", ThirdPartyDeps.kotlinCompilerDev)

registerConfig("kotlinR8TestResources", ThirdPartyDeps.kotlinR8TestResources)

registerConfig("kotlinxCoroutines", ThirdPartyDeps.kotlinxCoroutines)

registerConfig("multidex", ThirdPartyDeps.multidex)

registerConfig("nest", ThirdPartyDeps.nest)

registerConfig("newpipe", ThirdPartyDeps.newpipe)

registerConfig("nowinandroid", ThirdPartyDeps.nowinandroid)

registerConfig("omninotes", ThirdPartyDeps.omninotes)

registerConfig("opentracks", ThirdPartyDeps.opentracks)

registerConfig(
  "processKeepRulesBinaryCompatibility",
  ThirdPartyDeps.processKeepRulesBinaryCompatibility,
)

registerConfig("proguard7_0_0", ThirdPartyDeps.proguard7_0_0)

registerConfig("proguard7_7_0", ThirdPartyDeps.proguard7_7_0)

registerConfig("proguardsettings", ThirdPartyDeps.proguardsettings)

registerConfig("protoRuntimeEdition2023", ThirdPartyDeps.protoRuntimeEdition2023)

registerConfig("protoRuntimeLegacy", ThirdPartyDeps.protoRuntimeLegacy)

registerConfig("protoTestEdition2023", ThirdPartyDeps.protoTestEdition2023)

registerConfig("protoTestProto2", ThirdPartyDeps.protoTestProto2)

registerConfig("protoTestProto3", ThirdPartyDeps.protoTestProto3)

registerConfig("r8", ThirdPartyDeps.r8)

registerConfig("r8Mappings", ThirdPartyDeps.r8Mappings)

registerConfig("r8v2_0_74", ThirdPartyDeps.r8v2_0_74)

registerConfig("r8v3_2_54", ThirdPartyDeps.r8v3_2_54)

registerConfig("r8v8_0_46", ThirdPartyDeps.r8v8_0_46)

registerConfig("readyou", ThirdPartyDeps.readyou)

registerConfig("retraceBenchmark", ThirdPartyDeps.retraceBenchmark)

registerConfig("retraceBinaryCompatibility", ThirdPartyDeps.retraceBinaryCompatibility)

registerConfig("retraceInternal", ThirdPartyDeps.retraceInternal)

registerConfig("retracePartitionFormats", ThirdPartyDeps.retracePartitionFormats)

registerConfig("rhino", ThirdPartyDeps.rhino)

registerConfig("rhinoAndroid", ThirdPartyDeps.rhinoAndroid)

registerConfig("smali", ThirdPartyDeps.smali)

registerConfig("opensourceNewpipe", ThirdPartyDeps.opensourceNewpipe)

registerConfig("signalAndroid", ThirdPartyDeps.signalAndroid)

registerConfig("systemUI", ThirdPartyDeps.systemUI)

registerConfig("tachiyomi", ThirdPartyDeps.tachiyomi)

registerConfig("tivi", ThirdPartyDeps.tivi)

registerConfig("tusky", ThirdPartyDeps.tusky)

registerConfig("youtube2140", ThirdPartyDeps.youtube2140)

registerConfig("youtube2140D8Merge", ThirdPartyDeps.youtube2140D8Merge)

configurations.consumable("examplesClasses") {
  val task = tasks.named<DownloadDependency>("download_${ThirdPartyDeps.examples.packageName}")
  outgoing.artifact(task.flatMap { it.outputDir.dir("classes") })
}

configurations.consumable("jasminJar") {
  val task = tasks.named<DownloadDependency>("download_${ThirdPartyDeps.jasmin.packageName}")
  outgoing.artifact(task.flatMap { it.outputDir.file("jasmin-2.4.jar") })
}

configurations.consumable("jdk11TestJavaBasePatchSources") {
  val task = tasks.named<DownloadDependency>("download_${ThirdPartyDeps.jdk11Test.packageName}")
  outgoing.artifact(task.flatMap { it.outputDir.dir("lib/testlibrary/bootlib/java.base") })
}

configurations.consumable("jdk11TestLibrarySources") {
  val task = tasks.named<DownloadDependency>("download_${ThirdPartyDeps.jdk11Test.packageName}")
  outgoing.artifact(task.flatMap { it.outputDir.dir("lib/testlibrary/jdk") })
}

configurations.consumable("jdwpTestsJar") {
  val task = tasks.named<DownloadDependency>("download_${ThirdPartyDeps.jdwpTests.packageName}")
  outgoing.artifact(task.flatMap { it.outputDir.file("apache-harmony-jdwp-tests-host.jar") })
}

configurations.consumable("jdk25Home") {
  val task = tasks.named<DownloadDependency>("download_${ThirdPartyDeps.jdk25.packageName}")
  val isMac = DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX
  outgoing.artifact(task.flatMap { if (isMac) it.outputDir.dir("Contents/Home") else it.outputDir })
}

tasks.register("downloadDeps") { dependsOn(publicTasks) }

tasks.register("downloadTestDeps") { dependsOn(publicTestTasks) }

tasks.register("downloadTestDepsInternal") { dependsOn(internalTestTasks) }

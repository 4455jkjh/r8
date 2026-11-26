// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.nio.file.Paths
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

val publicTasks = mutableListOf<TaskProvider<DownloadDependency>>()
val publicTestTasks = mutableListOf<TaskProvider<DownloadDependency>>()
val internalTestTasks = mutableListOf<TaskProvider<DownloadDependency>>()

private fun path(first: String, vararg more: String): File = Paths.get(first, *more).toFile()

private fun registerDownload(
  packageName: String,
  path: File,
  testOnly: Boolean = false,
  type: DependencyType = DependencyType.GOOGLE_STORAGE,
  missingOnWindows: Boolean = false,
): TaskProvider<DownloadDependency> {
  // Task name must be unique and safe. packageNames should be unique.
  val taskName = "download_$packageName"
  val noInternal = providers.gradleProperty("no_internal")
  val isInternal = type == DependencyType.X20
  val isMissingOnWindows = missingOnWindows
  val isWindows = DefaultNativePlatform.getCurrentOperatingSystem().isWindows
  val taskProvider =
    tasks.register(taskName, DownloadDependency::class) {
      sha1File.set(getRoot().resolve(path.resolveSibling("${path.name}.tar.gz.sha1")))
      outputDir.set(getRoot().resolve(path))
      tarGzFile.set(getRoot().resolve(path.resolveSibling("${path.name}.tar.gz")))
      successFile.set(getRoot().resolve(path.resolveSibling("${path.name}.success")))
      dependencyType.set(type)
      usesService(downloadLimitService)
      onlyIf { !(isInternal && noInternal.isPresent) && !(isMissingOnWindows && isWindows) }
    }
  when {
    !testOnly && type == DependencyType.GOOGLE_STORAGE -> publicTasks.add(taskProvider)
    testOnly && type == DependencyType.GOOGLE_STORAGE -> publicTestTasks.add(taskProvider)
    testOnly && type == DependencyType.X20 -> internalTestTasks.add(taskProvider)
    else ->
      error("Unsupported third-party dependency category: $packageName ($type, testOnly=$testOnly)")
  }
  return taskProvider
}

private fun registerThirdParty(
  configurationName: String,
  path: File,
  packageName: String = configurationName,
  testOnly: Boolean = false,
  type: DependencyType = DependencyType.GOOGLE_STORAGE,
  missingOnWindows: Boolean = false,
): TaskProvider<DownloadDependency> {
  val taskProvider = registerDownload(packageName, path, testOnly, type, missingOnWindows)
  configurations.consumable(configurationName) {
    outgoing.capability("com.android.tools.r8.third_party:$configurationName:0")
    attributes {
      if (type == DependencyType.X20) {
        attribute(ARTIFACT_INTERNAL, true)
      }
      if (missingOnWindows) {
        attribute(ARTIFACT_MISSING_ON_WINDOWS, true)
      }
    }
    outgoing.artifact(taskProvider.flatMap { it.outputDir })
  }
  return taskProvider
}

private fun registerAndroidJar(configurationName: String, version: String) =
  registerThirdParty(
    configurationName,
    path("third_party", "android_jar", version),
    packageName = "android_jar_$version",
  )

private fun registerAndroidVm(configurationName: String, vararg version: String) =
  registerThirdParty(
    configurationName,
    path("tools", "linux", *version),
    packageName = version.last(),
    missingOnWindows = true,
  )

private fun registerDesugarLibraryRelease(configurationName: String, version: String) =
  registerThirdParty(
    configurationName,
    path("third_party", "openjdk", "desugar_jdk_libs_releases", version),
    packageName = "desugar-library-release-$version",
  )

private fun registerDex2Oat(configurationName: String, version: String) =
  registerThirdParty(
    configurationName,
    path("third_party", "dex2oat", version),
    packageName = "dex2oat-$version",
    missingOnWindows = true,
  )

private fun registerJdk(configurationName: String, jdk: Jdk): TaskProvider<DownloadDependency> {
  val os = DefaultNativePlatform.getCurrentOperatingSystem()
  val isJdk8 = jdk == Jdk.JDK_8
  val subFolder =
    when {
      os.isLinux -> if (isJdk8) "linux-x86" else "linux"
      os.isMacOsX -> if (isJdk8) "darwin-x86" else "osx"
      else -> {
        assert(os.isWindows)
        "windows"
      }
    }
  return registerThirdParty(
    configurationName,
    path("third_party", "openjdk", jdk.folder, subFolder),
    packageName = jdk.name,
    missingOnWindows = isJdk8,
  )
}

private fun registerKotlinCompiler(configurationName: String, version: String) =
  registerThirdParty(
    configurationName,
    path("third_party", "kotlin", "kotlin-compiler-$version"),
    packageName = "kotlin-compiler-$version",
  )

// 'dependencies' have to be treated differently since it contains files that Gradle uses to run.
// This should be called 'dependencies' but that is reserved in gradle, so dependenciesBucket.
configurations.consumable("dependenciesBucket") {
  outgoing.artifact(getRoot().resolve("third_party/dependencies"))
}

registerThirdParty("aapt2", path("third_party", "aapt2"))

registerThirdParty(
  "agsa",
  path("third_party", "closedsource-apps", "agsa", "20250412-v16.14.47"),
  testOnly = true,
  type = DependencyType.X20,
)

registerAndroidJar("androidJar14", "lib-v14")

registerAndroidJar("androidJar15", "lib-v15")

registerAndroidJar("androidJar19", "lib-v19")

registerAndroidJar("androidJar21", "lib-v21")

registerAndroidJar("androidJar22", "lib-v22")

registerAndroidJar("androidJar23", "lib-v23")

registerAndroidJar("androidJar24", "lib-v24")

registerAndroidJar("androidJar25", "lib-v25")

registerAndroidJar("androidJar26", "lib-v26")

registerAndroidJar("androidJar27", "lib-v27")

registerAndroidJar("androidJar28", "lib-v28")

registerAndroidJar("androidJar29", "lib-v29")

registerAndroidJar("androidJar30", "lib-v30")

registerAndroidJar("androidJar31", "lib-v31")

registerAndroidJar("androidJar32", "lib-v32")

registerAndroidJar("androidJar33", "lib-v33")

registerAndroidJar("androidJar34", "lib-v34")

registerAndroidJar("androidJar35", "lib-v35")

registerAndroidJar("androidJar36", "lib-v36")

registerAndroidJar("androidJar36_1", "lib-v36.1")

registerAndroidJar("androidJar37", "lib-v37")

registerAndroidJar("androidJar37_1", "lib-v37.1")

registerAndroidJar("androidJar37_2", "lib-v37.2")

registerAndroidJar("androidJarLibcoreLatest", "libcore_latest")

registerAndroidJar("androidJarMain", "lib-main")

registerThirdParty("apiDatabase", path("third_party", "api_database", "api_database"))

registerAndroidVm("artDefault", "art")

registerAndroidVm("art5_1_1", "art-5.1.1")

registerAndroidVm("art6_0_1", "art-6.0.1")

registerAndroidVm("art7_0_0", "art-7.0.0")

registerAndroidVm("art8_1_0", "art-8.1.0")

registerAndroidVm("art9_0_0", "art-9.0.0")

registerAndroidVm("art10_0_0", "art-10.0.0")

registerAndroidVm("art12_0_0", "host", "art-12.0.0-beta4")

registerAndroidVm("art13_0_0", "host", "art-13.0.0")

registerAndroidVm("art14_0_0", "host", "art-14.0.0-beta3")

registerAndroidVm("art15_0_0", "host", "art-15.0.0-beta2")

registerAndroidVm("art16_0_0", "host", "art-16.0.0")

registerAndroidVm("art16_1_0", "host", "art-16.1.0")

registerAndroidVm("art17_0_0", "host", "art-17.0.0-beta4")

registerAndroidVm("art17_1_0", "host", "art-17.1.0")

registerAndroidVm("art17_2_0", "host", "art-17.2.0")

registerAndroidVm("artMain", "host", "art-main")

registerThirdParty("artTests", path("tests", "2017-10-04", "art"), packageName = "art-tests")

registerThirdParty(
  "artTestsLegacy",
  path("tests", "2016-12-19", "art"),
  packageName = "art-tests-legacy",
)

registerThirdParty("bookstory", path("third_party", "opensource-apps", "android", "bookstory"))

registerThirdParty("bundletool", path("third_party", "bundletool", "bundletool-1.11.0"))

registerThirdParty(
  "chrome",
  path("third_party", "chrome", "chrome_200430"),
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "chromeBenchmark",
  path("third_party", "opensource-apps", "chrome"),
  packageName = "chrome-benchmark",
)

registerThirdParty(
  "chromeHeadless",
  path(
    "third_party",
    "chrome_headless",
    DefaultNativePlatform.getCurrentOperatingSystem().let { os ->
      when {
        os.isLinux -> "linux"
        os.isMacOsX -> "mac"
        else -> {
          assert(os.isWindows)
          "windows"
        }
      }
    },
  ),
  packageName = "chrome_headless",
  testOnly = true,
)

registerThirdParty(
  "chromeInternal",
  path("third_party", "closedsource-apps", "chrome", "20261116-v160.0.8166.0"),
  packageName = "chrome-internal",
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "clank",
  path("third_party", "chrome", "clank_google3_prebuilt"),
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "compilerApi",
  path("third_party", "binary_compatibility_tests", "compiler_api_tests"),
  packageName = "compiler-api",
)

registerThirdParty(
  "composeExamplesChangedBitwiseValuePropagation",
  path("third_party", "opensource-apps", "compose-examples", "changed-bitwise-value-propagation"),
  packageName = "compose-examples-changed-bitwise-value-propagation",
)

registerThirdParty(
  "composeSamplesCrane",
  path("third_party", "opensource-apps", "android", "compose-samples", "crane"),
  packageName = "compose-samples-crane",
)

registerThirdParty(
  "composeSamplesJetCaster",
  path("third_party", "opensource-apps", "android", "compose-samples", "jetcaster"),
  packageName = "compose-samples-jetcaster",
)

registerThirdParty(
  "composeSamplesJetChat",
  path("third_party", "opensource-apps", "android", "compose-samples", "jetchat"),
  packageName = "compose-samples-jetchat",
)

registerThirdParty(
  "composeSamplesJetLagged",
  path("third_party", "opensource-apps", "android", "compose-samples", "jetlagged"),
  packageName = "compose-samples-jetlagged",
)

registerThirdParty(
  "composeSamplesJetNews",
  path("third_party", "opensource-apps", "android", "compose-samples", "jetnews"),
  packageName = "compose-samples-jetnews",
)

registerThirdParty(
  "composeSamplesJetSnack",
  path("third_party", "opensource-apps", "android", "compose-samples", "jetsnack"),
  packageName = "compose-samples-jetsnack",
)

registerThirdParty(
  "composeSamplesOwl",
  path("third_party", "opensource-apps", "android", "compose-samples", "owl"),
  packageName = "compose-samples-owl",
)

registerThirdParty(
  "composeSamplesReply",
  path("third_party", "opensource-apps", "android", "compose-samples", "reply"),
  packageName = "compose-samples-reply",
)

registerThirdParty("coreLambdaStubs", path("third_party", "core-lambda-stubs"))

registerThirdParty("dagger", path("third_party", "dagger", "2.41"))

registerAndroidVm("dalvik4_0_4", "dalvik-4.0.4")

registerAndroidVm("dalvik4_4_4", "dalvik")

registerDownload("dart-sdk", path("third_party", "dart-sdk"))

registerThirdParty(
  "desugarJdkLibs8",
  path("third_party", "openjdk", "desugar_jdk_libs"),
  packageName = "desugar-jdk-libs-8",
)

registerThirdParty(
  "desugarJdkLibs11",
  path("third_party", "openjdk", "desugar_jdk_libs_11"),
  packageName = "desugar-jdk-libs-11",
)

registerThirdParty(
  "desugarLibraryConversions",
  path("third_party", "openjdk", "custom_conversion"),
  packageName = "customConversion",
)

registerDesugarLibraryRelease("desugarLibraryRelease1_0_9", "1.0.9")

registerDesugarLibraryRelease("desugarLibraryRelease1_0_10", "1.0.10")

registerDesugarLibraryRelease("desugarLibraryRelease1_1_0", "1.1.0")

registerDesugarLibraryRelease("desugarLibraryRelease1_1_1", "1.1.1")

registerDesugarLibraryRelease("desugarLibraryRelease1_1_5", "1.1.5")

registerDesugarLibraryRelease("desugarLibraryRelease2_0_3", "2.0.3")

registerDex2Oat("dex2Oat33_10", "33.10")

registerDex2Oat("dex2Oat35_14", "35.14")

registerDex2Oat("dex2Oat36_0", "36.0")

registerDex2Oat("dex2OatHead", "head")

val examples = registerThirdParty("examples", path("third_party", "examples"))

registerThirdParty("examplesAndroidN", path("third_party", "examplesAndroidN"))

registerThirdParty("examplesAndroidO", path("third_party", "examplesAndroidO"))

registerThirdParty("examplesAndroidOGenerated", path("third_party", "examplesAndroidOGenerated"))

registerThirdParty("examplesAndroidOLegacy", path("third_party", "examplesAndroidOLegacy"))

registerThirdParty("examplesAndroidP", path("third_party", "examplesAndroidP"))

registerThirdParty("feeder", path("third_party", "opensource-apps", "android", "feeder"))

registerThirdParty(
  "fossifyFileManager",
  path("third_party", "opensource-apps", "android", "fossify-filemanager"),
  packageName = "fossify-filemanager",
)

// TODO(b/289363570): This could probably be removed.
registerThirdParty(
  "framework",
  path("third_party", "framework"),
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "gmscoreLatest",
  path("third_party", "gmscore", "latest"),
  packageName = "gmscore-version-latest",
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "gmscoreV10",
  path("third_party", "gmscore", "gmscore_v10"),
  packageName = "gmscore-version-gmscore_v10",
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "googleJavaFormat",
  path("third_party", "google", "google-java-format", "1.24.0"),
  packageName = "google-java-format-1.24",
)

registerThirdParty(
  "googleKotlinFormat",
  path("third_party", "google", "google-kotlin-format", "0.54"),
  packageName = "google-kotlin-format-0.54",
)

registerDownload("google-yapf-20231013", path("third_party", "google", "yapf", "20231013"))

registerThirdParty("gson", path("third_party", "gson", "gson-2.10.1"))

registerThirdParty(
  "guavaJre",
  path("third_party", "guava", "guava-32.1.2-jre"),
  packageName = "guava-jre",
)

registerThirdParty(
  "internalIssue127524985",
  path("third_party", "internal", "issue-127524985"),
  packageName = "internal-issue-127524985",
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty("jacoco", path("third_party", "jacoco", "0.8.6"))

val jasmin = registerDownload("jasmin", path("third_party", "jasmin"))

registerThirdParty(
  "java8Runtime",
  path("third_party", "openjdk", "openjdk-rt-1.8"),
  packageName = "openjdk-rt-1.8",
)

registerJdk("jdk8", Jdk.JDK_8)

registerJdk("jdk9", Jdk.JDK_9)

registerJdk("jdk11", Jdk.JDK_11)

val jdk11Test =
  registerThirdParty(
    "jdk11Test",
    path("third_party", "openjdk", "jdk-11-test"),
    packageName = "jdk-11-test",
  )

registerJdk("jdk17", Jdk.JDK_17)

registerJdk("jdk21", Jdk.JDK_21)

registerThirdParty(
  "jdk21Float16Test",
  path("third_party", "openjdk", "float16-test"),
  packageName = "float16-test",
)

val jdk25 = registerJdk("jdk25", Jdk.JDK_25)

registerJdk("jdk27", Jdk.JDK_27)

val jdwpTests =
  registerThirdParty("jdwpTests", path("third_party", "jdwp-tests"), packageName = "jdwp-tests")

registerThirdParty("jsr223", path("third_party", "jsr223-api-1.0"))

registerThirdParty("keepassdx", path("third_party", "opensource-apps", "android", "keepassdx"))

registerKotlinCompiler("kotlinCompiler1_3_72", "1.3.72")

registerKotlinCompiler("kotlinCompiler1_4_20", "1.4.20")

registerKotlinCompiler("kotlinCompiler1_5_0", "1.5.0")

registerKotlinCompiler("kotlinCompiler1_6_0", "1.6.0")

registerKotlinCompiler("kotlinCompiler1_7_0", "1.7.0")

registerKotlinCompiler("kotlinCompiler1_8_0", "1.8.0")

registerKotlinCompiler("kotlinCompiler1_9_21", "1.9.21")

registerKotlinCompiler("kotlinCompiler2_0_20", "2.0.20")

registerKotlinCompiler("kotlinCompiler2_1_10", "2.1.10")

registerKotlinCompiler("kotlinCompiler2_2_0", "2.2.0")

registerKotlinCompiler("kotlinCompiler2_3_10", "2.3.10")

registerKotlinCompiler("kotlinCompiler2_4_20", "2.4.20")

registerKotlinCompiler("kotlinCompilerDev", "dev")

registerThirdParty("kotlinR8TestResources", path("third_party", "kotlinR8TestResources"))

registerThirdParty(
  "kotlinxCoroutines",
  path("third_party", "kotlinx-coroutines-1.3.6"),
  packageName = "kotlinx-coroutines-1.3.6",
)

registerThirdParty("multidex", path("third_party", "multidex"))

registerThirdParty(
  "nest",
  path("third_party", "nest", "nest_20180926_7c6cfb"),
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty("newpipe", path("third_party", "opensource-apps", "android", "newpipe"))

registerDownload("node", path("third_party", "node", "24.16.0", "linux"))

registerThirdParty(
  "nowinandroid",
  path("third_party", "opensource-apps", "android", "nowinandroid"),
)

registerThirdParty("omninotes", path("third_party", "opensource-apps", "android", "omninotes"))

registerThirdParty(
  "opensourceNewpipe",
  path("third_party", "opensource-apps", "newpipe"),
  packageName = "opensource-newpipe",
  testOnly = true,
)

registerThirdParty("opentracks", path("third_party", "opensource-apps", "android", "opentracks"))

registerDownload("prettier", path("third_party", "prettier", "3.8.3"))

registerThirdParty(
  "processKeepRulesBinaryCompatibility",
  path("third_party", "processkeeprules", "binary_compatibility"),
  packageName = "processkeeprules-binary-compatibility",
)

registerThirdParty(
  "proguard7_0_0",
  path("third_party", "proguard", "proguard-7.0.0"),
  packageName = "proguard-7.0.0",
)

registerThirdParty(
  "proguard7_7_0",
  path("third_party", "proguard", "proguard-7.7.0"),
  packageName = "proguard-7.7.0",
)

registerThirdParty(
  "proguardsettings",
  path("third_party", "proguardsettings"),
  testOnly = true,
  type = DependencyType.X20,
)

registerDownload("protoc", path("third_party", "protoc"))

registerThirdParty(
  "protoRuntimeEdition2023",
  path("third_party", "proto", "runtime", "edition2023"),
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "protoRuntimeLegacy",
  path("third_party", "proto", "runtime", "legacy"),
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "protoTestEdition2023",
  path("third_party", "proto", "test", "edition2023"),
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "protoTestProto2",
  path("third_party", "proto", "test", "proto2"),
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "protoTestProto3",
  path("third_party", "proto", "test", "proto3"),
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty("r8", path("third_party", "r8"))

registerThirdParty("r8Mappings", path("third_party", "r8mappings"), packageName = "r8-mappings")

registerThirdParty(
  "r8v2_0_74",
  path("third_party", "r8-releases", "2.0.74"),
  packageName = "r8-v2-0-74",
)

registerThirdParty(
  "r8v3_2_54",
  path("third_party", "r8-releases", "3.2.54"),
  packageName = "r8-v3-2-54",
)

registerThirdParty(
  "r8v8_0_46",
  path("third_party", "r8-releases", "8.0.46"),
  packageName = "r8-v8-0-46",
)

registerThirdParty("readyou", path("third_party", "opensource-apps", "android", "readyou"))

registerThirdParty(
  "retraceBenchmark",
  path("third_party", "retrace_benchmark"),
  packageName = "retrace-benchmark",
)

registerThirdParty(
  "retraceBinaryCompatibility",
  path("third_party", "retrace", "binary_compatibility"),
  packageName = "retrace-binary-compatibility",
)

registerThirdParty(
  "retraceInternal",
  path("third_party", "retrace_internal"),
  packageName = "retrace-internal",
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "retracePartitionFormats",
  path("third_party", "retrace", "partition_formats"),
  packageName = "retrace-partition-formats",
)

registerThirdParty("rhino", path("third_party", "rhino-1.7.10"))

registerThirdParty(
  "rhinoAndroid",
  path("third_party", "rhino-android-1.1.1"),
  packageName = "rhino-android",
)

registerThirdParty(
  "signalAndroid",
  path("third_party", "opensource-apps", "signal-android"),
  packageName = "signal-android",
  testOnly = true,
)

registerThirdParty("smali", path("third_party", "smali"))

registerThirdParty(
  "systemUI",
  path("third_party", "closedsource-apps", "systemui"),
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "tachiyomi",
  path("third_party", "opensource-apps", "tachiyomi"),
  testOnly = true,
)

registerThirdParty("tivi", path("third_party", "opensource-apps", "tivi"))

registerThirdParty("tusky", path("third_party", "opensource-apps", "android", "tusky"))

registerThirdParty(
  "youtube2140",
  path("third_party", "youtube", "youtube.android_21.40"),
  packageName = "youtube-21.40",
  testOnly = true,
  type = DependencyType.X20,
)

registerThirdParty(
  "youtube2140D8Merge",
  path("third_party", "youtube", "youtube.android_21.40_d8_merge"),
  packageName = "youtube-21.40-d8-merge",
  testOnly = true,
  type = DependencyType.X20,
)

configurations.consumable("examplesClasses") {
  outgoing.artifact(examples.flatMap { it.outputDir.dir("classes") })
}

configurations.consumable("jasminJar") {
  outgoing.artifact(jasmin.flatMap { it.outputDir.file("jasmin-2.4.jar") })
}

configurations.consumable("jdk11TestJavaBasePatchSources") {
  outgoing.artifact(jdk11Test.flatMap { it.outputDir.dir("lib/testlibrary/bootlib/java.base") })
}

configurations.consumable("jdk11TestLibrarySources") {
  outgoing.artifact(jdk11Test.flatMap { it.outputDir.dir("lib/testlibrary/jdk") })
}

configurations.consumable("jdwpTestsJar") {
  outgoing.artifact(jdwpTests.flatMap { it.outputDir.file("apache-harmony-jdwp-tests-host.jar") })
}

configurations.consumable("jdk25Home") {
  val isMac = DefaultNativePlatform.getCurrentOperatingSystem().isMacOsX
  outgoing.artifact(
    jdk25.flatMap { if (isMac) it.outputDir.dir("Contents/Home") else it.outputDir }
  )
}

tasks.register("downloadDeps") { dependsOn(publicTasks) }

tasks.register("downloadTestDeps") { dependsOn(publicTestTasks) }

tasks.register("downloadTestDepsInternal") { dependsOn(internalTestTasks) }

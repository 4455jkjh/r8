// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.io.Serializable
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.component.ProjectComponentIdentifier
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.project
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.gradle.process.CommandLineArgumentProvider

/**
 * Runtime data dependencies supported by [TestRuntimeDataDepPlugin].
 *
 * Each entry exposes a consumable configuration from an artifact-producing project under the system
 * property `-DTEST_DEP_<name>=<path>` (read by `com.android.tools.r8.TestDeps`), without requiring
 * the producing project to know about `TEST_DEP_*` conventions.
 */
public enum class TestRuntimeData(
  public val projectPath: String,
  public val configurationName: String,
) {
  // NOTE: Enum constant names are used directly as the -DTEST_DEP_<NAME> system property names
  // read by com.android.tools.r8.TestDeps. Renaming an entry requires updating TestDeps.java.

  // Project-built test data artifacts.
  JAVA_BASE_EXTENSION(":tests_java_8:desugar", "javaBaseExtension"),

  // Third-party test data artifacts.
  DEPENDENCIES(":third_party", "dependenciesBucket"),
  AAPT2(":third_party", "aapt2"),
  AGSA(":third_party", "agsa"),
  ANDROID_JAR_14(":third_party", "androidJar14"),
  ANDROID_JAR_15(":third_party", "androidJar15"),
  ANDROID_JAR_19(":third_party", "androidJar19"),
  ANDROID_JAR_21(":third_party", "androidJar21"),
  ANDROID_JAR_22(":third_party", "androidJar22"),
  ANDROID_JAR_23(":third_party", "androidJar23"),
  ANDROID_JAR_24(":third_party", "androidJar24"),
  ANDROID_JAR_25(":third_party", "androidJar25"),
  ANDROID_JAR_26(":third_party", "androidJar26"),
  ANDROID_JAR_27(":third_party", "androidJar27"),
  ANDROID_JAR_28(":third_party", "androidJar28"),
  ANDROID_JAR_29(":third_party", "androidJar29"),
  ANDROID_JAR_30(":third_party", "androidJar30"),
  ANDROID_JAR_31(":third_party", "androidJar31"),
  ANDROID_JAR_32(":third_party", "androidJar32"),
  ANDROID_JAR_33(":third_party", "androidJar33"),
  ANDROID_JAR_34(":third_party", "androidJar34"),
  ANDROID_JAR_35(":third_party", "androidJar35"),
  ANDROID_JAR_36(":third_party", "androidJar36"),
  ANDROID_JAR_36_1(":third_party", "androidJar36_1"),
  ANDROID_JAR_37(":third_party", "androidJar37"),
  ANDROID_JAR_37_1(":third_party", "androidJar37_1"),
  ANDROID_JAR_37_2(":third_party", "androidJar37_2"),
  ANDROID_JAR_LIBCORE_LATEST(":third_party", "androidJarLibcoreLatest"),
  ANDROID_JAR_MAIN(":third_party", "androidJarMain"),
  API_DATABASE(":third_party", "apiDatabase"),
  ART_10_0_0(":third_party", "art10_0_0"),
  ART_12_0_0(":third_party", "art12_0_0"),
  ART_13_0_0(":third_party", "art13_0_0"),
  ART_14_0_0(":third_party", "art14_0_0"),
  ART_15_0_0(":third_party", "art15_0_0"),
  ART_16_0_0(":third_party", "art16_0_0"),
  ART_16_1_0(":third_party", "art16_1_0"),
  ART_17_0_0(":third_party", "art17_0_0"),
  ART_17_1_0(":third_party", "art17_1_0"),
  ART_17_2_0(":third_party", "art17_2_0"),
  ART_5_1_1(":third_party", "art5_1_1"),
  ART_6_0_1(":third_party", "art6_0_1"),
  ART_7_0_0(":third_party", "art7_0_0"),
  ART_8_1_0(":third_party", "art8_1_0"),
  ART_9_0_0(":third_party", "art9_0_0"),
  ART_DEFAULT(":third_party", "artDefault"),
  ART_MAIN(":third_party", "artMain"),
  ART_TESTS(":third_party", "artTests"),
  ART_TESTS_LEGACY(":third_party", "artTestsLegacy"),
  BOOKSTORY(":third_party", "bookstory"),
  BUNDLETOOL(":third_party", "bundletool"),
  CHROME(":third_party", "chrome"),
  CHROME_BENCHMARK(":third_party", "chromeBenchmark"),
  CHROME_HEADLESS(":third_party", "chromeHeadless"),
  CHROME_INTERNAL(":third_party", "chromeInternal"),
  CLANK(":third_party", "clank"),
  COMPILER_API(":third_party", "compilerApi"),
  COMPOSE_EXAMPLES_CHANGED_BITWISE_VALUE_PROPAGATION(
    ":third_party",
    "composeExamplesChangedBitwiseValuePropagation",
  ),
  COMPOSE_SAMPLES_CRANE(":third_party", "composeSamplesCrane"),
  COMPOSE_SAMPLES_JETCASTER(":third_party", "composeSamplesJetCaster"),
  COMPOSE_SAMPLES_JETCHAT(":third_party", "composeSamplesJetChat"),
  COMPOSE_SAMPLES_JETLAGGED(":third_party", "composeSamplesJetLagged"),
  COMPOSE_SAMPLES_JETNEWS(":third_party", "composeSamplesJetNews"),
  COMPOSE_SAMPLES_JETSNACK(":third_party", "composeSamplesJetSnack"),
  COMPOSE_SAMPLES_OWL(":third_party", "composeSamplesOwl"),
  COMPOSE_SAMPLES_REPLY(":third_party", "composeSamplesReply"),
  CORE_LAMBDA_STUBS(":third_party", "coreLambdaStubs"),
  DAGGER(":third_party", "dagger"),
  DALVIK_4_0_4(":third_party", "dalvik4_0_4"),
  DALVIK_4_4_4(":third_party", "dalvik4_4_4"),
  DESUGAR_JDK_LIBS_11(":third_party", "desugarJdkLibs11"),
  DESUGAR_JDK_LIBS_8(":third_party", "desugarJdkLibs8"),
  DESUGAR_LIBRARY_CONVERSIONS(":third_party", "desugarLibraryConversions"),
  DESUGAR_LIBRARY_RELEASE_1_0_10(":third_party", "desugarLibraryRelease1_0_10"),
  DESUGAR_LIBRARY_RELEASE_1_0_9(":third_party", "desugarLibraryRelease1_0_9"),
  DESUGAR_LIBRARY_RELEASE_1_1_0(":third_party", "desugarLibraryRelease1_1_0"),
  DESUGAR_LIBRARY_RELEASE_1_1_1(":third_party", "desugarLibraryRelease1_1_1"),
  DESUGAR_LIBRARY_RELEASE_1_1_5(":third_party", "desugarLibraryRelease1_1_5"),
  DESUGAR_LIBRARY_RELEASE_2_0_3(":third_party", "desugarLibraryRelease2_0_3"),
  DEX2OAT_33_10(":third_party", "dex2Oat33_10"),
  DEX2OAT_35_14(":third_party", "dex2Oat35_14"),
  DEX2OAT_36_0(":third_party", "dex2Oat36_0"),
  DEX2OAT_HEAD(":third_party", "dex2OatHead"),
  EXAMPLES(":third_party", "examples"),
  EXAMPLES_ANDROID_N(":third_party", "examplesAndroidN"),
  EXAMPLES_ANDROID_O(":third_party", "examplesAndroidO"),
  EXAMPLES_ANDROID_O_GENERATED(":third_party", "examplesAndroidOGenerated"),
  EXAMPLES_ANDROID_O_LEGACY(":third_party", "examplesAndroidOLegacy"),
  EXAMPLES_ANDROID_P(":third_party", "examplesAndroidP"),
  FEEDER(":third_party", "feeder"),
  FOSSIFY_FILE_MANAGER(":third_party", "fossifyFileManager"),
  FRAMEWORK(":third_party", "framework"),
  GMSCORE_LATEST(":third_party", "gmscoreLatest"),
  GMSCORE_V10(":third_party", "gmscoreV10"),
  GOOGLE_JAVA_FORMAT(":third_party", "googleJavaFormat"),
  GOOGLE_KOTLIN_FORMAT(":third_party", "googleKotlinFormat"),
  GSON(":third_party", "gson"),
  GUAVA_JRE(":third_party", "guavaJre"),
  INTERNAL_ISSUE_127524985(":third_party", "internalIssue127524985"),
  JACOCO(":third_party", "jacoco"),
  JAVA_8_RUNTIME(":third_party", "java8Runtime"),
  JDK11_TEST(":third_party", "jdk11Test"),
  JDK21_FLOAT16_TEST(":third_party", "jdk21Float16Test"),
  JDK_11(":third_party", "jdk11"),
  JDK_17(":third_party", "jdk17"),
  JDK_21(":third_party", "jdk21"),
  JDK_25(":third_party", "jdk25"),
  JDK_27(":third_party", "jdk27"),
  JDK_8(":third_party", "jdk8"),
  JDK_9(":third_party", "jdk9"),
  JDWP_TESTS(":third_party", "jdwpTests"),
  JSR223(":third_party", "jsr223"),
  KEEPASSDX(":third_party", "keepassdx"),
  KOTLINX_COROUTINES(":third_party", "kotlinxCoroutines"),
  KOTLIN_COMPILER_1_3_72(":third_party", "kotlinCompiler1_3_72"),
  KOTLIN_COMPILER_1_4_20(":third_party", "kotlinCompiler1_4_20"),
  KOTLIN_COMPILER_1_5_0(":third_party", "kotlinCompiler1_5_0"),
  KOTLIN_COMPILER_1_6_0(":third_party", "kotlinCompiler1_6_0"),
  KOTLIN_COMPILER_1_7_0(":third_party", "kotlinCompiler1_7_0"),
  KOTLIN_COMPILER_1_8_0(":third_party", "kotlinCompiler1_8_0"),
  KOTLIN_COMPILER_1_9_21(":third_party", "kotlinCompiler1_9_21"),
  KOTLIN_COMPILER_2_0_20(":third_party", "kotlinCompiler2_0_20"),
  KOTLIN_COMPILER_2_1_10(":third_party", "kotlinCompiler2_1_10"),
  KOTLIN_COMPILER_2_2_0(":third_party", "kotlinCompiler2_2_0"),
  KOTLIN_COMPILER_2_3_10(":third_party", "kotlinCompiler2_3_10"),
  KOTLIN_COMPILER_2_4_20(":third_party", "kotlinCompiler2_4_20"),
  KOTLIN_COMPILER_DEV(":third_party", "kotlinCompilerDev"),
  KOTLIN_R8_TEST_RESOURCES(":third_party", "kotlinR8TestResources"),
  MULTIDEX(":third_party", "multidex"),
  NEST(":third_party", "nest"),
  NEWPIPE(":third_party", "newpipe"),
  NOWINANDROID(":third_party", "nowinandroid"),
  OMNINOTES(":third_party", "omninotes"),
  OPENSOURCE_NEWPIPE(":third_party", "opensourceNewpipe"),
  OPENTRACKS(":third_party", "opentracks"),
  PROCESS_KEEP_RULES_BINARY_COMPATIBILITY(":third_party", "processKeepRulesBinaryCompatibility"),
  PROGUARD_7_0_0(":third_party", "proguard7_0_0"),
  PROGUARD_7_7_0(":third_party", "proguard7_7_0"),
  PROGUARD_SETTINGS(":third_party", "proguardsettings"),
  PROTO_RUNTIME_EDITION2023(":third_party", "protoRuntimeEdition2023"),
  PROTO_RUNTIME_LEGACY(":third_party", "protoRuntimeLegacy"),
  PROTO_TEST_EDITION2023(":third_party", "protoTestEdition2023"),
  PROTO_TEST_PROTO2(":third_party", "protoTestProto2"),
  PROTO_TEST_PROTO3(":third_party", "protoTestProto3"),
  R8(":third_party", "r8"),
  R8_MAPPINGS(":third_party", "r8Mappings"),
  R8_V2_0_74(":third_party", "r8v2_0_74"),
  R8_V3_2_54(":third_party", "r8v3_2_54"),
  R8_V8_0_46(":third_party", "r8v8_0_46"),
  READYOU(":third_party", "readyou"),
  RETRACE_BENCHMARK(":third_party", "retraceBenchmark"),
  RETRACE_BINARY_COMPATIBILITY(":third_party", "retraceBinaryCompatibility"),
  RETRACE_INTERNAL(":third_party", "retraceInternal"),
  RETRACE_PARTITION_FORMATS(":third_party", "retracePartitionFormats"),
  RHINO(":third_party", "rhino"),
  RHINO_ANDROID(":third_party", "rhinoAndroid"),
  SIGNAL_ANDROID(":third_party", "signalAndroid"),
  SMALI(":third_party", "smali"),
  SYSTEM_UI(":third_party", "systemUI"),
  TACHIYOMI(":third_party", "tachiyomi"),
  TIVI(":third_party", "tivi"),
  TUSKY(":third_party", "tusky"),
  YOUTUBE_21_40(":third_party", "youtube2140"),
  YOUTUBE_21_40_D8_MERGE(":third_party", "youtube2140D8Merge");

  public val propName: String
    get() = name
}

public fun DependencyHandler.runtimeOnlyData(dep: TestRuntimeData): Dependency? {
  return add("runtimeOnlyDataScope", project(dep.projectPath, dep.configurationName))
}

public fun DependencyHandler.runtimeOnlyDataFrom(projectPath: String): Dependency? {
  return add("runtimeOnlyDataScope", project(projectPath, "runtimeOnlyDataElements"))
}

public sealed class DependencyPresence : Serializable {
  public object Present : DependencyPresence()

  public data class Missing(public val reason: String) : DependencyPresence() {
    init {
      require(reason.isNotEmpty() && reason.all { it in 'a'..'z' || it in 'A'..'Z' || it == '_' }) {
        "Invalid missing reason '$reason': must only contain ASCII letters and underscores"
      }
    }
  }
}

public class TestDepEntry(
  @get:Input public val prop: String,
  @get:Input public val presence: DependencyPresence,
  @get:Optional @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE) public val file: File?,
)

public class TestDepsCommandLineArgumentProvider(
  @get:Nested public val entries: Provider<List<TestDepEntry>>
) : CommandLineArgumentProvider {
  override fun asArguments(): Iterable<String> {
    return entries.get().map { entry ->
      val value =
        when (entry.presence) {
          is DependencyPresence.Present -> entry.file!!.absolutePath
          is DependencyPresence.Missing -> "None:${entry.presence.reason}"
        }
      "-DTEST_DEP_${entry.prop}=$value"
    }
  }
}

private val RUNTIME_DATA_BY_TARGET: Map<Pair<String, String>, TestRuntimeData> = buildMap {
  for (entry in TestRuntimeData.entries) {
    val key = entry.projectPath to entry.configurationName
    val previous = put(key, entry)
    check(previous == null) { "Duplicate TestRuntimeData target $key for $previous and $entry" }
  }
}

/**
 * Configures the `runtimeOnlyData` dependency mechanism for test modules.
 *
 * Test modules declare dependencies on supported [TestRuntimeData] entries via
 * `runtimeOnlyData(TestRuntimeData.<NAME>)`, and aggregate another test module's data dependencies
 * via `runtimeOnlyDataFrom("<projectPath>")`. Resolved artifacts are passed to test, `JavaExec`,
 * and benchmark tasks via `-DTEST_DEP_<name>=<path>` system properties, which
 * `com.android.tools.r8.TestDeps` reads at runtime.
 */
public class TestRuntimeDataDepPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    val runtimeOnlyDataScope = target.configurations.dependencyScope("runtimeOnlyDataScope")
    target.configurations.consumable("runtimeOnlyDataElements") {
      extendsFrom(runtimeOnlyDataScope.get())
    }
    val runtimeOnlyDataConfig =
      target.configurations.resolvable("runtimeOnlyDataConfig") {
        extendsFrom(runtimeOnlyDataScope.get())
      }

    val noInternal = target.providers.gradleProperty("no_internal")
    val testDepsEntries: Provider<List<TestDepEntry>> =
      runtimeOnlyDataConfig.flatMap { config ->
        config.incoming.artifacts.resolvedArtifacts.map { artifacts ->
          val skipInternal = noInternal.isPresent
          val isWindows = DefaultNativePlatform.getCurrentOperatingSystem().isWindows
          val seenProps = mutableSetOf<String>()
          artifacts.map { artifact ->
            val key =
              (artifact.variant.owner as? ProjectComponentIdentifier)?.let { owner ->
                val prefix = "configuration '${owner.projectPath}:"
                val displayName = artifact.variant.displayName
                if (displayName.startsWith(prefix) && displayName.endsWith("'")) {
                  owner.projectPath to displayName.substring(prefix.length, displayName.length - 1)
                } else {
                  null
                }
              }
            val data =
              key?.let { RUNTIME_DATA_BY_TARGET[it] }
                ?: throw GradleException(
                  "runtimeOnlyData artifact is not a registered TestRuntimeData entry: " +
                    "owner=${artifact.variant.owner}, variant=${artifact.variant.displayName}, " +
                    "file=${artifact.file}"
                )
            if (!seenProps.add(data.propName)) {
              throw GradleException("Duplicate TEST_DEP property: ${data.propName}")
            }
            val isInternal = artifact.variant.attributes.getAttribute(ARTIFACT_INTERNAL) == true
            val isMissingOnWindows =
              artifact.variant.attributes.getAttribute(ARTIFACT_MISSING_ON_WINDOWS) == true
            val presence =
              when {
                isInternal && skipInternal -> DependencyPresence.Missing("no_internal")
                isMissingOnWindows && isWindows -> DependencyPresence.Missing("missing_on_windows")
                else -> DependencyPresence.Present
              }
            val file = if (presence is DependencyPresence.Present) artifact.file else null
            TestDepEntry(data.propName, presence, file)
          }
        }
      }

    target.tasks.withType(Test::class.java).configureEach {
      dependsOn(runtimeOnlyDataConfig)
      jvmArgumentProviders.add(TestDepsCommandLineArgumentProvider(entries = testDepsEntries))
    }

    target.tasks.withType(JavaExec::class.java).configureEach {
      dependsOn(runtimeOnlyDataConfig)
      jvmArgumentProviders.add(TestDepsCommandLineArgumentProvider(entries = testDepsEntries))
    }

    target.tasks.withType(RunBenchmarkTask::class.java).configureEach {
      dependsOn(runtimeOnlyDataConfig)
      this.testDepsEntries.set(testDepsEntries)
    }
  }
}

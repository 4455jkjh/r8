// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.CustomConversionVersion;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

/**
 * All reading of external files in tests should be managed by this class, which encloses the
 * untyped interface with the Gradle setup and system properties.
 *
 * <p>All paths configured for and returned by {@link TestDeps} must be and are absolute.
 *
 * <p>Even though this code lies in testbase, all dependencies should not be declared in testbase.
 * It is the callers of these accessors that have responsibility to add the respective dependency.
 * E.g. if module A calls {@link #getJunitJar}, module A should declare the runtimeOnlyData
 * dependency on Junit.
 */
public class TestDeps {

  private static Path getTestDependency(String name) {
    String prop = "TEST_DEP_" + name;
    String value = System.getProperty(prop);
    if (value == null) {
      throw new RuntimeException("Required test dependency system property not set: " + prop);
    }
    Path path = Paths.get(value);
    if (!Files.exists(path)) {
      throw new RuntimeException("Required test dependency does not exit: " + prop + "=" + path);
    }
    return path;
  }

  private static final Map<String, Path> dependencies;

  static {
    // This list serves as a list of required properties to match in Gradle.
    dependencies = new HashMap<>();
    dependencies.put("AAPT2", null);
    dependencies.put("API_DATABASE", null);
    dependencies.put("ART_TESTS", null);
    dependencies.put("ART_TESTS_LEGACY", null);
    dependencies.put("BOOKSTORY", null);
    dependencies.put("BUNDLETOOL", null);
    dependencies.put("CHROME_BENCHMARK", null);
    dependencies.put("CHROME_HEADLESS", null);
    dependencies.put("COMPILER_API", null);
    dependencies.put("COMPOSE_EXAMPLES_CHANGED_BITWISE_VALUE_PROPAGATION", null);
    dependencies.put("COMPOSE_SAMPLES_CRANE", null);
    dependencies.put("COMPOSE_SAMPLES_JETCASTER", null);
    dependencies.put("COMPOSE_SAMPLES_JETCHAT", null);
    dependencies.put("COMPOSE_SAMPLES_JETLAGGED", null);
    dependencies.put("COMPOSE_SAMPLES_JETNEWS", null);
    dependencies.put("COMPOSE_SAMPLES_JETSNACK", null);
    dependencies.put("COMPOSE_SAMPLES_OWL", null);
    dependencies.put("COMPOSE_SAMPLES_REPLY", null);
    dependencies.put("CORE_LAMBDA_STUBS", null);
    dependencies.put("DAGGER", null);
    dependencies.put("DEPENDENCIES", null);
    dependencies.put("DESUGAR_JDK_LIBS_11", null);
    dependencies.put("DESUGAR_JDK_LIBS_8", null);
    dependencies.put("DESUGAR_LIBRARY_CONVERSIONS", null);
    dependencies.put("DESUGAR_LIBRARY_RELEASE_1_0_9", null);
    dependencies.put("DESUGAR_LIBRARY_RELEASE_1_0_10", null);
    dependencies.put("DESUGAR_LIBRARY_RELEASE_1_1_0", null);
    dependencies.put("DESUGAR_LIBRARY_RELEASE_1_1_1", null);
    dependencies.put("DESUGAR_LIBRARY_RELEASE_1_1_5", null);
    dependencies.put("DESUGAR_LIBRARY_RELEASE_2_0_3", null);
    dependencies.put("EXAMPLES", null);
    dependencies.put("EXAMPLES_ANDROID_N", null);
    dependencies.put("EXAMPLES_ANDROID_O", null);
    dependencies.put("EXAMPLES_ANDROID_O_GENERATED", null);
    dependencies.put("EXAMPLES_ANDROID_O_LEGACY", null);
    dependencies.put("EXAMPLES_ANDROID_P", null);
    dependencies.put("FEEDER", null);
    dependencies.put("FOSSIFY_FILE_MANAGER", null);
    dependencies.put("GOOGLE_JAVA_FORMAT", null);
    dependencies.put("GOOGLE_KOTLIN_FORMAT", null);
    dependencies.put("GSON", null);
    dependencies.put("GUAVA_JRE", null);
    dependencies.put("JACOCO", null);
    dependencies.put("JAVA_8_RUNTIME", null);
    dependencies.put("JAVA_BASE_EXTENSION", null);
    dependencies.put("JDK11_TEST", null);
    dependencies.put("JDK21_FLOAT16_TEST", null);
    dependencies.put("JDWP_TESTS", null);
    dependencies.put("JSR223", null);
    dependencies.put("KEEPASSDX", null);
    dependencies.put("KOTLIN_R8_TEST_RESOURCES", null);
    dependencies.put("KOTLINX_COROUTINES", null);
    dependencies.put("MULTIDEX", null);
    dependencies.put("NEWPIPE", null);
    dependencies.put("NOWINANDROID", null);
    dependencies.put("OMNINOTES", null);
    dependencies.put("OPENTRACKS", null);
    dependencies.put("PROCESS_KEEP_RULES_BINARY_COMPATIBILITY", null);
    dependencies.put("PROGUARD_7_0_0", null);
    dependencies.put("PROGUARD_7_7_0", null);
    dependencies.put("R8", null);
    dependencies.put("R8_MAPPINGS", null);
    dependencies.put("R8_V2_0_74", null);
    dependencies.put("R8_V3_2_54", null);
    dependencies.put("R8_V8_0_46", null);
    dependencies.put("READYOU", null);
    dependencies.put("RETRACE_BENCHMARK", null);
    dependencies.put("RETRACE_BINARY_COMPATIBILITY", null);
    dependencies.put("RETRACE_PARTITION_FORMATS", null);
    dependencies.put("RHINO", null);
    dependencies.put("RHINO_ANDROID", null);
    dependencies.put("SMALI", null);
    dependencies.put("TIVI", null);
    dependencies.put("TUSKY", null);
  }

  private static Path getDependency(String key) {
    return dependencies.computeIfAbsent(key, TestDeps::getTestDependency);
  }

  public static Path getAapt2() {
    return getDependencyPath("AAPT2", "aapt2");
  }

  public static Path getApiDatabasePath() {
    return getDependencyPath("API_DATABASE", "resources", "api_database.ser");
  }

  public static Path getArtTestsDir() {
    return getDependency("ART_TESTS");
  }

  public static Path getArtTestsPath(String... path) {
    return getDependencyPath("ART_TESTS", path);
  }

  public static Path getArtTestsLegacyDir() {
    return getDependency("ART_TESTS_LEGACY");
  }

  public static Path getArtTestsLegacyPath(String... path) {
    return getDependencyPath("ART_TESTS_LEGACY", path);
  }

  public static Path getBookStoryDir() {
    return getDependency("BOOKSTORY");
  }

  public static Path getBundleToolJar() {
    return getDependencyPath("BUNDLETOOL", "bundletool-all-1.11.0.jar");
  }

  public static Path getChromeBenchmarkDir() {
    return getDependency("CHROME_BENCHMARK");
  }

  public static Path getChromeHeadlessDir() {
    return getDependency("CHROME_HEADLESS");
  }

  public static Path getCompilerApiBinaryCompatibilityJar() {
    return getDependencyPath("COMPILER_API", "tests.jar");
  }

  public static Path getComposeExamplesChangedBitwiseValuePropagationDumpZip() {
    return getDependencyPath("COMPOSE_EXAMPLES_CHANGED_BITWISE_VALUE_PROPAGATION", "dump.zip");
  }

  public static Path getComposeSamplesCraneDir() {
    return getDependency("COMPOSE_SAMPLES_CRANE");
  }

  public static Path getComposeSamplesJetCasterDir() {
    return getDependency("COMPOSE_SAMPLES_JETCASTER");
  }

  public static Path getComposeSamplesJetChatDir() {
    return getDependency("COMPOSE_SAMPLES_JETCHAT");
  }

  public static Path getComposeSamplesJetLaggedDir() {
    return getDependency("COMPOSE_SAMPLES_JETLAGGED");
  }

  public static Path getComposeSamplesJetNewsDir() {
    return getDependency("COMPOSE_SAMPLES_JETNEWS");
  }

  public static Path getComposeSamplesJetSnackDir() {
    return getDependency("COMPOSE_SAMPLES_JETSNACK");
  }

  public static Path getComposeSamplesOwlDir() {
    return getDependency("COMPOSE_SAMPLES_OWL");
  }

  public static Path getComposeSamplesReplyDir() {
    return getDependency("COMPOSE_SAMPLES_REPLY");
  }

  public static Path getCoreLambdaStubsJar() {
    return getDependencyPath("CORE_LAMBDA_STUBS", "core-lambda-stubs.jar");
  }

  public static Path getDaggerPath(String... path) {
    return getDependencyPath("DAGGER", path);
  }

  public static Path getDesugarJdkLibs11Jar() {
    return getDependencyPath("DESUGAR_JDK_LIBS_11", "desugar_jdk_libs.jar");
  }

  public static Path getDesugarJdkLibs8Jar() {
    return getDependencyPath("DESUGAR_JDK_LIBS_8", "desugar_jdk_libs.jar");
  }

  public static Path getDesugarLibraryConversionsDir() {
    return getDependency("DESUGAR_LIBRARY_CONVERSIONS");
  }

  public static Path getDesugarLibraryConversions(CustomConversionVersion version) {
    return getDependencyPath("DESUGAR_LIBRARY_CONVERSIONS", version.getFileName());
  }

  public static Path getDesugarLibraryRelease1_0_9Dir() {
    return getDependency("DESUGAR_LIBRARY_RELEASE_1_0_9");
  }

  public static Path getDesugarLibraryRelease1_0_10Dir() {
    return getDependency("DESUGAR_LIBRARY_RELEASE_1_0_10");
  }

  public static Path getDesugarLibraryRelease1_1_0Dir() {
    return getDependency("DESUGAR_LIBRARY_RELEASE_1_1_0");
  }

  public static Path getDesugarLibraryRelease1_1_1Dir() {
    return getDependency("DESUGAR_LIBRARY_RELEASE_1_1_1");
  }

  public static Path getDesugarLibraryRelease1_1_5Dir() {
    return getDependency("DESUGAR_LIBRARY_RELEASE_1_1_5");
  }

  public static Path getDesugarLibraryRelease2_0_3Dir() {
    return getDependency("DESUGAR_LIBRARY_RELEASE_2_0_3");
  }

  public static Path getExamplesDir() {
    return getDependency("EXAMPLES");
  }

  public static Path getExamplesPath(String... path) {
    return getDependencyPath("EXAMPLES", path);
  }

  public static Path getExamplesAndroidNDir() {
    return getDependency("EXAMPLES_ANDROID_N");
  }

  public static Path getExamplesAndroidNPath(String... path) {
    return getDependencyPath("EXAMPLES_ANDROID_N", path);
  }

  public static Path getExamplesAndroidOPath(String... path) {
    return getDependencyPath("EXAMPLES_ANDROID_O", path);
  }

  public static Path getExamplesAndroidOGeneratedPath(String... path) {
    return getDependencyPath("EXAMPLES_ANDROID_O_GENERATED", path);
  }

  public static Path getExamplesAndroidOLegacyDir() {
    return getDependency("EXAMPLES_ANDROID_O_LEGACY");
  }

  public static Path getExamplesAndroidOLegacyPath(String... path) {
    return getDependencyPath("EXAMPLES_ANDROID_O_LEGACY", path);
  }

  public static Path getExamplesAndroidPPath(String... path) {
    return getDependencyPath("EXAMPLES_ANDROID_P", path);
  }

  public static Path getFeederDir() {
    return getDependency("FEEDER");
  }

  public static Path getFossifyFileManagerDir() {
    return getDependency("FOSSIFY_FILE_MANAGER");
  }

  public static Path getGoogleJavaFormatJar() {
    return getDependencyPath("GOOGLE_JAVA_FORMAT", "google-java-format-1.24.0-all-deps.jar");
  }

  public static Path getGoogleKotlinFormatJar() {
    return getDependencyPath("GOOGLE_KOTLIN_FORMAT", "ktfmt-0.54-jar-with-dependencies.jar");
  }

  public static Path getGsonJar() {
    return getDependencyPath("GSON", "gson-2.10.1.jar");
  }

  public static Path getGsonKeepRules() {
    return getDependencyPath("GSON", "gson.pro");
  }

  public static Path getGuavaJreJar() {
    return getDependencyPath("GUAVA_JRE", "guava-32.1.2-jre.jar");
  }

  public static Path getJacocoAgentJar() {
    return getDependencyPath("JACOCO", "lib", "jacocoagent.jar");
  }

  public static Path getJacocoCliJar() {
    return getDependencyPath("JACOCO", "lib", "jacococli.jar");
  }

  public static Path getJava8RuntimeDir() {
    return getDependency("JAVA_8_RUNTIME");
  }

  public static Path getJava8RuntimeJar() {
    return getDependencyPath("JAVA_8_RUNTIME", "rt.jar");
  }

  public static Path getJavaBaseExtensionJar() {
    return getDependency("JAVA_BASE_EXTENSION");
  }

  public static Path getJdk11TestPath(String... path) {
    return getDependencyPath("JDK11_TEST", path);
  }

  public static Path getJdk21Float16TestPath(String... path) {
    return getDependencyPath("JDK21_FLOAT16_TEST", path);
  }

  public static Path getJsr223RiJar() {
    return getDependencyPath("JSR223", "jsr223-api-1.0.jar");
  }

  public static Path getKeePassDXDir() {
    return getDependency("KEEPASSDX");
  }

  public static Path getKotlinR8TestResourcesPath(String... path) {
    return getDependencyPath("KOTLIN_R8_TEST_RESOURCES", path);
  }

  public static Path getKotlinxCoroutinesPath(String... path) {
    return getDependencyPath("KOTLINX_COROUTINES", path);
  }

  public static Path getMultidex1_0_3Jar() {
    return getDependencyPath("MULTIDEX", "multidex-1.0.3.jar");
  }

  public static Path getMultidexInstrumentation1_0_3Jar() {
    return getDependencyPath("MULTIDEX", "multidex-instrumentation-1.0.3.jar");
  }

  public static Path getMultidex2_0_1Jar() {
    return getDependencyPath("MULTIDEX", "multidex-2.0.1.jar");
  }

  public static Path getMultidexInstrumentation2_0_0Jar() {
    return getDependencyPath("MULTIDEX", "multidex-instrumentation-2.0.0.jar");
  }

  public static Path getNewPipeDir() {
    return getDependency("NEWPIPE");
  }

  public static Path getNowInAndroidDir() {
    return getDependency("NOWINANDROID");
  }

  public static Path getNowInAndroidDumpAppZip() {
    return getDependencyPath("NOWINANDROID", "dump_app.zip");
  }

  public static Path getOmniNotesDir() {
    return getDependency("OMNINOTES");
  }

  public static Path getOpenTracksDir() {
    return getDependency("OPENTRACKS");
  }

  public static Path getProcessKeepRulesBinaryCompatibilityJar() {
    return getDependencyPath("PROCESS_KEEP_RULES_BINARY_COMPATIBILITY", "tests.jar");
  }

  public static Path getProguard7_0_0Dir() {
    return getDependency("PROGUARD_7_0_0");
  }

  public static Path getProguard7_7_0Dir() {
    return getDependency("PROGUARD_7_7_0");
  }

  public static Path getR8Jar() {
    return getDependencyPath("R8", "r8.jar");
  }

  public static Path getR8WithDeps17Jar() {
    return getDependencyPath("R8", "r8_with_deps_17.jar");
  }

  public static Path getR8MappingsPath(String... path) {
    return getDependencyPath("R8_MAPPINGS", path);
  }

  public static Path getR8V2_0_74LibJar() {
    return getDependencyPath("R8_V2_0_74", "r8lib.jar");
  }

  public static Path getR8V3_2_54Jar() {
    return getDependencyPath("R8_V3_2_54", "r8.jar");
  }

  public static Path getR8V8_0_46LibJar() {
    return getDependencyPath("R8_V8_0_46", "r8lib.jar");
  }

  public static Path getReadYouDir() {
    return getDependency("READYOU");
  }

  public static Path getRetraceBenchmarkDir() {
    return getDependency("RETRACE_BENCHMARK");
  }

  public static Path getRetraceBinaryCompatibilityJar() {
    return getDependencyPath("RETRACE_BINARY_COMPATIBILITY", "tests.jar");
  }

  public static Path getRetracePartitionFormatsDir() {
    return getDependency("RETRACE_PARTITION_FORMATS");
  }

  public static Path getRhinoJar() {
    return getDependencyPath("RHINO", "rhino-1.7.10.jar");
  }

  public static Path getRhinoAndroidJar() {
    return getDependencyPath("RHINO_ANDROID", "rhino-android-1.1.1.jar");
  }

  public static Path getSmaliPath(String... path) {
    return getDependencyPath("SMALI", path);
  }

  public static Path getTiviDir() {
    return getDependency("TIVI");
  }

  public static Path getTiviDumpAppZip() {
    return getDependencyPath("TIVI", "dump_app.zip");
  }

  public static Path getTuskyDir() {
    return getDependency("TUSKY");
  }

  public static Path getJunitJar() {
    return getDependencyPath("DEPENDENCIES", "junit", "junit", "4.13.2", "junit-4.13.2.jar");
  }

  public static Path getHamcrestJar() {
    return getDependencyPath(
        "DEPENDENCIES", "org", "hamcrest", "hamcrest-core", "1.3", "hamcrest-core-1.3.jar");
  }

  public static Path getTestNgJar() {
    return getDependencyPath("DEPENDENCIES", "org", "testng", "testng", "6.10", "testng-6.10.jar");
  }

  public static Path getJCommanderJar() {
    return getDependencyPath(
        "DEPENDENCIES", "com", "beust", "jcommander", "1.48", "jcommander-1.48.jar");
  }

  public static Path getJdwpTestsDexJar() {
    return getDependencyPath("JDWP_TESTS", "apache-harmony-jdwp-tests-hostdex.jar");
  }

  public static Path getJdwpTestsJar(AndroidApiLevel apiLevel) {
    Path base = getDependency("JDWP_TESTS");
    if (apiLevel.isLessThan(AndroidApiLevel.N)) {
      return base.resolve("apache-harmony-jdwp-tests-host-preN.jar");
    } else {
      return base.resolve("apache-harmony-jdwp-tests-host.jar");
    }
  }

  private static Path getDependencyPath(String dependency, String... path) {
    return resolveMany(getDependency(dependency), path);
  }

  private static Path resolveMany(Path base, String... extensions) {
    Path result = base;
    for (String extension : extensions) {
      result = result.resolve(extension);
    }
    return result;
  }
}

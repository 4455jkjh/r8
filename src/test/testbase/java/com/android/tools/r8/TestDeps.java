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
    // This list is serves as a list of required properties to match in Gradle.
    dependencies = new HashMap<>();
    dependencies.put("AAPT2", null);
    dependencies.put("BUNDLETOOL", null);
    dependencies.put("COMPILER_API", null);
    dependencies.put("COMPOSE_EXAMPLES_CHANGED_BITWISE_VALUE_PROPAGATION", null);
    dependencies.put("CORE_LAMBDA_STUBS", null);
    dependencies.put("DAGGER", null);
    dependencies.put("DEPENDENCIES", null);
    dependencies.put("DESUGAR_LIBRARY_CONVERSIONS", null);
    dependencies.put("GOOGLE_JAVA_FORMAT", null);
    dependencies.put("GOOGLE_KOTLIN_FORMAT", null);
    dependencies.put("GSON", null);
    dependencies.put("GUAVA_JRE", null);
    dependencies.put("JACOCO", null);
    dependencies.put("JAVA_BASE_EXTENSION", null);
    dependencies.put("JDK21_FLOAT16_TEST", null);
    dependencies.put("JDWP_TESTS", null);
    dependencies.put("JSR223", null);
    dependencies.put("KOTLIN_R8_TEST_RESOURCES", null);
    dependencies.put("KOTLINX_COROUTINES", null);
    dependencies.put("MULTIDEX", null);
    dependencies.put("PROCESS_KEEP_RULES_BINARY_COMPATIBILITY", null);
    dependencies.put("R8_MAPPINGS", null);
    dependencies.put("RETRACE_BINARY_COMPATIBILITY", null);
    dependencies.put("RETRACE_PARTITION_FORMATS", null);
    dependencies.put("RHINO", null);
    dependencies.put("RHINO_ANDROID", null);
    dependencies.put("SMALI", null);
  }

  private static Path getDependency(String key) {
    return dependencies.computeIfAbsent(key, TestDeps::getTestDependency);
  }

  public static Path getAapt2() {
    return getDependencyPath("AAPT2", "aapt2");
  }

  public static Path getBundleToolJar() {
    return getDependencyPath("BUNDLETOOL", "bundletool-all-1.11.0.jar");
  }

  public static Path getCompilerApiBinaryCompatibilityJar() {
    return getDependencyPath("COMPILER_API", "tests.jar");
  }

  public static Path getComposeExamplesChangedBitwiseValuePropagationDumpZip() {
    return getDependencyPath("COMPOSE_EXAMPLES_CHANGED_BITWISE_VALUE_PROPAGATION", "dump.zip");
  }

  public static Path getCoreLambdaStubsJar() {
    return getDependencyPath("CORE_LAMBDA_STUBS", "core-lambda-stubs.jar");
  }

  public static Path getDaggerPath(String... path) {
    return getDependencyPath("DAGGER", path);
  }

  public static Path getDesugarLibraryConversionsDir() {
    return getDependency("DESUGAR_LIBRARY_CONVERSIONS");
  }

  public static Path getDesugarLibraryConversions(CustomConversionVersion version) {
    return getDependencyPath("DESUGAR_LIBRARY_CONVERSIONS", version.getFileName());
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

  public static Path getJavaBaseExtensionJar() {
    return getDependency("JAVA_BASE_EXTENSION");
  }

  public static Path getJdk21Float16TestPath(String... path) {
    return getDependencyPath("JDK21_FLOAT16_TEST", path);
  }

  public static Path getJsr223RiJar() {
    return getDependencyPath("JSR223", "jsr223-api-1.0.jar");
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

  public static Path getProcessKeepRulesBinaryCompatibilityJar() {
    return getDependencyPath("PROCESS_KEEP_RULES_BINARY_COMPATIBILITY", "tests.jar");
  }

  public static Path getR8MappingsDir() {
    return getDependency("R8_MAPPINGS");
  }

  public static Path getR8MappingsPath(String... path) {
    return getDependencyPath("R8_MAPPINGS", path);
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

  public static Path getJunitJar() {
    return getDependencyPath("DEPENDENCIES", "junit", "junit", "4.13.2", "junit-4.13.2.jar");
  }

  public static Path getHamcrestJar() {
    return getDependencyPath(
        "DEPENDENCIES", "org", "hamcrest", "hamcrest-core", "1.3", "hamcrest-core-1.3.jar");
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

// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.coroutines;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_3_72;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.kotlin.metadata.KotlinMetadataTestBase;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class KotlinxCoroutinesTestRunner extends KotlinMetadataTestBase {

  private static Path getBaseLibrary() {
    return TestDeps.getKotlinxCoroutinesPath("deps_all-1.3.6-SNAPSHOT.jar");
  }

  private static Path getTestSources() {
    return TestDeps.getKotlinxCoroutinesPath("kotlinx-coroutines-test-test-sources");
  }

  private static List<Path> getDependencies() {
    return ImmutableList.of(
        TestDeps.getKotlinxCoroutinesPath("atomicfu-0.14.3.jar"),
        TestDeps.getKotlinxCoroutinesPath("hamcrest-core-1.3.jar"),
        TestDeps.getKotlinxCoroutinesPath("junit-4.13.jar"),
        TestDeps.getKotlinxCoroutinesPath("kotlin-test-1.3.72.jar"),
        TestDeps.getKotlinxCoroutinesPath("kotlin-test-junit-1.3.71.jar"),
        TestDeps.getKotlinxCoroutinesPath("kotlinx.coroutines.testbase.jar"),
        TestDeps.getKotlinxCoroutinesPath("kotlinx.coroutines.test.main.jar"));
  }

  // Tests that do not run correctly in general - that is these tests are expected to fail always.
  private Set<String> notWorkingTests =
      Sets.newHashSet("kotlinx.coroutines.test.TestDispatchersTest");

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndLambdaGenerations().build());
  }

  private final TestParameters parameters;

  public KotlinxCoroutinesTestRunner(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void runKotlinxCoroutinesTests_smoke() throws Exception {
    // TODO(b/179860018): Make run for 1.4.20
    assumeTrue(kotlinc.is(KOTLINC_1_3_72));
    Path baseLibrary = getBaseLibrary();
    runTestsInJar(compileTestSources(baseLibrary), baseLibrary);
  }

  @Test
  public void runKotlinxCoroutinesTests_r8() throws Exception {
    // TODO(b/179860018): Make run for 1.4.20
    assumeTrue(kotlinc.is(KOTLINC_1_3_72));
    Path baseLibrary = getBaseLibrary();
    Path baseJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(baseLibrary)
            .addKeepAllClassesRule()
            .addKeepAllAttributes()
            // The BASE_LIBRARY contains proguard rules that do not match.
            .allowUnusedProguardConfigurationRules()
            .addDontWarn(
                "edu.umd.cs.findbugs.annotations.SuppressFBWarnings",
                "reactor.blockhound.BlockHound$Builder",
                "reactor.blockhound.integration.BlockHoundIntegration",
                "org.junit.rules.TestRule",
                "org.junit.runner.Description",
                "org.junit.runners.model.Statement",
                "org.junit.runners.model.TestTimedOutException")
            .compile()
            .inspect(
                inspector ->
                    assertEqualMetadataWithStringPoolValidation(
                        new CodeInspector(baseLibrary),
                        inspector,
                        (addedStrings, addedNonInitStrings) -> {}))
            .writeToZip();
    Path testJar = compileTestSources(baseJar);
    runTestsInJar(testJar, baseJar);
  }

  private Path compileTestSources(Path baseJar) throws Exception {
    return kotlinc(kotlinc, targetVersion, lambdaGeneration)
        .addArguments(
            "-Xuse-experimental=kotlinx.coroutines.InternalCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.ObsoleteCoroutinesApi",
            "-Xuse-experimental=kotlinx.coroutines.ExperimentalCoroutinesApi")
        .addClasspathFiles(getDependencies())
        .addClasspathFiles(baseJar)
        .addSourceFiles(getTestSources())
        .compile();
  }

  private void runTestsInJar(Path testJar, Path baseJar) throws Exception {
    List<Path> dependencies = new ArrayList<>(getDependencies());
    dependencies.add(baseJar);
    dependencies.add(testJar);
    ZipUtils.iter(
        testJar.toString(),
        (entry, input) -> {
          if (!entry.isDirectory() && entry.getName().endsWith("Test.class")) {
            runTest(dependencies, entry.getName());
          }
        });
  }

  private void runTest(List<Path> dependencies, String name) throws IOException {
    String testName = name.replace("/", ".").replace(".class", "");
    if (notWorkingTests.contains(testName)) {
      return;
    }
    ProcessResult processResult =
        ToolHelper.runJava(
            parameters.getRuntime().asCf(), dependencies, "org.junit.runner.JUnitCore", testName);
    assertEquals(0, processResult.exitCode);
  }
}

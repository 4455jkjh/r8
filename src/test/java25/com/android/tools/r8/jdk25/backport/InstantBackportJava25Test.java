// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jdk25.backport;

import static com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification.SPECIFICATIONS_WITH_CF2CF;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.CompilationSpecification;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.time.Instant;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstantBackportJava25Test extends DesugaredLibraryTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public LibraryDesugaringSpecification libraryDesugaringSpecification;

  @Parameter(2)
  public CompilationSpecification compilationSpecification;

  @Parameters(name = "{0}, spec: {1}, {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withDexRuntimes()
            .withCfRuntime(CfVm.JDK25)
            .withAllApiLevelsAlsoForCf()
            .build(),
        ImmutableList.of(JDK8, JDK11, JDK11_PATH),
        SPECIFICATIONS_WITH_CF2CF);
  }

  private static final String EXPECTED_OUTPUT = StringUtils.lines("PT8765837268672H");

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputIf(
            parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V8_1_0), EXPECTED_OUTPUT)
        .assertFailureWithErrorThatThrowsIf(
            !parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V8_1_0),
            NoClassDefFoundError.class);
  }

  @Test
  public void testD8Cf() throws Exception {
    parameters.assumeCfRuntime();
    testForD8(Backend.CF)
        .addInnerClassesAndStrippedOuter(getClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(opt -> opt.ignoreMissingClasses = true)
        .allowDiagnosticWarningMessages(parameters.getApiLevel().isLessThan(AndroidApiLevel.O))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputIf(
            parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V8_1_0), EXPECTED_OUTPUT)
        .assertFailureWithErrorThatThrowsIf(
            !parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V8_1_0),
            NoClassDefFoundError.class);
  }

  @Test
  public void testWithDesugaredLibrary() throws Exception {
    parameters.assumeDexRuntime();
    // With desugared library, Instant is available at all api levels.
    testForDesugaredLibrary(parameters, libraryDesugaringSpecification, compilationSpecification)
        .addInnerClassesAndStrippedOuter(getClass())
        .addKeepMainRule(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  public static class TestClass {

    public static void main(String[] args) {
      System.out.println(Instant.MIN.until(Instant.EPOCH));
    }
  }
}

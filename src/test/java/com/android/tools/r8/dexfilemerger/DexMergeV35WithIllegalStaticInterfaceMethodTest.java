// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexfilemerger;

import static com.android.tools.r8.ToolHelper.DexVm.Version.V7_0_0;
import static org.junit.Assert.assertThrows;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.UnsupportedDefaultInterfaceMethodDiagnostic;
import com.android.tools.r8.errors.UnsupportedStaticInterfaceMethodDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

// Reproduction of b/507323858.
@RunWith(Parameterized.class)
public class DexMergeV35WithIllegalStaticInterfaceMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withNoneRuntime()
        .withDexRuntimesStartingFromIncluding(V7_0_0)
        .build();
  }

  private Path fakeDexV35(Path dex) throws IOException {
    Path dex35 = temp.newFile("dex35.dex").toPath();
    byte[] dexBytes = Files.readAllBytes(dex);
    dexBytes[6] = 0x35;
    Files.write(dex35, dexBytes, StandardOpenOption.CREATE);
    return dex35;
  }

  @Test
  public void testStaticMethod() throws Exception {
    assumeTrue(parameters.isNoneRuntime());
    // Create DEX v37 with an interface with a static method.
    Path dexDir =
        testForD8()
            .setMinApi(AndroidApiLevel.N)
            .addProgramClasses(I1.class)
            .compile()
            .writeToDirectory();

    // Run v35 DEX with an interface with a static method through DEX merging.
    Path dex35 = fakeDexV35(dexDir.resolve("classes.dex"));
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(Backend.DEX)
                .addProgramFiles(dex35)
                .setMinApi(AndroidApiLevel.K)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics
                            .assertOnlyErrors()
                            .assertAllErrorsMatch(
                                DiagnosticsMatcher.diagnosticType(
                                    UnsupportedStaticInterfaceMethodDiagnostic.class))));
  }

  @Test
  public void testDefaultMethod() throws Exception {
    assumeTrue(parameters.isNoneRuntime());
    // Create DEX v37 with an interface with a default method.
    Path dexDir =
        testForD8()
            .setMinApi(AndroidApiLevel.N)
            .addProgramClasses(I2.class)
            .compile()
            .writeToDirectory();

    // Run v35 DEX with an interface with a default method through DEX merging.
    Path dex35 = fakeDexV35(dexDir.resolve("classes.dex"));
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForD8(Backend.DEX)
                .addProgramFiles(dex35)
                .setMinApi(AndroidApiLevel.K)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics
                            .assertOnlyErrors()
                            .assertAllErrorsMatch(
                                DiagnosticsMatcher.diagnosticType(
                                    UnsupportedDefaultInterfaceMethodDiagnostic.class))));
  }

  @Test
  public void testAllowV35WithStaticAndDefaultInterfaceMethodsWhenCompilingToV37()
      throws Exception {
    assumeFalse(parameters.isNoneRuntime());
    // Create DEX v37 with an interface with a static method and an interface with a default method.
    Path dexDir =
        testForD8()
            .setMinApi(AndroidApiLevel.N)
            .addInnerClasses(getClass())
            .compile()
            .writeToDirectory();

    // Run the v35 DEX with an interface with a static method and an interface with a default method
    // through DEX merging.
    Path dex35 = fakeDexV35(dexDir.resolve("classes.dex"));
    testForD8(Backend.DEX)
        .addProgramFiles(dex35)
        .setMinApi(AndroidApiLevel.N)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  interface I1 {
    static String f() {
      return "Hello,";
    }
  }

  interface I2 {
    default String f() {
      return " world!";
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(I1.f() + (new I2() {}).f());
    }
  }
}

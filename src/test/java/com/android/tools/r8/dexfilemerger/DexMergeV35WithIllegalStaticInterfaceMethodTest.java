// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dexfilemerger;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsMatcher;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// Reproduction of b/507323858.
@RunWith(Parameterized.class)
public class DexMergeV35WithIllegalStaticInterfaceMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DexMergeV35WithIllegalStaticInterfaceMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // Create DEX v37 with an interface with a static method and class initializer.
    Path dexDir =
        testForD8()
            .setMinApi(AndroidApiLevel.N)
            .addInnerClasses(getClass())
            .compile()
            .writeToDirectory();

    // Fake DEX v35 for the v37 DEX
    Path dex35 = temp.newFile("dex35.dex").toPath();
    byte[] dexBytes = Files.readAllBytes(dexDir.resolve("classes.dex"));
    dexBytes[6] = 0x35;
    Files.write(dex35, dexBytes, StandardOpenOption.CREATE);

    // Run the v35 DEX with an interface with a static method and class initializer through DEX
    // merging.
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
                                DiagnosticsMatcher.diagnosticException(
                                    NullPointerException.class))));
  }

  interface I {
    Object o = new Object();

    static String f() {
      return "Hello, world!";
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(I.f());
    }
  }
}

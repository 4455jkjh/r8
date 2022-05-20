// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.stackmap;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvalidStackHeightTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"42"};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Test
  public void smokeTest() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test(expected = CompilationFailedException.class)
  public void testD8Cf() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getMainWithChangedMaxStackHeight())
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(this::inspect);
  }

  @Test
  public void testD8Dex() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8(parameters.getBackend())
        .addProgramClassFileData(getMainWithChangedMaxStackHeight())
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(getMainWithChangedMaxStackHeight())
        .enableInliningAnnotations()
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(this::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private void inspect(TestDiagnosticMessages diagnostics) {
    diagnostics.assertWarningMessageThatMatches(
        containsString("The max stack height of 1 is violated"));
  }

  @Test()
  public void testR8InputVerification() throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addProgramClassFileData(getMainWithChangedMaxStackHeight())
          .enableInliningAnnotations()
          .addKeepMainRule(Main.class)
          .setMinApi(parameters.getApiLevel())
          .addOptionsModification(options -> options.testing.verifyInputs = true)
          .compile();
    } catch (CompilationFailedException e) {
      assertTrue(e.getCause().getMessage().contains("Insufficient maximum stack size"));
      return;
    }
    fail("Should always throw");
  }

  public byte[] getMainWithChangedMaxStackHeight() throws Exception {
    return transformer(Main.class).setMaxStackHeight(MethodPredicate.onName("main"), 1).transform();
  }

  public static class Main {

    @NeverInline
    private void test(int x, int y) {
      System.out.println(x + y);
    }

    public static void main(String[] args) {
      new Main().test(args.length, 42);
    }
  }
}

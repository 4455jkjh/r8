// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerIndirectReflectiveNameTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public VerticalClassMergerIndirectReflectiveNameTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, A.class, B.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A::foo", "B::foo");
  }

  @Test
  public void testR8() throws Exception {
    // TODO(b/173099479): This should not throw an assertion-error.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addProgramClasses(Main.class, A.class, B.class)
                .addKeepMainRule(Main.class)
                .setMinApi(parameters.getApiLevel())
                .enableInliningAnnotations()
                .enableNeverClassInliningAnnotations()
                .compileWithExpectedDiagnostics(
                    diagnostics -> {
                      diagnostics.assertErrorsMatch(
                          diagnosticMessage(
                              containsString(
                                  "Expected vertically merged class"
                                      + " `com.android.tools.r8.classmerging.vertical."
                                      + "VerticalClassMergerIndirectReflectiveNameTest$A`"
                                      + " to be absent")));
                    }));
  }

  public static class A {

    @NeverInline
    public void foo() {
      System.out.println("A::foo");
    }
  }

  @NeverClassInline
  public static class B extends A {

    @NeverInline
    public void bar() {
      System.out.println("B::foo");
    }
  }

  public static class Main {

    public static String getClassName() {
      return "com.android.tools.r8.classmerging.vertical."
          + "VerticalClassMergerIndirectReflectiveNameTest$A";
    }

    static {
      try {
        Class.forName(getClassName());
      } catch (ClassNotFoundException e) {
      }
    }

    public static void main(String[] args) {
      B b = new B();
      b.foo();
      b.bar();
    }
  }
}

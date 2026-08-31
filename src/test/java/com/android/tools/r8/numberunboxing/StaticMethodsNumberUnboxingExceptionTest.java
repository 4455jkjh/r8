// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.numberunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticMethodsNumberUnboxingExceptionTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StaticMethodsNumberUnboxingExceptionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNumberUnboxing() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addOptionsModification(opt -> opt.getTestingOptions().getNumberUnboxerOptions().enable())
        .compile()
        .inspect(this::assertUnboxing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("32", "33", "34", "42", "43", "44", "102", "caught");
  }

  private void assertFirstParameterUnboxed(ClassSubject mainClass, String methodName) {
    assertParameterUnboxed(mainClass, methodName, 0);
  }

  private void assertParameterUnboxed(ClassSubject mainClass, String methodName, int index) {
    MethodSubject methodSubject = mainClass.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getParameter(index).isIntType());
  }

  private void assertReturnUnboxed(ClassSubject mainClass, String methodName) {
    MethodSubject methodSubject = mainClass.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getReturnType().isIntType());
  }

  private void assertUnboxing(CodeInspector codeInspector) {
    ClassSubject mainClass = codeInspector.clazz(Main.class);
    assertThat(mainClass, isPresent());

    assertFirstParameterUnboxed(mainClass, "directPrintUnbox");
    assertFirstParameterUnboxed(mainClass, "forwardToPrint");
    assertFirstParameterUnboxed(mainClass, "getI");
    assertFirstParameterUnboxed(mainClass, "mixToPrint");
    assertParameterUnboxed(mainClass, "mixToPrint", 1);

    assertReturnUnboxed(mainClass, "get");
    assertReturnUnboxed(mainClass, "getI");
  }

  static class Main {

    static class Ex extends Exception {}

    public static void main(String[] args) {
      try {
        directPrintUnbox(31);
        directPrintUnbox(32);
        directPrintUnbox(getI(33));

        forwardToPrint(41);
        forwardToPrint(42);
        forwardToPrint(getI(43));

        mixToPrint(50, 51);
        mixToPrint(53, 54);

        // The number unboxer should unbox the return values.
        System.out.println(get() + 1);
      } catch (Ex ex) {
        System.out.println("caught");
      }
    }

    @NeverInline
    private static Integer get() {
      return System.currentTimeMillis() > 0 ? 100 : -1;
    }

    @NeverInline
    private static Integer getI(Integer i) {
      return System.currentTimeMillis() > 0 ? i : -1;
    }

    @NeverInline
    private static void forwardToPrint(Integer boxed) throws Ex {
      directPrintUnbox(boxed);
    }

    @NeverInline
    private static void mixToPrint(Integer boxed, Integer boxed2) throws Ex {
      directPrintUnbox(boxed + boxed2);
    }

    @NeverInline
    private static void directPrintUnbox(Integer boxed) throws Ex {
      System.out.println(boxed + 1);
      if (boxed > 100) {
        throw new Ex();
      }
    }
  }
}

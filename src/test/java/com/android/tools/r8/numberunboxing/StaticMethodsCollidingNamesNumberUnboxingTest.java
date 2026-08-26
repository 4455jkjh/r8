// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.numberunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticMethodsCollidingNamesNumberUnboxingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StaticMethodsCollidingNamesNumberUnboxingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNumberUnboxing() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addNoParameterTypeStrengtheningAnnotation()
        .addOptionsModification(opt -> opt.getTestingOptions().getNumberUnboxerOptions().enable())
        .compile()
        .inspect(this::assertUnboxing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("32", "33", "34", "35", "42", "43", "42", "43");
  }

  private void assertUnboxing(CodeInspector codeInspector) {
    ClassSubject mainClass = codeInspector.clazz(Main.class);
    assertThat(mainClass, isPresent());
    assertEquals(5, mainClass.allMethods().size());
    mainClass
        .allMethods()
        .forEach(
            m -> {
              if (!m.getOriginalMethodName().equals("main")) {
                assertEquals(1, m.getParameters().size());
                assertTrue(m.getProgramMethod().getParameter(0).isIntType());
              }
            });
  }

  static class Main {

    public static void main(String[] args) {
      directPrintUnbox(31);
      directPrintUnbox(32);
      directPrintUnbox(33, 0);
      directPrintUnbox(34, 1);

      forwardToPrint(41);
      forwardToPrint(42);
      forwardToPrint(41, 0);
      forwardToPrint(42, 1);
    }

    @NeverInline
    private static void forwardToPrint(Integer boxed) {
      directPrintUnbox(boxed);
    }

    // Final proto should overlap with method above.
    @NoParameterTypeStrengthening
    @NeverInline
    private static void forwardToPrint(int boxed, int unused) {
      directPrintUnbox(boxed, unused);
    }

    @NeverInline
    private static void directPrintUnbox(Integer boxed) {
      System.out.println(boxed + 1);
    }

    // Final proto should overlap with method above.
    @NoParameterTypeStrengthening
    @NeverInline
    private static void directPrintUnbox(int boxed, int unused) {
      System.out.println(boxed + 1);
    }
  }
}

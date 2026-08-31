// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.numberunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
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
public class StaticMethodsEnumNumberUnboxingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public StaticMethodsEnumNumberUnboxingTest(TestParameters parameters) {
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
        .assertSuccessWithOutputLines(
            "33", "37", "41", "45", "333", "337", "341", "345", "33", "37", "41", "45", "333",
            "337", "341", "345");
  }

  private void assertUnboxing(CodeInspector codeInspector) {
    ClassSubject mainClass = codeInspector.clazz(Main.class);
    assertThat(mainClass, isPresent());
    mainClass
        .allMethods()
        .forEach(
            m -> {
              if (!m.getOriginalMethodName().equals("main")) {
                assertEquals(4, m.getParameters().size());
                for (int i = 0; i < 4; i++) {
                  assertTrue(m.getProgramMethod().getParameter(i).isIntType());
                }
              }
            });
  }

  static class Main {

    enum E {
      A,
      B,
      C,
      D,
      E
    }

    public static void main(String[] args) {
      directPrintUnbox(E.A, 10, E.B, 20);
      directPrintUnbox(E.B, 11, -1, E.C, 21);
      directPrintUnbox(E.C, 12, E.D, 22);
      directPrintUnbox(E.D, 13, -2, E.E, 23);

      forwardToPrint(E.A, 110, E.B, 220);
      forwardToPrint(E.B, 111, -1, E.C, 221);
      forwardToPrint(E.C, 112, E.D, 222);
      forwardToPrint(E.D, 113, -2, E.E, 223);

      Main main = new Main();

      main.virtualDirectPrintUnbox(E.A, 10, E.B, 20);
      main.virtualDirectPrintUnbox(E.B, 11, -1, E.C, 21);
      main.virtualDirectPrintUnbox(E.C, 12, E.D, 22);
      main.virtualDirectPrintUnbox(E.D, 13, -2, E.E, 23);

      main.virtualForwardToPrint(E.A, 110, E.B, 220);
      main.virtualForwardToPrint(E.B, 111, -1, E.C, 221);
      main.virtualForwardToPrint(E.C, 112, E.D, 222);
      main.virtualForwardToPrint(E.D, 113, -2, E.E, 223);
    }

    @NeverInline
    private static void forwardToPrint(E e, Integer boxed, E e2, Integer boxed2) {
      directPrintUnbox(e, boxed, e2, boxed2);
    }

    // Final proto should overlap with method above.
    @NeverInline
    private static void forwardToPrint(E e, Integer boxed, int unused, E e2, Integer boxed2) {
      directPrintUnbox(e, boxed, unused, e2, boxed2);
    }

    @NeverInline
    private static void directPrintUnbox(E e, Integer boxed, E e2, Integer boxed2) {
      System.out.println(e.ordinal() + (boxed + 1) + e2.ordinal() + (boxed2 + 1));
    }

    // Final proto should overlap with method above.
    @NeverInline
    private static void directPrintUnbox(E e, Integer boxed, int unused, E e2, Integer boxed2) {
      System.out.println(e.ordinal() + (boxed + 1) + e2.ordinal() + (boxed2 + 1));
    }

    @NeverInline
    private void virtualForwardToPrint(E e, Integer boxed, E e2, Integer boxed2) {
      directPrintUnbox(e, boxed, e2, boxed2);
    }

    // Final proto should overlap with method above.
    @NeverInline
    private void virtualForwardToPrint(E e, Integer boxed, int unused, E e2, Integer boxed2) {
      directPrintUnbox(e, boxed, unused, e2, boxed2);
    }

    @NeverInline
    private void virtualDirectPrintUnbox(E e, Integer boxed, E e2, Integer boxed2) {
      System.out.println(e.ordinal() + (boxed + 1) + e2.ordinal() + (boxed2 + 1));
    }

    // Final proto should overlap with method above.
    @NeverInline
    private void virtualDirectPrintUnbox(E e, Integer boxed, int unused, E e2, Integer boxed2) {
      System.out.println(e.ordinal() + (boxed + 1) + e2.ordinal() + (boxed2 + 1));
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexFormat21t;
import com.android.tools.r8.dex.code.DexFormat22t;
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexGoto16;
import com.android.tools.r8.dex.code.DexGoto32;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexSwitchPayload;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests that each loop has a single backward branch, which is a goto. */
@RunWith(Parameterized.class)
public class LoopBackEdgeCanonicalizerTest extends TestBase {

  private static final String[] EXPECTED_OUTPUT = new String[] {"30", "6", "5", "1", "4", "3"};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8(parameters)
        .addInnerClasses(getClass())
        .debug()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertEquals(1, countBackwardGotos(testClassSubject, "doWhile"));
    assertEquals(1, countBackwardGotos(testClassSubject, "forLoop"));
    assertEquals(1, countBackwardGotos(testClassSubject, "withContinue"));
    assertEquals(1, countBackwardGotos(testClassSubject, "switchLoop"));
    assertEquals(2, countBackwardGotos(testClassSubject, "nested"));
    assertEquals(1, countBackwardGotos(testClassSubject, "tryCatchInLoop"));
  }

  // Returns the number of backward gotos and checks that all other branches are forward branches.
  static int countBackwardGotos(ClassSubject classSubject, String methodName) {
    MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    int backwardGotos = 0;
    for (DexInstruction instruction :
        methodSubject.getMethod().getCode().asDexCode().instructions) {
      if (instruction instanceof DexGoto
          || instruction instanceof DexGoto16
          || instruction instanceof DexGoto32) {
        if (instruction.getTargets()[0] <= 0) {
          backwardGotos++;
        }
      } else if (instruction instanceof DexFormat21t || instruction instanceof DexFormat22t) {
        assertTrue(instruction.toString(), instruction.getTargets()[0] > 0);
      } else if (instruction instanceof DexSwitchPayload) {
        for (int target : ((DexSwitchPayload) instruction).switchTargetOffsets()) {
          assertTrue(instruction.toString(), target > 0);
        }
      }
    }
    return backwardGotos;
  }

  static class TestClass {

    @NeverInline
    static int doWhile(int n) {
      int i = 0;
      int sum = 0;
      do {
        sum += i * i;
        i++;
      } while (i < n);
      return sum;
    }

    @NeverInline
    static int forLoop(int[] a) {
      int sum = 0;
      for (int i = 0; i < a.length; i++) {
        sum += a[i];
      }
      return sum;
    }

    @NeverInline
    static int withContinue(int[] a) {
      int sum = 0;
      for (int i = 0; i < a.length; i++) {
        if (a[i] < 0) {
          continue;
        }
        if (a[i] > 100) {
          sum -= 1;
          continue;
        }
        sum += a[i];
      }
      return sum;
    }

    @NeverInline
    static int switchLoop(int[] ops) {
      int acc = 0;
      int pc = 0;
      while (pc < ops.length) {
        switch (ops[pc]) {
          case 0:
            acc++;
            pc++;
            continue;
          case 1:
            acc--;
            pc++;
            continue;
          case 2:
            acc *= 2;
            pc += 2;
            continue;
          default:
            return acc;
        }
      }
      return acc;
    }

    @NeverInline
    static int nested(int[][] m) {
      int sum = 0;
      for (int i = 0; i < m.length; i++) {
        for (int j = 0; j < m[i].length; j++) {
          if (m[i][j] == 0) {
            continue;
          }
          sum += m[i][j];
        }
      }
      return sum;
    }

    @NeverInline
    static int tryCatchInLoop(String[] numbers) {
      int count = 0;
      for (String number : numbers) {
        try {
          count += Integer.parseInt(number);
        } catch (NumberFormatException e) {
          count--;
        }
      }
      return count;
    }

    public static void main(String[] args) {
      System.out.println(doWhile(5));
      System.out.println(forLoop(new int[] {1, 2, 3}));
      System.out.println(withContinue(new int[] {-1, 200, 6}));
      System.out.println(switchLoop(new int[] {0, 0, 1, 3}));
      System.out.println(nested(new int[][] {{1, 0}, {3}}));
      System.out.println(tryCatchInLoop(new String[] {"1", "x", "3"}));
    }
  }
}

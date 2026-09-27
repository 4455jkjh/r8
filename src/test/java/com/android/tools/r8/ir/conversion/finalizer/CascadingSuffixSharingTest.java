// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexAddInt;
import com.android.tools.r8.dex.code.DexAddInt2Addr;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexMulInt;
import com.android.tools.r8.dex.code.DexMulInt2Addr;
import com.android.tools.r8.dex.code.DexSubInt;
import com.android.tools.r8.dex.code.DexSubInt2Addr;
import com.android.tools.r8.dex.code.DexXorInt;
import com.android.tools.r8.dex.code.DexXorInt2Addr;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CascadingSuffixSharingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("8", "20", "31", "223");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("8", "20", "31", "223");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    MethodSubject methodSubject = testClassSubject.uniqueMethodWithOriginalName("runCascade");
    assertTrue(methodSubject.isPresent());
    DexCode dexCode = methodSubject.getMethod().getCode().asDexCode();
    int addCount = 0;
    int mulCount = 0;
    int xorCount = 0;
    int subCount = 0;
    for (DexInstruction instruction : dexCode.instructions) {
      if (instruction instanceof DexAddInt || instruction instanceof DexAddInt2Addr) {
        addCount++;
      } else if (instruction instanceof DexMulInt || instruction instanceof DexMulInt2Addr) {
        mulCount++;
      } else if (instruction instanceof DexXorInt || instruction instanceof DexXorInt2Addr) {
        xorCount++;
      } else if (instruction instanceof DexSubInt || instruction instanceof DexSubInt2Addr) {
        subCount++;
      }
    }
    assertEquals(1, addCount);
    assertEquals(1, mulCount);
    assertEquals(1, xorCount);
    assertEquals(1, subCount);
  }

  static class TestClass {

    @NeverInline
    static int b1(int a) {
      return a > 6 ? a + 10 : a + 1;
    }

    @NeverInline
    static int b2(int a) {
      return a > 6 ? a + 20 : a + 2;
    }

    @NeverInline
    static int b3(int a) {
      return a > 6 ? a + 30 : a + 3;
    }

    @NeverInline
    static int b4(int a) {
      return a > 6 ? a + 40 : a + 4;
    }

    @NeverInline
    static int runCascade(boolean c1, boolean c2, boolean c3, int a, int b, int c) {
      int x;
      if (c1) {
        if (c2) {
          if (c3) {
            x = b1(a);
            x += a;
          } else {
            x = b2(a);
          }
          x *= b;
        } else {
          x = b3(a);
        }
        x ^= c;
        x -= a;
      } else {
        x = b4(a);
        x += a;
        x *= b;
        x ^= c;
        x -= a;
      }
      return x;
    }

    public static void main(String[] args) {
      System.out.println(runCascade(true, true, true, 2, 3, 5));
      System.out.println(runCascade(true, true, false, 4, 5, 6));
      System.out.println(runCascade(true, false, false, 7, 2, 3));
      System.out.println(runCascade(false, false, false, 8, 4, 7));
    }
  }
}

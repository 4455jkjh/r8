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
public class CascadingPrefixSharingTest extends TestBase {

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
        .assertSuccessWithOutputLines("18", "30", "42", "54");
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
        .assertSuccessWithOutputLines("18", "30", "42", "54");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    MethodSubject methodSubject =
        testClassSubject.uniqueMethodWithOriginalName("computeWithCascadingSharedPrefix");
    assertTrue(methodSubject.isPresent());
    DexCode dexCode = methodSubject.getMethod().getCode().asDexCode();
    int addCount = 0;
    for (DexInstruction instruction : dexCode.instructions) {
      if (instruction instanceof DexAddInt || instruction instanceof DexAddInt2Addr) {
        addCount++;
      }
    }
    assertEquals(1, addCount);
  }

  static class TestClass {

    @NeverInline
    static int combine(int result, int extra) {
      return result + extra;
    }

    @NeverInline
    static int computeWithCascadingSharedPrefix(
        boolean c1, boolean c2, boolean c3, int first, int second) {
      int result;
      int extra;
      if (c1) {
        if (c2) {
          if (c3) {
            result = first + second;
            extra = 10;
          } else {
            result = first + second;
            extra = 20;
          }
        } else {
          result = first + second;
          extra = 30;
        }
      } else {
        result = first + second;
        extra = 40;
      }
      return combine(result, extra);
    }

    public static void main(String[] args) {
      System.out.println(computeWithCascadingSharedPrefix(true, true, true, 5, 3));
      System.out.println(computeWithCascadingSharedPrefix(true, true, false, 6, 4));
      System.out.println(computeWithCascadingSharedPrefix(true, false, false, 7, 5));
      System.out.println(computeWithCascadingSharedPrefix(false, false, false, 8, 6));
    }
  }
}

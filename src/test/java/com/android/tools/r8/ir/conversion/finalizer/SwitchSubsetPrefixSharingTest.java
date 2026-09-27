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
public class SwitchSubsetPrefixSharingTest extends TestBase {

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
        .assertSuccessWithOutputLines("18", "31", "44", "98");
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
        .assertSuccessWithOutputLines("18", "31", "44", "98");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    MethodSubject methodSubject =
        testClassSubject.uniqueMethodWithOriginalName("computeWithSubsetSharedPrefix");
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
    static int combine(int key, int result, int extra) {
      return key + result + extra;
    }

    @NeverInline
    static int computeWithSubsetSharedPrefix(int key, int first, int second) {
      int result;
      int extra;
      switch (key) {
        case 0:
          result = first + second;
          extra = 10;
          break;
        case 1:
          result = first + second;
          extra = 20;
          break;
        case 2:
          result = first + second;
          extra = 30;
          break;
        case 3:
          result = first + second;
          extra = 40;
          break;
        case 4:
          result = first + second;
          extra = 50;
          break;
        case 5:
          result = first + second;
          extra = 60;
          break;
        case 6:
          result = first + second;
          extra = 70;
          break;
        case 7:
          result = first + second;
          extra = 80;
          break;
        default:
          result = 0;
          extra = 90;
          break;
      }
      return combine(key, result, extra);
    }

    public static void main(String[] args) {
      System.out.println(computeWithSubsetSharedPrefix(0, 5, 3));
      System.out.println(computeWithSubsetSharedPrefix(1, 6, 4));
      System.out.println(computeWithSubsetSharedPrefix(2, 7, 5));
      System.out.println(computeWithSubsetSharedPrefix(8, 8, 6));
    }
  }
}

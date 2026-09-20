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
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BranchDiamondInverterTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testBranchDiamondInversionEliminatesFallthroughGoto() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject testClassSubject = inspector.clazz(TestClass.class);
              MethodSubject methodSubject =
                  testClassSubject.uniqueMethodWithOriginalName("computeWithSharedFallback");
              assertTrue(methodSubject.isPresent());
              DexCode dexCode = methodSubject.getMethod().getCode().asDexCode();
              int gotoCount = 0;
              for (DexInstruction instruction : dexCode.instructions) {
                if (instruction instanceof DexGoto) {
                  gotoCount++;
                }
              }
              assertEquals(0, gotoCount);
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("15", "-1", "-1");
  }

  static class TestClass {

    @NeverInline
    static int step(int value) {
      return value * 3;
    }

    @NeverInline
    static int computeWithSharedFallback(int first, int second) {
      if (first > 0) {
        if (second > 0) {
          return step(first + second);
        }
      }
      return -1;
    }

    public static void main(String[] args) {
      System.out.println(computeWithSharedFallback(2, 3));
      System.out.println(computeWithSharedFallback(2, -1));
      System.out.println(computeWithSharedFallback(-1, 3));
    }
  }
}

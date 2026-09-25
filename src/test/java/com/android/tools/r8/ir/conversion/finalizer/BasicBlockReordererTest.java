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
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BasicBlockReordererTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testTryRangeCoalescingAndFallthroughOrdering() throws Exception {
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
                  testClassSubject.uniqueMethodWithOriginalName("computeInSharedTryCatch");
              assertTrue(methodSubject.isPresent());
              DexCode dexCode = methodSubject.getMethod().getCode().asDexCode();
              assertEquals(1, dexCode.getTries().length);
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("7", "3", "-1");
  }

  static class TestClass {

    @NeverInline
    static int throwingStep(int value) {
      if (value == 0) {
        throw new RuntimeException("zero");
      }
      return value + 1;
    }

    @NeverInline
    static int computeInSharedTryCatch(int firstInput, int secondInput) {
      int total = 0;
      try {
        if (firstInput > 0) {
          total += throwingStep(firstInput);
        } else {
          total += throwingStep(-firstInput);
        }
        if (secondInput > 0) {
          total += throwingStep(secondInput);
        }
      } catch (RuntimeException exception) {
        return -1;
      }
      return total;
    }

    public static void main(String[] args) {
      System.out.println(computeInSharedTryCatch(2, 3));
      System.out.println(computeInSharedTryCatch(-2, 0));
      System.out.println(computeInSharedTryCatch(0, 1));
    }
  }
}

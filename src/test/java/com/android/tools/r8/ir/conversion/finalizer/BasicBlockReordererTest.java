// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BasicBlockReordererTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableBasicBlockReorderer;

  @Parameters(name = "{0}, enableBasicBlockReorderer: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testTryRangeCoalescingAndFallthroughOrdering() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .addOptionsModification(
            options ->
                options.getTestingOptions().enableBasicBlockReorderer = enableBasicBlockReorderer)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject testClassSubject = inspector.clazz(TestClass.class);
              MethodSubject methodSubject =
                  testClassSubject.uniqueMethodWithOriginalName("computeInSharedTryCatch");
              assertTrue(methodSubject.isPresent());
              DexCode dexCode = methodSubject.getMethod().getCode().asDexCode();
              assertEquals(enableBasicBlockReorderer ? 1 : 2, dexCode.getTries().length);
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("4", "4", "-1");
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
    static int unhandledStep(int value) {
      if (value < -100) {
        throw new IllegalStateException("negative");
      }
      return value + 2;
    }

    @NeverInline
    static int computeInSharedTryCatch(int firstInput, int secondInput) {
      try {
        if (firstInput > 0) {
          if (secondInput > 0) {
            return throwingStep(secondInput);
          }
        } else {
          return throwingStep(-firstInput) + 1;
        }
      } catch (RuntimeException exception) {
        return -1;
      }
      return unhandledStep(firstInput);
    }

    public static void main(String[] args) {
      System.out.println(computeInSharedTryCatch(2, 3));
      System.out.println(computeInSharedTryCatch(-2, 0));
      System.out.println(computeInSharedTryCatch(0, 1));
    }
  }
}

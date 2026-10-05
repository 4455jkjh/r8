// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IdenticalBlockRemoverReturnTest extends TestBase {

  private static final String[] EXPECTED_OUTPUT =
      new String[] {"null", "null", "1:null", "C", "2:null", "C"};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
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
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject method =
        inspector
            .clazz(TestClass.class)
            .uniqueMethodWithOriginalName("testDeduplicateReturnBlocks");
    assertTrue(method.isPresent());
    assertEquals(2, method.streamInstructions().filter(InstructionSubject::isReturnObject).count());
  }

  static class TestClass {

    @NeverInline
    static String[] getC() {
      return new String[] {"C"};
    }

    @NeverInline
    static long getLong(int v) {
      return v;
    }

    @NeverInline
    static void sideEffectRange(long l1, long l2, long l3, Object obj) {
      if (l1 == l2 && l2 == l3) {
        System.out.println(l1 + ":" + obj);
      }
    }

    @NeverInline
    static String[] testDeduplicateReturnBlocks(int a, int b) {
      if (a == 0) {
        long l = getLong(1);
        String[] r1 = null;
        if (b == 0) {
          return r1;
        }
        sideEffectRange(l, l, l, r1);
      } else {
        long l = getLong(2);
        String[] r2 = null;
        if (b == 0) {
          return r2;
        }
        sideEffectRange(l, l, l, r2);
      }
      return getC();
    }

    public static void main(String[] args) {
      sideEffectRange(0L, 1L, 2L, "init");
      System.out.println((Object) testDeduplicateReturnBlocks(0, 0));
      System.out.println((Object) testDeduplicateReturnBlocks(1, 0));
      System.out.println(testDeduplicateReturnBlocks(0, 1)[0]);
      System.out.println(testDeduplicateReturnBlocks(1, 1)[0]);
    }
  }
}

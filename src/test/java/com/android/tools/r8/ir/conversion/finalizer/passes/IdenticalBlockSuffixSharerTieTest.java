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
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexMulIntLit8;
import com.android.tools.r8.dex.code.DexXorIntLit8;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/571907627. */
@RunWith(Parameterized.class)
public class IdenticalBlockSuffixSharerTieTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("19", "31", "1");
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject method = inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("test");
    assertTrue(method.isPresent());
    DexInstruction[] instructions = method.getMethod().getCode().asDexCode().instructions;
    // The suffix `* 3 + 7` is shared by the first two branches, which have the same size. In case
    // of a tie, the shared suffix is placed after the last of the predecessors in the block order,
    // independently of the order of the predecessors.
    int mulIndex = -1;
    for (int i = 0; i < instructions.length; i++) {
      if (instructions[i] instanceof DexMulIntLit8) {
        assertEquals(-1, mulIndex);
        mulIndex = i;
      }
    }
    assertTrue(mulIndex > 0);
    DexInstruction previous = instructions[mulIndex - 1];
    assertTrue(previous instanceof DexXorIntLit8);
    assertEquals(9, ((DexXorIntLit8) previous).CC);
  }

  static class TestClass {

    @NeverInline
    static int test(int a, int b) {
      int r;
      if (a == 0) {
        r = (b ^ 5) * 3 + 7;
      } else if (a == 1) {
        r = (b ^ 9) * 3 + 7;
      } else {
        r = b;
      }
      return r;
    }

    public static void main(String[] args) {
      System.out.println(test(0, args.length + 1));
      System.out.println(test(1, args.length + 1));
      System.out.println(test(2, args.length + 1));
    }
  }
}

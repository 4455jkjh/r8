// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexGoto16;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexBuilderBackwardGotoMinus128Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testBackwardGotoAtMinus128Uses8BitGoto() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Loop header `if-lez` (2 units) + 63 `add-int/lit16` (63 * 2 = 126 units) = 128
              // code units before the backward Goto, giving relativeOffset == -128 (Byte.MIN_VALUE).
              assertTrue(
                  inspector
                      .clazz(TestClass.class)
                      .uniqueMethodWithOriginalName("loop128")
                      .streamInstructions()
                      .anyMatch(
                          i ->
                              i.asDexInstruction().getInstruction() instanceof DexGoto
                                  && ((DexGoto) i.asDexInstruction().getInstruction()).AA
                                      == Byte.MIN_VALUE));
              assertTrue(
                  inspector
                      .clazz(TestClass.class)
                      .uniqueMethodWithOriginalName("loop128")
                      .streamInstructions()
                      .noneMatch(
                          i -> i.asDexInstruction().getInstruction() instanceof DexGoto16));
            })
        .run(parameters.getRuntime(), TestClass.class, "1")
        .assertSuccessWithOutputLines("62000");
  }

  static class TestClass {

    @NeverInline
    static int loop128(int n) {
      int acc = 0;
      while (n > 0) {
        n--;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
        acc += 1000;
      }
      return acc;
    }

    public static void main(String[] args) {
      System.out.println(loop128(args.length));
    }
  }
}

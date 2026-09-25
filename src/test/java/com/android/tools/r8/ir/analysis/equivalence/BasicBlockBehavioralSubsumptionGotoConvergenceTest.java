// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.equivalence;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BasicBlockBehavioralSubsumptionGotoConvergenceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testGotoChainsConvergingAtNonReturnBlock() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector ->
                assertEquals(
                    1,
                    inspector
                        .clazz(TestClass.class)
                        .uniqueMethodWithOriginalName("init")
                        .streamInstructions()
                        .filter(InstructionSubject::isIf)
                        .count()))
        .run(parameters.getRuntime(), TestClass.class, "1")
        .assertSuccessWithOutputLines("srgb");
  }

  static class TestClass {
    boolean unusedWideGamut;
    boolean isSrgb;

    @NeverInline
    static boolean checkPrimary(int[] arr) {
      return arr[0] > 0;
    }

    @NeverInline
    void init(int[] arr, float min, int id) {
      // When unusedWideGamut is removed by tree shaking, the phi merging the || branches is
      // eliminated, leaving Cmp(min, 0.0f) + If GEZ with unequal-length Goto chains (0/1-hop vs
      // 1/2-hop via the shared || true-merge block with checkPrimary) converging at the following
      // non-Return `if (id != 0)` block. BasicBlockBehavioralSubsumption collapses both Ifs in
      // SSA IR, leaving only the single `if (id != 0)` branch in `init`.
      this.unusedWideGamut = checkPrimary(arr) || min < 0.0f;
      if (id != 0) {
        this.isSrgb = true;
      }
    }

    public static void main(String[] args) {
      TestClass obj = new TestClass();
      obj.init(new int[] {args.length}, (float) args.length, args.length);
      if (obj.isSrgb) {
        System.out.println("srgb");
      }
    }
  }
}

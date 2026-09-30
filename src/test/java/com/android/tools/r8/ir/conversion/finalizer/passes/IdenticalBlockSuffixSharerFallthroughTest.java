// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

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
public class IdenticalBlockSuffixSharerFallthroughTest extends TestBase {

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
        .assertSuccessWithOutputLines("10", "19", "1");
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject method = inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("test");
    assertTrue(method.isPresent());
    // The suffix `b * 3 + 7` is shared by the first two branches. The shared suffix is placed after
    // the second branch, which falls through to it, so that no goto is needed.
    assertTrue(method.streamInstructions().noneMatch(InstructionSubject::isGoto));
  }

  static class TestClass {

    @NeverInline
    static int test(int a, int b) {
      int r;
      if (a == 0) {
        r = b * 3 + 7;
      } else if (a == 1) {
        b = b ^ 5;
        r = b * 3 + 7;
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

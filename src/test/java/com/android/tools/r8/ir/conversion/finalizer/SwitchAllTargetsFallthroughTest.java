// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SwitchAllTargetsFallthroughTest extends TestBase {

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
        .assertSuccessWithOutputLines("15", "18", "21", "24", "39");
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
        .assertSuccessWithOutputLines("15", "18", "21", "24", "39");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(TestClass.class);
    MethodSubject method = clazz.uniqueMethodWithOriginalName("switchJoiningAtSharedBlock");
    assertTrue(method.isPresent());
    assertTrue(method.streamInstructions().noneMatch(InstructionSubject::isSwitch));
  }

  static class TestClass {

    @NeverInline
    static int switchJoiningAtSharedBlock(int key, int a, int b) {
      int result;
      switch (key) {
        case 0:
          result = key + a;
          break;
        case 1:
          result = key + a;
          break;
        case 2:
          result = key + a;
          break;
        case 3:
          result = key + a;
          break;
        case 4:
          result = key + a;
          break;
        case 5:
          result = key + a;
          break;
        case 6:
          result = key + a;
          break;
        case 7:
          result = key + a;
          break;
        default:
          result = key + a;
          break;
      }
      return result * b;
    }

    public static void main(String[] args) {
      System.out.println(switchJoiningAtSharedBlock(0, 5, 3));
      System.out.println(switchJoiningAtSharedBlock(1, 5, 3));
      System.out.println(switchJoiningAtSharedBlock(2, 5, 3));
      System.out.println(switchJoiningAtSharedBlock(3, 5, 3));
      System.out.println(switchJoiningAtSharedBlock(8, 5, 3));
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.loops;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NaturalIntLoopWithZeroTestTest extends TestBase {

  private static final String[] EXPECTED_OUTPUT = {
    "stopped", "0", "1", "aioobe1", "0", "1", "aioobe2", "0", "1", "aioobe3"
  };

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  static class Main {

    public static void main(String[] args) {
      try {
        testZeroTestLoop();
      } catch (StopException e) {
        System.out.println("stopped");
      }
      try {
        testZeroTestUnboxedEnumValuesLoop();
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("aioobe1");
      }
      try {
        testInvertedTargetUnboxedEnumValuesLoop();
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("aioobe2");
      }
      try {
        testOutOfBoundsUnboxedEnumValuesLoop();
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("aioobe3");
      }
    }

    @NeverInline
    static void testZeroTestLoop() {
      for (int i = 0; ; i++) {
        if (i < 0) {
          return;
        }
        sink(i);
      }
    }

    @NeverInline
    static void sink(int i) {
      if (i == 2) {
        throw new StopException();
      }
    }

    @NeverInline
    static void testZeroTestUnboxedEnumValuesLoop() {
      MyEnum[] values = MyEnum.values();
      for (int i = 0; ; i++) {
        if (i < 0) {
          return;
        }
        System.out.println(values[i].ordinal());
      }
    }

    static int zero() {
      return 0;
    }

    @NeverInline
    static void testInvertedTargetUnboxedEnumValuesLoop() {
      MyEnum[] values = MyEnum.values();
      int z = zero();
      for (int i = 0; ; i++) {
        if (i < z) {
          return;
        }
        System.out.println(values[i].ordinal());
      }
    }

    @NeverInline
    static void testOutOfBoundsUnboxedEnumValuesLoop() {
      MyEnum[] values = MyEnum.values();
      for (int i = 0; i < 5; i++) {
        System.out.println(values[i].ordinal());
      }
    }
  }

  enum MyEnum {
    A,
    B
  }

  static class StopException extends RuntimeException {}
}

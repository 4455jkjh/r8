// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayLengthOnEnumValuesArrayTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyUnboxedEnum.class))
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMyEnumMethod =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("testMyEnum");
              assertThat(testMyEnumMethod, isPresent());
              assertTrue(
                  testMyEnumMethod
                      .streamInstructions()
                      .noneMatch(InstructionSubject::isArrayLength));

              MethodSubject testMyUnboxedEnumMethod =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("testMyUnboxedEnum");
              assertThat(testMyUnboxedEnumMethod, isPresent());
              assertTrue(
                  testMyUnboxedEnumMethod
                      .streamInstructions()
                      .noneMatch(InstructionSubject::isArrayLength));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "B", "0", "1");
  }

  static class Main {

    public static void main(String[] args) throws Exception {
      testMyEnum();
      testMyUnboxedEnum();
    }

    @NeverInline
    private static void testMyEnum() {
      for (MyEnum e : MyEnum.values()) {
        System.out.println(e);
      }
    }

    @NeverInline
    private static void testMyUnboxedEnum() {
      for (MyUnboxedEnum e : MyUnboxedEnum.values()) {
        System.out.println(e.ordinal());
      }
    }
  }

  enum MyEnum {
    A,
    B
  }

  enum MyUnboxedEnum {
    A,
    B
  }
}

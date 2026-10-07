// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/152516470: Enum#valueOf in a try-catch should not block enum unboxing. */
@RunWith(Parameterized.class)
public class ValueOfInTryCatchEnumUnboxingTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "-1");
  }

  enum MyEnum {
    A,
    B
  }

  static class Main {

    public static void main(String[] args) {
      print(parse(args.length == 0 ? "B" : "A"));
      print(parse(args.length == 0 ? "C" : "A"));
    }

    @NeverInline
    static MyEnum parse(String name) {
      try {
        return MyEnum.valueOf(name);
      } catch (IllegalArgumentException e) {
        return null;
      }
    }

    @NeverInline
    static void print(MyEnum e) {
      System.out.println(e == null ? -1 : e.ordinal());
    }
  }
}

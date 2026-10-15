// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Regression test for unused calls and instance creations where the callee only has side effects
 * due to a null check on an enum argument, and the enum is unboxed. The side effect info that
 * depends on the argument being non-null must not be used after the argument has been unboxed to an
 * int.
 */
@RunWith(Parameterized.class)
public class EnumUnboxingNullCheckedArgumentSideEffectTest extends EnumUnboxingTestBase {

  @Parameterized.Parameter(0)
  public TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("NPE", "NPE", "NPE", "1", "38");
  }

  static class Main {

    public static void main(String[] args) {
      MyEnum nullIfNoArgs = args.length == 0 ? null : MyEnum.A;
      try {
        new A(nullIfNoArgs);
        System.out.println("No NPE");
      } catch (NullPointerException e) {
        System.out.println("NPE");
      }
      try {
        createA(nullIfNoArgs);
        System.out.println("No NPE");
      } catch (NullPointerException e) {
        System.out.println("NPE");
      }
      try {
        hash(nullIfNoArgs, args.length);
        System.out.println("No NPE");
      } catch (NullPointerException e) {
        System.out.println("NPE");
      }
      System.out.println(new A(MyEnum.B).getE().ordinal());
      System.out.println(hash(MyEnum.B, 1));
    }

    @NeverInline
    static void createA(MyEnum e) {
      new A(e);
    }

    @NeverInline
    static int hash(MyEnum e, int seed) {
      e.getClass();
      return seed * 31 + 7;
    }
  }

  @NeverClassInline
  static class A {

    MyEnum e;

    @NeverInline
    A(MyEnum e) {
      e.getClass();
      this.e = e;
    }

    @NeverInline
    MyEnum getE() {
      return e;
    }
  }

  enum MyEnum {
    A,
    B
  }
}

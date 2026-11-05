// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.convertchecknotnull;

import com.android.tools.r8.NeverClassInline;
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
public class ConvertCheckNotNullEnumUnboxingTest extends TestBase {

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
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Sub", "0");
  }

  enum MyEnum {
    A,
    B
  }

  static class Base {

    @NeverInline
    static void check(Base b, Object o) {
      if (o == null) {
        throw new NullPointerException(b.toString());
      }
    }
  }

  @NeverClassInline
  static class Sub extends Base {

    @NeverInline
    static void check(Sub s, Object o) {
      if (o == null) {
        throw new NullPointerException(s.toString());
      }
    }

    @NeverInline
    void ping() {
      System.out.println("Sub");
    }
  }

  static class Main {

    public static void main(String[] args) {
      Sub sub = new Sub();
      sub.ping();
      MyEnum e = args.length == 0 ? MyEnum.A : MyEnum.B;
      Base.check(sub, e);
      Sub.check(sub, e);
      checkNotNull(e);
      if (alwaysFalse()) {
        deadCaller(e);
      }
      System.out.println(e.ordinal());
    }

    static boolean alwaysFalse() {
      return false;
    }

    @NeverInline
    static void deadCaller(MyEnum e) {
      deadCheckNotNull(e);
    }

    @NeverInline
    static void checkNotNull(Object o) {
      if (o == null) {
        throw new NullPointerException("checkNotNull");
      }
    }

    @NeverInline
    static void deadCheckNotNull(Object o) {
      if (o == null) {
        throw new NullPointerException("deadCheckNotNull");
      }
    }
  }
}

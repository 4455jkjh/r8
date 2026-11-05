// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumevalues;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeValuesReturnNonNullWithSubtypingTest extends TestBase {

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
        .addKeepRules(
            "-assumevalues class " + Main.class.getTypeName() + " {",
            "  " + A.class.getTypeName() + " field return _NONNULL_;",
            "  " + A.class.getTypeName() + " get() return _NONNULL_;",
            "  java.lang.Object getObject() return _NONNULL_;",
            "}")
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("B", "B", "C");
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    String name() {
      return "A";
    }
  }

  @NeverClassInline
  static class B extends A {

    @NeverInline
    @Override
    String name() {
      return "B";
    }
  }

  static class Main {

    static A field = System.currentTimeMillis() > 0 ? new B() : null;

    @NeverInline
    static A get() {
      return System.currentTimeMillis() > 0 ? new B() : null;
    }

    @NeverInline
    static Object getObject() {
      return System.currentTimeMillis() > 0 ? "C" : null;
    }

    public static void main(String[] args) {
      try {
        A f = field;
        if (f != null) {
          System.out.println(f.name());
        }
        A m = get();
        if (m != null) {
          System.out.println(m.name());
        }
        Object o = getObject();
        if (o != null) {
          System.out.println(o);
        }
      } catch (RuntimeException e) {
        throw e;
      }
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
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
public class NonDeterministicPhiTypeWithAssumeTest extends TestBase {

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
        .addKeepClassAndMembersRules(B.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMethod =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
              assertThat(testMethod, isPresent());
              assertTrue(
                  testMethod.streamInstructions().noneMatch(InstructionSubject::isInstanceOf));
            });
  }

  static class Main {

    public static void main(String[] args) {
      test(false, false, false);
      System.out.println(System.currentTimeMillis() > 0 ? new A() : new B());
    }

    @KeepConstantArguments
    @NeverInline
    static void test(boolean cond1, boolean cond2, boolean cond3) {
      I i = null;
      while (cond1) {
        I a = getA();
        if (cond2) {
          if (a.count() > 0) {
            i = a;
          }
          branch1();
        } else if (cond3) {
          i = a;
          branch2();
        }
      }
      if (i != null) {
        System.out.println(i instanceof A);
      }
    }

    @NeverInline
    static void branch1() {
      System.out.print("");
    }

    @NeverInline
    static void branch2() {
      System.out.print("");
    }

    @NeverInline
    static I getA() {
      return System.currentTimeMillis() > 0 ? new A() : null;
    }
  }

  interface I {

    int count();
  }

  @NoHorizontalClassMerging
  static class A implements I {

    @NeverInline
    @Override
    public int count() {
      return 1;
    }
  }

  @NoHorizontalClassMerging
  static class B implements I {

    @NeverInline
    @Override
    public int count() {
      return 2;
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MathMinMaxTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MathMinMaxTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              MethodSubject testMinUnusedMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testMinUnused");
              assertThat(testMinUnusedMethodSubject, isPresent());
              assertThat(testMinUnusedMethodSubject, not(invokesMethodWithName("min")));

              MethodSubject testMaxUnusedMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("testMaxUnused");
              assertThat(testMaxUnusedMethodSubject, isPresent());
              assertThat(testMaxUnusedMethodSubject, not(invokesMethodWithName("max")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("min unused", "max unused", "1", "2");
  }

  static class Main {

    public static void main(String[] args) {
      testMinUnused();
      testMaxUnused();
      testMinUsed();
      testMaxUsed();
    }

    @NeverInline
    static void testMinUnused() {
      Math.min(1, 2);
      Math.min(1L, 2L);
      Math.min(1.0f, 2.0f);
      Math.min(1.0, 2.0);
      System.out.println("min unused");
    }

    @NeverInline
    static void testMaxUnused() {
      Math.max(1, 2);
      Math.max(1L, 2L);
      Math.max(1.0f, 2.0f);
      Math.max(1.0, 2.0);
      System.out.println("max unused");
    }

    @NeverInline
    static void testMinUsed() {
      System.out.println(Math.min(1, 2));
    }

    @NeverInline
    static void testMaxUsed() {
      System.out.println(Math.max(1, 2));
    }
  }
}

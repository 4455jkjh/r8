// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OutlineWithExceptionHandlerVerificationErrorTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addOptionsModification(
            options -> {
              options.outline.threshold = 2;
              options.outline.minSize = 3;
            })
        .setMinApi(parameters)
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              ClassSubject outlineClass =
                  inspector.syntheticClass(syntheticItems.syntheticOutlineClass(Main.class, 0));
              assertThat(outlineClass, isPresent());

              MethodSubject outlineMethod = outlineClass.uniqueMethod();
              assertThat(outlineMethod, isPresent());
              assertEquals(4, outlineMethod.getParameters().size());

              ClassSubject classSubject = inspector.clazz(Main.class);
              assertThat(classSubject, isPresent());

              MethodSubject sinkMethod = classSubject.uniqueMethodWithOriginalName("sink");
              assertThat(sinkMethod, isPresent());
              assertThat(outlineMethod, not(invokesMethod(sinkMethod)));

              MethodSubject m1Method = classSubject.uniqueMethodWithOriginalName("m1");
              assertThat(m1Method, isPresent());
              assertThat(m1Method, invokesMethod(outlineMethod));
              assertThat(m1Method, invokesMethod(sinkMethod));

              MethodSubject m2Method = classSubject.uniqueMethodWithOriginalName("m2");
              assertThat(m2Method, isPresent());
              assertThat(m2Method, invokesMethod(outlineMethod));
              assertThat(m2Method, invokesMethod(sinkMethod));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("12", "18");
  }

  public static class Main {

    public static void main(String[] args) {
      int a = args.length + 1;
      System.out.println(m1(a, a + 1, a + 2));
      System.out.println(m2(a + 1, a + 2, a + 3));
    }

    @NeverInline
    static int m1(int a, int b, int c) {
      Object o = getObj();
      useObj(o);
      int v1 = 0;
      try {
        int v0 = a + b;
        v1 = v0 + c;
        sink(v1);
        return 0;
      } catch (Exception e) {
        return v1 + a + b + c;
      }
    }

    @NeverInline
    static int m2(int a, int b, int c) {
      Object o = getObj();
      useObj(o);
      int v1 = 0;
      try {
        int v0 = a + b;
        v1 = v0 + c;
        sink(v1);
        return 1;
      } catch (Exception e) {
        return v1 + a + b + c;
      }
    }

    @NeverInline
    static Object getObj() {
      return new Object();
    }

    @NeverInline
    static void useObj(Object o) {
      if (System.identityHashCode(o) == 0) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void sink(int x) {
      if (x > 0) {
        throw new RuntimeException();
      }
    }
  }
}

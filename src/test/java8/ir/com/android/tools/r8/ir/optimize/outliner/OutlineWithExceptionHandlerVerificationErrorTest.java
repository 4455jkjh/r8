// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
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
    R8FullTestBuilder testBuilder =
        testForR8(parameters.getBackend())
            .addProgramClasses(Main.class)
            .addKeepMainRule(Main.class)
            .enableInliningAnnotations()
            .addOptionsModification(
                options -> {
                  options.outline.threshold = 2;
                  options.outline.minSize = 3;
                })
            .setMinApi(parameters)
            .collectSyntheticItems();
    if (parameters.isCfRuntime()) {
      // TODO(b/551348855): Outlining a sequence of non-throwing instructions followed by a
      //  throwing void invoke in a basic block with catch handlers causes the non-throwing
      //  return value of the outline to be defined by the throwing outline invoke, which is not
      //  defined on the exceptional edge to the catch handler.
      assertThrows(CompilationFailedException.class, testBuilder::compile);
    } else {
      // TODO(b/551348855): Should succeed with output lines "12", "18" instead of failing with
      //  VerifyError.
      testBuilder
          .compile()
          .inspectWithSyntheticItems(
              (inspector, syntheticItems) -> {
                ClassSubject outlineClass =
                    inspector.syntheticClass(syntheticItems.syntheticOutlineClass(Main.class, 0));
                assertThat(outlineClass, isPresent());

                MethodSubject outlineMethod =
                    outlineClass.uniqueMethodThatMatches(
                        method -> method.getParameters().size() == 3);
                assertThat(outlineMethod, isPresent());

                ClassSubject classSubject = inspector.clazz(Main.class);
                assertThat(classSubject, isPresent());
                assertThat(
                    outlineMethod,
                    invokesMethod(classSubject.uniqueMethodWithOriginalName("sink")));
                assertThat(
                    classSubject.uniqueMethodWithOriginalName("m1"), invokesMethod(outlineMethod));
                assertThat(
                    classSubject.uniqueMethodWithOriginalName("m2"), invokesMethod(outlineMethod));
              })
          .run(parameters.getRuntime(), Main.class)
          .assertFailureWithErrorThatThrows(VerifyError.class);
    }
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

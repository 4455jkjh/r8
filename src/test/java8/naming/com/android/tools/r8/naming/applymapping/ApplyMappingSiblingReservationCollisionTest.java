// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test for b/576060575.
@RunWith(Parameterized.class)
public class ApplyMappingSiblingReservationCollisionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public static class Base {
    public int base() {
      return 1;
    }
  }

  public static class A extends Base {
    private boolean qa = false;
    private boolean canModify = true;

    public boolean isCanModifyPost() {
      return canModify;
    }

    public boolean isQA() {
      return qa;
    }

    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      System.out.println(a.isCanModifyPost());
      System.out.println(a.isQA());
      System.out.println(b.isQA());
    }
  }

  public static class B extends Base {
    private boolean qa = true;

    public boolean isQA() {
      return qa;
    }
  }

  @Test
  public void test() throws Exception {
    // TODO(b/576060575): Should not rename both A.isCanModifyPost() and A.isQA() to S().
    AssertionError e =
        assertThrows(
            AssertionError.class,
            () ->
                testForR8(parameters.getBackend())
                    .addInnerClasses(getClass())
                    .addKeepMainRule(A.class)
                    .addKeepClassAndMembersRulesWithAllowObfuscation(Base.class, A.class, B.class)
                    .addDontOptimize()
                    .addApplyMapping(
                        StringUtils.lines(
                            A.class.getTypeName() + " -> " + A.class.getTypeName() + ":",
                            "    boolean isCanModifyPost() -> S",
                            B.class.getTypeName() + " -> " + B.class.getTypeName() + ":",
                            "    boolean isQA() -> S"))
                    .setMinApi(parameters)
                    .run(parameters.getRuntime(), A.class)
                    .inspect(
                        inspector -> {
                          ClassSubject classA = inspector.clazz(A.class);
                          MethodSubject isCanModifyPost =
                              classA.uniqueMethodWithOriginalName("isCanModifyPost");
                          MethodSubject isQA = classA.uniqueMethodWithOriginalName("isQA");
                          assertEquals("S", isCanModifyPost.getFinalName());
                          assertNotEquals("S", isQA.getFinalName());
                        })
                    .assertSuccessWithOutputLines("true", "false", "true"));
    assertThat(
        e.getMessage(),
        containsString(
            parameters.isCfRuntime()
                ? "Duplicate method name \"S\" with signature \"()Z\""
                : "Out-of-order method ids"));
  }
}

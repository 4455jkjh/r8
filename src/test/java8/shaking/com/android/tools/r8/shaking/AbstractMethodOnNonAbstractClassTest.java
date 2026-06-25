// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8TestCompileResult;
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
public class AbstractMethodOnNonAbstractClassTest extends TestBase {

  private static final String DEX2OAT_WARNING =
      "is abstract, but the declaring class is neither abstract nor an interface";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testCompat() throws Exception {
    R8TestCompileResult compileResult =
        testForR8Compat(parameters.getBackend())
            .addInnerClasses(AbstractMethodOnNonAbstractClassTest.class)
            .addKeepMainRule(TestClass.class)
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters)
            .compile();

    // A is not made abstract in compat mode.
    ClassSubject classSubject = compileResult.inspector().clazz(A.class);
    assertThat(classSubject, isPresent());
    assertFalse(classSubject.isAbstract());

    // A.m() is also not made abstract in compat mode.
    MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName("m");
    assertThat(methodSubject, isPresent());
    assertFalse(methodSubject.isAbstract());

    if (parameters.isDexRuntime()) {
      compileResult
          .runDex2Oat(parameters.getRuntime())
          .assertStderrMatches(not(containsString(DEX2OAT_WARNING)));
    }

    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testFull() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(AbstractMethodOnNonAbstractClassTest.class)
            .addKeepMainRule(TestClass.class)
            .enableNoHorizontalClassMergingAnnotations()
            .setMinApi(parameters)
            .compile();

    // A is made abstract in full mode.
    ClassSubject classSubject = compileResult.inspector().clazz(A.class);
    assertThat(classSubject, isPresent());
    assertTrue(classSubject.isAbstract());

    // A.m() is also made abstract in full mode and then B.m() is hoisted.
    MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName("m");
    assertThat(methodSubject, isPresent());
    assertThat(methodSubject, not(isAbstract()));
    assertTrue(methodSubject.streamInstructions().anyMatch(i -> i.isConstString("Hello, world!")));

    if (parameters.isDexRuntime()) {
      // There is no warning due to both A and A.m() being abstract.
      compileResult
          .runDex2Oat(parameters.getRuntime())
          .assertStderrMatches(not(containsString(DEX2OAT_WARNING)));
    }

    compileResult
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class TestClass {

    public static void main(String[] args) {
      A b = System.currentTimeMillis() > 0 ? new B() : new C();
      b.m();
    }
  }

  static class A {

    // Never called directly on A, thus can be made abstract.
    void m() {}
  }

  @NoHorizontalClassMerging
  static class B extends A {

    @Override
    void m() {
      System.out.println("Hello, world!");
    }
  }

  static class C extends A {

    @Override
    void m() {
      throw new RuntimeException();
    }
  }
}

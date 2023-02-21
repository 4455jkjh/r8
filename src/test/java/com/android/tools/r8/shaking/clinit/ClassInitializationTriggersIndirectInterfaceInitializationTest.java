// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.clinit;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInitializationTriggersIndirectInterfaceInitializationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInitializationTriggersIndirectInterfaceInitializationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoUnusedInterfaceRemovalAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject iClassSubject = inspector.clazz(I.class);
              ClassSubject jClassSubject = inspector.clazz(J.class);
              ClassSubject aClassSubject = inspector.clazz(A.class);
              if (hasDefaultInterfaceMethodsSupport(parameters)) {
                // Verify that I's class initializer is still present.
                assertThat(iClassSubject, isPresent());
                assertThat(iClassSubject.clinit(), isPresent());

                // Verify that J and A are pruned.
                assertThat(jClassSubject, isAbsent());
                assertThat(aClassSubject, isAbsent());
              } else {
                // All interfaces are gone and the default methods companion call is inlined.
                assertThat(iClassSubject, isAbsent());
                assertThat(jClassSubject, isAbsent());
                assertThat(aClassSubject, isAbsent());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(
            hasDefaultInterfaceMethodsSupport(parameters), "I.<clinit>()", "I.m()")
        .assertSuccessWithOutputLinesIf(!hasDefaultInterfaceMethodsSupport(parameters), "I.m()");
  }

  static class Main {

    public static void main(String[] args) {
      new A().m();
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface I {

    Greeter greeter = new Greeter("I.<clinit>()");

    // TODO(b/144266257): This should not require a @NeverInline annotation, since tree shaking
    //  should not be allowed to remove the default interface method if that could change interface
    //  initialization side effects.
    @NeverInline
    default void m() {
      System.out.println("I.m()");
    }
  }

  @NoHorizontalClassMerging
  @NoUnusedInterfaceRemoval
  @NoVerticalClassMerging
  interface J extends I {}

  @NeverClassInline
  static class A implements J {}

  static class Greeter {

    Greeter(String greeting) {
      System.out.println(greeting);
    }
  }
}

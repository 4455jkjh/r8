// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimpleInlineBridgeWithBoxUnboxTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> {
              options.inlinerOptions().multiCallerInliningInstructionLimits = new int[0];
              options.getTestingOptions().validInliningReasons =
                  ImmutableSet.of(Inliner.Reason.SIMPLE);
            })
        .enableInliningAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);

              MethodSubject bridgeMethod = mainClass.uniqueMethodWithOriginalName("bridge");
              assertThat(bridgeMethod, isPresent());

              MethodSubject implMethod = mainClass.uniqueMethodWithOriginalName("impl");
              assertThat(implMethod, isPresent());

              MethodSubject testKnownBoxedValueMethod =
                  mainClass.uniqueMethodWithOriginalName("testKnownBoxedValue");
              assertThat(testKnownBoxedValueMethod, isPresent());
              assertThat(testKnownBoxedValueMethod, invokesMethod(bridgeMethod));

              MethodSubject testUnknownBoxedValueMethod =
                  mainClass.uniqueMethodWithOriginalName("testUnknownBoxedValue");
              assertThat(testUnknownBoxedValueMethod, isPresent());
              assertThat(testUnknownBoxedValueMethod, invokesMethod(bridgeMethod));
            });
  }

  static class Main {

    public static void main(String[] args) {
      testKnownBoxedValue();
      testUnknownBoxedValue();
    }

    @NeverInline
    static void testKnownBoxedValue() {
      bridge(42);
    }

    @NeverInline
    static void testUnknownBoxedValue() {
      Integer i = System.currentTimeMillis() > 0 ? 42 : null;
      bridge(i);
    }

    @NoParameterTypeStrengthening
    static void bridge(Object o) {
      impl((Integer) o);
    }

    @NeverInline
    static void impl(int i) {
      System.out.println(i);
    }
  }
}

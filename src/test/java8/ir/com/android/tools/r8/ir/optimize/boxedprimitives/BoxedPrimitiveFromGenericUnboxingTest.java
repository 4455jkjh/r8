// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.boxedprimitives;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoReturnTypeStrengthening;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BoxedPrimitiveFromGenericUnboxingTest extends TestBase {

  @Parameter(0)
  public boolean enableBridgeHoistingToSharedSyntheticSuperclass;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, opt: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    boolean optimize =
        enableBridgeHoistingToSharedSyntheticSuperclass
            && parameters.canHaveNonReboundConstructorInvoke();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options ->
                options.testing.enableBridgeHoistingToSharedSyntheticSuperclass =
                    enableBridgeHoistingToSharedSyntheticSuperclass)
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoReturnTypeStrengtheningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Function should be removed as a result of bridge hoisting + inlining when adding a
              // shared superclass to Increment and Decrement.
              ClassSubject functionClass = inspector.clazz(Function.class);
              assertThat(functionClass, isAbsentIf(optimize));

              if (!optimize) {
                // Check that the Function#apply method signature is unchanged.
                MethodSubject functionClassApplyMethod =
                    functionClass.uniqueMethodWithOriginalName("apply");
                assertThat(functionClassApplyMethod, isPresent());
                assertTrue(functionClassApplyMethod.getParameter(0).is(Object.class));
                assertTrue(functionClassApplyMethod.getReturnType().is(Object.class));
              }

              // Check that the cast to java.lang.Integer in Increment.apply has been removed as a
              // result of devirtualization.
              ClassSubject incrementClass = inspector.clazz(Increment.class);
              assertThat(incrementClass, isPresent());

              MethodSubject incrementClassApplyMethod =
                  incrementClass.uniqueMethodWithOriginalName("apply");
              assertThat(incrementClassApplyMethod, isPresent());
              assertEquals(
                  optimize,
                  incrementClassApplyMethod
                      .streamInstructions()
                      .noneMatch(
                          instruction -> instruction.isCheckCast(Integer.class.getTypeName())));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "42");
  }

  static class Main {

    public static void main(String[] args) {
      Function<Integer, Integer> inc =
          System.currentTimeMillis() > 0 ? new Increment() : new Decrement();
      Function<Integer, Integer> dec =
          System.currentTimeMillis() > 0 ? new Decrement() : new Increment();
      System.out.println(inc.apply(41));
      System.out.println(dec.apply(43));
    }
  }

  interface Function<S, T> {

    @NoParameterTypeStrengthening
    @NoReturnTypeStrengthening
    T apply(S s);
  }

  @NoHorizontalClassMerging
  static class Increment implements Function<Integer, Integer> {

    @Override
    public Integer apply(Integer i) {
      return i + 1;
    }
  }

  @NoHorizontalClassMerging
  static class Decrement implements Function<Integer, Integer> {

    @Override
    public Integer apply(Integer i) {
      return i - 1;
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.boxedprimitives;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesBoxMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesUnboxMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoReturnTypeStrengthening;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.dex.code.DexAddIntLit8;
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
public class BoxedPrimitiveFromGenericUnboxingLambdaTest extends TestBase {

  @Parameter(0)
  public boolean enableBridgeHoistingToSharedSyntheticSuperclass;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, opt: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withDexRuntimesAndAllApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    boolean optimize =
        enableBridgeHoistingToSharedSyntheticSuperclass
            && parameters.canHaveNonReboundConstructorInvoke();
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options ->
                options.testing.enableBridgeHoistingToSharedSyntheticSuperclass =
                    enableBridgeHoistingToSharedSyntheticSuperclass)
        .collectSyntheticItems()
        .enableInliningAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoReturnTypeStrengtheningAnnotations()
        .noHorizontalClassMergingOfSynthetics()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              // Function should be removed as a result of bridge hoisting + inlining when adding a
              // shared superclass to Increment and Decrement.
              ClassSubject functionClass = inspector.clazz(Function.class);
              assertThat(functionClass, isAbsentIf(optimize));

              if (optimize) {
                // We should have injected a shared super class above the two lambda classes.
                ClassSubject syntheticSuperClass =
                    inspector.clazz(syntheticItems.syntheticSharedSuperClass(Decrement.class, 1));
                assertThat(syntheticSuperClass, isPresent());

                // This should have an apply method with specialized signature Integer -> Integer.
                MethodSubject syntheticSuperClassApplyMethod =
                    syntheticSuperClass.uniqueMethodThatMatches(m -> !m.isInstanceInitializer());
                assertThat(syntheticSuperClassApplyMethod, isPresent());
                assertTrue(syntheticSuperClassApplyMethod.getParameter(0).is(Integer.class));
                assertTrue(syntheticSuperClassApplyMethod.getReturnType().is(Integer.class));

                // The increment lambda class no longer has any methods due to virtual method
                // hoisting.
                ClassSubject incrementLambdaClass =
                    inspector.clazz(syntheticItems.syntheticLambdaClass(Increment.class, 0));
                assertThat(incrementLambdaClass, isPresent());
                assertEquals(0, incrementLambdaClass.allMethods().size());

                // Check that the implementation of the apply method on the super class matches the
                // lambda's body.
                assertThat(syntheticSuperClassApplyMethod, not(isAbstract()));
                assertTrue(
                    syntheticSuperClassApplyMethod
                        .streamInstructions()
                        .anyMatch(
                            i -> i.asDexInstruction().getInstruction() instanceof DexAddIntLit8));

                // Check that the cast to Integer in Increment.apply has been eliminated due to
                // specialization.
                assertTrue(
                    syntheticSuperClassApplyMethod
                        .streamInstructions()
                        .noneMatch(
                            instruction -> instruction.isCheckCast(Integer.class.getTypeName())));
                assertThat(syntheticSuperClassApplyMethod, invokesUnboxMethod(int.class));
                assertThat(syntheticSuperClassApplyMethod, invokesBoxMethod(int.class));

                // Check that the cast to Integer in Decrement.apply has been eliminated due to
                // specialization.
                ClassSubject decrementLambdaClass =
                    inspector.clazz(syntheticItems.syntheticLambdaClass(Decrement.class, 0));
                assertThat(decrementLambdaClass, isPresent());

                MethodSubject decrementLambdaClassApplyMethod =
                    decrementLambdaClass.uniqueMethodThatMatches(m -> !m.isInstanceInitializer());
                assertThat(decrementLambdaClassApplyMethod, isPresent());
                assertTrue(
                    decrementLambdaClassApplyMethod
                        .streamInstructions()
                        .noneMatch(
                            instruction -> instruction.isCheckCast(Integer.class.getTypeName())));
                assertThat(decrementLambdaClassApplyMethod, invokesUnboxMethod(int.class));
                assertThat(decrementLambdaClassApplyMethod, invokesBoxMethod(int.class));
              } else {
                // Check that the Function#apply method signature is unchanged.
                MethodSubject functionClassApplyMethod =
                    functionClass.uniqueMethodWithOriginalName("apply");
                assertThat(functionClassApplyMethod, isPresent());
                assertTrue(functionClassApplyMethod.getParameter(0).is(Object.class));
                assertTrue(functionClassApplyMethod.getReturnType().is(Object.class));

                // Check that the cast to java.lang.Integer in Increment.apply is present.
                ClassSubject incrementClass =
                    inspector.clazz(syntheticItems.syntheticLambdaClass(Increment.class, 0));
                assertThat(incrementClass, isPresent());

                MethodSubject incrementClassApplyMethod =
                    incrementClass.uniqueMethodThatMatches(m -> !m.isInstanceInitializer());
                assertThat(incrementClassApplyMethod, isPresent());
                assertTrue(
                    incrementClassApplyMethod
                        .streamInstructions()
                        .anyMatch(
                            instruction -> instruction.isCheckCast(Integer.class.getTypeName())));
                assertThat(incrementClassApplyMethod, invokesUnboxMethod(int.class));
                assertThat(incrementClassApplyMethod, invokesBoxMethod(int.class));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "42");
  }

  static class Main {

    public static void main(String[] args) {
      int capture = args.length + 1;
      Function<Integer, Integer> inc =
          System.currentTimeMillis() > 0 ? Increment.get() : Decrement.get(capture);
      Function<Integer, Integer> dec =
          System.currentTimeMillis() > 0 ? Decrement.get(capture) : Increment.get();
      System.out.println(apply(inc, 41));
      System.out.println(apply(dec, 43));
    }

    @NeverInline
    private static Integer apply(Function<Integer, Integer> fn, int arg) {
      return fn.apply(arg);
    }
  }

  interface Function<S, T> {

    @NoParameterTypeStrengthening
    @NoReturnTypeStrengthening
    T apply(S s);
  }

  static class Increment {

    static Function<Integer, Integer> get() {
      return i -> i + 1;
    }
  }

  static class Decrement {

    static Function<Integer, Integer> get(int capture) {
      return i -> i - capture;
    }
  }
}

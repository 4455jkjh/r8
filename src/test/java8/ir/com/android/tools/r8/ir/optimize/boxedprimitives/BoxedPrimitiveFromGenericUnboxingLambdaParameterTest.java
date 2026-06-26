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
public class BoxedPrimitiveFromGenericUnboxingLambdaParameterTest extends TestBase {

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
            options -> {
              options.testing.enableBridgeHoistingToSharedSyntheticSuperclass =
                  enableBridgeHoistingToSharedSyntheticSuperclass;
              options.testing.enableBridgeHoistingToSharedSyntheticSuperclassReturnSpecialization =
                  false;
            })
        .collectSyntheticItems()
        .enableInliningAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoReturnTypeStrengtheningAnnotations()
        .noHorizontalClassMergingOfSynthetics()
        .addDontObfuscate()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              // Main#apply is expected to box when calling Function#apply.
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());

              MethodSubject mainClassApplyMethod = mainClass.uniqueMethodWithOriginalName("apply");
              assertThat(mainClassApplyMethod, isPresent());
              assertThat(mainClassApplyMethod, invokesBoxMethod(int.class));

              // Function should be removed as a result of bridge hoisting + inlining when
              // introducing a shared superclass above the two lambdas.
              ClassSubject functionClass = inspector.clazz(Function.class);
              assertThat(functionClass, isAbsentIf(optimize));

              if (optimize) {
                // We should have injected a shared super class above the two lambda classes.
                ClassSubject syntheticSuperClass =
                    inspector.clazz(syntheticItems.syntheticSharedSuperClass(Main.class, 2));
                assertThat(syntheticSuperClass, isPresent());

                // This should have an apply method with specialized signature Integer -> Object.
                MethodSubject syntheticSuperClassApplyMethod =
                    syntheticSuperClass.uniqueMethodThatMatches(m -> !m.isInstanceInitializer());
                assertThat(syntheticSuperClassApplyMethod, isPresent());
                assertTrue(syntheticSuperClassApplyMethod.getParameter(0).is(Integer.class));
                assertTrue(syntheticSuperClassApplyMethod.getReturnType().is(Object.class));

                // The increment lambda class no longer has any methods due to virtual method
                // hoisting.
                ClassSubject incrementLambdaClass =
                    inspector.clazz(syntheticItems.syntheticLambdaClass(Main.class, 0));
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

                // Check that the cast to Integer has been eliminated due to specialization.
                assertTrue(
                    syntheticSuperClassApplyMethod
                        .streamInstructions()
                        .noneMatch(
                            instruction -> instruction.isCheckCast(Integer.class.getTypeName())));
                assertThat(syntheticSuperClassApplyMethod, invokesUnboxMethod(int.class));
                assertThat(syntheticSuperClassApplyMethod, invokesBoxMethod(int.class));
              } else {
                // Check that the Function#apply method signature is unchanged.
                MethodSubject functionClassApplyMethod =
                    functionClass.uniqueMethodWithOriginalName("apply");
                assertThat(functionClassApplyMethod, isPresent());
                assertTrue(functionClassApplyMethod.getParameter(0).is(Object.class));
                assertTrue(functionClassApplyMethod.getReturnType().is(Object.class));

                // Check that the cast to java.lang.Integer in Increment.apply is present.
                ClassSubject incrementLambdaClass =
                    inspector.clazz(syntheticItems.syntheticLambdaClass(Main.class, 0));
                assertThat(incrementLambdaClass, isPresent());

                MethodSubject incrementLambdaClassApplyMethod =
                    incrementLambdaClass.uniqueMethodThatMatches(m -> !m.isInstanceInitializer());
                assertThat(incrementLambdaClassApplyMethod, isPresent());
                assertTrue(
                    incrementLambdaClassApplyMethod
                        .streamInstructions()
                        .anyMatch(
                            instruction -> instruction.isCheckCast(Integer.class.getTypeName())));
                assertThat(incrementLambdaClassApplyMethod, invokesUnboxMethod(int.class));
                assertThat(incrementLambdaClassApplyMethod, invokesBoxMethod(int.class));
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "-1");
  }

  static class Main {

    public static void main(String[] args) {
      Function<Integer, Integer> incInt = System.currentTimeMillis() > 0 ? i -> i + 1 : null;
      Function<Integer, Long> decLong = System.currentTimeMillis() > 0 ? i -> i - 1L : null;
      System.out.println(apply(incInt, args.length));
      System.out.println(apply(decLong, args.length));
    }

    @NeverInline
    private static <T> T apply(Function<Integer, T> fn, int arg) {
      return fn.apply(arg);
    }
  }

  interface Function<S, T> {

    @NoParameterTypeStrengthening
    @NoReturnTypeStrengthening
    T apply(S s);
  }
}

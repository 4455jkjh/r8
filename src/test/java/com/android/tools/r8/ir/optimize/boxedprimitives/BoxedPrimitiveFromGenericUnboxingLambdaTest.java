// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.boxedprimitives;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsentIf;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

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
    // TODO(b/309575527): Should be optimized if enableBridgeHoistingToSharedSyntheticSuperclass
    //  && parameters.canHaveNonReboundConstructorInvoke().
    boolean optimize = false;
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options ->
                options.testing.enableBridgeHoistingToSharedSyntheticSuperclass =
                    enableBridgeHoistingToSharedSyntheticSuperclass)
        .collectSyntheticItems()
        .noHorizontalClassMergingOfSynthetics()
        .setMinApi(parameters)
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              // Function should be removed as a result of bridge hoisting + inlining when adding a
              // shared superclass to Increment and Decrement, and another shared superclass to
              // StdoutPrinter and StderrPrinter.
              ClassSubject functionClassSubject = inspector.clazz(Function.class);
              assertThat(functionClassSubject, isAbsentIf(optimize));

              // Check that the cast to java.lang.Integer in Increment.apply has been removed as a
              // result of devirtualization.
              ClassSubject incrementClassSubject =
                  inspector.clazz(syntheticItems.syntheticLambdaClass(Increment.class, 0));
              assertThat(incrementClassSubject, isPresent());

              MethodSubject incrementApplyMethodSubject =
                  incrementClassSubject.uniqueMethodThatMatches(m -> !m.isInstanceInitializer());
              assertThat(incrementApplyMethodSubject, isPresent());
              assertEquals(
                  optimize,
                  incrementApplyMethodSubject
                      .streamInstructions()
                      .noneMatch(
                          instruction -> instruction.isCheckCast(Integer.class.getTypeName())));

              // Check that the cast to java.lang.String in StdoutPrinter.apply has been removed as
              // result of devirtualization (in fact the `Void apply(String)` method has been
              // optimized to `void apply()` as a result of constant propagation).
              ClassSubject stdoutPrinterClassSubject =
                  inspector.clazz(syntheticItems.syntheticLambdaClass(StdoutPrinter.class, 0));
              assertThat(stdoutPrinterClassSubject, isPresent());

              MethodSubject stdoutPrinterApplyMethodSubject =
                  stdoutPrinterClassSubject.uniqueMethodThatMatches(
                      m -> !m.isInstanceInitializer());
              assertThat(stdoutPrinterApplyMethodSubject, isPresent());
              assertEquals(
                  optimize,
                  stdoutPrinterApplyMethodSubject.getProgramMethod().getReturnType().isVoidType());
              assertEquals(
                  optimize ? 0 : 1, stdoutPrinterApplyMethodSubject.getParameters().size());
              assertEquals(
                  optimize,
                  stdoutPrinterApplyMethodSubject
                      .streamInstructions()
                      .noneMatch(
                          instruction -> instruction.isCheckCast(String.class.getTypeName())));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "42", "42");
  }

  static class Main {

    public static void main(String[] args) {
      Function<Integer, Integer> inc =
          System.currentTimeMillis() > 0 ? Increment.get() : Decrement.get();
      Function<Integer, Integer> dec =
          System.currentTimeMillis() > 0 ? Decrement.get() : Increment.get();
      Function<String, Void> printer =
          System.currentTimeMillis() > 0 ? StdoutPrinter.get() : StderrPrinter.get();
      System.out.println(inc.apply(41));
      System.out.println(dec.apply(43));
      printer.apply("42");
    }
  }

  interface Function<S, T> {

    T apply(S s);
  }

  static class Increment {

    static Function<Integer, Integer> get() {
      return i -> i + 1;
    }
  }

  static class Decrement {

    static Function<Integer, Integer> get() {
      return i -> i - 1;
    }
  }

  static class StdoutPrinter {

    static Function<String, Void> get() {
      return obj -> {
        System.out.println(obj);
        return null;
      };
    }
  }

  static class StderrPrinter {

    static Function<String, Void> get() {
      return obj -> {
        System.err.println(obj);
        return null;
      };
    }
  }
}

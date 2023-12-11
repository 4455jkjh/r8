// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.numberunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VirtualMethodsNumberUnboxingTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public VirtualMethodsNumberUnboxingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNumberUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addOptionsModification(opt -> opt.testing.enableNumberUnboxer = true)
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertUnboxing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("32", "33", "42", "43", "51", "52", "2");
  }

  private void assertFirstParameterUnboxed(ClassSubject mainClass, String methodName) {
    MethodSubject methodSubject = mainClass.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getParameter(0).isIntType());
  }

  private void assertFirstParameterBoxed(ClassSubject mainClass, String methodName) {
    MethodSubject methodSubject = mainClass.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getParameter(0).isReferenceType());
  }

  private void assertReturnUnboxed(ClassSubject mainClass, String methodName) {
    MethodSubject methodSubject = mainClass.uniqueMethodWithOriginalName(methodName);
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getReturnType().isIntType());
  }

  private void assertUnboxing(CodeInspector codeInspector) {
    ClassSubject mainClass = codeInspector.clazz(Main.class);
    assertThat(mainClass, isPresent());

    assertFirstParameterUnboxed(mainClass, "print");
    assertFirstParameterUnboxed(mainClass, "forwardToPrint2");
    assertFirstParameterUnboxed(mainClass, "directPrintUnbox");
    assertFirstParameterUnboxed(mainClass, "forwardToPrint");

    assertReturnUnboxed(mainClass, "get");
    assertReturnUnboxed(mainClass, "forwardGet");

    assertFirstParameterBoxed(mainClass, "directPrintNotUnbox");
  }

  static class Main {

    private static final Main MAIN = new Main();

    public static void main(String[] args) {

      // The number unboxer should immediately find this method is worth unboxing.
      MAIN.directPrintUnbox(31);
      MAIN.directPrintUnbox(32);

      // The number unboxer should find the chain of calls is worth unboxing.
      MAIN.forwardToPrint(41);
      MAIN.forwardToPrint(42);

      // The number unboxer should find this method is *not* worth unboxing.
      Integer decode1 = Integer.decode("51");
      Objects.requireNonNull(decode1);
      MAIN.directPrintNotUnbox(decode1);
      Integer decode2 = Integer.decode("52");
      Objects.requireNonNull(decode2);
      MAIN.directPrintNotUnbox(decode2);

      // The number unboxer should unbox the return values.
      System.out.println(MAIN.forwardGet() + 1);
    }

    @NeverInline
    public Integer get() {
      return System.currentTimeMillis() > 0 ? 1 : -1;
    }

    @NeverInline
    public Integer forwardGet() {
      return get();
    }

    @NeverInline
    public void forwardToPrint(Integer boxed) {
      forwardToPrint2(boxed);
    }

    @NeverInline
    public void forwardToPrint2(Integer boxed) {
      print(boxed);
    }

    @NeverInline
    public void print(Integer boxed) {
      System.out.println(boxed + 1);
    }

    @NeverInline
    public void directPrintUnbox(Integer boxed) {
      System.out.println(boxed + 1);
    }

    @NeverInline
    public void directPrintNotUnbox(Integer boxed) {
      System.out.println(boxed);
    }
  }
}

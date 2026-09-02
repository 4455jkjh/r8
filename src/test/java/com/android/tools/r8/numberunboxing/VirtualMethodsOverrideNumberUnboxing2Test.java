// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.numberunboxing;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertFalse;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VirtualMethodsOverrideNumberUnboxing2Test extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public VirtualMethodsOverrideNumberUnboxing2Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNumberUnboxing() throws Throwable {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .addOptionsModification(opt -> opt.getTestingOptions().getNumberUnboxerOptions().enable())
        .compile()
        .inspect(this::assertUnboxing)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "16", "-23", "43", "93", "-68", "43", "14", "23", "33", "21", "42");
  }

  private void assertStatifiedAndUnboxed(MethodSubject methodSubject) {
    assert4ParameterAndReturnUnboxed(methodSubject);
    assertTrue(methodSubject.getProgramMethod().getAccessFlags().isStatic());
  }

  private void assertVirtualAndUnboxed(MethodSubject methodSubject) {
    assert4ParameterAndReturnUnboxed(methodSubject);
    assertFalse(methodSubject.getProgramMethod().getAccessFlags().isStatic());
  }

  private void assert4ParameterAndReturnUnboxed(MethodSubject methodSubject) {
    assertThat(methodSubject, isPresent());
    assertTrue(methodSubject.getProgramMethod().getParameter(0).isDoubleType());
    assertTrue(methodSubject.getProgramMethod().getParameter(1).isIntType());
    assertTrue(methodSubject.getProgramMethod().getParameter(2).isLongType());
    assertTrue(methodSubject.getProgramMethod().getParameter(3).isFloatType());
    assertTrue(methodSubject.getProgramMethod().getReturnType().isLongType());
  }

  private void assertUnboxing(CodeInspector codeInspector) {
    codeInspector.forAllClasses(
        c -> {
          if (c.getOriginalTypeName().equals("Statified")) {
            c.forAllMethods(this::assertStatifiedAndUnboxed);
          } else if (!c.getFinalName().equals("Main")) {
            c.forAllVirtualMethods(this::assertVirtualAndUnboxed);
          }
        });
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new Add().convert(1.3, 10, -1, 1L, 3.5f) + 1L);
      System.out.println(new Sub().convert(1.4, 20, -1, 2L, 3.6f) + 1L);
      System.out.println(new Cst().convert(1.5, 30, -1, 3L, 3.7f) + 1L);

      run(new Add());
      run(new Sub());
      run(new Cst());

      Statified statified = new Statified();
      System.out.println(statified.convert(1.3, 10, -1, 1L, 3.5f) + 1L);
      System.out.println(statified.convert(1.4, 20, -1, 2L, 3.6f) + 1L);
      System.out.println(statified.convert(1.5, 30, -1, 3L, 3.7f) + 1L);

      System.out.println(statified.manyUnused(10L, -1L, -1, -2, 11L));
      System.out.println(statified.manyUnused(20L, -11L, -11, -12, 22L));
    }

    @NeverInline
    private static void run(Top top) {
      System.out.println(top.convert(11.5, 33, -1, 44L, 4.0f) + 1L);
    }
  }

  @NeverClassInline
  static class Statified {

    // Make sure all number unboxing happens correctly when the method becomes static.
    @NeverInline
    Long convert(Double d, Integer i, int unused, Long l, Float f) {
      return Long.valueOf((long) (d.doubleValue() + i.intValue() - l.longValue() + f.floatValue()));
    }

    @NeverInline
    Long manyUnused(long l, long unusedl, int unused, int unused2, Long toUnbox) {
      return Long.valueOf((long) (l + toUnbox.longValue()));
    }
  }

  @NeverClassInline
  interface Top {

    // Make sure all number unboxing happens correctly on virtual methods.
    @NeverInline
    Long convert(Double d, Integer i, int unused, Long l, Float f);
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Add implements Top {

    @Override
    @NeverInline
    public Long convert(Double d, Integer i, int unused, Long l, Float f) {
      return Long.valueOf((long) (d.doubleValue() + i.intValue() + l.longValue() + f.floatValue()));
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Sub implements Top {

    @Override
    @NeverInline
    public Long convert(Double d, Integer i, int unused, Long l, Float f) {
      return Long.valueOf((long) (d.doubleValue() - i.intValue() - l.longValue() - f.floatValue()));
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class Cst implements Top {

    @Override
    @NeverInline
    public Long convert(Double d, Integer i, int unused, Long l, Float f) {
      return Long.valueOf(42L);
    }
  }
}

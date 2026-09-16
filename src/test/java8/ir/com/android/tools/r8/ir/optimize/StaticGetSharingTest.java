// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticGetSharingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8Reference() throws Exception {
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release() // Enabled only in release.
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("truetruetruetruetruetruetrue");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .inspect(StaticGetSharingTest::assertHoisted)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput("truetruetruetruetruetruetrue");
  }

  private static void assertHoisted(CodeInspector inspector) {
    assertEquals(
        1,
        inspector
            .clazz(A.class)
            .uniqueMethodWithOriginalName("foo")
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .count());
    assertEquals(
        1,
        inspector
            .clazz(ATry.class)
            .uniqueMethodWithOriginalName("foo")
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .count());
    assertEquals(
        2,
        inspector
            .clazz(ATryOneBranch.class)
            .uniqueMethodWithOriginalName("foo")
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .count());
    assertEquals(
        1,
        inspector
            .clazz(C.class)
            .uniqueMethodWithOriginalName("bar")
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .count());
    assertEquals(
        1,
        inspector
            .clazz(CTry.class)
            .uniqueMethodWithOriginalName("bar")
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .count());
    assertEquals(
        1,
        inspector
            .clazz(ANestedTry.class)
            .uniqueMethodWithOriginalName("foo")
            .streamInstructions()
            .filter(InstructionSubject::isStaticGet)
            .count());
    assertEquals(
        2,
        inspector
            .clazz(ANestedTry2.class)
            .uniqueMethodWithOriginalName("foo")
            .streamInstructions()
            .filter(
                i ->
                    i.isStaticGet()
                        && !i.getField().getHolderType().getSimpleName().equals("System"))
            .count());
  }

  static class TestClass {
    public static void main(String[] args) {
      System.out.print(new A().foo() > 0);
      System.out.print(new ATry().foo() > 0);
      System.out.print(new ATryOneBranch().foo() > 0);
      System.out.print(new C().bar() > 0);
      System.out.print(new CTry().bar() > 0);
      System.out.print(new ANestedTry().foo() > 0);
      System.out.print(new ANestedTry2().foo() > 0);
    }
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  static class ANestedTry {
    static void inlineableDummy() {}

    @NeverInline
    public long foo() {
      try {
        if (System.currentTimeMillis() > 0) {
          try {
            return B.num + 1;
          } catch (RuntimeException e) {
            inlineableDummy();
          }
        } else {
          try {
            return B.num + 2;
          } catch (RuntimeException e) {
            inlineableDummy();
          }
        }
      } catch (Throwable t) {
        CTry.dummy(0);
      }
      return 1L;
    }
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  static class ANestedTry2 {

    static class Throwing {
      static {
        if (System.currentTimeMillis() > 0) {
          int x = 1 / 0;
        }
      }

      static int num = 40;
    }

    @NeverInline
    public long foo() {
      try {
        if (System.currentTimeMillis() > 0) {
          try {
            return Throwing.num + 1;
          } catch (RuntimeException e) {
            System.out.println("A");
          }
        } else {
          try {
            return Throwing.num + 2;
          } catch (RuntimeException e) {
            System.out.println("B");
          }
        }
      } catch (Throwable t) {
        CTry.dummy(0);
      }
      return 1L;
    }
  }

  @NeverClassInline
  static class A {
    @NeverInline
    public long foo() {
      if (System.currentTimeMillis() > 0) {
        return B.num + 1;
      } else {
        return B.num + 2;
      }
    }
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  static class ATry {
    @NeverInline
    public long foo() {
      try {
        if (System.currentTimeMillis() > 0) {
          return B.num + 1;
        } else {
          return B.num + 2;
        }
      } catch (RuntimeException e) {
        CTry.dummy(0);
      }
      return 0L;
    }
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  static class ATryOneBranch {
    @NeverInline
    public long foo() {
      if (System.currentTimeMillis() > 0) {
        try {
          return B.num + 1;
        } catch (RuntimeException e) {
          CTry.dummy(0);
          return 0L;
        }
      } else {
        return B.num + 2;
      }
    }
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  static class CTry {
    @NeverInline
    static void dummy(int x) {}

    @NeverInline
    public long bar() {
      try {
        long num;
        if (System.currentTimeMillis() > 0) {
          dummy(1);
          num = B.num;
        } else {
          dummy(2);
          num = B.num;
        }
        return num + 1;
      } catch (RuntimeException e) {
        dummy(0);
      }
      return 0L;
    }
  }

  @NoHorizontalClassMerging
  @NeverClassInline
  static class C {
    @NeverInline
    static void dummy(int x) {}

    @NeverInline
    public long bar() {
      long num;
      if (System.currentTimeMillis() > 0) {
        dummy(1);
        num = B.num;
      } else {
        dummy(2);
        num = B.num;
      }
      return num + 1;
    }
  }

  @NeverClassInline
  static class B {
    public static long num = System.currentTimeMillis();
  }
}

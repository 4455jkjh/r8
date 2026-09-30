// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReturnBlockCanonicalizerRewriterTest extends TestBase {

  private static final String[] EXPECTED_OUTPUT =
      new String[] {"6", "10", "4", "2", "3", "10", "10", "2", "3", "fallback"};

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClass = inspector.clazz(TestClass.class);
    assertTrue(testClass.isPresent());

    MethodSubject computeInt = testClass.uniqueMethodWithOriginalName("computeInt");
    assertTrue(computeInt.isPresent());
    assertEquals(1, computeInt.streamInstructions().filter(InstructionSubject::isReturn).count());

    MethodSubject createInterfaceArray =
        testClass.uniqueMethodWithOriginalName("createInterfaceArray");
    assertTrue(createInterfaceArray.isPresent());
    assertEquals(
        3,
        createInterfaceArray
            .streamInstructions()
            .filter(InstructionSubject::isReturnObject)
            .count());

    MethodSubject createAsObject = testClass.uniqueMethodWithOriginalName("createAsObject");
    assertTrue(createAsObject.isPresent());
    assertEquals(
        1, createAsObject.streamInstructions().filter(InstructionSubject::isReturnObject).count());
  }

  @NoHorizontalClassMerging
  @NoVerticalClassMerging
  interface I {
    int id();
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class A implements I {
    @NeverInline
    @Override
    public int id() {
      return 10;
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class B implements I {
    @NeverInline
    @Override
    public int id() {
      return 20;
    }
  }

  static class TestClass {

    static int sink;

    @NeverInline
    static void sideEffect(int value) {
      sink += value;
    }

    @NeverInline
    static int computeInt(int key, int x) {
      if (key == 0) {
        return x + 1;
      } else if (key == 1) {
        return x * 2;
      } else {
        return x - 1;
      }
    }

    @NeverInline
    static I[] createInterfaceArray(int kind, int size, A[] defaultArray) {
      switch (kind) {
        case 0:
          return new A[size];
        case 1:
          return new B[size];
        case 2:
          sideEffect(size);
          return defaultArray;
        default:
          return defaultArray;
      }
    }

    @NeverInline
    static Object createAsObject(int kind, int size) {
      switch (kind) {
        case 0:
          return new A[size];
        case 1:
          return new B[size];
        default:
          return "fallback";
      }
    }

    @NeverInline
    static void printArray(I[] array) {
      I first = array[0];
      System.out.println(first != null ? first.id() : array.length);
    }

    public static void main(String[] args) {
      System.out.println(computeInt(0, 5));
      System.out.println(computeInt(1, 5));
      System.out.println(computeInt(2, 5));

      A[] fallback = new A[] {new A()};
      printArray(createInterfaceArray(0, 2, fallback));
      printArray(createInterfaceArray(1, 3, fallback));
      printArray(createInterfaceArray(2, 4, fallback));
      printArray(createInterfaceArray(3, 5, fallback));

      printArray((I[]) createAsObject(0, 2));
      printArray((I[]) createAsObject(1, 3));
      System.out.println(createAsObject(2, 4));
    }
  }
}

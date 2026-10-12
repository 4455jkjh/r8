// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NewArrayWithNonConstantElementsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  // Emit the 16-element arrays below as new-array + aput instead of filled-new-array.
  private static void lowerFilledNewArrayLimit(InternalOptions options) {
    options.rewriteArrayOptions().maxSizeForFilledNewArrayOfReferences = 4;
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .release()
        .addInnerClasses(getClass())
        .addOptionsModification(NewArrayWithNonConstantElementsTest::lowerFilledNewArrayLimit)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("120", "120", "16")
        .inspect(this::inspectDex);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(Letter.class)
        .addOptionsModification(NewArrayWithNonConstantElementsTest::lowerFilledNewArrayLimit)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("120", "120", "16")
        .inspect(this::inspectDex);
  }

  private void inspectDex(CodeInspector inspector) {
    if (!parameters.isDexRuntime()) {
      return;
    }
    // Without storing each element right after it is created, all 16 elements are live at the
    // same time.
    assertFewRegisters(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("createBoxedArray"));
    assertFewRegisters(
        inspector.clazz(Item.class).uniqueMethodWithOriginalName("createNewInstanceArray"));
    // The enum instances are also stored in their static fields.
    assertFewRegisters(inspector.clazz(Letter.class).clinit());
  }

  private static void assertFewRegisters(MethodSubject method) {
    assertThat(method, isPresent());
    int registers = method.getMethod().getCode().asDexCode().registerSize;
    assertTrue(method + ": expected < 10 registers, got " + registers, registers < 10);
  }

  @NeverClassInline
  static class Item {
    final int v;

    @NeverInline
    Item(int v) {
      this.v = v;
    }

    // Declared in Item so that D8 knows that Item[] is accessible.
    @NeverInline
    static Item[] createNewInstanceArray() {
      return new Item[] {
        new Item(0), new Item(1), new Item(2), new Item(3),
        new Item(4), new Item(5), new Item(6), new Item(7),
        new Item(8), new Item(9), new Item(10), new Item(11),
        new Item(12), new Item(13), new Item(14), new Item(15)
      };
    }
  }

  enum Letter {
    A,
    B,
    C,
    D,
    E,
    F,
    G,
    H,
    I,
    J,
    K,
    L,
    M,
    N,
    O,
    P
  }

  static class TestClass {

    @NeverInline
    static Integer[] createBoxedArray() {
      return new Integer[] {0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15};
    }

    public static void main(String[] args) {
      int sum1 = 0;
      for (Integer x : createBoxedArray()) {
        sum1 += x;
      }
      System.out.println(sum1);
      int sum2 = 0;
      for (Item item : Item.createNewInstanceArray()) {
        sum2 += item.v;
      }
      System.out.println(sum2);
      System.out.println(Letter.values().length);
    }
  }
}

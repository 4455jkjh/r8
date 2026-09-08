// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.boxedprimitives;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BoxedPrimitiveStringNonNullTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public BoxedPrimitiveStringNonNullTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNonNull() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .enableInliningAnnotations()
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyNoBranch)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "true", "1", "9", "111", "2", "18", "222", "3", "27", "333", "4", "36", "444", "1.1",
            "2.2");
  }

  private void verifyNoBranch(CodeInspector codeInspector) {
    for (FoundMethodSubject nonMain :
        codeInspector
            .clazz(TestClass.class)
            .allMethods(m -> !m.getOriginalMethodName().equals("main"))) {
      assertTrue(nonMain.streamInstructions().noneMatch(InstructionSubject::isIf));
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      booleanTest();
      byteTest();
      shortTest();
      integerTest();
      longTest();
      floatTest();
      doubleTest();
    }

    @NeverInline
    private static void booleanTest() {
      Boolean boxed = Boolean.valueOf("true");
      if (boxed == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed.booleanValue());
      }
    }

    @NeverInline
    private static void byteTest() {
      Byte boxed = Byte.valueOf("1");
      if (boxed == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed.byteValue());
      }
      Byte boxed2 = Byte.valueOf("11", 8);
      if (boxed2 == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed2.byteValue());
      }
      Byte boxed3 = Byte.decode("111");
      if (boxed3 == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed3.byteValue());
      }
    }

    @NeverInline
    private static void shortTest() {
      Short boxed = Short.valueOf("2");
      if (boxed == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed.shortValue());
      }
      Short boxed2 = Short.valueOf("22", 8);
      if (boxed2 == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed2.shortValue());
      }
      Short boxed3 = Short.decode("222");
      if (boxed3 == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed3.shortValue());
      }
    }

    @NeverInline
    private static void integerTest() {
      Integer boxed = Integer.valueOf("3");
      if (boxed == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed.intValue());
      }
      Integer boxed2 = Integer.valueOf("33", 8);
      if (boxed2 == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed2.intValue());
      }
      Integer boxed3 = Integer.decode("333");
      if (boxed3 == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed3.intValue());
      }
    }

    @NeverInline
    private static void longTest() {
      Long boxed = Long.valueOf("4");
      if (boxed == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed.longValue());
      }

      Long boxed2 = Long.valueOf("44", 8);
      if (boxed2 == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed2.longValue());
      }

      Long boxed3 = Long.decode("444");
      if (boxed3 == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed3.longValue());
      }
    }

    @NeverInline
    private static void floatTest() {
      Float boxed = Float.valueOf("1.1");
      if (boxed == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed.floatValue());
      }
    }

    @NeverInline
    private static void doubleTest() {
      Double boxed = Double.valueOf("2.2");
      if (boxed == null) {
        System.out.println("null");
      } else {
        System.out.println(boxed.doubleValue());
      }
    }
  }
}

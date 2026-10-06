// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.JavaLangClassTestClass.Bar;
import com.android.tools.r8.assistant.JavaLangClassTestClass.Foo;
import com.android.tools.r8.assistant.runtime.EmptyReflectiveOperationReceiver;
import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaLangClassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNativeMultidexDexRuntimes().withMaximumApiLevel().build();
  }

  private String names() {
    return parameters.getApiLevel().isLessThan(AndroidApiLevel.O)
        ? "com.android.tools.r8.assistant.JavaLangClassTestClass$Foocom.android.tools.r8.assistant.JavaLangClassTestClass.FooFoo"
        : "com.android.tools.r8.assistant.JavaLangClassTestClass$Foocom.android.tools.r8.assistant.JavaLangClassTestClass.FooFoocom.android.tools.r8.assistant.JavaLangClassTestClass$Foo";
  }

  @Test
  public void testInstrumentationWithCustomOracle() throws Exception {
    testForAssistant()
        .addProgramClasses(JavaLangClassTestClass.class, Foo.class, Bar.class)
        .addInstrumentationClasses(Instrumentation.class)
        .setCustomReflectiveOperationReceiver(Instrumentation.class)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), JavaLangClassTestClass.class)
        .assertSuccessWithOutputLines(
            "5",
            "1",
            "9",
            "fail",
            "Object",
            "2",
            "barr",
            "3",
            "a",
            "3",
            "b",
            "4",
            "32",
            "30",
            "com.android.tools.r8.assistant.JavaLangClassTestClass$Foo",
            "31",
            "5",
            "6",
            "7",
            "8",
            names(),
            "5",
            "1",
            "12",
            "13",
            "com.android.tools.r8.assistant",
            "15",
            "22",
            "33",
            "35",
            "20",
            "public int com.android.tools.r8.assistant.JavaLangClassTestClass$Bar.bar()",
            "21",
            "public int com.android.tools.r8.assistant.JavaLangClassTestClass$Bar.i",
            "34",
            "public com.android.tools.r8.assistant.JavaLangClassTestClass$Bar()",
            "42",
            "41",
            "true",
            "40",
            "class com.android.tools.r8.assistant.JavaLangClassTestClass$Bar",
            "50",
            "11",
            "END");
  }

  public static class Instrumentation extends EmptyReflectiveOperationReceiver {

    private void printNumIfTrue(boolean correct, int num) {
      if (correct) {
        System.out.println(num);
      } else {
        System.out.println("fail");
      }
    }

    @Override
    public int onClassForName(
        Stack stack, String className, boolean initialize, ClassLoader classLoader) {
      printNumIfTrue(className.endsWith("Foo"), 1);
      return NO_ID;
    }

    @Override
    public int onClassGetComponentType(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.toString().endsWith("Foo"), 12);
      return NO_ID;
    }

    @Override
    public int onClassGetPackage(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.toString().endsWith("Foo"), 13);
      return NO_ID;
    }

    @Override
    public int onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {
      printNumIfTrue(clazz.toString().endsWith("Foo"), 15);
      return NO_ID;
    }

    @Override
    public int onClassGetDeclaredMethod(
        Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 2);
      return NO_ID;
    }

    @Override
    public int onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 4);
      return NO_ID;
    }

    @Override
    public int onClassGetDeclaredField(
        Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
      printNumIfTrue(
          clazz.getName().endsWith("Foo") && (fieldName.equals("a") || fieldName.equals("b")), 3);
      return NO_ID;
    }

    @Override
    public int onClassGetDeclaredFields(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 32);
      return NO_ID;
    }

    @Override
    public int onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 30);
      return NO_ID;
    }

    @Override
    public int onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 31);
      return NO_ID;
    }

    @Override
    public int onClassGetMethod(
        Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 20);
      return NO_ID;
    }

    @Override
    public int onClassGetMethods(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 22);
      return NO_ID;
    }

    @Override
    public int onClassGetField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 21);
      return NO_ID;
    }

    @Override
    public int onClassGetFields(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 33);
      return NO_ID;
    }

    @Override
    public int onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 34);
      return NO_ID;
    }

    @Override
    public int onClassGetConstructors(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 35);
      return NO_ID;
    }

    @Override
    public int onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
      if (lookupType == NameLookupType.NAME) {
        printNumIfTrue(clazz.getName().endsWith("Foo"), 5);
      }
      if (lookupType == NameLookupType.CANONICAL_NAME) {
        printNumIfTrue(clazz.getName().endsWith("Foo"), 6);
      }
      if (lookupType == NameLookupType.SIMPLE_NAME) {
        printNumIfTrue(clazz.getName().endsWith("Foo"), 7);
      }
      if (lookupType == NameLookupType.TYPE_NAME) {
        printNumIfTrue(clazz.getName().endsWith("Foo"), 8);
      }
      return NO_ID;
    }

    @Override
    public int onClassNewInstance(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Bar"), 50);
      return NO_ID;
    }

    @Override
    public int onClassGetSuperclass(Stack stack, Class<?> clazz) {
      printNumIfTrue(clazz.getName().endsWith("Foo"), 9);
      return NO_ID;
    }

    @Override
    public int onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {
      printNumIfTrue(holder.getName().endsWith("Bar"), 40);
      return NO_ID;
    }

    @Override
    public int onClassIsInstance(Stack stack, Class<?> holder, Object object) {
      printNumIfTrue(holder.getName().endsWith("Bar"), 41);
      return NO_ID;
    }

    @Override
    public int onClassCast(Stack stack, Class<?> holder, Object object) {
      printNumIfTrue(holder.getName().endsWith("Bar"), 42);
      return NO_ID;
    }
  }
}

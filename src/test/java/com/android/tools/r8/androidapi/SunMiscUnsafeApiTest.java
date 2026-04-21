// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ListUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This class verifies which sun.misc.Unsafe methods exist at which API levels. */
@RunWith(Parameterized.class)
public class SunMiscUnsafeApiTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParameters.builder().withDexRuntimes().withMaximumApiLevel().build();
  }

  @Test
  public void test() throws Exception {
    parameters.assumeDexRuntime();
    Class<?> testClass = TestClass.class;
    Map<ClassReference, AndroidApiLevel> classApis = new HashMap<>();
    Map<FieldReference, AndroidApiLevel> fieldApis = new HashMap<>();
    Map<MethodReference, AndroidApiLevel> methodApis = new HashMap<>();
    populateApiMaps(classApis, fieldApis, methodApis);
    testForD8(parameters)
        .addProgramClasses(testClass)
        .compile()
        .run(parameters.getRuntime(), testClass)
        .assertSuccess()
        .assertStdoutLinesMatchesUnordered(expectedOutput(classApis, fieldApis, methodApis));
  }

  private List<Matcher<String>> expectedOutput(
      Map<ClassReference, AndroidApiLevel> classApis,
      Map<FieldReference, AndroidApiLevel> fieldApis,
      Map<MethodReference, AndroidApiLevel> methodApis) {
    List<Matcher<String>> expectedMatchers = new ArrayList<>();
    AndroidApiLevel apiLevel = parameters.getApiLevel();
    classApis.forEach(
        (clazz, api) -> {
          boolean shouldBeDefined = apiLevel.isGreaterThanOrEqualTo(api);
          expectedMatchers.add(equalTo(clazz.getTypeName() + ": " + shouldBeDefined));
        });
    fieldApis.forEach(
        (field, api) -> {
          boolean shouldBeDefined = apiLevel.isGreaterThanOrEqualTo(api);
          expectedMatchers.add(equalTo(field.toSourceString() + ": " + shouldBeDefined));
        });
    methodApis.forEach(
        (method, api) -> {
          boolean shouldBeDefined = apiLevel.isGreaterThanOrEqualTo(api);
          expectedMatchers.add(equalTo(method.toSourceString() + ": " + shouldBeDefined));
        });
    return expectedMatchers;
  }

  public static void populateApiMaps(
      Map<ClassReference, AndroidApiLevel> classApis,
      Map<FieldReference, AndroidApiLevel> fieldApis,
      Map<MethodReference, AndroidApiLevel> methodApis) {
    AndroidApiLevelDatabaseHelper.addUnsafeMethods(
        // A short-lived item factory only used to extract non-canonical Reference-types.
        new DexItemFactory(),
        (reference, apiLevel) -> {
          if (reference.isDexType()) {
            ClassReference holder = reference.asDexType().asClassReference();
            classApis.put(holder, apiLevel);
          } else if (reference.isDexField()) {
            DexField field = reference.asDexField();
            ClassReference holder = field.getHolderType().asClassReference();
            TypeReference fieldType = field.type.asTypeReference();
            String name = field.name.toString();
            fieldApis.put(Reference.field(holder, name, fieldType), apiLevel);
          } else if (reference.isDexMethod()) {
            DexMethod method = reference.asDexMethod();
            ClassReference holder = method.getHolderType().asClassReference();
            TypeReference returnType = method.getReturnType().asTypeReference();
            List<TypeReference> parameters =
                ListUtils.map(method.getParameters().values, DexType::asTypeReference);
            String name = method.name.toString();
            methodApis.put(Reference.method(holder, name, parameters, returnType), apiLevel);
          } else {
            throw new RuntimeException("Unexpected API entry: " + reference);
          }
        });
  }

  static class TestClass {
    public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException {
      String UNSAFE = "sun.misc.Unsafe";

      checkClass(UNSAFE);

      checkField(UNSAFE, "INVALID_FIELD_OFFSET", int.class);

      checkMethod(UNSAFE, "addressSize", int.class);
      checkMethod(UNSAFE, "allocateInstance", Object.class, Class.class);
      checkMethod(UNSAFE, "allocateMemory", long.class, long.class);
      checkMethod(UNSAFE, "arrayBaseOffset", int.class, Class.class);
      checkMethod(UNSAFE, "arrayIndexScale", int.class, Class.class);
      checkMethod(
          UNSAFE,
          "compareAndSwapInt",
          boolean.class,
          Object.class,
          long.class,
          int.class,
          int.class);
      checkMethod(
          UNSAFE,
          "compareAndSwapLong",
          boolean.class,
          Object.class,
          long.class,
          long.class,
          long.class);
      checkMethod(
          UNSAFE,
          "compareAndSwapObject",
          boolean.class,
          Object.class,
          long.class,
          Object.class,
          Object.class);
      checkMethod(UNSAFE, "copyMemory", void.class, long.class, long.class, long.class);
      checkMethod(
          UNSAFE,
          "copyMemoryFromPrimitiveArray",
          void.class,
          Object.class,
          long.class,
          long.class,
          long.class);
      checkMethod(
          UNSAFE,
          "copyMemoryToPrimitiveArray",
          void.class,
          long.class,
          Object.class,
          long.class,
          long.class);
      checkMethod(UNSAFE, "freeMemory", void.class, long.class);
      checkMethod(UNSAFE, "fullFence", void.class);
      checkMethod(UNSAFE, "getAndAddInt", int.class, Object.class, long.class, int.class);
      checkMethod(UNSAFE, "getAndAddLong", long.class, Object.class, long.class, long.class);
      checkMethod(UNSAFE, "getAndSetInt", int.class, Object.class, long.class, int.class);
      checkMethod(UNSAFE, "getAndSetLong", long.class, Object.class, long.class, long.class);
      checkMethod(UNSAFE, "getAndSetObject", Object.class, Object.class, long.class, Object.class);
      checkMethod(UNSAFE, "getBoolean", boolean.class, Object.class, long.class);
      checkMethod(UNSAFE, "getByte", byte.class, Object.class, long.class);
      checkMethod(UNSAFE, "getByte", byte.class, long.class);
      checkMethod(UNSAFE, "getChar", char.class, Object.class, long.class);
      checkMethod(UNSAFE, "getChar", char.class, long.class);
      checkMethod(UNSAFE, "getDouble", double.class, Object.class, long.class);
      checkMethod(UNSAFE, "getDouble", double.class, long.class);
      checkMethod(UNSAFE, "getFloat", float.class, Object.class, long.class);
      checkMethod(UNSAFE, "getFloat", float.class, long.class);
      checkMethod(UNSAFE, "getInt", int.class, Object.class, long.class);
      checkMethod(UNSAFE, "getInt", int.class, long.class);
      checkMethod(UNSAFE, "getIntVolatile", int.class, Object.class, long.class);
      checkMethod(UNSAFE, "getLong", long.class, Object.class, long.class);
      checkMethod(UNSAFE, "getLong", long.class, long.class);
      checkMethod(UNSAFE, "getLongVolatile", long.class, Object.class, long.class);
      checkMethod(UNSAFE, "getObject", Object.class, Object.class, long.class);
      checkMethod(UNSAFE, "getObjectVolatile", Object.class, Object.class, long.class);
      checkMethod(UNSAFE, "getShort", short.class, Object.class, long.class);
      checkMethod(UNSAFE, "getShort", short.class, long.class);
      checkMethod(UNSAFE, "getUnsafe", UNSAFE);
      checkMethod(UNSAFE, "loadFence", void.class);
      checkMethod(UNSAFE, "objectFieldOffset", long.class, Field.class);
      checkMethod(UNSAFE, "pageSize", int.class);
      checkMethod(UNSAFE, "park", void.class, boolean.class, long.class);
      checkMethod(UNSAFE, "putBoolean", void.class, Object.class, long.class, boolean.class);
      checkMethod(UNSAFE, "putByte", void.class, Object.class, long.class, byte.class);
      checkMethod(UNSAFE, "putByte", void.class, long.class, byte.class);
      checkMethod(UNSAFE, "putChar", void.class, Object.class, long.class, char.class);
      checkMethod(UNSAFE, "putChar", void.class, long.class, char.class);
      checkMethod(UNSAFE, "putDouble", void.class, Object.class, long.class, double.class);
      checkMethod(UNSAFE, "putDouble", void.class, long.class, double.class);
      checkMethod(UNSAFE, "putFloat", void.class, Object.class, long.class, float.class);
      checkMethod(UNSAFE, "putFloat", void.class, long.class, float.class);
      checkMethod(UNSAFE, "putInt", void.class, Object.class, long.class, int.class);
      checkMethod(UNSAFE, "putInt", void.class, long.class, int.class);
      checkMethod(UNSAFE, "putIntVolatile", void.class, Object.class, long.class, int.class);
      checkMethod(UNSAFE, "putLong", void.class, Object.class, long.class, long.class);
      checkMethod(UNSAFE, "putLong", void.class, long.class, long.class);
      checkMethod(UNSAFE, "putLongVolatile", void.class, Object.class, long.class, long.class);
      checkMethod(UNSAFE, "putObject", void.class, Object.class, long.class, Object.class);
      checkMethod(UNSAFE, "putObjectVolatile", void.class, Object.class, long.class, Object.class);
      checkMethod(UNSAFE, "putOrderedInt", void.class, Object.class, long.class, int.class);
      checkMethod(UNSAFE, "putOrderedLong", void.class, Object.class, long.class, long.class);
      checkMethod(UNSAFE, "putOrderedObject", void.class, Object.class, long.class, Object.class);
      checkMethod(UNSAFE, "putShort", void.class, Object.class, long.class, short.class);
      checkMethod(UNSAFE, "putShort", void.class, long.class, short.class);
      checkMethod(UNSAFE, "setMemory", void.class, long.class, long.class, byte.class);
      checkMethod(UNSAFE, "storeFence", void.class);
      checkMethod(UNSAFE, "unpark", void.class, Object.class);
    }

    private static void checkClass(String name) {
      boolean doesClassExit = getClass(name) != null;
      System.out.println(name + ": " + doesClassExit);
    }

    private static Class<?> getClass(String name) {
      try {
        return Class.forName(name);
      } catch (ClassNotFoundException e) {
        return null;
      }
    }

    private static void checkField(String holder, String name, Class<?> type) {
      printField(holder, name, typeString(type), doesUnsafeFieldExist(holder, name, type));
    }

    private static void checkField(String holder, String name, String type) {
      Class<?> fieldType = getClass(type);
      if (fieldType == null) {
        printField(holder, name, type, false);
      } else {
        checkField(holder, name, fieldType);
      }
    }

    private static String typeString(Class<?> type) {
      return type.getCanonicalName();
    }

    private static boolean doesUnsafeFieldExist(String holder, String name, Class<?> type) {
      Class<?> holderClass = getClass(holder);
      if (holderClass == null) {
        return false;
      }
      try {
        Field field = holderClass.getDeclaredField(name);
        return type.equals(field.getType());
      } catch (NoSuchFieldException e) {
        return false;
      }
    }

    private static void printField(String holder, String name, String type, boolean found) {
      System.out.println(type + " " + holder + "." + name + ": " + found);
    }

    private static void checkMethod(
        String holder, String name, Class<?> returnType, Class<?>... parameterTypes) {
      boolean doesExist = doesMethodExist(holder, name, returnType, parameterTypes);
      String[] params = new String[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; i++) {
        params[i] = typeString(parameterTypes[i]);
      }
      printMethod(holder, name, typeString(returnType), params, doesExist);
    }

    private static void checkMethod(String holder, String name, String returnType) {
      Class<?> returnTypeClass = getClass(returnType);
      if (returnTypeClass == null) {
        printMethod(holder, name, returnType, new String[0], false);
      } else {
        checkMethod(holder, name, returnTypeClass);
      }
    }

    private static void printMethod(
        String holder, String name, String returnType, String[] parameterTypes, boolean found) {
      String output =
          returnType
              + " "
              + holder
              + "."
              + name
              + "("
              + String.join(", ", parameterTypes)
              + "): "
              + found;
      System.out.println(output);
    }

    private static boolean doesMethodExist(
        String holder, String name, Class<?> returnType, Class<?>... parameterTypes) {
      Class<?> holderClass = getClass(holder);
      if (holderClass == null) {
        return false;
      }
      try {
        Method method = holderClass.getDeclaredMethod(name, parameterTypes);
        return returnType.equals(method.getReturnType());
      } catch (NoSuchMethodException e) {
        return false;
      }
    }
  }

}

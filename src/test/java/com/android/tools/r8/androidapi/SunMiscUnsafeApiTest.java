// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static com.android.tools.r8.references.Reference.BOOL;
import static com.android.tools.r8.references.Reference.BYTE;
import static com.android.tools.r8.references.Reference.CHAR;
import static com.android.tools.r8.references.Reference.DOUBLE;
import static com.android.tools.r8.references.Reference.FLOAT;
import static com.android.tools.r8.references.Reference.INT;
import static com.android.tools.r8.references.Reference.LONG;
import static com.android.tools.r8.references.Reference.SHORT;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
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

  public static final Map<ClassReference, AndroidApiLevel> classApiMap = new HashMap<>();
  public static final Map<FieldReference, AndroidApiLevel> fieldApiMap = new HashMap<>();
  public static final Map<MethodReference, AndroidApiLevel> methodApiMap = new HashMap<>();

  private static final ClassReference CLASS = Reference.classFromClass(Class.class);
  private static final ClassReference OBJECT = Reference.classFromClass(Object.class);
  private static final ClassReference FIELD = Reference.classFromClass(Field.class);
  private static final TypeReference VOID = Reference.returnTypeFromDescriptor("V");

  private static void addClass(ClassReference holder, AndroidApiLevel introducedAtApi) {
    classApiMap.put(holder, introducedAtApi);
  }

  private static void addField(
      ClassReference holder, String name, AndroidApiLevel introducedAtApi, TypeReference type) {
    fieldApiMap.put(Reference.field(holder, name, type), introducedAtApi);
  }

  private static void addMethod(
      ClassReference holder,
      String name,
      AndroidApiLevel introducedAtApi,
      TypeReference returnType,
      TypeReference... parameters) {
    methodApiMap.put(
        Reference.method(holder, name, ImmutableList.copyOf(parameters), returnType),
        introducedAtApi);
  }

  @Test
  public void test() throws Exception {
    parameters.assumeDexRuntime();
    Class<?> testClass = TestClass.class;
    testForD8(parameters)
        .addProgramClasses(testClass)
        .compile()
        .run(parameters.getRuntime(), testClass)
        .assertSuccess()
        .assertStdoutLinesMatchesUnordered(expectedOutput());
  }

  private List<Matcher<String>> expectedOutput() {
    List<Matcher<String>> expectedMatchers = new ArrayList<>();
    AndroidApiLevel apiLevel = parameters.getApiLevel();
    classApiMap.forEach(
        (clazz, api) -> {
          boolean shouldBeDefined = apiLevel.isGreaterThanOrEqualTo(api);
          expectedMatchers.add(equalTo(clazz.getTypeName() + ": " + shouldBeDefined));
        });
    fieldApiMap.forEach(
        (field, api) -> {
          boolean shouldBeDefined = apiLevel.isGreaterThanOrEqualTo(api);
          expectedMatchers.add(equalTo(field.toSourceString() + ": " + shouldBeDefined));
        });
    methodApiMap.forEach(
        (method, api) -> {
          boolean shouldBeDefined = apiLevel.isGreaterThanOrEqualTo(api);
          expectedMatchers.add(equalTo(method.toSourceString() + ": " + shouldBeDefined));
        });
    return expectedMatchers;
  }

  static {
    ClassReference sunUnsafe = Reference.classFromBinaryName("sun/misc/Unsafe");
    TypeReference sunUnsafeType = Reference.typeFromClassReference(sunUnsafe);
    ClassReference jdkUnsafe = Reference.classFromBinaryName("jdk/internal/misc/Unsafe");
    TypeReference jdkUnsafeType = Reference.typeFromClassReference(jdkUnsafe);

    // This should be set to the lowest API tested NOT the lowest possible API.
    AndroidApiLevel always = AndroidApiLevel.I_MR1;

    addClass(sunUnsafe, always);

    addField(sunUnsafe, "INVALID_FIELD_OFFSET", AndroidApiLevel.N, Reference.INT);
    addField(sunUnsafe, "THE_ONE", always, sunUnsafeType);
    addField(sunUnsafe, "theInternalUnsafe", AndroidApiLevel.MAIN, jdkUnsafeType);
    addField(sunUnsafe, "theUnsafe", AndroidApiLevel.K, sunUnsafeType);

    addMethod(sunUnsafe, "addressSize", AndroidApiLevel.N, INT);
    addMethod(sunUnsafe, "allocateInstance", AndroidApiLevel.J, OBJECT, CLASS);
    addMethod(sunUnsafe, "allocateMemory", AndroidApiLevel.N, LONG, LONG);
    addMethod(sunUnsafe, "arrayBaseOffset", always, INT, CLASS);
    addMethod(sunUnsafe, "arrayIndexScale", always, INT, CLASS);
    addMethod(sunUnsafe, "compareAndSwapInt", always, BOOL, OBJECT, LONG, INT, INT);
    addMethod(sunUnsafe, "compareAndSwapLong", always, BOOL, OBJECT, LONG, LONG, LONG);
    addMethod(sunUnsafe, "compareAndSwapObject", always, BOOL, OBJECT, LONG, OBJECT, OBJECT);
    addMethod(sunUnsafe, "copyMemory", AndroidApiLevel.N, VOID, LONG, LONG, LONG);
    addMethod(
        sunUnsafe,
        "copyMemoryFromPrimitiveArray",
        AndroidApiLevel.N,
        VOID,
        OBJECT,
        LONG,
        LONG,
        LONG);
    addMethod(
        sunUnsafe, "copyMemoryToPrimitiveArray", AndroidApiLevel.N, VOID, LONG, OBJECT, LONG, LONG);
    addMethod(sunUnsafe, "forbidObtainingRecordFieldOffsets", AndroidApiLevel.MAIN, BOOL);
    addMethod(sunUnsafe, "freeMemory", AndroidApiLevel.N, VOID, LONG);
    addMethod(sunUnsafe, "fullFence", AndroidApiLevel.N, VOID);
    addMethod(sunUnsafe, "getAndAddInt", AndroidApiLevel.N, INT, OBJECT, LONG, INT);
    addMethod(sunUnsafe, "getAndAddLong", AndroidApiLevel.N, LONG, OBJECT, LONG, LONG);
    addMethod(sunUnsafe, "getAndSetInt", AndroidApiLevel.N, INT, OBJECT, LONG, INT);
    addMethod(sunUnsafe, "getAndSetLong", AndroidApiLevel.N, LONG, OBJECT, LONG, LONG);
    addMethod(sunUnsafe, "getAndSetObject", AndroidApiLevel.N, OBJECT, OBJECT, LONG, OBJECT);
    addMethod(sunUnsafe, "getArrayBaseOffsetForComponentType", AndroidApiLevel.L_MR1, INT, CLASS);
    addMethod(sunUnsafe, "getArrayIndexScaleForComponentType", AndroidApiLevel.L_MR1, INT, CLASS);
    addMethod(sunUnsafe, "getBoolean", AndroidApiLevel.N, BOOL, OBJECT, LONG);
    addMethod(sunUnsafe, "getByte", AndroidApiLevel.N, BYTE, LONG);
    addMethod(sunUnsafe, "getByte", AndroidApiLevel.N, BYTE, OBJECT, LONG);
    addMethod(sunUnsafe, "getChar", AndroidApiLevel.N, CHAR, LONG);
    addMethod(sunUnsafe, "getChar", AndroidApiLevel.N, CHAR, OBJECT, LONG);
    addMethod(sunUnsafe, "getDouble", AndroidApiLevel.N, DOUBLE, LONG);
    addMethod(sunUnsafe, "getDouble", AndroidApiLevel.N, DOUBLE, OBJECT, LONG);
    addMethod(sunUnsafe, "getFloat", AndroidApiLevel.N, FLOAT, LONG);
    addMethod(sunUnsafe, "getFloat", AndroidApiLevel.N, FLOAT, OBJECT, LONG);
    addMethod(sunUnsafe, "getInt", AndroidApiLevel.N, INT, LONG);
    addMethod(sunUnsafe, "getInt", always, INT, OBJECT, LONG);
    addMethod(sunUnsafe, "getIntVolatile", always, INT, OBJECT, LONG);
    addMethod(sunUnsafe, "getLong", AndroidApiLevel.N, LONG, LONG);
    addMethod(sunUnsafe, "getLong", always, LONG, OBJECT, LONG);
    addMethod(sunUnsafe, "getLongVolatile", always, LONG, OBJECT, LONG);
    addMethod(sunUnsafe, "getObject", always, OBJECT, OBJECT, LONG);
    addMethod(sunUnsafe, "getObjectVolatile", always, OBJECT, OBJECT, LONG);
    addMethod(sunUnsafe, "getShort", AndroidApiLevel.N, SHORT, LONG);
    addMethod(sunUnsafe, "getShort", AndroidApiLevel.N, SHORT, OBJECT, LONG);
    addMethod(sunUnsafe, "getUnsafe", always, sunUnsafe);
    addMethod(sunUnsafe, "loadFence", AndroidApiLevel.N, VOID);
    addMethod(sunUnsafe, "objectFieldOffset", always, LONG, FIELD);
    addMethod(sunUnsafe, "pageSize", AndroidApiLevel.N, INT);
    addMethod(sunUnsafe, "park", always, VOID, BOOL, LONG);
    addMethod(sunUnsafe, "putBoolean", AndroidApiLevel.N, VOID, OBJECT, LONG, BOOL);
    addMethod(sunUnsafe, "putByte", AndroidApiLevel.N, VOID, LONG, BYTE);
    addMethod(sunUnsafe, "putByte", AndroidApiLevel.N, VOID, OBJECT, LONG, BYTE);
    addMethod(sunUnsafe, "putChar", AndroidApiLevel.N, VOID, LONG, CHAR);
    addMethod(sunUnsafe, "putChar", AndroidApiLevel.N, VOID, OBJECT, LONG, CHAR);
    addMethod(sunUnsafe, "putDouble", AndroidApiLevel.N, VOID, LONG, DOUBLE);
    addMethod(sunUnsafe, "putDouble", AndroidApiLevel.N, VOID, OBJECT, LONG, DOUBLE);
    addMethod(sunUnsafe, "putFloat", AndroidApiLevel.N, VOID, LONG, FLOAT);
    addMethod(sunUnsafe, "putFloat", AndroidApiLevel.N, VOID, OBJECT, LONG, FLOAT);
    addMethod(sunUnsafe, "putInt", AndroidApiLevel.N, VOID, LONG, INT);
    addMethod(sunUnsafe, "putInt", always, VOID, OBJECT, LONG, INT);
    addMethod(sunUnsafe, "putIntVolatile", always, VOID, OBJECT, LONG, INT);
    addMethod(sunUnsafe, "putLong", AndroidApiLevel.N, VOID, LONG, LONG);
    addMethod(sunUnsafe, "putLong", always, VOID, OBJECT, LONG, LONG);
    addMethod(sunUnsafe, "putLongVolatile", always, VOID, OBJECT, LONG, LONG);
    addMethod(sunUnsafe, "putObject", always, VOID, OBJECT, LONG, OBJECT);
    addMethod(sunUnsafe, "putObjectVolatile", always, VOID, OBJECT, LONG, OBJECT);
    addMethod(sunUnsafe, "putOrderedInt", always, VOID, OBJECT, LONG, INT);
    addMethod(sunUnsafe, "putOrderedLong", always, VOID, OBJECT, LONG, LONG);
    addMethod(sunUnsafe, "putOrderedObject", always, VOID, OBJECT, LONG, OBJECT);
    addMethod(sunUnsafe, "putShort", AndroidApiLevel.N, VOID, LONG, SHORT);
    addMethod(sunUnsafe, "putShort", AndroidApiLevel.N, VOID, OBJECT, LONG, SHORT);
    addMethod(sunUnsafe, "setMemory", AndroidApiLevel.N, VOID, LONG, LONG, BYTE);
    addMethod(sunUnsafe, "storeFence", AndroidApiLevel.N, VOID);
    addMethod(sunUnsafe, "unpark", always, VOID, OBJECT);
  }

  static class TestClass {
    public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException {
      String UNSAFE = "sun.misc.Unsafe";

      checkClass(UNSAFE);

      checkField(UNSAFE, "INVALID_FIELD_OFFSET", int.class);
      checkField(UNSAFE, "THE_ONE", UNSAFE);
      checkField(UNSAFE, "theInternalUnsafe", "jdk.internal.misc.Unsafe");
      checkField(UNSAFE, "theUnsafe", UNSAFE);

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
      checkMethod(UNSAFE, "forbidObtainingRecordFieldOffsets", boolean.class);
      checkMethod(UNSAFE, "freeMemory", void.class, long.class);
      checkMethod(UNSAFE, "fullFence", void.class);
      checkMethod(UNSAFE, "getAndAddInt", int.class, Object.class, long.class, int.class);
      checkMethod(UNSAFE, "getAndAddLong", long.class, Object.class, long.class, long.class);
      checkMethod(UNSAFE, "getAndSetInt", int.class, Object.class, long.class, int.class);
      checkMethod(UNSAFE, "getAndSetLong", long.class, Object.class, long.class, long.class);
      checkMethod(UNSAFE, "getAndSetObject", Object.class, Object.class, long.class, Object.class);
      checkMethod(UNSAFE, "getArrayBaseOffsetForComponentType", int.class, Class.class);
      checkMethod(UNSAFE, "getArrayIndexScaleForComponentType", int.class, Class.class);
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

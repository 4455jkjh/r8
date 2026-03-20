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

  private static final Map<ClassReference, AndroidApiLevel> classApiMap = new HashMap<>();
  private static final Map<FieldReference, AndroidApiLevel> fieldApiMap = new HashMap<>();
  private static final Map<MethodReference, AndroidApiLevel> methodApiMap = new HashMap<>();

  private static final ClassReference SUN_MISC_UNSAFE =
      Reference.classFromBinaryName("sun/misc/Unsafe");
  private static final TypeReference SUN_MISC_UNSAFE_TYPE =
      Reference.typeFromClassReference(SUN_MISC_UNSAFE);
  private static final TypeReference JDK_INTERNAL_MISC_UNSAFE_TYPE =
      Reference.typeFromClassReference(Reference.classFromBinaryName("jdk/internal/misc/Unsafe"));
  private static final ClassReference CLASS = Reference.classFromClass(Class.class);
  private static final ClassReference OBJECT = Reference.classFromClass(Object.class);
  private static final ClassReference FIELD = Reference.classFromClass(Field.class);
  private static final TypeReference VOID = Reference.returnTypeFromDescriptor("V");

  private static void addClass(AndroidApiLevel introducedAtApi) {
    classApiMap.put(SUN_MISC_UNSAFE, introducedAtApi);
  }

  private static void addField(String name, AndroidApiLevel introducedAtApi, TypeReference type) {
    fieldApiMap.put(Reference.field(SUN_MISC_UNSAFE, name, type), introducedAtApi);
  }

  private static void addMethod(
      String name,
      AndroidApiLevel introducedAtApi,
      TypeReference returnType,
      TypeReference... parameters) {
    methodApiMap.put(
        Reference.method(SUN_MISC_UNSAFE, name, ImmutableList.copyOf(parameters), returnType),
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
    // This should be set to the lowest API tested NOT the lowest possible API.
    AndroidApiLevel always = AndroidApiLevel.I_MR1;

    addClass(always);

    addField("INVALID_FIELD_OFFSET", AndroidApiLevel.N, Reference.INT);
    addField("THE_ONE", always, SUN_MISC_UNSAFE_TYPE);
    addField("theInternalUnsafe", AndroidApiLevel.MAIN, JDK_INTERNAL_MISC_UNSAFE_TYPE);
    addField("theUnsafe", AndroidApiLevel.K, SUN_MISC_UNSAFE_TYPE);

    addMethod("addressSize", AndroidApiLevel.N, INT);
    addMethod("allocateInstance", AndroidApiLevel.J, OBJECT, CLASS);
    addMethod("allocateMemory", AndroidApiLevel.N, LONG, LONG);
    addMethod("arrayBaseOffset", always, INT, CLASS);
    addMethod("arrayIndexScale", always, INT, CLASS);
    addMethod("compareAndSwapInt", always, BOOL, OBJECT, LONG, INT, INT);
    addMethod("compareAndSwapLong", always, BOOL, OBJECT, LONG, LONG, LONG);
    addMethod("compareAndSwapObject", always, BOOL, OBJECT, LONG, OBJECT, OBJECT);
    addMethod("copyMemory", AndroidApiLevel.N, VOID, LONG, LONG, LONG);
    addMethod("copyMemoryFromPrimitiveArray", AndroidApiLevel.N, VOID, OBJECT, LONG, LONG, LONG);
    addMethod("copyMemoryToPrimitiveArray", AndroidApiLevel.N, VOID, LONG, OBJECT, LONG, LONG);
    addMethod("forbidObtainingRecordFieldOffsets", AndroidApiLevel.MAIN, BOOL);
    addMethod("freeMemory", AndroidApiLevel.N, VOID, LONG);
    addMethod("fullFence", AndroidApiLevel.N, VOID);
    addMethod("getAndAddInt", AndroidApiLevel.N, INT, OBJECT, LONG, INT);
    addMethod("getAndAddLong", AndroidApiLevel.N, LONG, OBJECT, LONG, LONG);
    addMethod("getAndSetInt", AndroidApiLevel.N, INT, OBJECT, LONG, INT);
    addMethod("getAndSetLong", AndroidApiLevel.N, LONG, OBJECT, LONG, LONG);
    addMethod("getAndSetObject", AndroidApiLevel.N, OBJECT, OBJECT, LONG, OBJECT);
    addMethod("getArrayBaseOffsetForComponentType", AndroidApiLevel.L_MR1, INT, CLASS);
    addMethod("getArrayIndexScaleForComponentType", AndroidApiLevel.L_MR1, INT, CLASS);
    addMethod("getBoolean", AndroidApiLevel.N, BOOL, OBJECT, LONG);
    addMethod("getByte", AndroidApiLevel.N, BYTE, LONG);
    addMethod("getByte", AndroidApiLevel.N, BYTE, OBJECT, LONG);
    addMethod("getChar", AndroidApiLevel.N, CHAR, LONG);
    addMethod("getChar", AndroidApiLevel.N, CHAR, OBJECT, LONG);
    addMethod("getDouble", AndroidApiLevel.N, DOUBLE, LONG);
    addMethod("getDouble", AndroidApiLevel.N, DOUBLE, OBJECT, LONG);
    addMethod("getFloat", AndroidApiLevel.N, FLOAT, LONG);
    addMethod("getFloat", AndroidApiLevel.N, FLOAT, OBJECT, LONG);
    addMethod("getInt", AndroidApiLevel.N, INT, LONG);
    addMethod("getInt", always, INT, OBJECT, LONG);
    addMethod("getIntVolatile", always, INT, OBJECT, LONG);
    addMethod("getLong", AndroidApiLevel.N, LONG, LONG);
    addMethod("getLong", always, LONG, OBJECT, LONG);
    addMethod("getLongVolatile", always, LONG, OBJECT, LONG);
    addMethod("getObject", always, OBJECT, OBJECT, LONG);
    addMethod("getObjectVolatile", always, OBJECT, OBJECT, LONG);
    addMethod("getShort", AndroidApiLevel.N, SHORT, LONG);
    addMethod("getShort", AndroidApiLevel.N, SHORT, OBJECT, LONG);
    addMethod("getUnsafe", always, SUN_MISC_UNSAFE);
    addMethod("loadFence", AndroidApiLevel.N, VOID);
    addMethod("objectFieldOffset", always, LONG, FIELD);
    addMethod("pageSize", AndroidApiLevel.N, INT);
    addMethod("park", always, VOID, BOOL, LONG);
    addMethod("putBoolean", AndroidApiLevel.N, VOID, OBJECT, LONG, BOOL);
    addMethod("putByte", AndroidApiLevel.N, VOID, LONG, BYTE);
    addMethod("putByte", AndroidApiLevel.N, VOID, OBJECT, LONG, BYTE);
    addMethod("putChar", AndroidApiLevel.N, VOID, LONG, CHAR);
    addMethod("putChar", AndroidApiLevel.N, VOID, OBJECT, LONG, CHAR);
    addMethod("putDouble", AndroidApiLevel.N, VOID, LONG, DOUBLE);
    addMethod("putDouble", AndroidApiLevel.N, VOID, OBJECT, LONG, DOUBLE);
    addMethod("putFloat", AndroidApiLevel.N, VOID, LONG, FLOAT);
    addMethod("putFloat", AndroidApiLevel.N, VOID, OBJECT, LONG, FLOAT);
    addMethod("putInt", AndroidApiLevel.N, VOID, LONG, INT);
    addMethod("putInt", always, VOID, OBJECT, LONG, INT);
    addMethod("putIntVolatile", always, VOID, OBJECT, LONG, INT);
    addMethod("putLong", AndroidApiLevel.N, VOID, LONG, LONG);
    addMethod("putLong", always, VOID, OBJECT, LONG, LONG);
    addMethod("putLongVolatile", always, VOID, OBJECT, LONG, LONG);
    addMethod("putObject", always, VOID, OBJECT, LONG, OBJECT);
    addMethod("putObjectVolatile", always, VOID, OBJECT, LONG, OBJECT);
    addMethod("putOrderedInt", always, VOID, OBJECT, LONG, INT);
    addMethod("putOrderedLong", always, VOID, OBJECT, LONG, LONG);
    addMethod("putOrderedObject", always, VOID, OBJECT, LONG, OBJECT);
    addMethod("putShort", AndroidApiLevel.N, VOID, LONG, SHORT);
    addMethod("putShort", AndroidApiLevel.N, VOID, OBJECT, LONG, SHORT);
    addMethod("setMemory", AndroidApiLevel.N, VOID, LONG, LONG, BYTE);
    addMethod("storeFence", AndroidApiLevel.N, VOID);
    addMethod("unpark", always, VOID, OBJECT);
  }

  static class TestClass {
    private static final String UNSAFE = "sun.misc.Unsafe";

    public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException {
      checkClass(UNSAFE);

      checkField("INVALID_FIELD_OFFSET", int.class);
      checkField("THE_ONE", UNSAFE);
      checkField("theInternalUnsafe", "jdk.internal.misc.Unsafe");
      checkField("theUnsafe", UNSAFE);

      checkMethod("addressSize", int.class);
      checkMethod("allocateInstance", Object.class, Class.class);
      checkMethod("allocateMemory", long.class, long.class);
      checkMethod("arrayBaseOffset", int.class, Class.class);
      checkMethod("arrayIndexScale", int.class, Class.class);
      checkMethod(
          "compareAndSwapInt", boolean.class, Object.class, long.class, int.class, int.class);
      checkMethod(
          "compareAndSwapLong", boolean.class, Object.class, long.class, long.class, long.class);
      checkMethod(
          "compareAndSwapObject",
          boolean.class,
          Object.class,
          long.class,
          Object.class,
          Object.class);
      checkMethod("copyMemory", void.class, long.class, long.class, long.class);
      checkMethod(
          "copyMemoryFromPrimitiveArray",
          void.class,
          Object.class,
          long.class,
          long.class,
          long.class);
      checkMethod(
          "copyMemoryToPrimitiveArray",
          void.class,
          long.class,
          Object.class,
          long.class,
          long.class);
      checkMethod("forbidObtainingRecordFieldOffsets", boolean.class);
      checkMethod("freeMemory", void.class, long.class);
      checkMethod("fullFence", void.class);
      checkMethod("getAndAddInt", int.class, Object.class, long.class, int.class);
      checkMethod("getAndAddLong", long.class, Object.class, long.class, long.class);
      checkMethod("getAndSetInt", int.class, Object.class, long.class, int.class);
      checkMethod("getAndSetLong", long.class, Object.class, long.class, long.class);
      checkMethod("getAndSetObject", Object.class, Object.class, long.class, Object.class);
      checkMethod("getArrayBaseOffsetForComponentType", int.class, Class.class);
      checkMethod("getArrayIndexScaleForComponentType", int.class, Class.class);
      checkMethod("getBoolean", boolean.class, Object.class, long.class);
      checkMethod("getByte", byte.class, Object.class, long.class);
      checkMethod("getByte", byte.class, long.class);
      checkMethod("getChar", char.class, Object.class, long.class);
      checkMethod("getChar", char.class, long.class);
      checkMethod("getDouble", double.class, Object.class, long.class);
      checkMethod("getDouble", double.class, long.class);
      checkMethod("getFloat", float.class, Object.class, long.class);
      checkMethod("getFloat", float.class, long.class);
      checkMethod("getInt", int.class, Object.class, long.class);
      checkMethod("getInt", int.class, long.class);
      checkMethod("getIntVolatile", int.class, Object.class, long.class);
      checkMethod("getLong", long.class, Object.class, long.class);
      checkMethod("getLong", long.class, long.class);
      checkMethod("getLongVolatile", long.class, Object.class, long.class);
      checkMethod("getObject", Object.class, Object.class, long.class);
      checkMethod("getObjectVolatile", Object.class, Object.class, long.class);
      checkMethod("getShort", short.class, Object.class, long.class);
      checkMethod("getShort", short.class, long.class);
      checkMethod("getUnsafe", UNSAFE);
      checkMethod("loadFence", void.class);
      checkMethod("objectFieldOffset", long.class, Field.class);
      checkMethod("pageSize", int.class);
      checkMethod("park", void.class, boolean.class, long.class);
      checkMethod("putBoolean", void.class, Object.class, long.class, boolean.class);
      checkMethod("putByte", void.class, Object.class, long.class, byte.class);
      checkMethod("putByte", void.class, long.class, byte.class);
      checkMethod("putChar", void.class, Object.class, long.class, char.class);
      checkMethod("putChar", void.class, long.class, char.class);
      checkMethod("putDouble", void.class, Object.class, long.class, double.class);
      checkMethod("putDouble", void.class, long.class, double.class);
      checkMethod("putFloat", void.class, Object.class, long.class, float.class);
      checkMethod("putFloat", void.class, long.class, float.class);
      checkMethod("putInt", void.class, Object.class, long.class, int.class);
      checkMethod("putInt", void.class, long.class, int.class);
      checkMethod("putIntVolatile", void.class, Object.class, long.class, int.class);
      checkMethod("putLong", void.class, Object.class, long.class, long.class);
      checkMethod("putLong", void.class, long.class, long.class);
      checkMethod("putLongVolatile", void.class, Object.class, long.class, long.class);
      checkMethod("putObject", void.class, Object.class, long.class, Object.class);
      checkMethod("putObjectVolatile", void.class, Object.class, long.class, Object.class);
      checkMethod("putOrderedInt", void.class, Object.class, long.class, int.class);
      checkMethod("putOrderedLong", void.class, Object.class, long.class, long.class);
      checkMethod("putOrderedObject", void.class, Object.class, long.class, Object.class);
      checkMethod("putShort", void.class, Object.class, long.class, short.class);
      checkMethod("putShort", void.class, long.class, short.class);
      checkMethod("setMemory", void.class, long.class, long.class, byte.class);
      checkMethod("storeFence", void.class);
      checkMethod("unpark", void.class, Object.class);
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

    private static void checkField(String name, Class<?> type) {
      printField(name, typeString(type), doesUnsafeFieldExist(name, type));
    }

    private static void checkField(String name, String type) {
      Class<?> fieldType = getClass(type);
      if (fieldType == null) {
        printField(name, type, false);
      } else {
        checkField(name, fieldType);
      }
    }

    private static String typeString(Class<?> type) {
      return type.getCanonicalName();
    }

    private static boolean doesUnsafeFieldExist(String name, Class<?> type) {
      Class<?> unsafe = getClass(UNSAFE);
      if (unsafe == null) {
        return false;
      }
      try {
        Field field = unsafe.getDeclaredField(name);
        return type.equals(field.getType());
      } catch (NoSuchFieldException e) {
        return false;
      }
    }

    private static void printField(String name, String type, boolean found) {
      System.out.println(type + " " + UNSAFE + "." + name + ": " + found);
    }

    private static void checkMethod(String name, Class<?> returnType, Class<?>... parameterTypes) {
      boolean doesExist = doesMethodExist(name, returnType, parameterTypes);
      String[] params = new String[parameterTypes.length];
      for (int i = 0; i < parameterTypes.length; i++) {
        params[i] = typeString(parameterTypes[i]);
      }
      printMethod(name, typeString(returnType), params, doesExist);
    }

    private static void checkMethod(String name, String returnType) {
      Class<?> returnTypeClass = getClass(returnType);
      if (returnTypeClass == null) {
        printMethod(name, returnType, new String[0], false);
      } else {
        checkMethod(name, returnTypeClass);
      }
    }

    private static void printMethod(
        String name, String returnType, String[] parameterTypes, boolean found) {
      String output =
          returnType
              + " "
              + UNSAFE
              + "."
              + name
              + "("
              + String.join(", ", parameterTypes)
              + "): "
              + found;
      System.out.println(output);
    }

    private static boolean doesMethodExist(
        String name, Class<?> returnType, Class<?>... parameterTypes) {
      Class<?> unsafe = getClass(UNSAFE);
      if (unsafe == null) {
        return false;
      }
      try {
        Method method = unsafe.getDeclaredMethod(name, parameterTypes);
        return returnType.equals(method.getReturnType());
      } catch (NoSuchMethodException e) {
        return false;
      }
    }
  }

}

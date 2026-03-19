// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.lang.reflect.Field;
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

  private static class MethodReference {

    public final String name;
    public final Class<?>[] params;

    private MethodReference(String name, Class<?>[] params) {
      this.name = name;
      this.params = params;
    }
  }

  private static final Map<MethodReference, AndroidApiLevel> apiMap = new HashMap<>();

  private static void addMethod(
      AndroidApiLevel introducedAtApi, String name, Class<?>... parameterTypes) {
    apiMap.put(new MethodReference(name, parameterTypes), introducedAtApi);
  }

  @Test
  public void verifyApi() throws Exception {
    parameters.assumeDexRuntime();
    Class<TestClass> testClass = TestClass.class;
    List<Matcher<String>> expectedMatchers = new ArrayList<>(apiMap.size());
    apiMap.forEach(
        (method, api) ->
            expectedMatchers.add(
                equalTo(
                    method.name
                        + "("
                        + TestClass.parameterString(method.params)
                        + "): "
                        + (parameters.getApiLevel().isGreaterThanOrEqualTo(api)))));
    testForD8(parameters)
        .addProgramClasses(testClass)
        .compile()
        .run(parameters.getRuntime(), testClass)
        .assertSuccess()
        .assertStdoutLinesMatchesUnordered(expectedMatchers);
  }

  static {
    AndroidApiLevel always = AndroidApiLevel.I_MR1;
    addMethod(AndroidApiLevel.N, "addressSize");
    addMethod(AndroidApiLevel.J, "allocateInstance", Class.class);
    addMethod(AndroidApiLevel.N, "allocateMemory", long.class);
    addMethod(always, "arrayBaseOffset", Class.class);
    addMethod(always, "arrayIndexScale", Class.class);
    addMethod(always, "compareAndSwapInt", Object.class, long.class, int.class, int.class);
    addMethod(always, "compareAndSwapLong", Object.class, long.class, long.class, long.class);
    addMethod(always, "compareAndSwapObject", Object.class, long.class, Object.class, Object.class);
    addMethod(AndroidApiLevel.N, "copyMemory", long.class, long.class, long.class);
    addMethod(
        AndroidApiLevel.N,
        "copyMemoryFromPrimitiveArray",
        Object.class,
        long.class,
        long.class,
        long.class);
    addMethod(
        AndroidApiLevel.N,
        "copyMemoryToPrimitiveArray",
        long.class,
        Object.class,
        long.class,
        long.class);
    addMethod(AndroidApiLevel.MAIN, "forbidObtainingRecordFieldOffsets");
    addMethod(AndroidApiLevel.N, "freeMemory", long.class);
    addMethod(AndroidApiLevel.N, "fullFence");
    addMethod(AndroidApiLevel.N, "getAndAddInt", Object.class, long.class, int.class);
    addMethod(AndroidApiLevel.N, "getAndAddLong", Object.class, long.class, long.class);
    addMethod(AndroidApiLevel.N, "getAndSetInt", Object.class, long.class, int.class);
    addMethod(AndroidApiLevel.N, "getAndSetLong", Object.class, long.class, long.class);
    addMethod(AndroidApiLevel.N, "getAndSetObject", Object.class, long.class, Object.class);
    addMethod(AndroidApiLevel.L_MR1, "getArrayBaseOffsetForComponentType", Class.class);
    addMethod(AndroidApiLevel.L_MR1, "getArrayIndexScaleForComponentType", Class.class);
    addMethod(AndroidApiLevel.N, "getBoolean", Object.class, long.class);
    addMethod(AndroidApiLevel.N, "getByte", Object.class, long.class);
    addMethod(AndroidApiLevel.N, "getByte", long.class);
    addMethod(AndroidApiLevel.N, "getChar", Object.class, long.class);
    addMethod(AndroidApiLevel.N, "getChar", long.class);
    addMethod(AndroidApiLevel.N, "getDouble", Object.class, long.class);
    addMethod(AndroidApiLevel.N, "getDouble", long.class);
    addMethod(AndroidApiLevel.N, "getFloat", Object.class, long.class);
    addMethod(AndroidApiLevel.N, "getFloat", long.class);
    addMethod(always, "getInt", Object.class, long.class);
    addMethod(AndroidApiLevel.N, "getInt", long.class);
    addMethod(always, "getIntVolatile", Object.class, long.class);
    addMethod(always, "getLong", Object.class, long.class);
    addMethod(AndroidApiLevel.N, "getLong", long.class);
    addMethod(always, "getLongVolatile", Object.class, long.class);
    addMethod(always, "getObject", Object.class, long.class);
    addMethod(always, "getObjectVolatile", Object.class, long.class);
    addMethod(AndroidApiLevel.N, "getShort", Object.class, long.class);
    addMethod(AndroidApiLevel.N, "getShort", long.class);
    addMethod(always, "getUnsafe");
    addMethod(AndroidApiLevel.N, "loadFence");
    addMethod(always, "objectFieldOffset", Field.class);
    addMethod(AndroidApiLevel.N, "pageSize");
    addMethod(always, "park", boolean.class, long.class);
    addMethod(AndroidApiLevel.N, "putBoolean", Object.class, long.class, boolean.class);
    addMethod(AndroidApiLevel.N, "putByte", Object.class, long.class, byte.class);
    addMethod(AndroidApiLevel.N, "putByte", long.class, byte.class);
    addMethod(AndroidApiLevel.N, "putChar", Object.class, long.class, char.class);
    addMethod(AndroidApiLevel.N, "putChar", long.class, char.class);
    addMethod(AndroidApiLevel.N, "putDouble", Object.class, long.class, double.class);
    addMethod(AndroidApiLevel.N, "putDouble", long.class, double.class);
    addMethod(AndroidApiLevel.N, "putFloat", Object.class, long.class, float.class);
    addMethod(AndroidApiLevel.N, "putFloat", long.class, float.class);
    addMethod(always, "putInt", Object.class, long.class, int.class);
    addMethod(AndroidApiLevel.N, "putInt", long.class, int.class);
    addMethod(always, "putIntVolatile", Object.class, long.class, int.class);
    addMethod(always, "putLong", Object.class, long.class, long.class);
    addMethod(AndroidApiLevel.N, "putLong", long.class, long.class);
    addMethod(always, "putLongVolatile", Object.class, long.class, long.class);
    addMethod(always, "putObject", Object.class, long.class, Object.class);
    addMethod(always, "putObjectVolatile", Object.class, long.class, Object.class);
    addMethod(always, "putOrderedInt", Object.class, long.class, int.class);
    addMethod(always, "putOrderedLong", Object.class, long.class, long.class);
    addMethod(always, "putOrderedObject", Object.class, long.class, Object.class);
    addMethod(AndroidApiLevel.N, "putShort", Object.class, long.class, short.class);
    addMethod(AndroidApiLevel.N, "putShort", long.class, short.class);
    addMethod(AndroidApiLevel.N, "setMemory", long.class, long.class, byte.class);
    addMethod(AndroidApiLevel.N, "storeFence");
    addMethod(always, "unpark", Object.class);
  }

  static class TestClass {

    public static void main(String[] args) throws ClassNotFoundException, NoSuchMethodException {
      printExistence("addressSize");
      printExistence("allocateInstance", Class.class);
      printExistence("allocateMemory", long.class);
      printExistence("arrayBaseOffset", Class.class);
      printExistence("arrayIndexScale", Class.class);
      printExistence("compareAndSwapInt", Object.class, long.class, int.class, int.class);
      printExistence("compareAndSwapLong", Object.class, long.class, long.class, long.class);
      printExistence("compareAndSwapObject", Object.class, long.class, Object.class, Object.class);
      printExistence("copyMemory", long.class, long.class, long.class);
      printExistence(
          "copyMemoryFromPrimitiveArray", Object.class, long.class, long.class, long.class);
      printExistence(
          "copyMemoryToPrimitiveArray", long.class, Object.class, long.class, long.class);
      printExistence("forbidObtainingRecordFieldOffsets");
      printExistence("freeMemory", long.class);
      printExistence("fullFence");
      printExistence("getAndAddInt", Object.class, long.class, int.class);
      printExistence("getAndAddLong", Object.class, long.class, long.class);
      printExistence("getAndSetInt", Object.class, long.class, int.class);
      printExistence("getAndSetLong", Object.class, long.class, long.class);
      printExistence("getAndSetObject", Object.class, long.class, Object.class);
      printExistence("getArrayBaseOffsetForComponentType", Class.class);
      printExistence("getArrayIndexScaleForComponentType", Class.class);
      printExistence("getBoolean", Object.class, long.class);
      printExistence("getByte", Object.class, long.class);
      printExistence("getByte", long.class);
      printExistence("getChar", Object.class, long.class);
      printExistence("getChar", long.class);
      printExistence("getDouble", Object.class, long.class);
      printExistence("getDouble", long.class);
      printExistence("getFloat", Object.class, long.class);
      printExistence("getFloat", long.class);
      printExistence("getInt", Object.class, long.class);
      printExistence("getInt", long.class);
      printExistence("getIntVolatile", Object.class, long.class);
      printExistence("getLong", Object.class, long.class);
      printExistence("getLong", long.class);
      printExistence("getLongVolatile", Object.class, long.class);
      printExistence("getObject", Object.class, long.class);
      printExistence("getObjectVolatile", Object.class, long.class);
      printExistence("getShort", Object.class, long.class);
      printExistence("getShort", long.class);
      printExistence("getUnsafe");
      printExistence("loadFence");
      printExistence("objectFieldOffset", Field.class);
      printExistence("pageSize");
      printExistence("park", boolean.class, long.class);
      printExistence("putBoolean", Object.class, long.class, boolean.class);
      printExistence("putByte", Object.class, long.class, byte.class);
      printExistence("putByte", long.class, byte.class);
      printExistence("putChar", Object.class, long.class, char.class);
      printExistence("putChar", long.class, char.class);
      printExistence("putDouble", Object.class, long.class, double.class);
      printExistence("putDouble", long.class, double.class);
      printExistence("putFloat", Object.class, long.class, float.class);
      printExistence("putFloat", long.class, float.class);
      printExistence("putInt", Object.class, long.class, int.class);
      printExistence("putInt", long.class, int.class);
      printExistence("putIntVolatile", Object.class, long.class, int.class);
      printExistence("putLong", Object.class, long.class, long.class);
      printExistence("putLong", long.class, long.class);
      printExistence("putLongVolatile", Object.class, long.class, long.class);
      printExistence("putObject", Object.class, long.class, Object.class);
      printExistence("putObjectVolatile", Object.class, long.class, Object.class);
      printExistence("putOrderedInt", Object.class, long.class, int.class);
      printExistence("putOrderedLong", Object.class, long.class, long.class);
      printExistence("putOrderedObject", Object.class, long.class, Object.class);
      printExistence("putShort", Object.class, long.class, short.class);
      printExistence("putShort", long.class, short.class);
      printExistence("setMemory", long.class, long.class, byte.class);
      printExistence("storeFence");
      printExistence("unpark", Object.class);
    }

    private static String parameterString(Class<?>... parameterTypes) {
      StringBuilder parameterList = new StringBuilder();
      boolean isFirst = true;
      for (Class<?> clazz : parameterTypes) {
        if (isFirst) {
          isFirst = false;
        } else {
          parameterList.append(", ");
        }
        parameterList.append(clazz.getCanonicalName());
      }
      return parameterList.toString();
    }

    private static Class<?> cachedUnsafe = null;

    private static Class<?> unsafeClass() throws ClassNotFoundException {
      if (cachedUnsafe == null) {
        cachedUnsafe = Class.forName("sun.misc.Unsafe");
      }
      return cachedUnsafe;
    }

    private static void printExistence(String name, Class<?>... paramterTypes)
        throws ClassNotFoundException {
      boolean doesExist = doesMethodExist(name, paramterTypes);
      String message = name + "(" + parameterString(paramterTypes) + "): " + doesExist;
      System.out.println(message);
    }

    private static boolean doesMethodExist(String name, Class<?>... parameterTypes)
        throws ClassNotFoundException {
      try {
        unsafeClass().getDeclaredMethod(name, parameterTypes);
        return true;
      } catch (NoSuchMethodException e) {
        return false;
      }
    }
  }
}

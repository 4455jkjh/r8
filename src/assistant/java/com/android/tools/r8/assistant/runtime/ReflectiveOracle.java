// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver.ClassFlag;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver.NameLookupType;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.Arrays;

@KeepForApi
public class ReflectiveOracle {

  private static Object instanceLock = new Object();
  private static volatile ReflectiveOperationReceiver INSTANCE;

  private static ReflectiveOperationReceiver getInstance() {
    if (INSTANCE == null) {
      // TODO(b/393249304): Support injecting alternative receiver.
      synchronized (instanceLock) {
        if (INSTANCE == null) {
          INSTANCE = getReceiver();
        }
      }
    }
    return INSTANCE;
  }

  // Might be rewritten to call new instance on a custom receiver.
  private static ReflectiveOperationReceiver getReceiver() {
    // Default, might be replaced, don't change this without changing the instrumentation
    return new ReflectiveOperationJsonLogger();
  }

  @KeepForApi
  public static class Stack {

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private final StackTraceElement[] stackTraceElements;

    private Stack(StackTraceElement[] stackTraceElements) {
      this.stackTraceElements = stackTraceElements;
    }

    static Stack createStack() {
      assert INSTANCE != null;
      if (INSTANCE.requiresStackInformation()) {
        StackTraceElement[] stackTrace = new RuntimeException().getStackTrace();
        return new Stack(Arrays.copyOfRange(stackTrace, 2, stackTrace.length));
      }
      return new Stack(null);
    }

    public StackTraceElement[] getStackTraceElements() {
      return stackTraceElements;
    }

    public String toStringStackTrace(int numberOfLevels) {
      if (stackTraceElements == null) {
        return "Stack extraction not enabled.";
      }
      StringBuilder sb = new StringBuilder();
      for (StackTraceElement element : stackTraceElements) {
        sb.append(" at ").append(element).append("\n");
        if (numberOfLevels == 0) {
          break;
        }
        numberOfLevels--;
      }
      return sb.toString();
    }

    public String[] stackTraceElementsAsString(int numberOfLevels) {
      if (stackTraceElements == null) {
        return EMPTY_STRING_ARRAY;
      }
      int size = Math.max(0, Math.min(numberOfLevels, stackTraceElements.length));
      if (size == 0) {
        return EMPTY_STRING_ARRAY;
      }
      String[] result = new String[size];
      for (int i = 0; i < size; i++) {
        result[i] = stackTraceElements[i].toString();
      }
      return result;
    }
  }

  public static int onClassNewInstance(Class<?> clazz) {
    return getInstance().onClassNewInstance(Stack.createStack(), clazz);
  }

  public static int onClassForNameDefault(String className) {
    if (className == null) {
      return ReflectiveOperationReceiver.NO_ID;
    }
    // The last parameter is implicitly the caller's holder class class loader.
    return getInstance().onClassForName(Stack.createStack(), className, true, null);
  }

  public static int onClassForNameWithLoader(
      String className, boolean initialize, ClassLoader classLoader) {
    if (className == null) {
      return ReflectiveOperationReceiver.NO_ID;
    }
    return getInstance().onClassForName(Stack.createStack(), className, initialize, classLoader);
  }

  public static int onClassGetDeclaredMethod(Class<?> clazz, String name, Class<?>... parameters) {
    if (clazz == null || name == null) {
      return ReflectiveOperationReceiver.NO_ID;
    }
    Class<?> returnType = null;
    try {
      Method declaredMethod = clazz.getDeclaredMethod(name, parameters);
      returnType = declaredMethod.getReturnType();
    } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
      // Logged as a failed lookup; the reflective call that follows throws the same exception.
    }
    return getInstance()
        .onClassGetDeclaredMethod(Stack.createStack(), returnType, clazz, name, parameters);
  }

  public static int onClassGetDeclaredMethods(Class<?> clazz) {
    return getInstance().onClassGetDeclaredMethods(Stack.createStack(), clazz);
  }

  public static int onClassGetDeclaredField(Class<?> clazz, String fieldName) {
    if (clazz == null || fieldName == null) {
      return ReflectiveOperationReceiver.NO_ID;
    }
    Class<?> fieldType = null;
    try {
      fieldType = clazz.getDeclaredField(fieldName).getType();
    } catch (NoSuchFieldException | RuntimeException | LinkageError e) {
      // Logged as a failed lookup; the reflective call that follows throws the same exception.
    }
    return getInstance().onClassGetDeclaredField(Stack.createStack(), fieldType, clazz, fieldName);
  }

  public static int onClassGetDeclaredFields(Class<?> clazz) {
    return getInstance().onClassGetDeclaredFields(Stack.createStack(), clazz);
  }

  public static int onClassGetDeclaredConstructor(Class<?> clazz, Class<?>... parameters) {
    if (clazz == null) {
      return ReflectiveOperationReceiver.NO_ID;
    }
    return getInstance().onClassGetDeclaredConstructor(Stack.createStack(), clazz, parameters);
  }

  public static int onClassGetDeclaredConstructors(Class<?> clazz) {
    return getInstance().onClassGetDeclaredConstructors(Stack.createStack(), clazz);
  }

  public static int onClassGetMethod(Class<?> clazz, String name, Class<?>[] parameterTypes) {
    if (clazz == null || name == null) {
      return ReflectiveOperationReceiver.NO_ID;
    }
    Class<?> returnType = null;
    try {
      returnType = clazz.getMethod(name, parameterTypes).getReturnType();
    } catch (NoSuchMethodException | RuntimeException | LinkageError e) {
      // Logged as a failed lookup; the reflective call that follows throws the same exception.
    }
    return getInstance()
        .onClassGetMethod(Stack.createStack(), returnType, clazz, name, parameterTypes);
  }

  public static int onClassGetMethods(Class<?> clazz) {
    return getInstance().onClassGetMethods(Stack.createStack(), clazz);
  }

  public static int onClassGetField(Class<?> clazz, String name) {
    if (clazz == null || name == null) {
      return ReflectiveOperationReceiver.NO_ID;
    }
    Class<?> fieldType = null;
    try {
      fieldType = clazz.getField(name).getType();
    } catch (NoSuchFieldException | RuntimeException | LinkageError e) {
      // Logged as a failed lookup; the reflective call that follows throws the same exception.
    }
    return getInstance().onClassGetField(Stack.createStack(), fieldType, clazz, name);
  }

  public static int onClassGetFields(Class<?> clazz) {
    return getInstance().onClassGetFields(Stack.createStack(), clazz);
  }

  public static int onClassGetConstructor(Class<?> clazz, Class<?>... parameterTypes) {
    if (clazz == null) {
      return ReflectiveOperationReceiver.NO_ID;
    }
    return getInstance().onClassGetConstructor(Stack.createStack(), clazz, parameterTypes);
  }

  public static int onClassGetConstructors(Class<?> clazz) {
    return getInstance().onClassGetConstructors(Stack.createStack(), clazz);
  }

  public static int onClassGetName(Class<?> clazz) {
    return getInstance().onClassGetName(Stack.createStack(), clazz, NameLookupType.NAME);
  }

  public static int onClassGetSimpleName(Class<?> clazz) {
    return getInstance().onClassGetName(Stack.createStack(), clazz, NameLookupType.SIMPLE_NAME);
  }

  public static int onClassGetCanonicalName(Class<?> clazz) {
    return getInstance().onClassGetName(Stack.createStack(), clazz, NameLookupType.CANONICAL_NAME);
  }

  public static int onClassGetTypeName(Class<?> clazz) {
    return getInstance().onClassGetName(Stack.createStack(), clazz, NameLookupType.TYPE_NAME);
  }

  public static int onClassGetSuperclass(Class<?> clazz) {
    return getInstance().onClassGetSuperclass(Stack.createStack(), clazz);
  }

  public static int onClassAsSubclass(Class<?> holder, Class<?> clazz) {
    return getInstance().onClassAsSubclass(Stack.createStack(), holder, clazz);
  }

  public static int onClassIsInstance(Class<?> holder, Object object) {
    return getInstance().onClassIsInstance(Stack.createStack(), holder, object);
  }

  public static int onClassCast(Class<?> holder, Object object) {
    return getInstance().onClassCast(Stack.createStack(), holder, object);
  }

  public static int onClassGetComponentType(Class<?> clazz) {
    return getInstance().onClassGetComponentType(Stack.createStack(), clazz);
  }

  public static int onClassGetPackage(Class<?> clazz) {
    return getInstance().onClassGetPackage(Stack.createStack(), clazz);
  }

  public static int onClassIsAssignableFrom(Class<?> clazz, Class<?> sup) {
    return getInstance().onClassIsAssignableFrom(Stack.createStack(), clazz, sup);
  }

  public static int onClassIsAnnotation(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.ANNOTATION);
  }

  public static int onClassIsAnonymousClass(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.ANONYMOUS_CLASS);
  }

  public static int onClassIsArray(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.ARRAY);
  }

  public static int onClassIsEnum(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.ENUM);
  }

  public static int onClassIsHidden(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.HIDDEN);
  }

  public static int onClassIsInterface(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.INTERFACE);
  }

  public static int onClassIsLocalClass(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.LOCAL_CLASS);
  }

  public static int onClassIsMemberClass(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.MEMBER_CLASS);
  }

  public static int onClassIsPrimitive(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.PRIMITIVE);
  }

  public static int onClassIsRecord(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.RECORD);
  }

  public static int onClassIsSealed(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.SEALED);
  }

  public static int onClassIsSynthetic(Class<?> clazz) {
    return getInstance().onClassFlag(Stack.createStack(), clazz, ClassFlag.SYNTHETIC);
  }

  public static int onAtomicIntegerFieldUpdaterNewUpdater(Class<?> clazz, String name) {
    return getInstance()
        .onAtomicFieldUpdaterNewUpdater(Stack.createStack(), int.class, clazz, name);
  }

  public static int onAtomicLongFieldUpdaterNewUpdater(Class<?> clazz, String name) {
    return getInstance()
        .onAtomicFieldUpdaterNewUpdater(Stack.createStack(), long.class, clazz, name);
  }

  public static int onAtomicReferenceFieldUpdaterNewUpdater(
      Class<?> clazz, Class<?> fieldClass, String name) {
    return getInstance()
        .onAtomicFieldUpdaterNewUpdater(Stack.createStack(), fieldClass, clazz, name);
  }

  public static int onServiceLoaderLoad(Class<?> clazz) {
    return getInstance().onServiceLoaderLoad(Stack.createStack(), clazz, null);
  }

  public static int onServiceLoaderLoadWithClassLoader(Class<?> clazz, ClassLoader classLoader) {
    return getInstance().onServiceLoaderLoad(Stack.createStack(), clazz, classLoader);
  }

  public static int onServiceLoaderLoadInstalled(Class<?> clazz) {
    return getInstance()
        .onServiceLoaderLoad(Stack.createStack(), clazz, ClassLoader.getSystemClassLoader());
  }

  public static int onProxyNewProxyInstance(
      ClassLoader classLoader, Class<?>[] interfaces, InvocationHandler invocationHandler) {
    return getInstance()
        .onProxyNewProxyInstance(Stack.createStack(), classLoader, interfaces, invocationHandler);
  }

  // Called after an instrumented reflective call returns normally, with the id returned by the
  // corresponding onXxx stub before the call.
  public static void onReturn(int id, Object result) {
    if (id != ReflectiveOperationReceiver.NO_ID) {
      getInstance().onReflectiveOperationReturned(id, result);
    }
  }

  public static void onReturn(int id, boolean result) {
    if (id != ReflectiveOperationReceiver.NO_ID) {
      getInstance().onReflectiveOperationReturned(id, result);
    }
  }
}

// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.lang.reflect.InvocationHandler;

// The onXxx callbacks are called before the reflective call and return an id for the event, or
// NO_ID if the receiver does not track results. If the reflective call returns normally,
// onReflectiveOperationReturned is called with that id and the result (booleans are boxed).
@KeepForApi
public interface ReflectiveOperationReceiver {

  int NO_ID = -1;

  boolean requiresStackInformation();

  int onClassForName(Stack stack, String className, boolean initialize, ClassLoader classLoader);

  int onClassNewInstance(Stack stack, Class<?> clazz);

  int onClassGetDeclaredMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters);

  int onClassGetDeclaredMethods(Stack stack, Class<?> clazz);

  int onClassGetDeclaredField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName);

  int onClassGetDeclaredFields(Stack stack, Class<?> clazz);

  int onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters);

  int onClassGetDeclaredConstructors(Stack stack, Class<?> clazz);

  int onClassGetMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters);

  int onClassGetMethods(Stack stack, Class<?> clazz);

  int onClassGetField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName);

  int onClassGetFields(Stack stack, Class<?> clazz);

  int onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters);

  int onClassGetConstructors(Stack stack, Class<?> clazz);

  int onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType);

  int onClassGetSuperclass(Stack stack, Class<?> clazz);

  int onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz);

  int onClassIsInstance(Stack stack, Class<?> holder, Object object);

  int onClassCast(Stack stack, Class<?> holder, Object object);

  int onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag);

  int onClassGetComponentType(Stack stack, Class<?> clazz);

  int onClassGetPackage(Stack stack, Class<?> clazz);

  int onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup);

  int onAtomicFieldUpdaterNewUpdater(Stack stack, Class<?> fieldClass, Class<?> clazz, String name);

  int onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader);

  int onProxyNewProxyInstance(
      Stack stack,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler);

  void onReflectiveOperationReturned(int id, Object result);

  @KeepForApi
  enum ClassFlag {
    ANNOTATION,
    ANONYMOUS_CLASS,
    ARRAY,
    ENUM,
    HIDDEN,
    INTERFACE,
    LOCAL_CLASS,
    MEMBER_CLASS,
    PRIMITIVE,
    RECORD,
    SEALED,
    SYNTHETIC
  }

  @KeepForApi
  enum NameLookupType {
    NAME,
    SIMPLE_NAME,
    CANONICAL_NAME,
    TYPE_NAME
  }
}

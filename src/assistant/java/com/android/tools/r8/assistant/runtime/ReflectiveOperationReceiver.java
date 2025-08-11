// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.lang.reflect.InvocationHandler;

@KeepForApi
public interface ReflectiveOperationReceiver {

  default boolean requiresStackInformation() {
    return false;
  }

  void onClassForName(Stack stack, String className, boolean initialize, ClassLoader classLoader);

  void onClassNewInstance(Stack stack, Class<?> clazz);

  void onClassGetDeclaredMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters);

  void onClassGetDeclaredMethods(Stack stack, Class<?> clazz);

  void onClassGetDeclaredField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName);

  void onClassGetDeclaredFields(Stack stack, Class<?> clazz);

  void onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters);

  void onClassGetDeclaredConstructors(Stack stack, Class<?> clazz);

  void onClassGetMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters);

  void onClassGetMethods(Stack stack, Class<?> clazz);

  void onClassGetField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName);

  void onClassGetFields(Stack stack, Class<?> clazz);

  void onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters);

  void onClassGetConstructors(Stack stack, Class<?> clazz);

  void onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType);

  void onClassGetSuperclass(Stack stack, Class<?> clazz);

  void onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz);

  void onClassIsInstance(Stack stack, Class<?> holder, Object object);

  void onClassCast(Stack stack, Class<?> holder, Object object);

  void onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag);

  void onClassGetComponentType(Stack stack, Class<?> clazz);

  void onClassGetPackage(Stack stack, Class<?> clazz);

  void onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup);

  void onAtomicFieldUpdaterNewUpdater(
      Stack stack, Class<?> fieldClass, Class<?> clazz, String name);

  void onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader);

  void onProxyNewProxyInstance(
      Stack stack,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler);

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

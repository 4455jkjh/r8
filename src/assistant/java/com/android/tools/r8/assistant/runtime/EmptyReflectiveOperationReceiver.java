// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.lang.reflect.InvocationHandler;

@KeepForApi
public class EmptyReflectiveOperationReceiver implements ReflectiveOperationReceiver {

  @Override
  public boolean requiresStackInformation() {
    return false;
  }

  @Override
  public int onClassForName(
      Stack stack, String className, boolean initialize, ClassLoader classLoader) {
    return NO_ID;
  }

  @Override
  public int onClassGetComponentType(Stack stack, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassGetPackage(Stack stack, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {
    return NO_ID;
  }

  @Override
  public int onClassNewInstance(Stack stack, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredField(
      Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredFields(Stack stack, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassGetMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    return NO_ID;
  }

  @Override
  public int onClassGetMethods(Stack stack, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassGetField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    return NO_ID;
  }

  @Override
  public int onClassGetFields(Stack stack, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    return NO_ID;
  }

  @Override
  public int onClassGetConstructors(Stack stack, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
    return NO_ID;
  }

  @Override
  public int onClassGetSuperclass(Stack stack, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {
    return NO_ID;
  }

  @Override
  public int onClassIsInstance(Stack stack, Class<?> holder, Object object) {
    return NO_ID;
  }

  @Override
  public int onClassCast(Stack stack, Class<?> holder, Object object) {
    return NO_ID;
  }

  @Override
  public int onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag) {
    return NO_ID;
  }

  @Override
  public int onAtomicFieldUpdaterNewUpdater(
      Stack stack, Class<?> fieldClass, Class<?> clazz, String name) {
    return NO_ID;
  }

  @Override
  public int onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader) {
    return NO_ID;
  }

  @Override
  public int onProxyNewProxyInstance(
      Stack stack,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler) {
    return NO_ID;
  }

  @Override
  public void onReflectiveOperationReturned(int id, Object result) {}
}

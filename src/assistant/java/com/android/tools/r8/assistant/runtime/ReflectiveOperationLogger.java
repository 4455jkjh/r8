// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.runtime;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.lang.reflect.InvocationHandler;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@KeepForApi
public class ReflectiveOperationLogger implements ReflectiveOperationReceiver {

  private final Set<String> reported = new HashSet<>();

  private void report(Stack stack, String message) {
    if (reported.add(message)) {
      System.out.println(message);
      System.out.println(stack.toStringStackTrace(3));
    }
  }

  @Override
  public int onClassNewInstance(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively created new instance of " + clazz.getName());
    return NO_ID;
  }

  private String printMethod(String method, Class<?>... parameters) {
    return method + printParameters(parameters);
  }

  private String printConstructor(Class<?>... parameters) {
    return "<init>" + printParameters(parameters);
  }

  private String printParameters(Class<?>... parameters) {
    if (parameters == null) {
      return "(null)";
    }
    if (parameters.length == 0) {
      return "()";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("(");
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) {
        sb.append(", ");
      }
      Class<?> parameter = parameters[i];
      sb.append(parameter == null ? "null" : parameter.getName());
    }
    sb.append(")");
    return sb.toString();
  }

  @Override
  public int onClassGetDeclaredMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    report(
        stack,
        "Reflectively got declared method "
            + printMethod(method, parameters)
            + " on "
            + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got declared methods on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredField(
      Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    report(stack, "Reflectively got declared field " + fieldName + " on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredFields(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got declared fields on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    report(
        stack,
        "Reflectively got declared constructor "
            + printConstructor(parameters)
            + " on "
            + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got declared constructors on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    report(
        stack,
        "Reflectively got method " + printMethod(method, parameters) + " on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetMethods(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got methods on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    report(stack, "Reflectively got field " + fieldName + " on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetFields(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got fields on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    report(
        stack,
        "Reflectively got constructor " + printConstructor(parameters) + " on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetConstructors(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively got constructors on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
    report(
        stack, "Reflectively got name on " + clazz.getName() + "(" + lookupType.toString() + ")");
    return NO_ID;
  }

  @Override
  public int onClassForName(
      Stack stack, String className, boolean initialize, ClassLoader classLoader) {
    report(stack, "Reflectively called Class.forName on " + className);
    return NO_ID;
  }

  @Override
  public int onClassGetComponentType(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively called Class.getComponentType on " + clazz);
    return NO_ID;
  }

  @Override
  public int onClassGetPackage(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively called Class.getPackage on " + clazz);
    return NO_ID;
  }

  @Override
  public int onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {
    report(stack, "Reflectively called Class.isAssignableFrom on " + clazz + " with " + sup);
    return NO_ID;
  }

  @Override
  public int onClassGetSuperclass(Stack stack, Class<?> clazz) {
    report(stack, "Reflectively called Class.getSuperclass on " + clazz.getName());
    return NO_ID;
  }

  @Override
  public int onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {
    report(
        stack,
        "Reflectively called Class.asSubclass on " + clazz.getName() + " from " + holder.getName());
    return NO_ID;
  }

  @Override
  public int onClassIsInstance(Stack stack, Class<?> holder, Object object) {
    report(
        stack, "Reflectively called Class.isInstance on " + object + " from " + holder.getName());
    return NO_ID;
  }

  @Override
  public int onClassCast(Stack stack, Class<?> holder, Object object) {
    report(stack, "Reflectively called Class.cast on " + object + " from " + holder.getName());
    return NO_ID;
  }

  @Override
  public int onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag) {
    report(stack, "Reflectively got class flag " + classFlag);
    return NO_ID;
  }

  @Override
  public int onAtomicFieldUpdaterNewUpdater(
      Stack stack, Class<?> fieldClass, Class<?> clazz, String name) {
    report(
        stack,
        "Reflectively got AtomicReferenceFieldUpdater.newUpdater on "
            + fieldClass
            + " "
            + clazz
            + "#"
            + name);
    return NO_ID;
  }

  @Override
  public int onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader) {
    report(stack, "Reflectively got ServiceLoader.load on " + clazz + " with " + classLoader);
    return NO_ID;
  }

  @Override
  public int onProxyNewProxyInstance(
      Stack stack,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler) {
    report(
        stack,
        "Reflectively got Proxy.newProxyInstance on "
            + Arrays.toString(interfaces)
            + " with "
            + classLoader
            + " and "
            + invocationHandler);
    return NO_ID;
  }

  @Override
  public void onReflectiveOperationReturned(int id, Object result) {}

  public boolean requiresStackInformation() {
    return true;
  }
}

// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.runtime;

import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.ATOMIC_FIELD_UPDATER_NEW_UPDATER;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_AS_SUBCLASS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_CAST;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_FLAG;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_FOR_NAME;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_COMPONENT_TYPE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_CONSTRUCTOR;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_CONSTRUCTORS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTOR;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTORS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_FIELD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_FIELDS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_METHOD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_METHODS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_FIELD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_FIELDS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_METHOD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_METHODS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_NAME;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_PACKAGE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_SUPERCLASS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_IS_ASSIGNABLE_FROM;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_IS_INSTANCE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_NEW_INSTANCE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.PROXY_NEW_PROXY_INSTANCE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.SERVICE_LOADER_LOAD;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

// This logs the information in JSON-like format,
// manually encoded to avoid loading gson on the mobile.
@KeepForApi
public class ReflectiveOperationJsonLogger implements ReflectiveOperationReceiver {

  private final FileWriter output;
  private final Set<Long> seenEvents = new HashSet<>();

  // TODO(b/486090382): Consider injecting the app id as part of instrumentation.
  public String getApplicationId() {
    try {
      Class<?> activityThreadClass = Class.forName("android.app.ActivityThread");
      String packageName = null;
      try {
        Method currentPackageNameMethod =
            activityThreadClass.getDeclaredMethod("currentPackageName");
        currentPackageNameMethod.setAccessible(true);
        packageName = (String) currentPackageNameMethod.invoke(null);
      } catch (Throwable t) {
        // Ignore.
      }
      if (packageName != null) {
        return packageName;
      }
      try {
        Method currentAppMethod = activityThreadClass.getDeclaredMethod("currentApplication");
        currentAppMethod.setAccessible(true);
        Object applicationContext = currentAppMethod.invoke(null);
        if (applicationContext != null) {
          packageName =
              (String)
                  applicationContext
                      .getClass()
                      .getMethod("getPackageName")
                      .invoke(applicationContext);
        }
      } catch (Throwable t) {
        // Ignore.
      }
      if (packageName != null) {
        return packageName;
      }
      try {
        Method currentProcessNameMethod =
            activityThreadClass.getDeclaredMethod("currentProcessName");
        currentProcessNameMethod.setAccessible(true);
        packageName = (String) currentProcessNameMethod.invoke(null);
      } catch (Throwable t) {
        // Ignore.
      }
      return packageName;
    } catch (Throwable t) {
      return null;
    }
  }

  private int getSdkInt() {
    try {
      Class<?> buildVersionClass = Class.forName("android.os.Build$VERSION");
      return buildVersionClass.getField("SDK_INT").getInt(null);
    } catch (Throwable t) {
      return 0;
    }
  }

  public ReflectiveOperationJsonLogger() {
    String outputFileName = System.getProperty("com.android.tools.r8.reflectiveJsonLogger");
    File file;
    if (outputFileName == null) {
      String appId = getApplicationId();
      if (appId == null) {
        appId = "unknown";
      }
      int apiLevel = getSdkInt();
      if (apiLevel > 0 && apiLevel < 16) {
        this.output = null;
        return;
      }
      StringBuilder tmpDirBuilder = new StringBuilder();
      if (apiLevel >= 29 || apiLevel == 0) {
        tmpDirBuilder
            .append("/sdcard/Android/media/")
            .append(appId)
            .append("/additional_test_output");
      } else {
        tmpDirBuilder.append("/sdcard/Android/data/").append(appId).append("/files/test_data");
      }
      file = new File(tmpDirBuilder.toString(), "reflection_log.json");
    } else {
      file = new File(outputFileName);
    }
    try {
      File parent = file.getParentFile();
      if (parent != null && !parent.exists()) {
        try {
          parent.mkdirs();
        } catch (Throwable t) {
          // Ignore.
        }
      }
      this.output = new FileWriter(file);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private String[] methodToString(
      Class<?> returnType, Class<?> holder, String method, Class<?>... parameters) {
    int parametersLength = parameters == null ? 0 : parameters.length;
    String[] methodStrings = new String[parametersLength + 3];
    methodStrings[0] = printClass(returnType);
    methodStrings[1] = printClass(holder);
    methodStrings[2] = method;
    for (int i = 0; i < parametersLength; i++) {
      methodStrings[i + 3] = printClass(parameters[i]);
    }
    return methodStrings;
  }

  private String[] constructorToString(Class<?> holder, Class<?>... parameters) {
    return methodToString(Void.TYPE, holder, "<init>", parameters);
  }

  private String printClass(Class<?> clazz) {
    return clazz == null ? "null" : clazz.getName();
  }

  private String printClassLoader(ClassLoader classLoader) {
    return classLoader == null ? "null" : printClass(classLoader.getClass());
  }

  private boolean isIgnoredClass(Class<?> clazz) {
    return clazz != null && isIgnoredTarget(clazz.getName());
  }

  private boolean isIgnoredTarget(String name) {
    return name != null
        && (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android."));
  }

  private boolean isIgnoredCaller(Stack stack) {
    if (stack == null
        || stack.getStackTraceElements() == null
        || stack.getStackTraceElements().length == 0) {
      return false;
    }
    String name = stack.getStackTraceElements()[0].getClassName();
    return name != null
        && (name.startsWith("java.")
            || name.startsWith("javax.")
            || name.startsWith("android.")
            || name.startsWith("androidx.")
            || name.startsWith("kotlin.")
            || name.startsWith("kotlinx."));
  }

  private synchronized void output(ReflectiveEventType event, Stack stack, String... args) {
    if (output == null) {
      return;
    }
    long hash = 0xcbf29ce484222325L;
    hash = updateHash(hash, event.name());
    String[] stackStrings = stack != null ? stack.stackTraceElementsAsString(5) : null;
    if (stackStrings != null) {
      for (String s : stackStrings) {
        hash = updateHash(hash, s);
      }
    }
    for (String arg : args) {
      hash = updateHash(hash, arg);
    }
    if (!seenEvents.add(hash)) {
      return;
    }
    try {
      output.write("{\"event\": \"");
      output.write(event.name());
      output.write("\"");
      if (stackStrings != null) {
        output.write(", \"stack\": ");
        printArray(stackStrings);
      }
      assert args != null;
      output.write(", \"args\": ");
      printArray(args);
      output.write("}");
      output.write(System.lineSeparator());
      output.flush();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  // 64-bit FNV-1a hash algorithm to minimize collision risk when deduplicating
  // large volumes of reflection events across long application runs.
  private long updateHash(long hash, String s) {
    if (s == null) {
      return hash;
    }
    for (int i = 0; i < s.length(); i++) {
      hash ^= s.charAt(i);
      hash *= 0x100000001b3L;
    }
    return hash;
  }

  private void printArray(String... args) throws IOException {
    output.write("[");
    for (int i = 0; i < args.length; i++) {
      writeJsonString(args[i]);
      if (i != args.length - 1) {
        output.write(", ");
      }
    }
    output.write("]");
  }

  private void writeJsonString(String s) throws IOException {
    if (s == null) {
      output.write("null");
      return;
    }
    output.write("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"') {
        output.write("\\\"");
      } else if (c == '\\') {
        output.write("\\\\");
      } else if (c == '\b') {
        output.write("\\b");
      } else if (c == '\f') {
        output.write("\\f");
      } else if (c == '\n') {
        output.write("\\n");
      } else if (c == '\r') {
        output.write("\\r");
      } else if (c == '\t') {
        output.write("\\t");
      } else {
        output.write(c);
      }
    }
    output.write("\"");
  }

  @Override
  public void onClassNewInstance(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    if (clazz.isInterface() || Modifier.isAbstract(clazz.getModifiers())) {
      return;
    }
    // Class#newInstance() requires a public nullary constructor.
    // Probe it to ensure one exists before recording the instantiation.
    try {
      clazz.getConstructor();
    } catch (Throwable e) {
      return;
    }
    output(CLASS_NEW_INSTANCE, stack, printClass(clazz));
  }

  @Override
  public void onClassGetDeclaredMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    if (clazz == null || returnType == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_DECLARED_METHOD, stack, methodToString(returnType, clazz, method, parameters));
  }

  @Override
  public void onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_DECLARED_METHODS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetDeclaredField(
      Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    if (clazz == null || fieldType == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_DECLARED_FIELD, stack, printClass(fieldType), printClass(clazz), fieldName);
  }

  @Override
  public void onClassGetDeclaredFields(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_DECLARED_FIELDS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    // Probe the declared constructor to ensure it exists before logging.
    try {
      clazz.getDeclaredConstructor(parameters);
    } catch (Throwable e) {
      return;
    }
    output(CLASS_GET_DECLARED_CONSTRUCTOR, stack, constructorToString(clazz, parameters));
  }

  @Override
  public void onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_DECLARED_CONSTRUCTORS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    if (clazz == null || returnType == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_METHOD, stack, methodToString(returnType, clazz, method, parameters));
  }

  @Override
  public void onClassGetMethods(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_METHODS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    if (clazz == null || fieldType == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_FIELD, stack, printClass(fieldType), printClass(clazz), fieldName);
  }

  @Override
  public void onClassGetFields(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_FIELDS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    // Probe the public constructor to ensure it exists before logging.
    try {
      clazz.getConstructor(parameters);
    } catch (Throwable e) {
      return;
    }
    output(CLASS_GET_CONSTRUCTOR, stack, constructorToString(clazz, parameters));
  }

  @Override
  public void onClassGetConstructors(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_CONSTRUCTORS, stack, printClass(clazz));
  }

  @Override
  public void onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
    if (clazz == null || lookupType == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_NAME, stack, printClass(clazz), lookupType.name());
  }

  @Override
  public void onClassForName(
      Stack stack, String className, boolean initialize, ClassLoader classLoader) {
    if (className == null || isIgnoredTarget(className) || isIgnoredCaller(stack)) {
      return;
    }
    try {
      ClassLoader loader = classLoader;
      if (loader == null
          && stack != null
          && stack.getStackTraceElements() != null
          && stack.getStackTraceElements().length > 0) {
        try {
          // When classLoader is null, Class#forName defaults to the caller's class loader,
          // which would be the assistant runtime library's loader rather than the application.
          // Look up the actual caller's class loader from the stack to resolve in the app context.
          String callerClassName = stack.getStackTraceElements()[0].getClassName();
          loader = Class.forName(callerClassName).getClassLoader();
        } catch (Throwable t) {
        }
      }
      if (loader == null) {
        Class.forName(className);
      } else {
        Class.forName(className, initialize, loader);
      }
    } catch (Throwable e) {
      return;
    }
    output(
        CLASS_FOR_NAME,
        stack,
        className,
        Boolean.toString(initialize),
        printClassLoader(classLoader));
  }

  @Override
  public void onClassGetComponentType(Stack stack, Class<?> clazz) {
    if (isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_COMPONENT_TYPE, stack, printClass(clazz));
  }

  @Override
  public void onClassGetPackage(Stack stack, Class<?> clazz) {
    if (isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_PACKAGE, stack, printClass(clazz));
  }

  @Override
  public void onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {
    if (isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_IS_ASSIGNABLE_FROM, stack, printClass(clazz), printClass(sup));
  }

  @Override
  public void onClassGetSuperclass(Stack stack, Class<?> clazz) {
    if (isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_GET_SUPERCLASS, stack, printClass(clazz));
  }

  @Override
  public void onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {
    if (holder == null || clazz == null || isIgnoredClass(holder) || isIgnoredCaller(stack)) {
      return;
    }
    try {
      holder.asSubclass(clazz);
    } catch (Throwable e) {
      return;
    }
    output(CLASS_AS_SUBCLASS, stack, printClass(holder), printClass(clazz));
  }

  @Override
  public void onClassIsInstance(Stack stack, Class<?> holder, Object object) {
    if (holder == null || isIgnoredClass(holder) || isIgnoredCaller(stack)) {
      return;
    }
    output(
        CLASS_IS_INSTANCE,
        stack,
        printClass(holder),
        printClass(object != null ? object.getClass() : null));
  }

  @Override
  public void onClassCast(Stack stack, Class<?> holder, Object object) {
    if (holder == null || isIgnoredClass(holder) || isIgnoredCaller(stack)) {
      return;
    }
    try {
      holder.cast(object);
    } catch (Throwable e) {
      return;
    }
    output(
        CLASS_CAST,
        stack,
        printClass(holder),
        printClass(object != null ? object.getClass() : null));
  }

  @Override
  public void onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag) {
    if (clazz == null || classFlag == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(CLASS_FLAG, stack, printClass(clazz), classFlag.name());
  }

  @Override
  public void onAtomicFieldUpdaterNewUpdater(
      Stack stack, Class<?> fieldClass, Class<?> clazz, String name) {
    if (fieldClass == null || clazz == null || name == null) {
      return;
    }
    if (isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(
        ATOMIC_FIELD_UPDATER_NEW_UPDATER, stack, printClass(fieldClass), printClass(clazz), name);
  }

  @Override
  public void onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return;
    }
    output(SERVICE_LOADER_LOAD, stack, printClass(clazz), printClassLoader(classLoader));
  }

  @Override
  public void onProxyNewProxyInstance(
      Stack stack,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler) {
    if (interfaces == null || invocationHandler == null || isIgnoredCaller(stack)) {
      return;
    }
    boolean allIgnored = true;
    for (Class<?> itf : interfaces) {
      if (itf != null && !isIgnoredClass(itf)) {
        allIgnored = false;
        break;
      }
    }
    if (allIgnored) {
      return;
    }
    String[] methodStrings = new String[interfaces.length + 2];
    methodStrings[0] = printClassLoader(classLoader);
    methodStrings[1] = invocationHandler.toString();
    for (int i = 0; i < interfaces.length; i++) {
      methodStrings[i + 2] = printClass(interfaces[i]);
    }
    output(PROXY_NEW_PROXY_INSTANCE, stack, methodStrings);
  }

  public boolean requiresStackInformation() {
    return true;
  }
}

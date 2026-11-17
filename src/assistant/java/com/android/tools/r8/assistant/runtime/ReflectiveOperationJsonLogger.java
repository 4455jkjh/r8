// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.runtime;

import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.ATOMIC_FIELD_UPDATER_NEW_UPDATER;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_AS_SUBCLASS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_CAST;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_FLAG;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_FOR_NAME;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_ANNOTATION;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_ANNOTATIONS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_CLASSES;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_COMPONENT_TYPE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_CONSTRUCTOR;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_CONSTRUCTORS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_CLASSES;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTOR;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTORS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_FIELD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_FIELDS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_METHOD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARED_METHODS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_DECLARING_CLASS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_ENCLOSING_CLASS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_ENUM_CONSTANTS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_FIELD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_FIELDS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_GENERIC_INTERFACES;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_GENERIC_SUPERCLASS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_METHOD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_METHODS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_NAME;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_PACKAGE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_GET_SUPERCLASS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_IS_ANNOTATION_PRESENT;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_IS_ASSIGNABLE_FROM;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_IS_INSTANCE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_LOADER_LOAD_CLASS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CLASS_NEW_INSTANCE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CONSTRUCTOR_GET_ANNOTATION;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CONSTRUCTOR_GET_ANNOTATIONS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.CONSTRUCTOR_IS_ANNOTATION_PRESENT;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.ENUM_VALUE_OF;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.FIELD_GET_ANNOTATION;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.FIELD_GET_ANNOTATIONS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.FIELD_GET_GENERIC_TYPE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.FIELD_IS_ANNOTATION_PRESENT;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.METHOD_GET_ANNOTATION;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.METHOD_GET_ANNOTATIONS;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.METHOD_GET_GENERIC_EXCEPTION_TYPES;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.METHOD_GET_GENERIC_PARAMETER_TYPES;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.METHOD_GET_GENERIC_RETURN_TYPE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.METHOD_IS_ANNOTATION_PRESENT;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.PROXY_NEW_PROXY_INSTANCE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.RESOURCE_BUNDLE_GET_BUNDLE;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.SERVICE_LOADER_LOAD;
import static com.android.tools.r8.assistant.runtime.ReflectiveEventType.UNSAFE_ALLOCATE_INSTANCE;

import com.android.tools.r8.assistant.runtime.ReflectiveOracle.Stack;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

// This logs the information in JSON-like format,
// manually encoded to avoid loading gson on the mobile.
// Each distinct event is logged once with an id before the reflective call. When the call returns
// normally, {"returned": id, "result": summary} is logged once per distinct summary of the result,
// so an event without a returned line never returned normally (it threw).
@KeepForApi
public class ReflectiveOperationJsonLogger implements ReflectiveOperationReceiver {

  private final FileWriter output;
  private final Map<Long, Integer> eventIds = new HashMap<>();
  private final Set<Long> seenResults = new HashSet<>();
  // Set when writing the log fails. Logging then stops instead of throwing from the hooks, which
  // would change the control flow of the app around the reflective call.
  private boolean outputFailed = false;

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
      throw new RuntimeException(e);
    }
  }

  private String[] methodToString(
      Class<?> returnType, Class<?> holder, String method, Class<?>... parameters) {
    int parametersLength = parameters == null ? 0 : parameters.length;
    String[] methodStrings = new String[parametersLength + 3];
    methodStrings[0] = printMemberType(returnType);
    methodStrings[1] = printClass(holder);
    methodStrings[2] = method;
    for (int i = 0; i < parametersLength; i++) {
      methodStrings[i + 3] = printClass(parameters[i]);
    }
    return methodStrings;
  }

  private String[] methodWithAnnotationToString(Method method, Class<?> annotationClass) {
    Class<?>[] parameters = method.getParameterTypes();
    int parametersLength = parameters == null ? 0 : parameters.length;
    String[] methodStrings = new String[parametersLength + 4];
    methodStrings[0] = printMemberType(method.getReturnType());
    methodStrings[1] = printClass(method.getDeclaringClass());
    methodStrings[2] = method.getName();
    for (int i = 0; i < parametersLength; i++) {
      methodStrings[i + 3] = printClass(parameters[i]);
    }
    methodStrings[parametersLength + 3] = printClass(annotationClass);
    return methodStrings;
  }

  private String[] constructorToString(Class<?> holder, Class<?>... parameters) {
    return methodToString(Void.TYPE, holder, "<init>", parameters);
  }

  private String[] constructorWithAnnotationToString(
      Constructor<?> constructor, Class<?> annotationClass) {
    Class<?>[] parameters = constructor.getParameterTypes();
    int parametersLength = parameters == null ? 0 : parameters.length;
    String[] methodStrings = new String[parametersLength + 4];
    methodStrings[0] = printMemberType(Void.TYPE);
    methodStrings[1] = printClass(constructor.getDeclaringClass());
    methodStrings[2] = "<init>";
    for (int i = 0; i < parametersLength; i++) {
      methodStrings[i + 3] = printClass(parameters[i]);
    }
    methodStrings[parametersLength + 3] = printClass(annotationClass);
    return methodStrings;
  }

  private String printClass(Class<?> clazz) {
    return clazz == null ? "null" : printType(clazz);
  }

  // The member type is null if the member lookup fails, which is logged as JSON null.
  private String printMemberType(Class<?> type) {
    return type == null ? null : printClass(type);
  }

  private String printClassLoader(ClassLoader classLoader) {
    return classLoader == null ? "null" : printClass(classLoader.getClass());
  }

  private boolean isIgnored(Stack stack, Class<?> clazz) {
    return clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack, clazz);
  }

  private boolean isIgnored(Stack stack, String className) {
    String target = unwrapArrayTypeName(className);
    return target == null || isIgnoredTarget(target) || isIgnoredCaller(stack, target);
  }

  private String unwrapArrayTypeName(String name) {
    if (name == null) {
      return null;
    }
    int dimensions = 0;
    while (dimensions < name.length() && name.charAt(dimensions) == '[') {
      dimensions++;
    }
    if (dimensions == 0) {
      return name;
    }
    String element = name.substring(dimensions);
    if (element.startsWith("L") && element.endsWith(";")) {
      return element.substring(1, element.length() - 1);
    }
    if (element.length() == 1 && "ZBCSIJFD".indexOf(element.charAt(0)) >= 0) {
      return null;
    }
    return name;
  }

  private boolean isIgnoredClass(Class<?> clazz) {
    if (clazz == null) {
      return true;
    }
    Class<?> c = clazz;
    while (c.isArray()) {
      c = c.getComponentType();
    }
    if (c.isPrimitive()) {
      return true;
    }
    return isIgnoredTarget(c.getName());
  }

  private boolean isIgnoredTarget(String name) {
    return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.");
  }

  private String libraryGroup(String name) {
    if (name == null) {
      return null;
    }
    if (name.startsWith("androidx.")) {
      int nextDot = name.indexOf('.', "androidx.".length());
      return nextDot > 0 ? name.substring(0, nextDot) : "androidx";
    }
    if (name.startsWith("kotlin.") || name.startsWith("kotlinx.")) {
      return "kotlin";
    }
    if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.")) {
      return "platform";
    }
    return null;
  }

  private boolean isIgnoredCaller(Stack stack, Class<?> target) {
    Class<?> c = target;
    while (c != null && c.isArray()) {
      c = c.getComponentType();
    }
    return isIgnoredCaller(stack, c != null ? c.getName() : null);
  }

  // Reflection from library code, such as androidx or kotlinx code in the app, is only ignored if
  // the target is in the same library group. Cross-library reflection (such as androidx.room
  // instantiating androidx.work.impl.WorkDatabase_Impl) or library reflection on app classes is
  // logged.
  private boolean isIgnoredCaller(Stack stack, String target) {
    if (target == null
        || stack == null
        || stack.getStackTraceElements() == null
        || stack.getStackTraceElements().length == 0) {
      return false;
    }
    String targetGroup = libraryGroup(target);
    if (targetGroup == null) {
      return false;
    }
    String name = stack.getStackTraceElements()[0].getClassName();
    String callerGroup = libraryGroup(name);
    return callerGroup != null && callerGroup.equals(targetGroup);
  }

  private synchronized int output(ReflectiveEventType event, Stack stack, String... args) {
    if (output == null || outputFailed) {
      return NO_ID;
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
    Integer id = eventIds.get(hash);
    if (id != null) {
      return id;
    }
    id = eventIds.size();
    eventIds.put(hash, id);
    try {
      output.write("{\"event\": \"");
      output.write(event.name());
      output.write("\"");
      output.write(", \"id\": ");
      output.write(Integer.toString(id));
      if (stackStrings != null) {
        output.write(", \"stack\": ");
        printArray(stackStrings);
      }
      assert args != null;
      output.write(", \"args\": ");
      printArray(args);
      output.write("}");
      output.write("\n");
      output.flush();
    } catch (IOException e) {
      outputFailed = true;
      return NO_ID;
    }
    return id;
  }

  // 64-bit FNV-1a hash algorithm to minimize collision risk when deduplicating
  // large volumes of reflection events across long application runs.
  private long updateHash(long hash, String s) {
    if (s == null) {
      // Mix in a value that no char can produce, so that a null argument still changes the hash.
      return (hash ^ 0x10000) * 0x100000001b3L;
    }
    for (int i = 0; i < s.length(); i++) {
      hash ^= s.charAt(i);
      hash *= 0x100000001b3L;
    }
    return (hash ^ 0x10001) * 0x100000001b3L;
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
    StringBuilder builder = new StringBuilder();
    appendJsonString(builder, s);
    output.write(builder.toString());
  }

  private void appendJsonString(StringBuilder builder, String s) {
    if (s == null) {
      builder.append("null");
      return;
    }
    builder.append("\"");
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '"') {
        builder.append("\\\"");
      } else if (c == '\\') {
        builder.append("\\\\");
      } else if (c == '\b') {
        builder.append("\\b");
      } else if (c == '\f') {
        builder.append("\\f");
      } else if (c == '\n') {
        builder.append("\\n");
      } else if (c == '\r') {
        builder.append("\\r");
      } else if (c == '\t') {
        builder.append("\\t");
      } else {
        builder.append(c);
      }
    }
    builder.append("\"");
  }

  @Override
  public int onClassNewInstance(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_NEW_INSTANCE, stack, printClass(clazz));
  }

  @Override
  public int onClassGetDeclaredMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(
        CLASS_GET_DECLARED_METHOD, stack, methodToString(returnType, clazz, method, parameters));
  }

  @Override
  public int onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_DECLARED_METHODS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetDeclaredField(
      Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(
        CLASS_GET_DECLARED_FIELD, stack, printMemberType(fieldType), printClass(clazz), fieldName);
  }

  @Override
  public int onClassGetDeclaredFields(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_DECLARED_FIELDS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_DECLARED_CONSTRUCTOR, stack, constructorToString(clazz, parameters));
  }

  @Override
  public int onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_DECLARED_CONSTRUCTORS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_METHOD, stack, methodToString(returnType, clazz, method, parameters));
  }

  @Override
  public int onClassGetMethods(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_METHODS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_FIELD, stack, printMemberType(fieldType), printClass(clazz), fieldName);
  }

  @Override
  public int onClassGetFields(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_FIELDS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_CONSTRUCTOR, stack, constructorToString(clazz, parameters));
  }

  @Override
  public int onClassGetConstructors(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_CONSTRUCTORS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
    if (lookupType == null || isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_NAME, stack, printClass(clazz), lookupType.name());
  }

  @Override
  public int onClassForName(
      Stack stack, String className, boolean initialize, ClassLoader classLoader) {
    if (isIgnored(stack, className)) {
      return NO_ID;
    }
    return output(
        CLASS_FOR_NAME,
        stack,
        className,
        Boolean.toString(initialize),
        printClassLoader(classLoader));
  }

  @Override
  public int onClassGetComponentType(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_COMPONENT_TYPE, stack, printClass(clazz));
  }

  @Override
  public int onClassGetPackage(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_PACKAGE, stack, printClass(clazz));
  }

  @Override
  public int onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {
    if (sup == null || isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_IS_ASSIGNABLE_FROM, stack, printClass(clazz), printClass(sup));
  }

  @Override
  public int onClassGetSuperclass(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_SUPERCLASS, stack, printClass(clazz));
  }

  @Override
  public int onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {
    if (clazz == null || isIgnored(stack, holder)) {
      return NO_ID;
    }
    return output(CLASS_AS_SUBCLASS, stack, printClass(holder), printClass(clazz));
  }

  @Override
  public int onClassIsInstance(Stack stack, Class<?> holder, Object object) {
    if (isIgnored(stack, holder)) {
      return NO_ID;
    }
    return output(
        CLASS_IS_INSTANCE,
        stack,
        printClass(holder),
        printClass(object != null ? object.getClass() : null));
  }

  @Override
  public int onClassCast(Stack stack, Class<?> holder, Object object) {
    if (isIgnored(stack, holder)) {
      return NO_ID;
    }
    return output(
        CLASS_CAST,
        stack,
        printClass(holder),
        printClass(object != null ? object.getClass() : null));
  }

  @Override
  public int onClassFlag(Stack stack, Class<?> clazz, ClassFlag classFlag) {
    if (classFlag == null || isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_FLAG, stack, printClass(clazz), classFlag.name());
  }

  @Override
  public int onAtomicFieldUpdaterNewUpdater(
      Stack stack, Class<?> fieldClass, Class<?> clazz, String name) {
    if (fieldClass == null || name == null || isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(
        ATOMIC_FIELD_UPDATER_NEW_UPDATER, stack, printClass(fieldClass), printClass(clazz), name);
  }

  @Override
  public int onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(SERVICE_LOADER_LOAD, stack, printClass(clazz), printClassLoader(classLoader));
  }

  @Override
  public int onProxyNewProxyInstance(
      Stack stack,
      ClassLoader classLoader,
      Class<?>[] interfaces,
      InvocationHandler invocationHandler) {
    if (interfaces == null || invocationHandler == null) {
      return NO_ID;
    }
    boolean allIgnored = true;
    for (Class<?> itf : interfaces) {
      if (!isIgnored(stack, itf)) {
        allIgnored = false;
        break;
      }
    }
    if (allIgnored) {
      return NO_ID;
    }
    String[] methodStrings = new String[interfaces.length + 2];
    methodStrings[0] = printClassLoader(classLoader);
    methodStrings[1] = printClass(invocationHandler.getClass());
    for (int i = 0; i < interfaces.length; i++) {
      methodStrings[i + 2] = printClass(interfaces[i]);
    }
    return output(PROXY_NEW_PROXY_INSTANCE, stack, methodStrings);
  }

  @Override
  public int onClassGetGenericSuperclass(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_GENERIC_SUPERCLASS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetGenericInterfaces(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_GENERIC_INTERFACES, stack, printClass(clazz));
  }

  @Override
  public int onClassLoaderLoadClass(Stack stack, ClassLoader classLoader, String className) {
    if (isIgnored(stack, className)) {
      return NO_ID;
    }
    return output(CLASS_LOADER_LOAD_CLASS, stack, className, printClassLoader(classLoader));
  }

  @Override
  public int onResourceBundleGetBundle(
      Stack stack, String baseName, Locale locale, ClassLoader classLoader) {
    if (isIgnored(stack, baseName)) {
      return NO_ID;
    }
    return output(
        RESOURCE_BUNDLE_GET_BUNDLE,
        stack,
        baseName,
        locale == null ? "null" : locale.toString(),
        printClassLoader(classLoader));
  }

  @Override
  public int onClassGetAnnotation(Stack stack, Class<?> clazz, Class<?> annotationClass) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_ANNOTATION, stack, printClass(clazz), printClass(annotationClass));
  }

  @Override
  public int onClassGetAnnotations(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_ANNOTATIONS, stack, printClass(clazz));
  }

  @Override
  public int onClassIsAnnotationPresent(Stack stack, Class<?> clazz, Class<?> annotationClass) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(
        CLASS_IS_ANNOTATION_PRESENT, stack, printClass(clazz), printClass(annotationClass));
  }

  @Override
  public int onFieldGetAnnotation(Stack stack, Field field, Class<?> annotationClass) {
    if (field == null || isIgnored(stack, field.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        FIELD_GET_ANNOTATION,
        stack,
        printMemberType(field.getType()),
        printClass(field.getDeclaringClass()),
        field.getName(),
        printClass(annotationClass));
  }

  @Override
  public int onFieldGetAnnotations(Stack stack, Field field) {
    if (field == null || isIgnored(stack, field.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        FIELD_GET_ANNOTATIONS,
        stack,
        printMemberType(field.getType()),
        printClass(field.getDeclaringClass()),
        field.getName());
  }

  @Override
  public int onFieldIsAnnotationPresent(Stack stack, Field field, Class<?> annotationClass) {
    if (field == null || isIgnored(stack, field.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        FIELD_IS_ANNOTATION_PRESENT,
        stack,
        printMemberType(field.getType()),
        printClass(field.getDeclaringClass()),
        field.getName(),
        printClass(annotationClass));
  }

  @Override
  public int onMethodGetAnnotation(Stack stack, Method method, Class<?> annotationClass) {
    if (method == null || isIgnored(stack, method.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        METHOD_GET_ANNOTATION, stack, methodWithAnnotationToString(method, annotationClass));
  }

  @Override
  public int onMethodGetAnnotations(Stack stack, Method method) {
    if (method == null || isIgnored(stack, method.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        METHOD_GET_ANNOTATIONS,
        stack,
        methodToString(
            method.getReturnType(),
            method.getDeclaringClass(),
            method.getName(),
            method.getParameterTypes()));
  }

  @Override
  public int onMethodIsAnnotationPresent(Stack stack, Method method, Class<?> annotationClass) {
    if (method == null || isIgnored(stack, method.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        METHOD_IS_ANNOTATION_PRESENT, stack, methodWithAnnotationToString(method, annotationClass));
  }

  @Override
  public int onConstructorGetAnnotation(
      Stack stack, Constructor<?> constructor, Class<?> annotationClass) {
    if (constructor == null || isIgnored(stack, constructor.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        CONSTRUCTOR_GET_ANNOTATION,
        stack,
        constructorWithAnnotationToString(constructor, annotationClass));
  }

  @Override
  public int onConstructorGetAnnotations(Stack stack, Constructor<?> constructor) {
    if (constructor == null || isIgnored(stack, constructor.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        CONSTRUCTOR_GET_ANNOTATIONS,
        stack,
        constructorToString(constructor.getDeclaringClass(), constructor.getParameterTypes()));
  }

  @Override
  public int onConstructorIsAnnotationPresent(
      Stack stack, Constructor<?> constructor, Class<?> annotationClass) {
    if (constructor == null || isIgnored(stack, constructor.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        CONSTRUCTOR_IS_ANNOTATION_PRESENT,
        stack,
        constructorWithAnnotationToString(constructor, annotationClass));
  }

  @Override
  public int onMethodGetGenericParameterTypes(Stack stack, Method method) {
    if (method == null || isIgnored(stack, method.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        METHOD_GET_GENERIC_PARAMETER_TYPES,
        stack,
        methodToString(
            method.getReturnType(),
            method.getDeclaringClass(),
            method.getName(),
            method.getParameterTypes()));
  }

  @Override
  public int onMethodGetGenericReturnType(Stack stack, Method method) {
    if (method == null || isIgnored(stack, method.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        METHOD_GET_GENERIC_RETURN_TYPE,
        stack,
        methodToString(
            method.getReturnType(),
            method.getDeclaringClass(),
            method.getName(),
            method.getParameterTypes()));
  }

  @Override
  public int onMethodGetGenericExceptionTypes(Stack stack, Method method) {
    if (method == null || isIgnored(stack, method.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        METHOD_GET_GENERIC_EXCEPTION_TYPES,
        stack,
        methodToString(
            method.getReturnType(),
            method.getDeclaringClass(),
            method.getName(),
            method.getParameterTypes()));
  }

  @Override
  public int onFieldGetGenericType(Stack stack, Field field) {
    if (field == null || isIgnored(stack, field.getDeclaringClass())) {
      return NO_ID;
    }
    return output(
        FIELD_GET_GENERIC_TYPE,
        stack,
        printMemberType(field.getType()),
        printClass(field.getDeclaringClass()),
        field.getName());
  }

  @Override
  public int onClassGetDeclaredClasses(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_DECLARED_CLASSES, stack, printClass(clazz));
  }

  @Override
  public int onClassGetClasses(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_CLASSES, stack, printClass(clazz));
  }

  @Override
  public int onClassGetDeclaringClass(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_DECLARING_CLASS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetEnclosingClass(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_ENCLOSING_CLASS, stack, printClass(clazz));
  }

  @Override
  public int onEnumValueOf(Stack stack, Class<?> enumType, String name) {
    if (isIgnored(stack, enumType)) {
      return NO_ID;
    }
    return output(ENUM_VALUE_OF, stack, printClass(enumType), name);
  }

  @Override
  public int onClassGetEnumConstants(Stack stack, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(CLASS_GET_ENUM_CONSTANTS, stack, printClass(clazz));
  }

  @Override
  public int onUnsafeAllocateInstance(Stack stack, Object unsafe, Class<?> clazz) {
    if (isIgnored(stack, clazz)) {
      return NO_ID;
    }
    return output(UNSAFE_ALLOCATE_INSTANCE, stack, printClass(clazz));
  }

  @Override
  public void onReflectiveOperationReturned(int id, Object result) {
    if (id == NO_ID || output == null) {
      return;
    }
    StringBuilder builder = new StringBuilder();
    appendResult(builder, result);
    outputReturned(id, builder.toString());
  }

  private synchronized void outputReturned(int id, String result) {
    if (output == null || outputFailed) {
      return;
    }
    String idString = Integer.toString(id);
    long hash = 0xcbf29ce484222325L;
    hash = updateHash(hash, idString);
    hash = updateHash(hash, result);
    if (!seenResults.add(hash)) {
      return;
    }
    try {
      output.write("{\"returned\": ");
      output.write(idString);
      output.write(", \"result\": ");
      output.write(result);
      output.write("}");
      output.write("\n");
      output.flush();
    } catch (IOException e) {
      outputFailed = true;
    }
  }

  // Summarizes the result without calling into app code, such as toString() on a proxy. A
  // ParameterizedType is summarized by its raw type, such as TypeToken<...>, and other Types that
  // are not classes by their runtime class, as Type.toString() can throw TypeNotPresentException
  // for types that are missing in a shrunk app.
  private void appendResult(StringBuilder builder, Object result) {
    if (result == null || result instanceof Boolean) {
      builder.append(result);
    } else if (result instanceof String) {
      appendJsonString(builder, (String) result);
    } else if (result instanceof Class) {
      appendJsonString(builder, printType((Class<?>) result));
    } else if (result instanceof Enum<?>) {
      appendJsonString(builder, ((Enum<?>) result).name());
    } else if (result instanceof Enum<?>[]) {
      Enum<?>[] enums = (Enum<?>[]) result;
      builder.append("[");
      for (int i = 0; i < enums.length; i++) {
        if (i > 0) {
          builder.append(", ");
        }
        appendJsonString(builder, enums[i] == null ? null : enums[i].name());
      }
      builder.append("]");
    } else if (result instanceof Annotation) {
      List<String> items = new ArrayList<>();
      collectAnnotationStrings((Annotation) result, items);
      if (items.size() == 1) {
        appendJsonString(builder, items.get(0));
      } else {
        appendStringList(builder, items);
      }
    } else if (result instanceof Annotation[]) {
      List<String> items = new ArrayList<>();
      for (Annotation annotation : (Annotation[]) result) {
        collectAnnotationStrings(annotation, items);
      }
      appendStringList(builder, items);
    } else if (result instanceof Annotation[][]) {
      List<String> items = new ArrayList<>();
      for (Annotation[] row : (Annotation[][]) result) {
        if (row != null) {
          for (Annotation annotation : row) {
            collectAnnotationStrings(annotation, items);
          }
        }
      }
      appendStringList(builder, items);
    } else if (result instanceof ServiceLoader<?>) {
      List<String> providers = new ArrayList<>();
      try {
        for (Object provider : (ServiceLoader<?>) result) {
          if (provider != null) {
            providers.add(printType(provider.getClass()));
          }
        }
      } catch (Throwable t) {
        // Ignore provider instantiation errors during logging.
      }
      if (providers.isEmpty()) {
        appendJsonString(builder, printType(result.getClass()));
      } else {
        appendStringList(builder, providers);
      }
    } else if (result instanceof Member) {
      appendJsonString(builder, printMember((Member) result));
    } else if (result instanceof Member[]) {
      Member[] members = (Member[]) result;
      String[] memberStrings = new String[members.length];
      for (int i = 0; i < members.length; i++) {
        memberStrings[i] = printMember(members[i]);
      }
      Arrays.sort(memberStrings);
      builder.append("[");
      for (int i = 0; i < memberStrings.length; i++) {
        if (i > 0) {
          builder.append(", ");
        }
        appendJsonString(builder, memberStrings[i]);
      }
      builder.append("]");
    } else if (result instanceof Type[]) {
      // Keep the declaration order, which is significant for generic interfaces.
      Type[] types = (Type[]) result;
      builder.append("[");
      for (int i = 0; i < types.length; i++) {
        if (i > 0) {
          builder.append(", ");
        }
        appendResult(builder, types[i]);
      }
      builder.append("]");
    } else if (result instanceof ParameterizedType) {
      appendJsonString(builder, printParameterizedType((ParameterizedType) result));
    } else {
      appendJsonString(builder, printType(result.getClass()));
    }
  }

  private void appendStringList(StringBuilder builder, List<String> items) {
    builder.append("[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        builder.append(", ");
      }
      appendJsonString(builder, items.get(i));
    }
    builder.append("]");
  }

  private void collectAnnotationStrings(Annotation annotation, List<String> items) {
    if (annotation == null) {
      return;
    }
    Class<? extends Annotation> annotationType = annotation.annotationType();
    if (annotationType == null) {
      return;
    }
    items.add(printType(annotationType));
    try {
      for (Method m : annotationType.getDeclaredMethods()) {
        Class<?> returnType = m.getReturnType();
        if (returnType == Class.class) {
          m.setAccessible(true);
          collectAnnotationClassValue((Class<?>) m.invoke(annotation), items);
        } else if (returnType == Class[].class) {
          m.setAccessible(true);
          Class<?>[] classes = (Class<?>[]) m.invoke(annotation);
          if (classes != null) {
            for (Class<?> c : classes) {
              collectAnnotationClassValue(c, items);
            }
          }
        }
      }
    } catch (Throwable t) {
      // Ignore errors inspecting annotation elements.
    }
  }

  private void collectAnnotationClassValue(Class<?> clazz, List<String> items) {
    if (clazz == null || isIgnoredClass(clazz)) {
      return;
    }
    items.add("class:" + printType(clazz));
    Class<?> curr = clazz;
    while (curr != null && curr != Object.class) {
      for (Class<?> iface : curr.getInterfaces()) {
        if (!isIgnoredClass(iface)) {
          items.add("interface:" + printType(iface));
        }
      }
      curr = curr.getSuperclass();
    }
  }

  // Prints the raw type without the type arguments. libcore resolves the raw type lazily, which can
  // throw TypeNotPresentException or MalformedParameterizedTypeException in a shrunk app, so that
  // falls back to the runtime class.
  private String printParameterizedType(ParameterizedType type) {
    Type rawType;
    try {
      rawType = type.getRawType();
    } catch (Throwable t) {
      rawType = null;
    }
    return rawType instanceof Class
        ? printType((Class<?>) rawType) + "<...>"
        : printType(type.getClass());
  }

  private String printMember(Member member) {
    try {
      if (member instanceof Field) {
        Field field = (Field) member;
        return printType(field.getType()) + " " + field.getName();
      }
      if (member instanceof Method) {
        Method method = (Method) member;
        return printType(method.getReturnType())
            + " "
            + method.getName()
            + printParameters(method.getParameterTypes());
      }
      Constructor<?> constructor = (Constructor<?>) member;
      return "<init>" + printParameters(constructor.getParameterTypes());
    } catch (LinkageError e) {
      return member.getName();
    }
  }

  private String printParameters(Class<?>[] parameters) {
    StringBuilder builder = new StringBuilder("(");
    for (int i = 0; i < parameters.length; i++) {
      if (i > 0) {
        builder.append(",");
      }
      builder.append(printType(parameters[i]));
    }
    return builder.append(")").toString();
  }

  // Same as Class.getTypeName(), which is only available from API level 26.
  private String printType(Class<?> clazz) {
    Class<?> type = clazz;
    int dimensions = 0;
    while (type.isArray()) {
      type = type.getComponentType();
      dimensions++;
    }
    StringBuilder builder = new StringBuilder(type.getName());
    for (int i = 0; i < dimensions; i++) {
      builder.append("[]");
    }
    return builder.toString();
  }

  public boolean requiresStackInformation() {
    return true;
  }
}

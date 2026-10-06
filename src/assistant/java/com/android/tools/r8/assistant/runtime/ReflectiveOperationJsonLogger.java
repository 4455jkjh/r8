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
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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

  private String[] constructorToString(Class<?> holder, Class<?>... parameters) {
    return methodToString(Void.TYPE, holder, "<init>", parameters);
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
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_NEW_INSTANCE, stack, printClass(clazz));
  }

  @Override
  public int onClassGetDeclaredMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(
        CLASS_GET_DECLARED_METHOD, stack, methodToString(returnType, clazz, method, parameters));
  }

  @Override
  public int onClassGetDeclaredMethods(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_DECLARED_METHODS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetDeclaredField(
      Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(
        CLASS_GET_DECLARED_FIELD, stack, printMemberType(fieldType), printClass(clazz), fieldName);
  }

  @Override
  public int onClassGetDeclaredFields(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_DECLARED_FIELDS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetDeclaredConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_DECLARED_CONSTRUCTOR, stack, constructorToString(clazz, parameters));
  }

  @Override
  public int onClassGetDeclaredConstructors(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_DECLARED_CONSTRUCTORS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetMethod(
      Stack stack, Class<?> returnType, Class<?> clazz, String method, Class<?>... parameters) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_METHOD, stack, methodToString(returnType, clazz, method, parameters));
  }

  @Override
  public int onClassGetMethods(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_METHODS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetField(Stack stack, Class<?> fieldType, Class<?> clazz, String fieldName) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_FIELD, stack, printMemberType(fieldType), printClass(clazz), fieldName);
  }

  @Override
  public int onClassGetFields(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_FIELDS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetConstructor(Stack stack, Class<?> clazz, Class<?>... parameters) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_CONSTRUCTOR, stack, constructorToString(clazz, parameters));
  }

  @Override
  public int onClassGetConstructors(Stack stack, Class<?> clazz) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_CONSTRUCTORS, stack, printClass(clazz));
  }

  @Override
  public int onClassGetName(Stack stack, Class<?> clazz, NameLookupType lookupType) {
    if (clazz == null || lookupType == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_NAME, stack, printClass(clazz), lookupType.name());
  }

  @Override
  public int onClassForName(
      Stack stack, String className, boolean initialize, ClassLoader classLoader) {
    if (className == null || isIgnoredTarget(className) || isIgnoredCaller(stack)) {
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
    if (isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_COMPONENT_TYPE, stack, printClass(clazz));
  }

  @Override
  public int onClassGetPackage(Stack stack, Class<?> clazz) {
    if (isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_PACKAGE, stack, printClass(clazz));
  }

  @Override
  public int onClassIsAssignableFrom(Stack stack, Class<?> clazz, Class<?> sup) {
    if (isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_IS_ASSIGNABLE_FROM, stack, printClass(clazz), printClass(sup));
  }

  @Override
  public int onClassGetSuperclass(Stack stack, Class<?> clazz) {
    if (isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_GET_SUPERCLASS, stack, printClass(clazz));
  }

  @Override
  public int onClassAsSubclass(Stack stack, Class<?> holder, Class<?> clazz) {
    if (holder == null || clazz == null || isIgnoredClass(holder) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_AS_SUBCLASS, stack, printClass(holder), printClass(clazz));
  }

  @Override
  public int onClassIsInstance(Stack stack, Class<?> holder, Object object) {
    if (holder == null || isIgnoredClass(holder) || isIgnoredCaller(stack)) {
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
    if (holder == null || isIgnoredClass(holder) || isIgnoredCaller(stack)) {
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
    if (clazz == null || classFlag == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(CLASS_FLAG, stack, printClass(clazz), classFlag.name());
  }

  @Override
  public int onAtomicFieldUpdaterNewUpdater(
      Stack stack, Class<?> fieldClass, Class<?> clazz, String name) {
    if (fieldClass == null || clazz == null || name == null) {
      return NO_ID;
    }
    if (isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    return output(
        ATOMIC_FIELD_UPDATER_NEW_UPDATER, stack, printClass(fieldClass), printClass(clazz), name);
  }

  @Override
  public int onServiceLoaderLoad(Stack stack, Class<?> clazz, ClassLoader classLoader) {
    if (clazz == null || isIgnoredClass(clazz) || isIgnoredCaller(stack)) {
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
    if (interfaces == null || invocationHandler == null || isIgnoredCaller(stack)) {
      return NO_ID;
    }
    boolean allIgnored = true;
    for (Class<?> itf : interfaces) {
      if (itf != null && !isIgnoredClass(itf)) {
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

  // Summarizes the result without calling into app code, such as toString() on a proxy.
  private void appendResult(StringBuilder builder, Object result) {
    if (result == null || result instanceof Boolean) {
      builder.append(result);
    } else if (result instanceof String) {
      appendJsonString(builder, (String) result);
    } else if (result instanceof Class) {
      appendJsonString(builder, printType((Class<?>) result));
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
    } else {
      appendJsonString(builder, printType(result.getClass()));
    }
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

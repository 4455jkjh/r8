// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.assistant.postprocessing.model.ResourceBundleGetBundle;
import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The parts of the events of {@code ReflectiveOperationJsonParser} that the commands use, such as
 * the classes and members an event is about, its caller and its results, with types as Java type
 * names (e.g. {@code com.example.Foo[]}).
 */
final class ReflectiveEvents {

  // The runtime classes of parameterized types on ART and the JVM.
  private static final Set<String> PARAMETERIZED_TYPE_CLASSES =
      ImmutableSet.of(
          "libcore.reflect.ParameterizedTypeImpl",
          "sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl");

  private ReflectiveEvents() {}

  private static String typeName(DexType type) {
    return type.toSourceString();
  }

  /**
   * The class the event is about, such as {@code com.example.Foo} or {@code com.example.Foo[]}, or
   * the base name for {@code ResourceBundle.getBundle}. Null for a proxy without interfaces.
   */
  static String getTargetName(ReflectiveEvent event) {
    if (event.isClassGetMember()) {
      return typeName(event.asClassGetMember().getMember().getHolderType());
    }
    if (event.isClassGetMembers()) {
      return typeName(event.asClassGetMembers().getHolder());
    }
    if (event.isClassNewInstance()) {
      return typeName(event.asClassNewInstance().getType());
    }
    if (event.isClassGetName()) {
      return typeName(event.asClassGetName().getType());
    }
    if (event.isClassForName()) {
      ClassReference className = event.asClassForName().getClassName();
      return className == null ? null : TypeNames.toTypeName(className.getTypeName());
    }
    if (event.isClassLoaderLoadClass()) {
      ClassReference className = event.asClassLoaderLoadClass().getClassName();
      return className == null ? null : TypeNames.toTypeName(className.getTypeName());
    }
    if (event.isClassFlagEvent()) {
      return typeName(event.asClassFlagEvent().getHolder());
    }
    if (event.isAtomicFieldUpdaterNewUpdater()) {
      return typeName(event.asAtomicFieldUpdaterNewUpdater().getField().getHolderType());
    }
    if (event.isServiceLoaderLoad()) {
      return typeName(event.asServiceLoaderLoad().getType());
    }
    if (event.isProxyNewProxyInstance()) {
      DexType[] interfaces = event.asProxyNewProxyInstance().getInterfaces();
      return interfaces.length == 0 ? null : typeName(interfaces[0]);
    }
    if (event.isClassGetGenericSupertypes()) {
      return typeName(event.asClassGetGenericSupertypes().getType());
    }
    if (event.isResourceBundleGetBundle()) {
      return event.asResourceBundleGetBundle().getBaseName();
    }
    if (event.isClassOperation()) {
      return typeName(event.asClassOperation().getType());
    }
    throw new IllegalStateException("Unexpected event: " + event);
  }

  /**
   * The other classes the event is about: the class argument of operations such as {@code
   * Class.isAssignableFrom}, or the other interfaces of a proxy.
   */
  static List<String> getSecondaryClassNames(ReflectiveEvent event) {
    List<String> result = new ArrayList<>();
    if (event.isClassOperation()) {
      DexType argumentType = event.asClassOperation().getArgumentType();
      if (argumentType != null) {
        result.add(typeName(argumentType));
      }
    } else if (event.isProxyNewProxyInstance()) {
      DexType[] interfaces = event.asProxyNewProxyInstance().getInterfaces();
      for (int i = 1; i < interfaces.length; i++) {
        result.add(typeName(interfaces[i]));
      }
    }
    return result;
  }

  /** All classes the event is about. */
  static List<String> getClassNames(ReflectiveEvent event) {
    List<String> result = new ArrayList<>();
    String target = getTargetName(event);
    if (target != null) {
      result.add(target);
    }
    result.addAll(getSecondaryClassNames(event));
    return result;
  }

  /**
   * The field, method or constructor the event is about, or null. The type is {@link
   * MemberSignature#UNKNOWN_TYPE} for lookups that failed, which are logged without it.
   */
  static MemberSignature getMember(ReflectiveEvent event) {
    if (event.isClassGetMember()) {
      DexMember<?, ?> member = event.asClassGetMember().getMember();
      boolean hasType = event.asClassGetMember().hasMemberType();
      if (member.isDexField()) {
        DexField field = member.asDexField();
        return MemberSignature.field(
            hasType ? typeName(field.getType()) : MemberSignature.UNKNOWN_TYPE,
            field.getName().toString());
      }
      DexMethod method = member.asDexMethod();
      List<String> parameters = new ArrayList<>();
      for (DexType parameter : method.getParameters()) {
        parameters.add(typeName(parameter));
      }
      if (method.getName().toString().equals("<init>")) {
        return MemberSignature.constructor(parameters);
      }
      return MemberSignature.method(
          hasType ? typeName(method.getReturnType()) : MemberSignature.UNKNOWN_TYPE,
          method.getName().toString(),
          parameters);
    }
    if (event.isAtomicFieldUpdaterNewUpdater()) {
      DexField field = event.asAtomicFieldUpdaterNewUpdater().getField();
      return MemberSignature.field(typeName(field.getType()), field.getName().toString());
    }
    if (event.isClassNewInstance()) {
      return MemberSignature.constructor(Collections.emptyList());
    }
    return null;
  }

  /** The qualifier that completes the API name, e.g. {@code SIMPLE_NAME}, or null. */
  static String getQualifier(ReflectiveEvent event) {
    if (event.isClassGetName()) {
      return event.asClassGetName().getNameLookupType().name();
    }
    if (event.isClassFlagEvent()) {
      return event.asClassFlagEvent().getClassFlag().name();
    }
    return null;
  }

  /** Returns true for lookups of all members of a kind, such as {@code Class.getFields}. */
  static boolean isBulkLookup(ReflectiveEvent event) {
    return event.isClassGetMembers();
  }

  /** Returns true for lookups that only find public members, such as {@code Class.getField}. */
  static boolean isPublicLookup(ReflectiveEvent event) {
    switch (event.getEventType()) {
      case CLASS_GET_CONSTRUCTOR:
      case CLASS_GET_CONSTRUCTORS:
      case CLASS_GET_FIELD:
      case CLASS_GET_FIELDS:
      case CLASS_GET_METHOD:
      case CLASS_GET_METHODS:
        return true;
      default:
        return false;
    }
  }

  /** The Java API the event corresponds to, e.g. {@code Class.getDeclaredField}. */
  static String getApiName(ReflectiveEvent event) {
    switch (event.getEventType()) {
      case ATOMIC_FIELD_UPDATER_NEW_UPDATER:
        String fieldType = typeName(event.asAtomicFieldUpdaterNewUpdater().getField().getType());
        if (fieldType.equals("int")) {
          return "AtomicIntegerFieldUpdater.newUpdater";
        }
        if (fieldType.equals("long")) {
          return "AtomicLongFieldUpdater.newUpdater";
        }
        return "AtomicReferenceFieldUpdater.newUpdater";
      case CLASS_FLAG:
        return "Class.is" + toCamelCase(getQualifier(event), true);
      case CLASS_GET_NAME:
        return "Class.get" + toCamelCase(getQualifier(event), true);
      case CLASS_LOADER_LOAD_CLASS:
        return "ClassLoader.loadClass";
      case PROXY_NEW_PROXY_INSTANCE:
        return "Proxy.newProxyInstance";
      case RESOURCE_BUNDLE_GET_BUNDLE:
        return "ResourceBundle.getBundle";
      case SERVICE_LOADER_LOAD:
        return "ServiceLoader.load";
      default:
        String name = event.getEventType().name();
        assert name.startsWith("CLASS_");
        return "Class." + toCamelCase(name.substring("CLASS_".length()), false);
    }
  }

  /** Converts an underscore-separated name such as {@code SIMPLE_NAME} to camel case. */
  private static String toCamelCase(String name, boolean capitalizeFirst) {
    StringBuilder builder = new StringBuilder();
    boolean upper = capitalizeFirst;
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (c == '_') {
        upper = true;
      } else {
        builder.append(upper ? Character.toUpperCase(c) : Character.toLowerCase(c));
        upper = false;
      }
    }
    return builder.toString();
  }

  /** A short description of the subject of the event, e.g. {@code com.example.Foo.bar(int)}. */
  static String getSubject(ReflectiveEvent event) {
    if (event.isResourceBundleGetBundle()) {
      ResourceBundleGetBundle getBundle = event.asResourceBundleGetBundle();
      String locale = getBundle.getLocale();
      return getBundle.getBaseName() + (TypeNames.isMissing(locale) ? "" : " (" + locale + ")");
    }
    String target = getTargetName(event);
    if (target == null) {
      return "?";
    }
    MemberSignature member = getMember(event);
    if (member != null) {
      if (member.isField()) {
        return target + "." + member.getName();
      }
      return target
          + "."
          + member.getName()
          + "("
          + String.join(", ", member.getParameters())
          + ")";
    }
    return String.join(", ", getClassNames(event));
  }

  /** A short description of the event, e.g. {@code Class.getDeclaredField com.example.Foo.x}. */
  static String describe(ReflectiveEvent event) {
    return getApiName(event) + " " + getSubject(event);
  }

  /** The logged stack frames, or an empty list if the event has no stack. */
  static List<String> getStack(ReflectiveEvent event) {
    String[] stack = event.getStack();
    return stack == null ? Collections.emptyList() : Arrays.asList(stack);
  }

  /**
   * The first stack frame that is not part of the assistant runtime, without class loader and
   * module prefixes, or null.
   */
  static String getCallerFrame(ReflectiveEvent event) {
    for (String frame : getStack(event)) {
      if (frame != null && !isAssistantRuntimeFrame(frame)) {
        return stripFramePrefix(frame);
      }
    }
    return null;
  }

  static String getCallerDescription(ReflectiveEvent event) {
    String caller = getCallerFrame(event);
    return caller == null ? "<unknown caller>" : caller;
  }

  /**
   * Removes the class loader and module prefixes of a frame as printed by {@code
   * StackTraceElement.toString()}, e.g. {@code app//com.example.Foo.bar(Unknown Source)} or {@code
   * java.base/java.lang.Class.forName(Native Method)}.
   */
  static String stripFramePrefix(String frame) {
    String trimmed = frame.trim();
    int paren = trimmed.indexOf('(');
    int slash = trimmed.lastIndexOf('/', paren >= 0 ? paren : trimmed.length());
    return slash >= 0 ? trimmed.substring(slash + 1) : trimmed;
  }

  /** The method of a frame qualified with its class, e.g. {@code com.example.Foo.bar}. */
  static String getFrameMethod(String frame) {
    String stripped = stripFramePrefix(frame);
    int paren = stripped.indexOf('(');
    return paren >= 0 ? stripped.substring(0, paren) : stripped;
  }

  static String getFrameClassName(String frame) {
    String method = getFrameMethod(frame);
    int dot = method.lastIndexOf('.');
    return dot > 0 ? method.substring(0, dot) : method;
  }

  private static boolean isAssistantRuntimeFrame(String frame) {
    String className = getFrameClassName(frame);
    return className.startsWith("com.android.tools.r8.assistant.")
        || className.equals("java.lang.Thread")
        || className.equals("dalvik.system.VMStack");
  }

  /** The results as display strings, e.g. {@code com.example.Foo} or {@code [int x, int y]}. */
  static List<String> getResultValues(ReflectiveEvent event) {
    List<String> values = new ArrayList<>();
    for (Object result : event.getResults()) {
      values.add(toDisplayString(result));
    }
    return values;
  }

  static String toDisplayString(Object result) {
    if (result instanceof List) {
      List<String> elements = new ArrayList<>();
      for (Object element : (List<?>) result) {
        elements.add(String.valueOf(element));
      }
      return "[" + String.join(", ", elements) + "]";
    }
    return String.valueOf(result);
  }

  /**
   * The class names in the results of events whose results are classes or lists of types, with
   * parameterized types as their raw types.
   */
  static List<String> getResultClassNames(ReflectiveEvent event) {
    List<String> names = new ArrayList<>();
    for (String name : getResultTypeNames(event)) {
      names.add(getRawTypeName(name));
    }
    return names;
  }

  /**
   * The type names in the results of events whose results are classes or lists of types, with
   * parameterized types marked as in the log, e.g. {@code java.util.List<...>}.
   */
  static List<String> getResultTypeNames(ReflectiveEvent event) {
    List<String> names = new ArrayList<>();
    switch (event.getEventType()) {
      case CLASS_AS_SUBCLASS:
      case CLASS_CAST:
      case CLASS_FOR_NAME:
      case CLASS_GET_COMPONENT_TYPE:
      case CLASS_GET_GENERIC_SUPERCLASS:
      case CLASS_GET_SUPERCLASS:
      case CLASS_LOADER_LOAD_CLASS:
      case CLASS_NEW_INSTANCE:
      case RESOURCE_BUNDLE_GET_BUNDLE:
        for (Object result : event.getResults()) {
          if (result instanceof String) {
            names.add((String) result);
          }
        }
        break;
      case CLASS_GET_GENERIC_INTERFACES:
        for (Object result : event.getResults()) {
          if (result instanceof List) {
            for (Object element : (List<?>) result) {
              if (element instanceof String) {
                names.add((String) element);
              }
            }
          }
        }
        break;
      default:
        break;
    }
    return names;
  }

  /**
   * The raw types of the parameterized types in the results, e.g. {@code
   * com.google.gson.reflect.TypeToken} for {@code com.google.gson.reflect.TypeToken<...>}.
   */
  static Set<String> getParameterizedResultTypes(ReflectiveEvent event) {
    Set<String> rawTypes = new TreeSet<>();
    for (String name : getResultTypeNames(event)) {
      if (isParameterized(name)) {
        String rawType = TypeNames.getClassName(getRawTypeName(name));
        if (rawType != null) {
          rawTypes.add(rawType);
        }
      }
    }
    return rawTypes;
  }

  /**
   * Returns true for a parameterized type in the results, which the log gives as its raw type
   * followed by {@code <...>}. Class names cannot contain {@code <}.
   */
  static boolean isParameterized(String name) {
    return name.indexOf('<') > 0;
  }

  /**
   * Returns true for the runtime class of a parameterized type, which the log gives instead of the
   * raw type when the raw type cannot be resolved (e.g. its class is missing).
   */
  static boolean isUnresolvedParameterizedType(String name) {
    return PARAMETERIZED_TYPE_CLASSES.contains(name);
  }

  /** Returns true if the results include a parameterized type, resolved or not. */
  static boolean hasParameterizedResult(ReflectiveEvent event) {
    for (String name : getResultTypeNames(event)) {
      if (isParameterized(name) || isUnresolvedParameterizedType(name)) {
        return true;
      }
    }
    return false;
  }

  /** Returns true if the results include a parameterized type whose raw type is unknown. */
  static boolean hasUnresolvedParameterizedResult(ReflectiveEvent event) {
    for (String name : getResultTypeNames(event)) {
      if (isUnresolvedParameterizedType(name)) {
        return true;
      }
    }
    return false;
  }

  /** The raw type of a parameterized type in the results, or the name if it is not one. */
  static String getRawTypeName(String name) {
    int index = name.indexOf('<');
    return index < 0 ? name : name.substring(0, index);
  }

  /** Returns true for events whose results are the direct supertypes of the class. */
  static boolean isSupertypeLookup(ReflectiveEvent event) {
    ReflectiveEventType type = event.getEventType();
    return type == ReflectiveEventType.CLASS_GET_SUPERCLASS
        || type == ReflectiveEventType.CLASS_GET_GENERIC_SUPERCLASS
        || type == ReflectiveEventType.CLASS_GET_GENERIC_INTERFACES;
  }

  /** Returns true if the results include the list of members returned by a bulk lookup. */
  static boolean hasResultMembers(ReflectiveEvent event) {
    if (!isBulkLookup(event)) {
      return false;
    }
    for (Object result : event.getResults()) {
      if (result instanceof List) {
        return true;
      }
    }
    return false;
  }

  /** The members returned by a bulk lookup (the union over all results), in sorted order. */
  static Set<String> getResultMembers(ReflectiveEvent event) {
    Set<String> members = new TreeSet<>();
    if (isBulkLookup(event)) {
      for (Object result : event.getResults()) {
        if (result instanceof List) {
          for (Object element : (List<?>) result) {
            members.add(String.valueOf(element));
          }
        }
      }
    }
    return members;
  }
}

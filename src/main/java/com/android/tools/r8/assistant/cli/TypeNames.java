// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.List;
import java.util.Set;

/** Helpers for the class and type names found in reflection logs. */
final class TypeNames {

  private static final Set<String> PRIMITIVE_TYPES =
      ImmutableSet.of("boolean", "byte", "char", "short", "int", "long", "float", "double", "void");

  // Classes in these packages are provided by the platform (or are the injected assistant runtime)
  // and are not part of the app, so keep rules in the app do not apply to them.
  private static final List<String> PLATFORM_PREFIXES =
      ImmutableList.of(
          "android.",
          "com.android.tools.r8.assistant.",
          "com.sun.",
          "dalvik.",
          "java.",
          "javax.",
          "jdk.",
          "libcore.",
          "org.json.",
          "org.w3c.dom.",
          "org.xml.sax.",
          "sun.");

  private TypeNames() {}

  static boolean isMissing(String name) {
    return name == null || name.isEmpty() || name.equals("null");
  }

  static boolean isPrimitive(String typeName) {
    return PRIMITIVE_TYPES.contains(typeName);
  }

  static boolean isPlatformClass(String className) {
    for (String prefix : PLATFORM_PREFIXES) {
      if (className.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Converts a name as returned by {@code Class.getName()}, such as {@code [Ljava.lang.String;}, to
   * a Java type name as used in keep rules, such as {@code java.lang.String[]}.
   */
  static String toTypeName(String name) {
    if (isMissing(name)) {
      return name;
    }
    String descriptor = name.replace('.', '/');
    return DescriptorUtils.isValidArrayDescriptor(descriptor)
        ? DescriptorUtils.descriptorToJavaType(descriptor)
        : name;
  }

  /**
   * Returns the class name of the (element) type, or null for primitive types and missing names.
   *
   * <p>Accepts both {@code Class.getName()} and Java type names, e.g. {@code [Lcom.Foo;} and {@code
   * com.Foo[]} both give {@code com.Foo}.
   */
  static String getClassName(String name) {
    String typeName = toTypeName(name);
    if (isMissing(typeName)) {
      return null;
    }
    while (typeName.endsWith("[]")) {
      typeName = typeName.substring(0, typeName.length() - 2);
    }
    if (typeName.isEmpty() || isPrimitive(typeName) || typeName.startsWith("[")) {
      return null;
    }
    return typeName;
  }

  /** Returns true if the class name looks like a nested class, such as {@code Outer$Inner}. */
  static boolean isNestedClassName(String className) {
    int lastDot = className.lastIndexOf('.');
    int dollar = className.indexOf('$', lastDot + 1);
    return dollar > lastDot + 1 && dollar < className.length() - 1;
  }

  /**
   * Returns true if the class name looks like an anonymous class, such as {@code Outer$1}, whose
   * number changes when anonymous classes are added to or removed from the outer class.
   */
  static boolean isAnonymousClassName(String className) {
    int dollar = className.lastIndexOf('$');
    if (dollar <= className.lastIndexOf('.') + 1 || dollar == className.length() - 1) {
      return false;
    }
    for (int i = dollar + 1; i < className.length(); i++) {
      if (!Character.isDigit(className.charAt(i))) {
        return false;
      }
    }
    return true;
  }
}

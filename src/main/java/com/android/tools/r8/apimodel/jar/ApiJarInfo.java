// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel.jar;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ApiJarInfo {

  private final Map<String, ApiClassInfo> classes;

  public ApiJarInfo(Map<String, ApiClassInfo> classes) {
    this.classes = classes;
  }

  public static ApiJarInfo empty() {
    return new ApiJarInfo(new HashMap<>());
  }

  public void addClass(ApiClassInfo info) {
    assert !classes.containsKey(info.getBinaryName()) : info.getBinaryName() + " already exists";
    classes.put(info.getBinaryName(), info);
  }

  public void overwriteClass(ApiClassInfo info) {
    assert classes.containsKey(info.getBinaryName())
        : info.getBinaryName() + " does not exist, cannot overwrite it";
    classes.put(info.getBinaryName(), info);
  }

  public int size() {
    return classes.size();
  }

  public boolean hasClass(String binaryName) {
    return getClassInfo(binaryName) != null;
  }

  public boolean hasClass(ClassReference classReference) {
    return hasClass(classReference.getBinaryName());
  }

  public ApiClassInfo getClassInfo(String binaryName) {
    return classes.get(binaryName);
  }

  public ApiClassInfo getClassInfo(ClassReference classReference) {
    return getClassInfo(classReference.getBinaryName());
  }

  public Iterable<ApiClassInfo> getClasses() {
    return classes.values();
  }

  public boolean hasMethod(MethodReference methodReference) throws ApiDatabaseGeneratorException {
    return hasMethod(
        methodReference.getHolderClass().getBinaryName(),
        methodReference.getMethodName(),
        methodReference.getMethodDescriptor());
  }

  public boolean hasMethod(String binaryClassName, String name, String descriptor)
      throws ApiDatabaseGeneratorException {
    return getMethod(binaryClassName, name, descriptor) != null;
  }

  public ApiMethodInfo getMethod(String binaryClassName, String name, String descriptor)
      throws ApiDatabaseGeneratorException {
    ApiClassInfo classInfo = classes.get(binaryClassName);
    if (classInfo == null) {
      return null;
    }
    if (name.equals("<init>")) {
      return classInfo.getMethod(name, descriptor);
    }
    return getMethod(classInfo, name, descriptor, false);
  }

  /**
   * Returns true if the method would exist if static methods were inherited from interfaces.
   *
   * <p>Returns false if the method doesn't exist or the method exists via valid inheritance.
   */
  public boolean hasMethodViaFalseInheritance(
      String binaryClassName, String name, String descriptor) throws ApiDatabaseGeneratorException {
    return getMethodViaFalseInheritance(binaryClassName, name, descriptor) != null;
  }

  /**
   * Returns the info if method would exist if static methods were inherited from interfaces.
   *
   * <p>Returns null if the method doesn't exist or the method exists via valid inheritance.
   */
  public ApiMethodInfo getMethodViaFalseInheritance(
      String binaryClassName, String name, String descriptor) throws ApiDatabaseGeneratorException {
    ApiClassInfo classInfo = classes.get(binaryClassName);
    if (classInfo == null) {
      return null;
    }
    if (name.equals("<init>")) {
      return null;
    }
    return getMethod(classInfo, name, descriptor, true);
  }

  private ApiMethodInfo getMethod(
      ApiClassInfo classInfo, String name, String descriptor, boolean onlyViaFalseInheritance)
      throws ApiDatabaseGeneratorException {
    return getMethod(classInfo, name, descriptor, onlyViaFalseInheritance, true, new HashSet<>());
  }

  private ApiMethodInfo getMethod(
      ApiClassInfo classInfo,
      String name,
      String descriptor,
      boolean onlyViaFalseInheritance,
      boolean isReceiver,
      Set<String> visited)
      throws ApiDatabaseGeneratorException {
    if (!visited.add(classInfo.getBinaryName())) {
      throw new ApiDatabaseGeneratorException(
          "Class hierarchy cycle detected involving " + classInfo.getBinaryName());
    }
    try {
      // Check local methods.
      {
        ApiMethodInfo methodInfo = classInfo.getMethod(name, descriptor);
        if (methodInfo != null) {
          // Static methods are not inherited from interfaces.
          boolean isStaticInterfaceInheritance =
              classInfo.isInterface() && methodInfo.isStatic && !isReceiver;
          if (onlyViaFalseInheritance == isStaticInterfaceInheritance) {
            return methodInfo;
          } else {
            return null;
          }
        }
      }
      // Check superclass inheritance.
      if (!classInfo.isInterface() && classInfo.getSuperClass() != null) {
        ApiClassInfo superInfo = classes.get(classInfo.getSuperClass());
        if (superInfo != null) {
          ApiMethodInfo methodInfo =
              getMethod(superInfo, name, descriptor, onlyViaFalseInheritance, false, visited);
          if (methodInfo != null) {
            return methodInfo;
          }
        }
      }

      // Check interface inheritance.
      for (String ifaceName : classInfo.getInterfaces()) {
        ApiClassInfo ifaceInfo = classes.get(ifaceName);
        if (ifaceInfo != null) {
          ApiMethodInfo methodInfo =
              getMethod(ifaceInfo, name, descriptor, onlyViaFalseInheritance, false, visited);
          if (methodInfo != null) {
            return methodInfo;
          }
        }
      }

      // Check implicit interface inheritance.
      if (classInfo.isInterface() && isReceiver) {
        ApiClassInfo objectInfo = classes.get("java/lang/Object");
        if (objectInfo != null) {
          ApiMethodInfo methodInfo =
              getMethod(objectInfo, name, descriptor, onlyViaFalseInheritance, false, visited);
          if (methodInfo != null) {
            return methodInfo;
          }
        }
      }

      return null;
    } finally {
      visited.remove(classInfo.getBinaryName());
    }
  }

  public boolean hasField(String className, String name) throws ApiDatabaseGeneratorException {
    ApiClassInfo classInfo = classes.get(className);
    if (classInfo == null) {
      return false;
    }
    return hasField(classInfo, name, new HashSet<>());
  }

  private boolean hasField(ApiClassInfo classInfo, String name, Set<String> visited)
      throws ApiDatabaseGeneratorException {
    if (!visited.add(classInfo.getBinaryName())) {
      throw new ApiDatabaseGeneratorException(
          "Class hierarchy cycle detected involving " + classInfo.getBinaryName());
    }
    try {
      // Check local fields.
      if (classInfo.hasField(name)) {
        return true;
      }

      // Check superclass inheritance.
      if (classInfo.getSuperClass() != null) {
        ApiClassInfo superInfo = classes.get(classInfo.getSuperClass());
        if (superInfo != null) {
          if (hasField(superInfo, name, visited)) {
            return true;
          }
        }
      }

      // Check interface inheritance.
      for (String ifaceName : classInfo.getInterfaces()) {
        ApiClassInfo ifaceInfo = classes.get(ifaceName);
        if (ifaceInfo != null) {
          if (hasField(ifaceInfo, name, visited)) {
            return true;
          }
        }
      }

      return false;
    } finally {
      visited.remove(classInfo.getBinaryName());
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel.jar;

import com.android.tools.r8.utils.DescriptorUtils;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;

public class ApiClassInfo {
  private final String internalName;
  private final String superClass;
  private final boolean isInterface;
  private final Set<String> interfaces;
  private final Set<ApiMethodInfo> methods;
  private final Set<String> fields;

  public ApiClassInfo(String internalName, String superClass, boolean isInterface) {
    this(internalName, superClass, isInterface, new HashSet<>(), new HashSet<>(), new HashSet<>());
  }

  private ApiClassInfo(
      String internalName,
      String superClass,
      boolean isInterface,
      Set<String> interfaces,
      Set<ApiMethodInfo> methods,
      Set<String> fields) {
    assert internalName != null;
    assert DescriptorUtils.isValidInternalName(internalName);
    assert superClass == null || DescriptorUtils.isValidInternalName(superClass);
    assert interfaces != null;
    assert interfaces.stream().allMatch(DescriptorUtils::isValidInternalName);
    assert methods != null;
    assert fields != null;
    this.internalName = internalName;
    this.superClass = superClass;
    this.interfaces = interfaces;
    this.isInterface = isInterface;
    this.methods = methods;
    this.fields = fields;
  }

  public String getBinaryName() {
    return internalName;
  }

  public String getSuperClass() {
    return superClass;
  }

  public boolean implementsInterface(String internalInterfaceName) {
    return interfaces.contains(internalInterfaceName);
  }

  public boolean isInterface() {
    return isInterface;
  }

  /** Returns a read-only set. */
  public Set<String> getInterfaces() {
    return Collections.unmodifiableSet(interfaces);
  }

  public void addInterface(String internalInterfaceName) {
    assert DescriptorUtils.isValidInternalName(internalInterfaceName)
        : internalInterfaceName + " is not a valid name";
    assert !interfaces.contains(internalInterfaceName)
        : internalInterfaceName + " is already present";
    interfaces.add(internalInterfaceName);
  }

  public <T> void addInterfaces(Iterable<T> interfaces, Function<T, String> getInternalName) {
    interfaces.forEach(i -> addInterface(getInternalName.apply(i)));
  }

  public boolean hasMethod(String name, String descriptor) {
    return getMethod(name, descriptor) != null;
  }

  public ApiMethodInfo getMethod(String name, String descriptor) {
    ApiMethodInfo staticMethod = new ApiMethodInfo(name, descriptor, true);
    ApiMethodInfo instanceMethod = new ApiMethodInfo(name, descriptor, false);
    if (methods.contains(staticMethod)) {
      assert !methods.contains(instanceMethod)
          : this.internalName
              + "."
              + name
              + descriptor
              + " exists both as static and instance method";
      return staticMethod;
    }
    if (methods.contains(instanceMethod)) {
      return instanceMethod;
    }
    return null;
  }

  public void addMethod(String name, String descriptor, boolean isStatic) {
    addMethod(new ApiMethodInfo(name, descriptor, isStatic));
  }

  public void addMethod(ApiMethodInfo methodInfo) {
    assert !hasMethod(methodInfo.name, methodInfo.descriptor)
        : "method already exists "
            + this.internalName
            + "."
            + methodInfo.name
            + methodInfo.descriptor;
    methods.add(methodInfo);
  }

  /** Returns a read-only set. */
  public Set<ApiMethodInfo> getMethods() {
    return Collections.unmodifiableSet(methods);
  }

  public boolean hasField(String fieldName) {
    return fields.contains(fieldName);
  }

  /** Returns a read-only set. */
  public Set<String> getFields() {
    return Collections.unmodifiableSet(fields);
  }

  public void addField(String name) {
    assert !hasField(name) : "field already exists: " + this.internalName + "." + name;
    fields.add(name);
  }

  public ApiClassInfo copy() {
    return new ApiClassInfo(
        internalName,
        superClass,
        isInterface,
        new HashSet<>(interfaces),
        new HashSet<>(methods),
        new HashSet<>(fields));
  }
}

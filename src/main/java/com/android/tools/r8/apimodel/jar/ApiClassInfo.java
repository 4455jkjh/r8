// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel.jar;

import com.android.tools.r8.utils.DescriptorUtils;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class ApiClassInfo {
  private final String binaryName;
  private final String superClass;
  private final Set<String> interfaces;
  private final boolean isInterface;
  private final Set<ApiMethodInfo> methods;
  private final Set<String> fields;

  public ApiClassInfo(
      String binaryName,
      String superClass,
      Collection<String> interfaces,
      boolean isInterface,
      Collection<ApiMethodInfo> methods,
      Collection<String> fields) {
    assert binaryName != null;
    assert DescriptorUtils.isValidInternalName(binaryName);
    assert superClass == null || DescriptorUtils.isValidInternalName(superClass);
    assert interfaces != null;
    assert interfaces.stream().allMatch(DescriptorUtils::isValidInternalName);
    assert methods != null;
    assert fields != null;
    this.binaryName = binaryName;
    this.superClass = superClass;
    this.interfaces = new HashSet<>(interfaces);
    this.isInterface = isInterface;
    this.methods = new HashSet<>(methods);
    this.fields = new HashSet<>(fields);
  }

  public String getBinaryName() {
    return binaryName;
  }

  public String getSuperClass() {
    return superClass;
  }

  public boolean implementsInterface(String binaryInterfaceName) {
    return interfaces.contains(binaryInterfaceName);
  }

  public boolean isInterface() {
    return isInterface;
  }

  public Iterable<String> getInterfaces() {
    return interfaces;
  }

  public boolean hasMethod(String name, String descriptor) {
    return getMethod(name, descriptor) != null;
  }

  public ApiMethodInfo getMethod(String name, String descriptor) {
    ApiMethodInfo staticMethod = new ApiMethodInfo(name, descriptor, true);
    ApiMethodInfo instanceMethod = new ApiMethodInfo(name, descriptor, false);
    if (methods.contains(staticMethod)) {
      assert !methods.contains(instanceMethod)
          : this.binaryName
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
    assert !hasMethod(name, descriptor)
        : "method already exists " + this.binaryName + "." + name + descriptor;
    methods.add(new ApiMethodInfo(name, descriptor, isStatic));
  }

  public Iterable<ApiMethodInfo> getMethods() {
    return methods;
  }

  public boolean hasField(String fieldName) {
    return fields.contains(fieldName);
  }

  public Iterable<String> getFields() {
    return fields;
  }

  public void addField(String name) {
    assert !hasField(name) : "field already exists: " + this.binaryName + "." + name;
    fields.add(name);
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.references.ClassReference;
import java.util.ArrayList;
import java.util.List;

public class JarAmendments {

  public static class ClassAmendment {
    private final ClassReference classReference;
    private final ClassReference superClass;
    private final List<ClassReference> interfaces;

    public ClassAmendment(
        ClassReference classReference, ClassReference superClass, List<ClassReference> interfaces) {
      this.classReference = classReference;
      this.superClass = superClass;
      this.interfaces = interfaces;
    }

    public ClassReference getClassReference() {
      return classReference;
    }

    public ClassReference getSuperClass() {
      return superClass;
    }

    public List<ClassReference> getInterfaces() {
      return interfaces;
    }
  }

  public static class InterfaceAmendment {
    private final ClassReference interfaceReference;
    private final List<ClassReference> interfaces;

    public InterfaceAmendment(ClassReference interfaceReference, List<ClassReference> interfaces) {
      this.interfaceReference = interfaceReference;
      this.interfaces = interfaces;
    }

    public ClassReference getInterfaceReference() {
      return interfaceReference;
    }

    public List<ClassReference> getInterfaces() {
      return interfaces;
    }
  }

  public static class MethodAmendment {
    private final ClassReference holder;
    private final String name;
    private final String descriptor;
    private final boolean isStatic;

    public MethodAmendment(
        ClassReference holder, String name, String descriptor, boolean isStatic) {
      this.holder = holder;
      this.name = name;
      this.descriptor = descriptor;
      this.isStatic = isStatic;
    }

    public ClassReference getHolder() {
      return holder;
    }

    public String getName() {
      return name;
    }

    public String getDescriptor() {
      return descriptor;
    }

    public boolean isStatic() {
      return isStatic;
    }
  }

  public static class FieldAmendment {
    private final ClassReference holder;
    private final String name;

    public FieldAmendment(ClassReference holder, String name) {
      this.holder = holder;
      this.name = name;
    }

    public ClassReference getHolder() {
      return holder;
    }

    public String getName() {
      return name;
    }
  }

  private final List<ClassAmendment> classes = new ArrayList<>();
  private final List<InterfaceAmendment> interfaces = new ArrayList<>();
  private final List<MethodAmendment> methods = new ArrayList<>();
  private final List<FieldAmendment> fields = new ArrayList<>();

  public void addClass(
      ClassReference classReference, ClassReference superClass, List<ClassReference> interfaces) {
    classes.add(new ClassAmendment(classReference, superClass, interfaces));
  }

  public void addInterface(
      ClassReference interfaceReference, List<ClassReference> implementedInterfaces) {
    interfaces.add(new InterfaceAmendment(interfaceReference, implementedInterfaces));
  }

  public void addMethod(ClassReference holder, String name, String descriptor, boolean isStatic) {
    methods.add(new MethodAmendment(holder, name, descriptor, isStatic));
  }

  public void addField(ClassReference holder, String name) {
    fields.add(new FieldAmendment(holder, name));
  }

  public List<ClassAmendment> getClasses() {
    return classes;
  }

  public List<InterfaceAmendment> getInterfaces() {
    return interfaces;
  }

  public List<MethodAmendment> getMethods() {
    return methods;
  }

  public List<FieldAmendment> getFields() {
    return fields;
  }
}

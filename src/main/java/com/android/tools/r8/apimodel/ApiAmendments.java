// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.ArrayList;
import java.util.List;

public class ApiAmendments {

  public static class ClassAmendment {
    private final ClassReference classReference;
    private final AndroidApiLevel apiLevel;

    public ClassAmendment(ClassReference classReference, AndroidApiLevel apiLevel) {
      this.classReference = classReference;
      this.apiLevel = apiLevel;
    }

    public ClassReference getClassReference() {
      return classReference;
    }

    public AndroidApiLevel getApiLevel() {
      return apiLevel;
    }
  }

  public static class MethodAmendment {
    private final MethodReference methodReference;
    private final AndroidApiLevel apiLevel;

    public MethodAmendment(MethodReference methodReference, AndroidApiLevel apiLevel) {
      this.methodReference = methodReference;
      this.apiLevel = apiLevel;
    }

    public MethodReference getMethodReference() {
      return methodReference;
    }

    public AndroidApiLevel getApiLevel() {
      return apiLevel;
    }
  }

  public static class FieldAmendment {
    private final FieldTypelessReference fieldReference;
    private final AndroidApiLevel apiLevel;

    public FieldAmendment(FieldTypelessReference fieldReference, AndroidApiLevel apiLevel) {
      this.fieldReference = fieldReference;
      this.apiLevel = apiLevel;
    }

    public FieldTypelessReference getFieldReference() {
      return fieldReference;
    }

    public AndroidApiLevel getApiLevel() {
      return apiLevel;
    }
  }

  private final List<ClassAmendment> classes = new ArrayList<>();
  private final List<MethodAmendment> methods = new ArrayList<>();
  private final List<FieldAmendment> fields = new ArrayList<>();

  public void addClass(ClassReference classReference, AndroidApiLevel apiLevel) {
    classes.add(new ClassAmendment(classReference, apiLevel));
  }

  public void addMethod(MethodReference methodReference, AndroidApiLevel apiLevel) {
    methods.add(new MethodAmendment(methodReference, apiLevel));
  }

  public void addField(FieldTypelessReference fieldReference, AndroidApiLevel apiLevel) {
    fields.add(new FieldAmendment(fieldReference, apiLevel));
  }

  public List<ClassAmendment> getClasses() {
    return classes;
  }

  public List<MethodAmendment> getMethods() {
    return methods;
  }

  public List<FieldAmendment> getFields() {
    return fields;
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.apimodel.ApiAmendments.ClassAmendment;
import com.android.tools.r8.apimodel.ApiAmendments.FieldAmendment;
import com.android.tools.r8.apimodel.ApiAmendments.MethodAmendment;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ParsedApiClassAmending {

  public static void amendApi(
      Collection<ParsedApiClass<ApiRange>> apiClasses, ApiAmendments amendments)
      throws ApiDatabaseGeneratorException {
    Map<ClassReference, ParsedApiClass<ApiRange>> classMap = new HashMap<>();
    for (ParsedApiClass<ApiRange> apiClass : apiClasses) {
      classMap.put(apiClass.getClassReference(), apiClass);
    }

    for (ApiAmendments.ClassAmendment classAmendment : amendments.getClasses()) {
      amendApiClass(apiClasses, classAmendment, classMap);
    }
    for (ApiAmendments.MethodAmendment methodAmendment : amendments.getMethods()) {
      amendApiMethod(methodAmendment, classMap);
    }
    for (ApiAmendments.FieldAmendment fieldAmendment : amendments.getFields()) {
      amendApiField(fieldAmendment, classMap);
    }
  }

  private static void amendApiField(
      FieldAmendment fieldAmendment, Map<ClassReference, ParsedApiClass<ApiRange>> classMap)
      throws ApiDatabaseGeneratorException {
    FieldTypelessReference fieldRef = fieldAmendment.getFieldReference();
    ParsedApiClass<ApiRange> holder = classMap.get(fieldRef.getHolderClass());
    if (holder == null) {
      throw new ApiDatabaseGeneratorException(
          "Holder class "
              + fieldRef.getHolderClass()
              + " for field "
              + fieldRef
              + " does not exist");
    }
    if (holder.hasField(fieldRef)) {
      throw new ApiDatabaseGeneratorException(
          "Field " + fieldRef + " already exists in " + holder.getClassReference());
    }
    holder.registerField(fieldRef, new ApiRange(fieldAmendment.getApiLevel()));
  }

  private static void amendApiMethod(
      MethodAmendment methodAmendment, Map<ClassReference, ParsedApiClass<ApiRange>> classMap)
      throws ApiDatabaseGeneratorException {
    MethodReference methodRef = methodAmendment.getMethodReference();
    ParsedApiClass<ApiRange> holder = classMap.get(methodRef.getHolderClass());
    if (holder == null) {
      throw new ApiDatabaseGeneratorException(
          "Holder class "
              + methodRef.getHolderClass()
              + " for method "
              + methodRef
              + " does not exist");
    }
    if (holder.hasMethod(methodRef)) {
      throw new ApiDatabaseGeneratorException(
          "Method " + methodRef + " already exists in " + holder.getClassReference());
    }
    holder.registerMethod(methodRef, new ApiRange(methodAmendment.getApiLevel()));
  }

  private static void amendApiClass(
      Collection<ParsedApiClass<ApiRange>> apiClasses,
      ClassAmendment classAmendment,
      Map<ClassReference, ParsedApiClass<ApiRange>> classMap)
      throws ApiDatabaseGeneratorException {
    ClassReference classRef = classAmendment.getClassReference();
    if (classMap.containsKey(classRef)) {
      throw new ApiDatabaseGeneratorException("Class " + classRef + " already exists");
    } else {
      ParsedApiClass<ApiRange> newClass =
          new ParsedApiClass<>(classRef, new ApiRange(classAmendment.getApiLevel()));
      apiClasses.add(newClass);
      classMap.put(classRef, newClass);
    }
  }
}

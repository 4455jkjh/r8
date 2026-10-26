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
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.SemiInfiniteRange;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class ParsedApiClassAmending {

  public static void amendApi(
      Collection<ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>>> apiClasses,
      ApiAmendments amendments,
      AmendmentListener listener)
      throws ApiDatabaseGeneratorException {
    Map<ClassReference, ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>>> classMap =
        new HashMap<>();
    for (ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>> apiClass : apiClasses) {
      classMap.put(apiClass.getClassReference(), apiClass);
    }

    for (ApiAmendments.ClassAmendment classAmendment : amendments.getClasses()) {
      amendApiClass(apiClasses, classAmendment, classMap, listener);
    }
    for (ApiAmendments.MethodAmendment methodAmendment : amendments.getMethods()) {
      amendApiMethod(methodAmendment, classMap, listener);
    }
    for (ApiAmendments.FieldAmendment fieldAmendment : amendments.getFields()) {
      amendApiField(fieldAmendment, classMap, listener);
    }
  }

  private static void amendApiField(
      FieldAmendment fieldAmendment,
      Map<ClassReference, ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>>> classMap,
      AmendmentListener listener)
      throws ApiDatabaseGeneratorException {
    FieldTypelessReference fieldRef = fieldAmendment.getFieldReference();
    ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>> holder =
        classMap.get(fieldRef.getHolderClass());
    if (holder == null) {
      throw new ApiDatabaseGeneratorException(
          "Holder class "
              + fieldRef.getHolderClass()
              + " for field "
              + fieldRef
              + " does not exist");
    }
    if (holder.hasField(fieldRef)) {
      listener.onAlreadyPresentApiField(fieldAmendment);
    } else {
      holder.registerField(fieldRef, SemiInfiniteRange.startingAt(fieldAmendment.getApiLevel()));
      listener.onAmendApiField(fieldAmendment);
    }
  }

  private static void amendApiMethod(
      MethodAmendment methodAmendment,
      Map<ClassReference, ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>>> classMap,
      AmendmentListener listener)
      throws ApiDatabaseGeneratorException {
    MethodReference methodRef = methodAmendment.getMethodReference();
    ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>> holder =
        classMap.get(methodRef.getHolderClass());
    if (holder == null) {
      throw new ApiDatabaseGeneratorException(
          "Holder class "
              + methodRef.getHolderClass()
              + " for method "
              + methodRef
              + " does not exist");
    }
    if (holder.hasMethod(methodRef)) {
      listener.onAlreadyPresentApiMethod(methodAmendment);
    } else {
      holder.registerMethod(methodRef, SemiInfiniteRange.startingAt(methodAmendment.getApiLevel()));
      listener.onAmendApiMethod(methodAmendment);
    }
  }

  private static void amendApiClass(
      Collection<ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>>> apiClasses,
      ClassAmendment classAmendment,
      Map<ClassReference, ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>>> classMap,
      AmendmentListener listener) {
    ClassReference classRef = classAmendment.getClassReference();
    if (classMap.containsKey(classRef)) {
      listener.onAlreadyPresentApiClass(classAmendment);
    } else {
      ParsedApiClass<SemiInfiniteRange<AndroidApiLevel>> newClass =
          new ParsedApiClass<>(
              classRef, SemiInfiniteRange.startingAt(classAmendment.getApiLevel()));
      apiClasses.add(newClass);
      classMap.put(classRef, newClass);
      listener.onAmendApiClass(classAmendment);
    }
  }
}

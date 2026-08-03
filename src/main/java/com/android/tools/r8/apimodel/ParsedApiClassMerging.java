// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.androidapi.DuplicateApiDatabaseEntryDiagnostic;
import com.android.tools.r8.references.ClassReference;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ParsedApiClassMerging {

  private final DiagnosticsHandler diagnosticsHandler;
  private final Map<ClassReference, ParsedApiClass> merged = new LinkedHashMap<>();

  private ParsedApiClassMerging(DiagnosticsHandler diagnosticsHandler) {
    this.diagnosticsHandler = diagnosticsHandler;
  }

  /** The returned collection has hash-independent iteration. */
  public static Collection<ParsedApiClass> merge(
      List<ParsedApiClass> parsedClasses, DiagnosticsHandler diagnosticsHandler)
      throws ApiDatabaseGeneratorException {
    ParsedApiClassMerging merger = new ParsedApiClassMerging(diagnosticsHandler);
    merger.merge(parsedClasses);
    return merger.merged.values();
  }

  private void merge(List<ParsedApiClass> classes) throws ApiDatabaseGeneratorException {
    for (ParsedApiClass apiClass : classes) {
      merge(apiClass);
    }
  }

  private void merge(ParsedApiClass apiClass) throws ApiDatabaseGeneratorException {
    ClassReference ref = apiClass.getClassReference();
    if (!merged.containsKey(ref)) {
      merged.put(ref, apiClass);
    } else {
      merged.put(ref, merge(merged.get(ref), apiClass));
    }
  }

  private ParsedApiClass merge(ParsedApiClass a, ParsedApiClass b)
      throws ApiDatabaseGeneratorException {
    assert a.getClassReference().equals(b.getClassReference());
    diagnosticsHandler.error(duplicateClassError(a));

    if (!a.getRange().equals(b.getRange())) {
      throw new ApiDatabaseGeneratorException(
          "Trying to merge "
              + a.getClassReference()
              + " with incompatible ranges "
              + a.getRange()
              + " and "
              + b.getRange());
    }
    ParsedApiClass mergedClass = new ParsedApiClass(a.getClassReference(), a.getRange());

    a.forEachSupertype(mergedClass::registerSupertype);
    b.forEachSupertypeThrowing(
        (classReference, apiRange) -> {
          if (!mergedClass.hasSupertype(classReference)) {
            mergedClass.registerSupertype(classReference, apiRange);
          } else {
            ApiRange mergedRange = mergedClass.getSupertypeRange(classReference);
            if (!mergedRange.equals(apiRange)) {
              throw new ApiDatabaseGeneratorException(
                  "Cannot merge incompatible ranges for extends "
                      + classReference
                      + " in class "
                      + mergedClass.getClassReference()
                      + ". "
                      + mergedRange
                      + " and "
                      + apiRange);
            }
          }
        });

    a.forEachInterface(mergedClass::registerInterface);
    b.forEachInterfaceThrowing(
        (classReference, apiRange) -> {
          if (!mergedClass.hasInterface(classReference)) {
            mergedClass.registerInterface(classReference, apiRange);
          } else {
            ApiRange mergedRange = mergedClass.getInterfaceRange(classReference);
            if (!mergedRange.equals(apiRange)) {
              throw new ApiDatabaseGeneratorException(
                  "Cannot merge incompatible ranges for implements "
                      + classReference
                      + " in class "
                      + mergedClass.getClassReference()
                      + ". "
                      + mergedRange
                      + " and "
                      + apiRange);
            }
          }
        });
    a.forEachMethod(mergedClass::registerMethod);
    b.forEachMethodThrowing(
        (methodReference, apiRange) -> {
          if (!mergedClass.hasMethod(methodReference)) {
            mergedClass.registerMethod(methodReference, apiRange);
          } else {
            ApiRange mergedRange = mergedClass.getMethodRange(methodReference);
            if (!mergedRange.equals(apiRange)) {
              throw new ApiDatabaseGeneratorException(
                  "Cannot merge incompatible ranges for "
                      + methodReference
                      + ". "
                      + mergedRange
                      + " and "
                      + apiRange);
            }
          }
        });
    a.forEachField(mergedClass::registerField);
    b.forEachFieldThrowing(
        (fieldReference, apiRange) -> {
          if (!mergedClass.hasField(fieldReference)) {
            mergedClass.registerField(fieldReference, apiRange);
          } else {
            ApiRange mergedRange = mergedClass.getFieldRange(fieldReference);
            if (!mergedRange.equals(apiRange)) {
              throw new ApiDatabaseGeneratorException(
                  "Cannot merge incompatible ranges for "
                      + fieldReference
                      + ". "
                      + mergedRange
                      + " and "
                      + apiRange);
            }
          }
        });

    return mergedClass;
  }

  private static DuplicateApiDatabaseEntryDiagnostic duplicateClassError(
      ParsedApiClass duplicateClass) {
    String key = duplicateClass.getClassReference().getTypeName();
    String message = "Duplicate class " + key + " found when merging .xml files.";
    return new DuplicateApiDatabaseEntryDiagnostic(message);
  }
}

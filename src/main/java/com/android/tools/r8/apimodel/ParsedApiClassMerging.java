// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.androidapi.DuplicateApiDatabaseEntryDiagnostic;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.StringUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

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

    AndroidApiLevel mergedIntro = a.getApiLevel().min(b.getApiLevel());
    ParsedApiClass mergedClass = new ParsedApiClass(a.getClassReference(), mergedIntro);

    mergeMembers(
        a::forEachSupertype,
        b::forEachSupertype,
        a::getSupertypeApiLevel,
        b::getSupertypeApiLevel,
        mergedClass::registerSupertype);

    checkSupertypeConflict(mergedClass);

    mergeMembers(
        a::forEachInterface,
        b::forEachInterface,
        a::getInterfaceApiLevel,
        b::getInterfaceApiLevel,
        mergedClass::registerInterface);
    mergeMembers(
        a::forEachMethod,
        b::forEachMethod,
        a::getMethodApiLevel,
        b::getMethodApiLevel,
        mergedClass::registerMethod);
    mergeMembers(
        a::forEachField,
        b::forEachField,
        a::getFieldApiLevel,
        b::getFieldApiLevel,
        mergedClass::registerField);

    return mergedClass;
  }

  private static DuplicateApiDatabaseEntryDiagnostic duplicateClassError(
      ParsedApiClass duplicateClass) {
    String key = duplicateClass.getClassReference().getTypeName();
    String message = "Duplicate class " + key + " found when merging .xml files.";
    return new DuplicateApiDatabaseEntryDiagnostic(message);
  }

  private static <T> void mergeMembers(
      Consumer<BiConsumer<T, AndroidApiLevel>> forEachA,
      Consumer<BiConsumer<T, AndroidApiLevel>> forEachB,
      Function<T, AndroidApiLevel> lookupA,
      Function<T, AndroidApiLevel> lookupB,
      BiConsumer<T, AndroidApiLevel> register) {
    forEachA.accept(
        (member, lvlA) -> {
          AndroidApiLevel lvlB = lookupB.apply(member);
          register.accept(member, lvlB != null ? lvlA.min(lvlB) : lvlA);
        });
    forEachB.accept(
        (member, lvlB) -> {
          if (lookupA.apply(member) == null) {
            register.accept(member, lvlB);
          }
        });
  }

  private static void checkSupertypeConflict(ParsedApiClass mergedClass)
      throws ApiDatabaseGeneratorException {
    List<ClassReference> mergedSupertypes = new ArrayList<>();
    mergedClass.forEachSupertype((ref, lvl) -> mergedSupertypes.add(ref));
    if (mergedSupertypes.size() > 1) {
      String supertypesString =
          StringUtils.join(", ", mergedSupertypes, ClassReference::getTypeName);
      throw new ApiDatabaseGeneratorException(
          "Class "
              + mergedClass.getClassReference().getTypeName()
              + " has conflicting supertypes: "
              + supertypesString);
    }
  }
}

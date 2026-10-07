// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public class ParsedApiClassFlattening {

  /**
   * Returns a new list of classes where the class hierarchy has been flattened. Classes will be
   * enlarged to contain all inherited members.
   *
   * <p>The members of Object will not be propagated to keep size smaller.
   *
   * <p>Note that since {@link ParsedApiClass} does not inform whether methods are static or not,
   * this is a superset of the actual members present.
   */
  public static Collection<ParsedApiClass<ApiVersionSet>> flatten(
      Collection<ParsedApiClass<ApiRange>> parsedApiClasses) throws ApiDatabaseGeneratorException {
    return new ParsedApiClassFlattening(parsedApiClasses).flatten();
  }

  private final Map<ClassReference, ParsedApiClass<ApiRange>> classMap = new LinkedHashMap<>();
  private final Map<ClassReference, ParsedApiClass<ApiVersionSet>> flattenedCache =
      new LinkedHashMap<>();
  private final Set<ClassReference> visited = new HashSet<>();

  private ParsedApiClassFlattening(Collection<ParsedApiClass<ApiRange>> parsedApiClasses) {
    for (ParsedApiClass<ApiRange> clazz : parsedApiClasses) {
      classMap.put(clazz.getClassReference(), clazz);
    }
  }

  /** Returns flattened classes with all inherited members except from Object. */
  private Collection<ParsedApiClass<ApiVersionSet>> flatten() throws ApiDatabaseGeneratorException {
    for (ClassReference ref : classMap.keySet()) {
      flattenClass(ref);
    }
    return new ArrayList<>(flattenedCache.values());
  }

  /** Returns a flattened class of {@code ref} with all inherited members except from Object. */
  private ParsedApiClass<ApiVersionSet> flattenClass(ClassReference ref)
      throws ApiDatabaseGeneratorException {
    if (flattenedCache.containsKey(ref)) {
      return flattenedCache.get(ref);
    }

    if (!classMap.containsKey(ref)) {
      throw new ApiDatabaseGeneratorException("Missing class: " + ref);
    }
    ParsedApiClass<ApiRange> original = classMap.get(ref);

    if (visited.contains(ref)) {
      throw new ApiDatabaseGeneratorException("Class hierarchy cycle found that includes " + ref);
    }
    visited.add(ref);

    ParsedApiClass<ApiVersionSet> flattened =
        new ParsedApiClass<>(original.getClassReference(), original.getData());
    original.forEachSupertype(flattened::registerSupertype);
    original.forEachInterface(flattened::registerInterface);

    Map<MethodReference, ApiVersionSet> flattenedMethods = new LinkedHashMap<>();
    Map<FieldTypelessReference, ApiVersionSet> flattenedFields = new LinkedHashMap<>();

    // Original members.
    original.forEachMethod(flattenedMethods::put);
    original.forEachField(flattenedFields::put);

    // Inherited members.
    original.forEachSupertypeThrowing(
        (superRef, inheritanceRange) -> {
          if (superRef.getBinaryName().equals("java/lang/Object")) {
            return;
          }
          inheritMembers(
              flattened.getClassReference(),
              flattenClass(superRef),
              inheritanceRange,
              flattenedMethods,
              flattenedFields);
        });
    original.forEachInterfaceThrowing(
        (ifaceRef, inheritanceRange) ->
            inheritMembers(
                flattened.getClassReference(),
                flattenClass(ifaceRef),
                inheritanceRange,
                flattenedMethods,
                flattenedFields));

    // Pack and store class.
    flattenedMethods.forEach(flattened::registerMethod);
    flattenedFields.forEach(flattened::registerField);
    flattenedCache.put(ref, flattened);
    return flattened;
  }

  private static void inheritMembers(
      ClassReference child,
      ParsedApiClass<ApiVersionSet> parent,
      ApiVersionSet inheritanceRange,
      Map<MethodReference, ApiVersionSet> childMethods,
      Map<FieldTypelessReference, ApiVersionSet> childFields) {
    parent.forEachMethod(
        (methodRef, methodRange) -> {
          if (methodRef.getMethodName().equals("<init>")) {
            return;
          }
          ApiVersionSet inheritedRange = inheritanceRange.intersect(methodRange);
          if (inheritedRange == null) {
            return;
          }
          mergeMethod(childMethods, methodWithNewHolder(child, methodRef), inheritedRange);
        });

    parent.forEachField(
        (fieldRef, fieldRange) -> {
          ApiVersionSet inheritedRange = inheritanceRange.intersect(fieldRange);
          if (inheritedRange == null) {
            return;
          }
          mergeField(childFields, fieldWithNewHolder(child, fieldRef), inheritedRange);
        });
  }

  private static FieldTypelessReference fieldWithNewHolder(
      ClassReference child, FieldTypelessReference fieldRef) {
    return new FieldTypelessReference(child, fieldRef.getFieldName());
  }

  private static MethodReference methodWithNewHolder(
      ClassReference child, MethodReference methodRef) {
    return Reference.method(
        child, methodRef.getMethodName(), methodRef.getFormalTypes(), methodRef.getReturnType());
  }

  private static void mergeMethod(
      Map<MethodReference, ApiVersionSet> methods, MethodReference newMethod, ApiVersionSet range) {
    ApiVersionSet existingMethod = methods.get(newMethod);
    if (existingMethod != null) {
      methods.put(newMethod, existingMethod.union(range));
    } else {
      methods.put(newMethod, range);
    }
  }

  private static void mergeField(
      Map<FieldTypelessReference, ApiVersionSet> fields,
      FieldTypelessReference newField,
      ApiVersionSet range) {
    ApiVersionSet existingField = fields.get(newField);
    if (existingField != null) {
      fields.put(newField, existingField.union(range));
    } else {
      fields.put(newField, range);
    }
  }
}

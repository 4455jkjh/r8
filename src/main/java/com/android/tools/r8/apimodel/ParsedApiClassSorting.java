// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

public class ParsedApiClassSorting {

  private static final Comparator<ClassReference> CLASS_REFERENCE_COMPARATOR =
      Comparator.comparing(ClassReference::getDescriptor);

  private static final Comparator<MethodReference> METHOD_REFERENCE_COMPARATOR =
      Comparator.comparing(MethodReference::getMethodName)
          .thenComparing(MethodReference::getMethodDescriptor)
          .thenComparing(method -> method.getHolderClass().getDescriptor());

  private static final Comparator<FieldTypelessReference> FIELD_REFERENCE_COMPARATOR =
      Comparator.comparing(FieldTypelessReference::getFieldName)
          .thenComparing(field -> field.getHolderClass().getDescriptor());

  private static final Comparator<ParsedApiClass<?>> PARSED_API_CLASS_COMPARATOR =
      Comparator.comparing(ParsedApiClass::getClassReference, CLASS_REFERENCE_COMPARATOR);

  public static <D> Collection<ParsedApiClass<D>> sorted(Collection<ParsedApiClass<D>> apiClasses)
      throws ApiDatabaseGeneratorException {
    List<ParsedApiClass<D>> sortedClasses = new ArrayList<>(apiClasses.size());
    for (ParsedApiClass<D> apiClass : apiClasses) {
      sortedClasses.add(sortedClass(apiClass));
    }
    return uniquelySorted(
        sortedClasses, PARSED_API_CLASS_COMPARATOR, ParsedApiClass::getClassReference);
  }

  private static <D> ParsedApiClass<D> sortedClass(ParsedApiClass<D> apiClass)
      throws ApiDatabaseGeneratorException {
    ParsedApiClass<D> sortedClass =
        new ParsedApiClass<>(apiClass.getClassReference(), apiClass.getData());

    List<ClassReference> supertypes = new ArrayList<>();
    apiClass.forEachSupertype((ref, range) -> supertypes.add(ref));
    for (ClassReference supertype : uniquelySorted(supertypes, CLASS_REFERENCE_COMPARATOR)) {
      sortedClass.registerSupertype(supertype, apiClass.getSupertypeData(supertype));
    }

    List<ClassReference> interfaces = new ArrayList<>();
    apiClass.forEachInterface((ref, range) -> interfaces.add(ref));
    for (ClassReference iface : uniquelySorted(interfaces, CLASS_REFERENCE_COMPARATOR)) {
      sortedClass.registerInterface(iface, apiClass.getInterfaceData(iface));
    }

    List<MethodReference> methods = new ArrayList<>();
    apiClass.forEachMethod((ref, range) -> methods.add(ref));
    for (MethodReference method : uniquelySorted(methods, METHOD_REFERENCE_COMPARATOR)) {
      sortedClass.registerMethod(method, apiClass.getMethodData(method));
    }

    List<FieldTypelessReference> fields = new ArrayList<>();
    apiClass.forEachField((ref, range) -> fields.add(ref));
    for (FieldTypelessReference field : uniquelySorted(fields, FIELD_REFERENCE_COMPARATOR)) {
      sortedClass.registerField(field, apiClass.getFieldData(field));
    }

    return sortedClass;
  }

  private static <T> List<T> uniquelySorted(
      Collection<T> items, Comparator<? super T> comparator, Function<T, ?> toMessage)
      throws ApiDatabaseGeneratorException {
    List<T> sorted = new ArrayList<>(items);
    sorted.sort(comparator);
    T previous = null;
    for (T current : sorted) {
      if (previous != null && comparator.compare(previous, current) == 0) {
        throw new ApiDatabaseGeneratorException(
            "Found duplicates for: " + toMessage.apply(current));
      }
      previous = current;
    }
    return sorted;
  }

  private static <T> List<T> uniquelySorted(Collection<T> items, Comparator<? super T> comparator)
      throws ApiDatabaseGeneratorException {
    return uniquelySorted(items, comparator, Object::toString);
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.utils.internal.FunctionUtils.ignoreArgument;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.MapUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

public class ParsedApiClass {

  private final ClassReference apiClassReference;
  private final AndroidApiLevel introApiLevel;
  private final Map<ClassReference, AndroidApiLevel> supertypes = new LinkedHashMap<>();
  private final Map<ClassReference, AndroidApiLevel> interfaces = new LinkedHashMap<>();
  private final Map<MethodReference, AndroidApiLevel> methods = new LinkedHashMap<>();
  private final Map<FieldTypelessReference, AndroidApiLevel> fields = new LinkedHashMap<>();

  public ParsedApiClass(ClassReference apiClassReference, AndroidApiLevel introApiLevel) {
    this.apiClassReference = apiClassReference;
    this.introApiLevel = introApiLevel;
  }

  public ClassReference getClassReference() {
    return apiClassReference;
  }

  public AndroidApiLevel getApiLevel() {
    return introApiLevel;
  }

  public void registerSupertype(ClassReference reference, AndroidApiLevel introApiLevel) {
    assert !supertypes.containsKey(reference) : reference + " is already registered";
    supertypes.put(reference, introApiLevel);
  }

  public boolean hasSupertype(ClassReference reference) {
    return supertypes.containsKey(reference);
  }

  public Map<ClassReference, AndroidApiLevel> getSupertypes() {
    return MapUtils.unmodifiableForTesting(supertypes);
  }

  public void visitSuperType(BiConsumer<ClassReference, AndroidApiLevel> consumer) {
    supertypes.forEach(consumer);
  }

  public void registerInterface(ClassReference reference, AndroidApiLevel introApiLevel) {
    assert !interfaces.containsKey(reference) : reference + " is already registered";
    interfaces.put(reference, introApiLevel);
  }

  public boolean hasInterface(ClassReference reference) {
    return interfaces.containsKey(reference);
  }

  public Map<ClassReference, AndroidApiLevel> getInterfaces() {
    return MapUtils.unmodifiableForTesting(interfaces);
  }

  public void visitInterface(BiConsumer<ClassReference, AndroidApiLevel> consumer) {
    interfaces.forEach(consumer);
  }

  public void registerMethod(MethodReference reference, AndroidApiLevel introApiLevel) {
    assert !methods.containsKey(reference) : reference + " is already registered";
    methods.put(reference, introApiLevel);
  }

  public boolean hasMethod(MethodReference reference) {
    return methods.containsKey(reference);
  }

  public Map<MethodReference, AndroidApiLevel> getMethods() {
    return MapUtils.unmodifiableForTesting(methods);
  }

  public void visitMethodReferences(BiConsumer<AndroidApiLevel, List<MethodReference>> consumer) {
    var fixedOrderedMethods = new TreeMap<AndroidApiLevel, List<MethodReference>>();
    methods.forEach(
        (reference, apiLevel) ->
            fixedOrderedMethods
                .computeIfAbsent(apiLevel, ignoreArgument(ArrayList::new))
                .add(reference));
    fixedOrderedMethods.forEach(
        (apiLevel, references) -> {
          references.sort(Comparator.comparing(MethodReference::getMethodName));
          consumer.accept(apiLevel, references);
        });
  }

  public void registerField(FieldTypelessReference reference, AndroidApiLevel introApiLevel) {
    assert !fields.containsKey(reference) : reference + " is already registered";
    fields.put(reference, introApiLevel);
  }

  public boolean hasField(FieldTypelessReference reference) {
    return fields.containsKey(reference);
  }

  public Map<FieldTypelessReference, AndroidApiLevel> getFields() {
    return MapUtils.unmodifiableForTesting(fields);
  }

  public void visitFieldReferences(
      BiConsumer<AndroidApiLevel, List<FieldTypelessReference>> consumer) {
    var fixedOrderedFields = new TreeMap<AndroidApiLevel, List<FieldTypelessReference>>();
    fields.forEach(
        (reference, apiLevel) ->
            fixedOrderedFields
                .computeIfAbsent(apiLevel, ignoreArgument(ArrayList::new))
                .add(reference));
    fixedOrderedFields.forEach(
        (apiLevel, references) -> {
          references.sort(Comparator.comparing(FieldTypelessReference::getFieldName));
          consumer.accept(apiLevel, references);
        });
  }
}

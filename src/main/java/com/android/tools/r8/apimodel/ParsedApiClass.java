// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.internal.ThrowingBiConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

public class ParsedApiClass {

  private final ClassReference apiClassReference;
  private final ApiRange apiRange;
  private final Map<ClassReference, ApiRange> supertypes = new LinkedHashMap<>();
  private final Map<ClassReference, ApiRange> interfaces = new LinkedHashMap<>();
  private final Map<MethodReference, ApiRange> methods = new LinkedHashMap<>();
  private final Map<FieldTypelessReference, ApiRange> fields = new LinkedHashMap<>();

  public ParsedApiClass(ClassReference apiClassReference, ApiRange apiRange) {
    assert apiClassReference != null;
    assert apiRange != null : "null api range for " + apiClassReference;
    this.apiClassReference = apiClassReference;
    this.apiRange = apiRange;
  }

  public ClassReference getClassReference() {
    return apiClassReference;
  }

  public ApiRange getRange() {
    return apiRange;
  }

  public void registerSupertype(ClassReference reference, ApiRange apiRange) {
    assert !supertypes.containsKey(reference) : reference + " is already registered";
    supertypes.put(reference, apiRange);
  }

  public boolean hasSupertype(ClassReference reference) {
    return supertypes.containsKey(reference);
  }

  public ApiRange getSupertypeRange(ClassReference reference) {
    return supertypes.get(reference);
  }

  /** Visited in insertion order */
  public void forEachSupertype(BiConsumer<ClassReference, ApiRange> consumer) {
    supertypes.forEach(consumer);
  }

  /** Visited in insertion order. */
  public <E extends Throwable> void forEachSupertypeThrowing(
      ThrowingBiConsumer<ClassReference, ApiRange, E> consumer) throws E {
    for (Map.Entry<ClassReference, ApiRange> entry : supertypes.entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }

  public void registerInterface(ClassReference reference, ApiRange apiRange) {
    assert !interfaces.containsKey(reference) : reference + " is already registered";
    interfaces.put(reference, apiRange);
  }

  public boolean hasInterface(ClassReference reference) {
    return interfaces.containsKey(reference);
  }

  public ApiRange getInterfaceRange(ClassReference reference) {
    return interfaces.get(reference);
  }

  /** Visited in insertion order. */
  public void forEachInterface(BiConsumer<ClassReference, ApiRange> consumer) {
    interfaces.forEach(consumer);
  }

  /** Visited in insertion order. */
  public <E extends Throwable> void forEachInterfaceThrowing(
      ThrowingBiConsumer<ClassReference, ApiRange, E> consumer) throws E {
    for (Map.Entry<ClassReference, ApiRange> entry : interfaces.entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }

  public void registerMethod(MethodReference reference, ApiRange apiRange) {
    assert !methods.containsKey(reference) : reference + " is already registered";
    methods.put(reference, apiRange);
  }

  public boolean hasMethod(MethodReference reference) {
    return methods.containsKey(reference);
  }

  public ApiRange getMethodRange(MethodReference reference) {
    return methods.get(reference);
  }

  public int methodCount() {
    return methods.size();
  }

  /** Visited in insertion order. */
  public void forEachMethod(BiConsumer<MethodReference, ApiRange> consumer) {
    methods.forEach(consumer);
  }

  /** Visited in insertion order. */
  public <E extends Throwable> void forEachMethodThrowing(
      ThrowingBiConsumer<MethodReference, ApiRange, E> consumer) throws E {
    for (Map.Entry<MethodReference, ApiRange> entry : methods.entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }

  public void registerField(FieldTypelessReference reference, ApiRange apiRange) {
    assert !fields.containsKey(reference) : reference + " is already registered";
    fields.put(reference, apiRange);
  }

  public boolean hasField(FieldTypelessReference reference) {
    return fields.containsKey(reference);
  }

  public ApiRange getFieldRange(FieldTypelessReference reference) {
    return fields.get(reference);
  }

  public int fieldCount() {
    return fields.size();
  }

  /** Visited in insertion order. */
  public void forEachField(BiConsumer<FieldTypelessReference, ApiRange> consumer) {
    fields.forEach(consumer);
  }

  /** Visited in insertion order. */
  public <E extends Throwable> void forEachFieldThrowing(
      ThrowingBiConsumer<FieldTypelessReference, ApiRange, E> consumer) throws E {
    for (Map.Entry<FieldTypelessReference, ApiRange> entry : fields.entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }
}

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

public class ParsedApiClass<D> {

  private final ClassReference classReference;
  private final D data;
  private final Map<ClassReference, D> supertypes = new LinkedHashMap<>();
  private final Map<ClassReference, D> interfaces = new LinkedHashMap<>();
  private final Map<MethodReference, D> methods = new LinkedHashMap<>();
  private final Map<FieldTypelessReference, D> fields = new LinkedHashMap<>();

  public ParsedApiClass(ClassReference classReference, D data) {
    assert classReference != null;
    assert data != null : "null data for " + classReference;
    this.classReference = classReference;
    this.data = data;
  }

  public ClassReference getClassReference() {
    return classReference;
  }

  public D getData() {
    return data;
  }

  public void registerSupertype(ClassReference reference, D data) {
    assert !supertypes.containsKey(reference) : reference + " is already registered";
    supertypes.put(reference, data);
  }

  public boolean hasSupertype(ClassReference reference) {
    return supertypes.containsKey(reference);
  }

  public D getSupertypeData(ClassReference reference) {
    return supertypes.get(reference);
  }

  /** Visited in insertion order */
  public void forEachSupertype(BiConsumer<ClassReference, D> consumer) {
    supertypes.forEach(consumer);
  }

  /** Visited in insertion order. */
  public <E extends Throwable> void forEachSupertypeThrowing(
      ThrowingBiConsumer<ClassReference, D, E> consumer) throws E {
    for (Map.Entry<ClassReference, D> entry : supertypes.entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }

  public void registerInterface(ClassReference reference, D data) {
    assert !interfaces.containsKey(reference) : reference + " is already registered";
    interfaces.put(reference, data);
  }

  public boolean hasInterface(ClassReference reference) {
    return interfaces.containsKey(reference);
  }

  public D getInterfaceData(ClassReference reference) {
    return interfaces.get(reference);
  }

  /** Visited in insertion order. */
  public void forEachInterface(BiConsumer<ClassReference, D> consumer) {
    interfaces.forEach(consumer);
  }

  /** Visited in insertion order. */
  public <E extends Throwable> void forEachInterfaceThrowing(
      ThrowingBiConsumer<ClassReference, D, E> consumer) throws E {
    for (Map.Entry<ClassReference, D> entry : interfaces.entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }

  public void registerMethod(MethodReference reference, D data) {
    assert !methods.containsKey(reference) : reference + " is already registered";
    methods.put(reference, data);
  }

  public boolean hasMethod(MethodReference reference) {
    return methods.containsKey(reference);
  }

  public D getMethodData(MethodReference reference) {
    return methods.get(reference);
  }

  public int methodCount() {
    return methods.size();
  }

  /** Visited in insertion order. */
  public void forEachMethod(BiConsumer<MethodReference, D> consumer) {
    methods.forEach(consumer);
  }

  /** Visited in insertion order. */
  public <E extends Throwable> void forEachMethodThrowing(
      ThrowingBiConsumer<MethodReference, D, E> consumer) throws E {
    for (Map.Entry<MethodReference, D> entry : methods.entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }

  public boolean hasConstructor() {
    for (var method : methods.keySet()) {
      if (method.getMethodName().equals("<init>")) {
        return true;
      }
    }
    return false;
  }

  public void registerField(FieldTypelessReference reference, D data) {
    assert !fields.containsKey(reference) : reference + " is already registered";
    fields.put(reference, data);
  }

  public boolean hasField(FieldTypelessReference reference) {
    return fields.containsKey(reference);
  }

  public D getFieldData(FieldTypelessReference reference) {
    return fields.get(reference);
  }

  public int fieldCount() {
    return fields.size();
  }

  /** Visited in insertion order. */
  public void forEachField(BiConsumer<FieldTypelessReference, D> consumer) {
    fields.forEach(consumer);
  }

  /** Visited in insertion order. */
  public <E extends Throwable> void forEachFieldThrowing(
      ThrowingBiConsumer<FieldTypelessReference, D, E> consumer) throws E {
    for (Map.Entry<FieldTypelessReference, D> entry : fields.entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }
}

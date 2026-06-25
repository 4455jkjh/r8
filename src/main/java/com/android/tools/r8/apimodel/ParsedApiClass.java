// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.ThrowingBiConsumer;
import java.util.LinkedHashMap;
import java.util.Map;
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

  /** Visited in insertion order */
  public void forEachSupertype(BiConsumer<ClassReference, AndroidApiLevel> consumer) {
    supertypes.forEach(consumer);
  }

  /** Visited in insertion order. */
  public <E extends Throwable> void forEachSupertypeThrowing(
      ThrowingBiConsumer<ClassReference, AndroidApiLevel, E> consumer) throws E {
    for (Map.Entry<ClassReference, AndroidApiLevel> entry : supertypes.entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }

  public void registerInterface(ClassReference reference, AndroidApiLevel introApiLevel) {
    assert !interfaces.containsKey(reference) : reference + " is already registered";
    interfaces.put(reference, introApiLevel);
  }

  public boolean hasInterface(ClassReference reference) {
    return interfaces.containsKey(reference);
  }

  /** Visited in insertion order. */
  public void forEachInterface(BiConsumer<ClassReference, AndroidApiLevel> consumer) {
    interfaces.forEach(consumer);
  }

  /** Visited in insertion order. */
  public <E extends Throwable> void forEachInterfaceThrowing(
      ThrowingBiConsumer<ClassReference, AndroidApiLevel, E> consumer) throws E {
    for (Map.Entry<ClassReference, AndroidApiLevel> entry : interfaces.entrySet()) {
      consumer.accept(entry.getKey(), entry.getValue());
    }
  }

  public void registerMethod(MethodReference reference, AndroidApiLevel introApiLevel) {
    assert !methods.containsKey(reference) : reference + " is already registered";
    methods.put(reference, introApiLevel);
  }

  public boolean hasMethod(MethodReference reference) {
    return methods.containsKey(reference);
  }

  public int methodCount() {
    return methods.size();
  }

  /** Visited in insertion order. */
  public void forEachMethod(BiConsumer<MethodReference, AndroidApiLevel> consumer) {
    methods.forEach(consumer);
  }

  public void registerField(FieldTypelessReference reference, AndroidApiLevel introApiLevel) {
    assert !fields.containsKey(reference) : reference + " is already registered";
    fields.put(reference, introApiLevel);
  }

  public boolean hasField(FieldTypelessReference reference) {
    return fields.containsKey(reference);
  }

  public int fieldCount() {
    return fields.size();
  }

  /** Visited in insertion order. */
  public void forEachField(BiConsumer<FieldTypelessReference, AndroidApiLevel> consumer) {
    fields.forEach(consumer);
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.utils.internal.SetUtils;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class DexClassAndFieldSetBase<T extends DexClassAndField> implements Collection<T> {

  Map<DexField, T> backing;

  DexClassAndFieldSetBase() {
    this.backing = createBacking();
  }

  DexClassAndFieldSetBase(Map<DexField, T> backing) {
    this.backing = backing;
  }

  DexClassAndFieldSetBase(int capacity) {
    this.backing = createBacking(capacity);
  }

  abstract Map<DexField, T> createBacking();

  abstract Map<DexField, T> createBacking(int capacity);

  @Override
  public boolean add(T field) {
    T existing = backing.put(field.getReference(), field);
    return existing == null;
  }

  @Override
  public boolean addAll(Collection<? extends T> fields) {
    boolean changed = false;
    for (T field : fields) {
      changed |= add(field);
    }
    return changed;
  }

  public T get(DexField field) {
    return backing.get(field);
  }

  public T getFirst() {
    return iterator().next();
  }

  @Override
  public boolean contains(Object o) {
    if (o instanceof DexClassAndField) {
      DexClassAndField field = (DexClassAndField) o;
      return contains(field.getReference());
    }
    return false;
  }

  public boolean contains(DexField field) {
    return backing.containsKey(field);
  }

  public boolean contains(DexEncodedField field) {
    return backing.containsKey(field.getReference());
  }

  public boolean contains(T field) {
    return backing.containsKey(field.getReference());
  }

  @Override
  public boolean containsAll(Collection<?> collection) {
    return Iterables.all(collection, this::contains);
  }

  @Override
  public void clear() {
    backing.clear();
  }

  @Override
  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    return backing.values().iterator();
  }

  @Override
  public boolean remove(Object o) {
    if (o instanceof DexClassAndField) {
      DexClassAndField field = (DexClassAndField) o;
      return remove(field.getReference());
    }
    return false;
  }

  public boolean remove(DexField field) {
    T existing = backing.remove(field);
    return existing != null;
  }

  public boolean remove(DexEncodedField field) {
    return remove(field.getReference());
  }

  @Override
  public boolean removeAll(Collection<?> collection) {
    boolean changed = false;
    for (Object o : collection) {
      changed |= remove(o);
    }
    return changed;
  }

  @Override
  public boolean removeIf(Predicate<? super T> predicate) {
    return backing.values().removeIf(predicate);
  }

  @Override
  public boolean retainAll(Collection<?> collection) {
    return backing.values().retainAll(collection);
  }

  @Override
  public int size() {
    return backing.size();
  }

  @Override
  public Stream<T> stream() {
    return backing.values().stream();
  }

  @Override
  public Object[] toArray() {
    return backing.values().toArray();
  }

  @Override
  public <S> S[] toArray(S[] ss) {
    return backing.values().toArray(ss);
  }

  public Collection<T> toCollection() {
    return backing.values();
  }

  public Set<DexEncodedField> toDefinitionSet() {
    assert backing instanceof IdentityHashMap;
    return toDefinitionSet(SetUtils::newIdentityHashSet);
  }

  public Set<DexEncodedField> toDefinitionSet(IntFunction<Set<DexEncodedField>> factory) {
    Set<DexEncodedField> definitions = factory.apply(size());
    forEach(field -> definitions.add(field.getDefinition()));
    return definitions;
  }

  public Set<DexField> toReferenceSet(IntFunction<Set<DexField>> factory) {
    Set<DexField> definitions = factory.apply(size());
    forEach(field -> definitions.add(field.getReference()));
    return definitions;
  }

  public void trimCapacityIfSizeLessThan(int expectedSize) {
    if (size() < expectedSize) {
      Map<DexField, T> newBacking = createBacking(size());
      newBacking.putAll(backing);
      backing = newBacking;
    }
  }
}

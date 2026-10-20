// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.internal.collections;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Base implementation for a set-like collection of elements {@code T} whose uniqueness and lookup
 * are determined by a derived key {@code K}. Elements are stored in a backing {@link Map} from each
 * element's key to the element itself. Key equality, iteration ordering, and thread-safety are
 * determined by the backing map provided by the implementing class.
 */
public abstract class KeyedSetBase<K, T> implements Collection<T> {

  protected final Map<K, T> backing;

  protected KeyedSetBase(Map<K, T> backing) {
    this.backing = backing;
  }

  protected abstract K getKey(T element);

  protected abstract boolean isElement(Object o);

  @Override
  public boolean add(T element) {
    T existing = backing.put(getKey(element), element);
    return existing == null;
  }

  @Override
  public boolean addAll(Collection<? extends T> elements) {
    boolean changed = false;
    for (T element : elements) {
      changed |= add(element);
    }
    return changed;
  }

  public boolean allMatch(Predicate<? super T> predicate) {
    for (T element : this) {
      if (!predicate.test(element)) {
        return false;
      }
    }
    return true;
  }

  public T get(K key) {
    return backing.get(key);
  }

  public T getFirst() {
    return iterator().next();
  }

  @Override
  @SuppressWarnings("unchecked")
  public boolean contains(Object o) {
    return isElement(o) && containsKey(getKey((T) o));
  }

  public boolean containsKey(K key) {
    return backing.containsKey(key);
  }

  @Override
  public boolean containsAll(Collection<?> collection) {
    for (Object element : collection) {
      if (!contains(element)) {
        return false;
      }
    }
    return true;
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
  @SuppressWarnings("unchecked")
  public boolean remove(Object o) {
    return isElement(o) && removeKey(getKey((T) o));
  }

  public boolean removeKey(K key) {
    T existing = backing.remove(key);
    return existing != null;
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
}

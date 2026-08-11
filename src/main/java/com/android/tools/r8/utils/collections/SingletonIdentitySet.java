// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.collections;

import com.google.common.collect.Iterators;
import java.util.AbstractSet;
import java.util.Iterator;

public class SingletonIdentitySet<T> extends AbstractSet<T> {

  private final T element;

  public SingletonIdentitySet(T element) {
    assert element != null;
    this.element = element;
  }

  public T getElement() {
    return element;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public boolean contains(Object o) {
    return o == element;
  }

  @Override
  public Iterator<T> iterator() {
    return Iterators.singletonIterator(element);
  }

  @Override
  public int size() {
    return 1;
  }
}

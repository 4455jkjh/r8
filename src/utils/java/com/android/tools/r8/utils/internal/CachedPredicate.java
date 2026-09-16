// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.internal;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

public class CachedPredicate<T> implements Predicate<T> {

  private final Predicate<T> predicate;
  private Set<T> isTrue = null;
  private Set<T> isFalse = null;

  public CachedPredicate(Predicate<T> predicate) {
    this.predicate = predicate;
  }

  @Override
  public boolean test(T t) {
    if (isTrue != null && isTrue.contains(t)) {
      return true;
    } else if (isFalse != null && isFalse.contains(t)) {
      return false;
    } else {
      var result = predicate.test(t);
      if (result) {
        if (isTrue == null) {
          isTrue = new HashSet<>();
        }
        isTrue.add(t);
      } else {
        if (isFalse == null) {
          isFalse = new HashSet<>();
        }
        isFalse.add(t);
      }
      return result;
    }
  }
}

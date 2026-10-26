// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.internal;

import com.android.tools.r8.utils.internal.structural.Ordered;

/**
 * Represents a non-empty set of ordered elements, only implemented by {@link SemiInfiniteRange} or
 * {@link SemiInfiniteMultiRange}.
 */
public abstract class SemiInfiniteRangeSet<T extends Ordered<T>> {

  public SemiInfiniteRangeSet() {
    assert this instanceof SemiInfiniteRange || this instanceof SemiInfiniteMultiRange;
  }

  /**
   * Returns the largest possible range that is a subset of this set and contains the greatest
   * element of the set.
   */
  public abstract SemiInfiniteRange<T> getLargestEndRange();

  /** Returns null if the intersection is empty. */
  public abstract SemiInfiniteRangeSet<T> intersect(SemiInfiniteRangeSet<T> other);

  public abstract SemiInfiniteRangeSet<T> union(SemiInfiniteRangeSet<T> other);

  public SemiInfiniteRange<T> asSingleRange() {
    return null;
  }

  public boolean isSingleRange() {
    return asSingleRange() != null;
  }

  public SemiInfiniteMultiRange<T> asMultiRange() {
    return null;
  }

  public boolean isMultiRange() {
    return asMultiRange() != null;
  }
}

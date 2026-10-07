// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

/**
 * Represents a non-empty set of API levels, only implemented by {@link ApiRange} or {@link
 * ApiMultiRange}.
 */
public abstract class ApiVersionSet {

  public ApiVersionSet() {
    assert this instanceof ApiRange || this instanceof ApiMultiRange;
  }

  /**
   * Returns the largest possible range that is a subset of this set and contains the greatest
   * element of the set.
   */
  public abstract ApiRange getLargestEndRange();

  /** Returns null if the intersection is empty. */
  public abstract ApiVersionSet intersect(ApiVersionSet other);

  public abstract ApiVersionSet union(ApiVersionSet other);

  public ApiRange asSingleRange() {
    return null;
  }

  public boolean isSingleRange() {
    return asSingleRange() != null;
  }

  public ApiMultiRange asMultiRange() {
    return null;
  }

  public boolean isMultiRange() {
    return asMultiRange() != null;
  }
}

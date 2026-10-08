// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.utils.internal.Box;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a non-contiguous set of API levels stored as a sorted list of at least two disjoint,
 * non-adjacent {@link ApiRange}s.
 *
 * <p>A valid example: {@code [2, 4[, [5, infinity[} (includes 2, 3, 5, and everything above 5).
 *
 * <p>An invalid example: {@code [2, 4[, [4, infinity[}. This is invalid since it contains adjacent
 * ranges. It could be better be represented as {@code [2, infinity[}.
 *
 * <p>An {@link ApiMultiRange} is invalid if it can be represented with fewer ranges. This also
 * means that no {@link ApiRange} and {@link ApiMultiRange} can represent the same set.
 */
public final class ApiMultiRange extends ApiVersionSet {

  private final ImmutableList<ApiRange> ranges;

  public ApiMultiRange(ImmutableList<ApiRange> ranges) {
    this.ranges = ranges;
    assert verifyInvariants();
  }

  /** Tries to reuse {@code this} or {@code other}, otherwise creates a set from {@code ranges}. */
  private ApiVersionSet createOrReuse(ImmutableList<ApiRange> ranges, ApiVersionSet other) {
    assert !ranges.isEmpty();
    if (ranges.size() == 1) {
      // The individual ranges has their own reuse setup.
      return ranges.get(0);
    }
    if (this.ranges.equals(ranges)) {
      return this;
    }
    ApiMultiRange otherMulti = other.asMultiRange();
    if (otherMulti != null && otherMulti.ranges.equals(ranges)) {
      return other;
    }
    return new ApiMultiRange(ImmutableList.copyOf(ranges));
  }

  private boolean verifyInvariants() {
    assert ranges != null;
    assert ranges.size() >= 2;
    ApiRange prev = null;
    for (var current : ranges) {
      assert current != null;
      if (prev != null) {
        assert prev.removed != null;
        assert prev.removed.isLessThan(current.intro);
      }
      prev = current;
    }
    return true;
  }

  @Override
  public ApiMultiRange asMultiRange() {
    return this;
  }

  @Override
  public ApiRange getLargestEndRange() {
    return ranges.get(ranges.size() - 1);
  }

  private static List<ApiRange> getRanges(ApiVersionSet set) {
    ApiRange single = set.asSingleRange();
    if (single != null) {
      return ImmutableList.of(single);
    }
    assert set.isMultiRange();
    return set.asMultiRange().ranges;
  }

  @Override
  public ApiVersionSet intersect(ApiVersionSet other) {
    List<ApiRange> otherRanges = getRanges(other);
    ImmutableList.Builder<ApiRange> resultBuilder = ImmutableList.builderWithExpectedSize(1);
    int i = 0;
    int j = 0;
    while (i < ranges.size() && j < otherRanges.size()) {
      ApiRange iRange = ranges.get(i);
      ApiRange jRange = otherRanges.get(j);
      ApiRange intersection = iRange.intersect(jRange);
      if (intersection != null) {
        resultBuilder.add(intersection);
      }
      if (jRange.endsAfterEndOf(iRange)) {
        i++;
      } else {
        j++;
      }
    }
    var result = resultBuilder.build();
    if (result.isEmpty()) {
      return null;
    }
    return createOrReuse(result, other);
  }

  @Override
  public ApiVersionSet union(ApiVersionSet other) {
    List<ApiRange> otherRanges = getRanges(other);
    ImmutableList.Builder<ApiRange> resultBuilder =
        ImmutableList.builderWithExpectedSize(ranges.size() + otherRanges.size());
    Box<ApiRange> pending = new Box<>(null);
    int i = 0;
    int j = 0;
    while (i < ranges.size() && j < otherRanges.size()) {
      ApiRange iRange = ranges.get(i);
      ApiRange jRange = otherRanges.get(j);
      if (iRange.startsBeforeStartOf(jRange)) {
        addRange(iRange, resultBuilder, pending);
        i++;
      } else {
        addRange(jRange, resultBuilder, pending);
        j++;
      }
    }
    while (i < ranges.size()) {
      addRange(ranges.get(i++), resultBuilder, pending);
    }
    while (j < otherRanges.size()) {
      addRange(otherRanges.get(j++), resultBuilder, pending);
    }
    resultBuilder.add(pending.get());
    return createOrReuse(resultBuilder.build(), other);
  }

  /**
   * Either merges {@code next} into {@code pending} or adds {@code pending} and moves {@code next}
   * to {@code pending}.
   */
  private static void addRange(
      ApiRange next, ImmutableList.Builder<ApiRange> result, Box<ApiRange> pending) {
    ApiRange pendingRange = pending.get();
    if (pendingRange == null) {
      pending.set(next);
    } else {
      ApiRange merged = pendingRange.unionAsSingleRange(next);
      if (merged != null) {
        pending.set(merged);
      } else {
        result.add(pendingRange);
        pending.set(next);
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    // ApiRange and ApiMultiRange can never be equal by construction.
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ApiMultiRange)) {
      return false;
    }
    ApiMultiRange that = (ApiMultiRange) obj;
    return Objects.equals(ranges, that.ranges);
  }

  @Override
  public int hashCode() {
    return ranges.hashCode();
  }

  @Override
  public String toString() {
    return StringUtils.join(", ", ranges);
  }
}

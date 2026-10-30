// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.internal;

import com.android.tools.r8.utils.internal.structural.Ordered;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a non-contiguous set of ordered elements stored as a sorted list of at least two
 * disjoint, non-adjacent {@link SemiInfiniteRange}s.
 *
 * <p>A valid example: {@code [2, 4[, [5, infinity[} (includes 2, 3, 5, and everything above 5).
 *
 * <p>An invalid example: {@code [2, 4[, [4, infinity[}. This is invalid since it contains adjacent
 * ranges. It could be better represented as {@code [2, infinity[}.
 *
 * <p>A {@link SemiInfiniteMultiRange} is invalid if it can be represented with fewer ranges. This
 * also means that no {@link SemiInfiniteRange} and {@link SemiInfiniteMultiRange} can represent the
 * same set.
 */
public final class SemiInfiniteMultiRange<T extends Ordered<T>> extends SemiInfiniteRangeSet<T> {

  private final ImmutableList<SemiInfiniteRange<T>> ranges;

  public SemiInfiniteMultiRange(ImmutableList<SemiInfiniteRange<T>> ranges) {
    this.ranges = ranges;
    assert verifyInvariants();
  }

  /** Tries to reuse {@code this} or {@code other}, otherwise creates a set from {@code ranges}. */
  private SemiInfiniteRangeSet<T> createOrReuse(
      ImmutableList<SemiInfiniteRange<T>> ranges, SemiInfiniteRangeSet<T> other) {
    assert !ranges.isEmpty();
    if (ranges.size() == 1) {
      // The individual ranges have their own reuse setup.
      return ranges.get(0);
    }
    if (this.ranges.equals(ranges)) {
      return this;
    }
    SemiInfiniteMultiRange<T> otherMulti = other.asMultiRange();
    if (otherMulti != null && otherMulti.ranges.equals(ranges)) {
      return other;
    }
    return new SemiInfiniteMultiRange<>(ImmutableList.copyOf(ranges));
  }

  private boolean verifyInvariants() {
    assert ranges != null;
    assert ranges.size() >= 2;
    IterableUtils.forEachWithPrevious(
        ranges,
        (prev, current) -> {
          assert prev != null;
          assert current != null;
          assert prev.end != null;
          assert prev.end.isLessThan(current.start);
        });
    return true;
  }

  @Override
  public SemiInfiniteMultiRange<T> asMultiRange() {
    return this;
  }

  @Override
  public SemiInfiniteRange<T> getLargestEndRange() {
    return ranges.get(ranges.size() - 1);
  }

  private static <T extends Ordered<T>> List<SemiInfiniteRange<T>> getRanges(
      SemiInfiniteRangeSet<T> set) {
    SemiInfiniteRange<T> single = set.asSingleRange();
    if (single != null) {
      return ImmutableList.of(single);
    }
    assert set.isMultiRange();
    return set.asMultiRange().ranges;
  }

  @Override
  public SemiInfiniteRangeSet<T> intersect(SemiInfiniteRangeSet<T> other) {
    List<SemiInfiniteRange<T>> otherRanges = getRanges(other);
    ImmutableList.Builder<SemiInfiniteRange<T>> resultBuilder =
        ImmutableList.builderWithExpectedSize(1);
    int i = 0;
    int j = 0;
    while (i < ranges.size() && j < otherRanges.size()) {
      SemiInfiniteRange<T> iRange = ranges.get(i);
      SemiInfiniteRange<T> jRange = otherRanges.get(j);
      SemiInfiniteRange<T> intersection = iRange.intersect(jRange);
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
  public SemiInfiniteRangeSet<T> union(SemiInfiniteRangeSet<T> other) {
    List<SemiInfiniteRange<T>> otherRanges = getRanges(other);
    ImmutableList.Builder<SemiInfiniteRange<T>> resultBuilder =
        ImmutableList.builderWithExpectedSize(ranges.size() + otherRanges.size());
    Box<SemiInfiniteRange<T>> pending = new Box<>(null);
    int i = 0;
    int j = 0;
    while (i < ranges.size() && j < otherRanges.size()) {
      SemiInfiniteRange<T> iRange = ranges.get(i);
      SemiInfiniteRange<T> jRange = otherRanges.get(j);
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
  private static <T extends Ordered<T>> void addRange(
      SemiInfiniteRange<T> next,
      ImmutableList.Builder<SemiInfiniteRange<T>> result,
      Box<SemiInfiniteRange<T>> pending) {
    SemiInfiniteRange<T> pendingRange = pending.get();
    if (pendingRange == null) {
      pending.set(next);
    } else {
      SemiInfiniteRange<T> merged = pendingRange.unionAsSingleRange(next);
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
    // SemiInfiniteRange and SemiInfiniteMultiRange can never be equal by construction.
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SemiInfiniteMultiRange<?>)) {
      return false;
    }
    SemiInfiniteMultiRange<?> that = (SemiInfiniteMultiRange<?>) obj;
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

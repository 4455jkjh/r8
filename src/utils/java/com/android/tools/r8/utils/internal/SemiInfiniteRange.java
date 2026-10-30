// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.internal;

import com.android.tools.r8.utils.internal.structural.Ordered;
import com.google.common.collect.ImmutableList;
import java.util.Objects;

/**
 * Represents a non-empty half-open range starting from {@code start} (inclusive) up to {@code end}
 * (exclusive).
 *
 * <p>{@code start} is always non-null, while {@code end} can be {@code null} to denote an infinite
 * upper bound {@code [start, infinity[}.
 */
public final class SemiInfiniteRange<T extends Ordered<T>> extends SemiInfiniteRangeSet<T> {

  public final T start;
  public final T end;

  public SemiInfiniteRange(T start, T end) {
    assert start != null;
    assert end == null || start.isLessThan(end) : "Invalid range: " + formatted(start, end);
    this.start = start;
    this.end = end;
  }

  public static <T extends Ordered<T>> SemiInfiniteRange<T> startingAt(T start) {
    return new SemiInfiniteRange<>(start, null);
  }

  @Override
  public SemiInfiniteRange<T> asSingleRange() {
    return this;
  }

  @Override
  public SemiInfiniteRange<T> getLargestEndRange() {
    return this;
  }

  public boolean hasEnd() {
    return end != null;
  }

  @Override
  public boolean equals(Object obj) {
    // SemiInfiniteRange and SemiInfiniteMultiRange can never be equal by construction.
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof SemiInfiniteRange<?>)) {
      return false;
    }
    SemiInfiniteRange<?> that = (SemiInfiniteRange<?>) obj;
    return this.start.equals(that.start) && Objects.equals(this.end, that.end);
  }

  private boolean equals(T start, T end) {
    if (!this.start.equals(start)) {
      return false;
    }
    return Objects.equals(this.end, end);
  }

  @Override
  public int hashCode() {
    return Objects.hash(start, end);
  }

  @Override
  public String toString() {
    return formatted(start, end);
  }

  private static <T> String formatted(T start, T end) {
    String formattedEnd = end != null ? end.toString() : "infinity";
    return "[" + start + ", " + formattedEnd + "[";
  }

  public boolean isWithin(SemiInfiniteRange<T> other) {
    return !this.startsBeforeStartOf(other) && !this.endsAfterEndOf(other);
  }

  /** Strictly before. */
  public boolean startsBeforeStartOf(SemiInfiniteRange<T> other) {
    return this.start.isLessThan(other.start);
  }

  /** Strictly after. */
  public boolean endsAfterEndOf(SemiInfiniteRange<T> other) {
    if (other.end == null) {
      return false;
    }
    if (this.end == null) {
      return true;
    }
    return this.end.isGreaterThan(other.end);
  }

  public boolean isOverlappingWith(SemiInfiniteRange<T> other) {
    return !this.startsAfterEndOf(other) && !this.endsBeforeStartOf(other);
  }

  public boolean endsBeforeStartOf(SemiInfiniteRange<T> other) {
    return this.hasEnd() && other.start.isGreaterThanOrEqualTo(this.end);
  }

  public boolean startsAfterEndOf(SemiInfiniteRange<T> other) {
    return other.hasEnd() && this.start.isGreaterThanOrEqualTo(other.end);
  }

  /** Returns null if the intersection is empty. */
  public SemiInfiniteRange<T> intersect(SemiInfiniteRange<T> other) {
    T start = Ordered.max(this.start, other.start);
    T end = Ordered.minIgnoreNull(this.end, other.end);
    boolean isEmpty = end != null && start.isGreaterThanOrEqualTo(end);
    if (isEmpty) {
      return null;
    }
    return createOrReuse(start, end, other);
  }

  @Override
  public SemiInfiniteRangeSet<T> intersect(SemiInfiniteRangeSet<T> other) {
    SemiInfiniteRange<T> otherSingle = other.asSingleRange();
    if (otherSingle != null) {
      return intersect(otherSingle);
    }
    assert other.isMultiRange();
    return other.asMultiRange().intersect(this);
  }

  /** Returns null if the union is not expressible as {@link SemiInfiniteRange}. */
  public SemiInfiniteRange<T> unionAsSingleRange(SemiInfiniteRange<T> other) {
    SemiInfiniteRange<T> first;
    SemiInfiniteRange<T> second;
    if (start.isLessThanOrEqualTo(other.start)) {
      first = this;
      second = other;
    } else {
      first = other;
      second = this;
    }
    if (first.end != null && first.end.isLessThan(second.start)) {
      return null;
    }
    T unionStart = first.start;
    T unionEnd;
    if (end == null || other.end == null) {
      unionEnd = null;
    } else {
      unionEnd = Ordered.max(this.end, other.end);
    }
    return createOrReuse(unionStart, unionEnd, other);
  }

  @Override
  public SemiInfiniteRangeSet<T> union(SemiInfiniteRangeSet<T> other) {
    SemiInfiniteRange<T> otherSingle = other.asSingleRange();
    if (otherSingle != null) {
      SemiInfiniteRange<T> contiguous = unionAsSingleRange(otherSingle);
      if (contiguous != null) {
        return contiguous;
      }
      if (startsBeforeStartOf(otherSingle)) {
        return new SemiInfiniteMultiRange<>(ImmutableList.of(this, otherSingle));
      } else {
        return new SemiInfiniteMultiRange<>(ImmutableList.of(otherSingle, this));
      }
    }
    assert other.isMultiRange();
    return other.asMultiRange().union(this);
  }

  /** Tries to reuse {@code this} or {@code other}, otherwise creates a range from the arguments. */
  private SemiInfiniteRange<T> createOrReuse(T start, T end, SemiInfiniteRange<T> other) {
    if (equals(start, end)) {
      return this;
    }
    if (other.equals(start, end)) {
      return other;
    }
    return new SemiInfiniteRange<>(start, end);
  }
}

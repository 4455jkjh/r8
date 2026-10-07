// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import java.util.Objects;

/**
 * Represents a non-empty API range starting from {@code intro} (inclusive) up to {@code removed}
 * (exclusive).
 *
 * <p>{@code intro} is always non-null but {@code removed} can be {@code null} which means that the
 * end-point is infinite, i.e. that some entry has not been removed.
 */
public final class ApiRange extends ApiVersionSet {

  public final AndroidApiLevel intro;
  public final AndroidApiLevel removed;

  public ApiRange(AndroidApiLevel intro, AndroidApiLevel removed) {
    assert intro != null;
    assert removed == null || intro.isLessThan(removed)
        : "Invalid Api range: " + formatted(intro, removed);
    this.intro = intro;
    this.removed = removed;
  }

  public ApiRange(AndroidApiLevel intro) {
    this.intro = intro;
    this.removed = null;
  }

  @Override
  public ApiRange asSingleRange() {
    return this;
  }

  @Override
  public ApiRange getLargestEndRange() {
    return this;
  }

  public boolean isRemoved() {
    return removed != null;
  }

  @Override
  public boolean equals(Object obj) {
    // ApiRange and ApiMultiRange can never be equal by construction.
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ApiRange)) {
      return false;
    }
    ApiRange that = (ApiRange) obj;
    return equals(that.intro, that.removed);
  }

  private boolean equals(AndroidApiLevel intro, AndroidApiLevel removed) {
    if (!this.intro.equals(intro)) {
      return false;
    }
    return Objects.equals(this.removed, removed);
  }

  @Override
  public int hashCode() {
    return Objects.hash(intro, removed);
  }

  @Override
  public String toString() {
    return formatted(intro, removed);
  }

  private static String formatted(AndroidApiLevel intro, AndroidApiLevel removed) {
    String formattedRemoved = removed != null ? removed.toString() : "infinity";
    return "[" + intro + ", " + formattedRemoved + "[";
  }

  public boolean isWithin(ApiRange other) {
    return !this.startsBeforeStartOf(other) && !this.endsAfterEndOf(other);
  }

  /** Strictly before. */
  public boolean startsBeforeStartOf(ApiRange other) {
    return this.intro.isLessThan(other.intro);
  }

  /** Strictly after. */
  public boolean endsAfterEndOf(ApiRange other) {
    if (other.removed == null) {
      return false;
    }
    if (this.removed == null) {
      return true;
    }
    return this.removed.isGreaterThan(other.removed);
  }

  public boolean isOverlappingWith(ApiRange other) {
    return !this.startsAfterEndOf(other) && !this.endsBeforeStartOf(other);
  }

  public boolean endsBeforeStartOf(ApiRange other) {
    return this.isRemoved() && other.intro.isGreaterThanOrEqualTo(this.removed);
  }

  public boolean startsAfterEndOf(ApiRange other) {
    return other.isRemoved() && this.intro.isGreaterThanOrEqualTo(other.removed);
  }

  /** Returns null if the intersection is empty. */
  public ApiRange intersect(ApiRange other) {
    AndroidApiLevel intro = this.intro.max(other.intro);
    AndroidApiLevel removed = Ordered.minIgnoreNull(this.removed, other.removed);
    boolean isEmpty = removed != null && intro.isGreaterThanOrEqualTo(removed);
    if (isEmpty) {
      return null;
    }
    return createOrReuse(intro, removed, other);
  }

  @Override
  public ApiVersionSet intersect(ApiVersionSet other) {
    ApiRange otherSingle = other.asSingleRange();
    if (otherSingle != null) {
      return intersect(otherSingle);
    }
    assert other.isMultiRange();
    return other.asMultiRange().intersect(this);
  }

  /** Returns null if the union is not expressible as {@link ApiRange}. */
  public ApiRange unionAsSingleRange(ApiRange other) {
    ApiRange first;
    ApiRange second;
    if (intro.isLessThanOrEqualTo(other.intro)) {
      first = this;
      second = other;
    } else {
      first = other;
      second = this;
    }
    if (first.removed != null && first.removed.isLessThan(second.intro)) {
      return null;
    }
    AndroidApiLevel unionIntro = first.intro;
    AndroidApiLevel unionRemoved;
    if (removed == null || other.removed == null) {
      unionRemoved = null;
    } else {
      unionRemoved = this.removed.max(other.removed);
    }
    return createOrReuse(unionIntro, unionRemoved, other);
  }

  @Override
  public ApiVersionSet union(ApiVersionSet other) {
    ApiRange otherSingle = other.asSingleRange();
    if (otherSingle != null) {
      ApiRange contiguous = unionAsSingleRange(otherSingle);
      if (contiguous != null) {
        return contiguous;
      }
      if (startsBeforeStartOf(otherSingle)) {
        return new ApiMultiRange(ImmutableList.of(this, otherSingle));
      } else {
        return new ApiMultiRange(ImmutableList.of(otherSingle, this));
      }
    }
    assert other.isMultiRange();
    return other.asMultiRange().union(this);
  }

  /** Tries to reuse {@code this} or {@code other}, otherwise creates a range from the arguments. */
  private ApiRange createOrReuse(AndroidApiLevel intro, AndroidApiLevel removed, ApiRange other) {
    if (equals(intro, removed)) {
      return this;
    }
    if (other.equals(intro, removed)) {
      return other;
    }
    return new ApiRange(intro, removed);
  }
}

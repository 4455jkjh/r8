// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Objects;

/**
 * Represents a non-empty API range starting from {@code intro} (inclusive) up to {@code removed}
 * (exclusive).
 *
 * <p>{@code intro} is always non-null but {@code removed} can be {@code null} which means that the
 * end-point is infinite, i.e. that some entry has not been removed.
 */
public class ApiRange {

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

  public boolean isRemoved() {
    return removed != null;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ApiRange)) {
      return false;
    }
    ApiRange that = (ApiRange) obj;
    if (!intro.isEqualTo(that.intro)) {
      return false;
    }
    if (removed == null) {
      return that.removed == null;
    }
    if (that.removed == null) {
      return false;
    }
    return removed.isEqualTo(that.removed);
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
}

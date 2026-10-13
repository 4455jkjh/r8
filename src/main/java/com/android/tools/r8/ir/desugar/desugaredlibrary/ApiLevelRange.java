// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.UncheckedApiLevel;
import com.android.tools.r8.utils.internal.ObjectUtils;
import java.util.Objects;

public class ApiLevelRange {

  private final UncheckedApiLevel apiLevelBelowOrEqual;
  private final UncheckedApiLevel apiLevelGreaterOrEqual;

  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public ApiLevelRange(int apiLevelBelowOrEqual) {
    this(new UncheckedApiLevel(apiLevelBelowOrEqual), null);
  }

  public ApiLevelRange(UncheckedApiLevel apiLevelBelowOrEqual) {
    this(apiLevelBelowOrEqual, null);
  }

  public ApiLevelRange(
      UncheckedApiLevel apiLevelBelowOrEqual, UncheckedApiLevel apiLevelGreaterOrEqual) {
    assert apiLevelGreaterOrEqual == null
        || apiLevelBelowOrEqual.isGreaterThanOrEqualTo(apiLevelGreaterOrEqual);
    this.apiLevelBelowOrEqual = apiLevelBelowOrEqual;
    this.apiLevelGreaterOrEqual = apiLevelGreaterOrEqual;
  }

  public ApiLevelRange(
      AndroidApiLevel apiLevelBelowOrEqual, AndroidApiLevel apiLevelGreaterOrEqual) {
    this(
        apiLevelBelowOrEqual.asUnchecked(),
        apiLevelGreaterOrEqual == null ? null : apiLevelGreaterOrEqual.asUnchecked());
  }

  public UncheckedApiLevel getApiLevelBelowOrEqual() {
    return apiLevelBelowOrEqual;
  }

  public UncheckedApiLevel getApiLevelGreaterOrEqual() {
    return apiLevelGreaterOrEqual;
  }

  public boolean hasApiLevelGreaterOrEqual() {
    return apiLevelGreaterOrEqual != null;
  }

  public boolean overlap(ApiLevelRange other) {
    UncheckedApiLevel start =
        apiLevelGreaterOrEqual == null ? AndroidApiLevel.B.asUnchecked() : apiLevelGreaterOrEqual;
    UncheckedApiLevel otherStart =
        other.apiLevelGreaterOrEqual == null
            ? AndroidApiLevel.B.asUnchecked()
            : other.apiLevelGreaterOrEqual;
    return start.isLessThanOrEqualTo(other.apiLevelBelowOrEqual)
        && otherStart.isLessThanOrEqualTo(apiLevelBelowOrEqual);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ApiLevelRange)) {
      return false;
    }
    ApiLevelRange that = (ApiLevelRange) o;
    return apiLevelBelowOrEqual.equals(that.apiLevelBelowOrEqual)
        && Objects.equals(apiLevelGreaterOrEqual, that.apiLevelGreaterOrEqual);
  }

  @Override
  public int hashCode() {
    return ObjectUtils.hashLL(apiLevelBelowOrEqual, apiLevelGreaterOrEqual);
  }

  public int deterministicOrder(ApiLevelRange other) {
    int compare = apiLevelBelowOrEqual.compareTo(other.apiLevelBelowOrEqual);
    if (compare != 0) {
      return compare;
    }
    if (apiLevelGreaterOrEqual == null) {
      if (other.apiLevelGreaterOrEqual == null) {
        return 0;
      }
      return 1;
    }
    if (other.apiLevelGreaterOrEqual == null) {
      return -1;
    }
    return apiLevelGreaterOrEqual.compareTo(other.apiLevelGreaterOrEqual);
  }

  @Override
  public String toString() {
    return "[ "
        + (apiLevelGreaterOrEqual == null ? "B" : apiLevelGreaterOrEqual)
        + " ; "
        + apiLevelBelowOrEqual
        + " ]";
  }
}

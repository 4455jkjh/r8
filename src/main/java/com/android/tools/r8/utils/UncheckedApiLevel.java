// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.utils.internal.ObjectUtils;
import com.android.tools.r8.utils.structural.Ordered;

public class UncheckedApiLevel implements Ordered<UncheckedApiLevel> {

  protected final int major;
  protected final int minor;

  public UncheckedApiLevel(int major, int minor) {
    if (major <= 0 || minor < 0) {
      throw new RuntimeException("Invalid API version: " + toString(major, minor));
    }
    this.major = major;
    this.minor = minor;
  }

  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public UncheckedApiLevel(int major) {
    // TODO(b/356841164): Remove all uses of this.
    this(major, 0);
  }

  public int getMajor() {
    return major;
  }

  public int getMinor() {
    return minor;
  }

  public static String toString(int major, int minor) {
    return major + "." + minor;
  }

  @Override
  public String toString() {
    return toString(major, minor);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (!(obj instanceof UncheckedApiLevel)) {
      return false;
    } else {
      var other = (UncheckedApiLevel) obj;
      return major == other.major && minor == other.minor;
    }
  }

  @Override
  public int hashCode() {
    return ObjectUtils.hashII(major, minor);
  }

  @Override
  public int compareTo(UncheckedApiLevel other) {
    if (major == other.major) {
      return Integer.compare(minor, other.minor);
    } else {
      return Integer.compare(major, other.major);
    }
  }
}

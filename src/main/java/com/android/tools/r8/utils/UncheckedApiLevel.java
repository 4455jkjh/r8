// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import com.android.tools.r8.utils.internal.ObjectUtils;
import com.android.tools.r8.utils.internal.ThrowingConsumer;
import com.android.tools.r8.utils.internal.structural.Ordered;

public class UncheckedApiLevel implements Ordered<UncheckedApiLevel> {

  protected final int major;
  protected final int minor;

  public UncheckedApiLevel(int major, int minor) {
    if (major <= 0 || minor < 0) {
      throw new IllegalArgumentException("Invalid API version: " + toString(major, minor));
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

  public static UncheckedApiLevel parse(String apiLevel) {
    return parse(
        apiLevel,
        message -> {
          throw new IllegalArgumentException(message);
        });
  }

  public static <E extends Throwable> UncheckedApiLevel parse(
      String apiLevel, ThrowingConsumer<String, E> onError) throws E {
    if (!apiLevel.matches("\\d+(\\.\\d+)?")) {
      onError.accept(
          "Invalid API version '" + apiLevel + "': must be of the form '<int>' or '<int>.<int>'");
      return null;
    }
    int dotPosition = apiLevel.indexOf('.');
    Integer major;
    Integer minor;
    if (dotPosition == -1) {
      major = parseInt(apiLevel, onError);
      if (major == null) {
        return null;
      }
      minor = 0;
    } else {
      major = parseInt(apiLevel.substring(0, dotPosition), onError);
      if (major == null) {
        return null;
      }
      minor = parseInt(apiLevel.substring(dotPosition + 1), onError);
      if (minor == null) {
        return null;
      }
    }
    if (major <= 0) {
      onError.accept("Invalid API version '" + apiLevel + "': major version must be positive");
      return null;
    }
    if (minor < 0) {
      onError.accept("Invalid API version '" + apiLevel + "': minor version must be non-negative");
      return null;
    }
    return new UncheckedApiLevel(major, minor);
  }

  private static <E extends Throwable> Integer parseInt(
      String value, ThrowingConsumer<String, E> onError) throws E {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      onError.accept("Invalid API version '" + value + "': cannot be parsed as an integer");
      return null;
    }
  }

  public int getMajor() {
    return major;
  }

  public int getMajorCeiling() {
    return minor != 0 ? major + 1 : major;
  }

  public int getMinor() {
    return minor;
  }

  public static String toString(int major, int minor) {
    if (minor == 0) {
      return Integer.toString(major);
    } else {
      return major + "." + minor;
    }
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

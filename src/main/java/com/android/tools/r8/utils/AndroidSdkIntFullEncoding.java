// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

/**
 * From <a href="https://developer.android.com/reference/android/os/Build.VERSION_CODES_FULL">
 * Build.VERSION_CODES_FULL</a>:
 *
 * <blockquote>
 *
 * [..] The actual values should be considered an implementation detail, and the current encoding
 * scheme may change in the future.
 *
 * </blockquote>
 */
public class AndroidSdkIntFullEncoding {

  public static int encode(int major, int minor) {
    return major * 100_000 + minor;
  }

  public static int encode(AndroidApiLevel apiLevel) {
    return encode(apiLevel.getMajor(), apiLevel.getMinor());
  }

  public static int encode(UncheckedApiLevel apiLevel) {
    return encode(apiLevel.getMajor(), apiLevel.getMinor());
  }
}

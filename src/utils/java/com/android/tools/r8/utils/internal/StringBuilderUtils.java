// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.internal;

public class StringBuilderUtils {

  /** Append lines with platform-dependent newline characters. */
  public static StringBuilder appendLines(StringBuilder builder, Iterable<String> lines) {
    lines.forEach(line -> appendLine(builder, line));
    return builder;
  }

  /** Append line with platform-dependent newline characters. */
  public static StringBuilder appendLine(StringBuilder builder, String line) {
    builder.append(line).append(System.lineSeparator());
    return builder;
  }
}

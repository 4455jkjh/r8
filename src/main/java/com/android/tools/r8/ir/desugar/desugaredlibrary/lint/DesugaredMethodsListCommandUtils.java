// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.lint;

import com.android.tools.r8.utils.UncheckedApiLevel;

public class DesugaredMethodsListCommandUtils {

  // Bridge to avoid public methods in @KeepForApi classes.
  public static void setMinApiLevel(
      DesugaredMethodsListCommand.Builder builder, UncheckedApiLevel minApiLevel) {
    builder.setMinApi(minApiLevel);
  }
}

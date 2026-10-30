// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.VersionProperties;

/** Version of the R8 Assistant tool. */
public final class R8AssistantVersion {

  // This field is accessed from release scripts using simple pattern matching.
  // Therefore, changing this field could break our release scripts.
  public static final String LABEL = "1.0.0-alpha01";

  private R8AssistantVersion() {}

  /** Returns current R8 Assistant version (with additional info) as a string. */
  public static String getVersionString() {
    return LABEL + " (" + VersionProperties.INSTANCE.getDescription() + ")";
  }
}

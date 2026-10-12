// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.UncheckedApiLevel;

@KeepForApi
public class UnsupportedAndroidApiLevelDiagnostic implements Diagnostic {

  private final UncheckedApiLevel apiLevel;

  public UnsupportedAndroidApiLevelDiagnostic(int apiLevelMajor, int apiLevelMinor) {
    apiLevel = new UncheckedApiLevel(apiLevelMajor, apiLevelMinor);
  }

  @Override
  public Origin getOrigin() {
    return Origin.unknown();
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return "An API level of "
        + apiLevel
        + " is not supported by this compiler. Please use an API level of "
        + AndroidApiLevel.LATEST.getNumericString()
        + " or earlier)";
  }
}

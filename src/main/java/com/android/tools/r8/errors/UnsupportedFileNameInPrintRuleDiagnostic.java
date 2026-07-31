// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@KeepForApi
public class UnsupportedFileNameInPrintRuleDiagnostic implements Diagnostic {

  private final Origin origin;
  private final Position position;

  UnsupportedFileNameInPrintRuleDiagnostic(Origin origin, Position position) {
    this.origin = origin;
    this.position = position;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Options with file names are not supported";
  }

  // To not include ProguardPrintRuleUnsupportedFileNameDiagnostic.<init> in the public API.
  public static class Factory {

    public static UnsupportedFileNameInPrintRuleDiagnostic create(
        Origin origin, Position position) {
      return new UnsupportedFileNameInPrintRuleDiagnostic(origin, position);
    }
  }
}

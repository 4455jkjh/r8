// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

/** Diagnostic message emitted when duplicate API entries are merged. */
@KeepForApi
public class DuplicateApiDatabaseEntryDiagnostic extends AndroidApiDiagnostic {

  private final String message;

  public DuplicateApiDatabaseEntryDiagnostic(String message) {
    this.message = message;
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
    return message;
  }
}

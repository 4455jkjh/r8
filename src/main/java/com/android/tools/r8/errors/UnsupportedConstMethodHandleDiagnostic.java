// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import static com.android.tools.r8.utils.InternalOptions.constantMethodHandleApiLevel;

import com.android.tools.r8.Keep;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;

@Keep
public class UnsupportedConstMethodHandleDiagnostic extends UnsupportedFeatureDiagnostic {

  // API: MUST NOT CHANGE!
  private static final String DESCRIPTOR = "const-method-handle";

  public UnsupportedConstMethodHandleDiagnostic(Origin origin, Position position) {
    super(DESCRIPTOR, constantMethodHandleApiLevel(), origin, position);
  }

  @Override
  public String getDiagnosticMessage() {
    return UnsupportedFeatureDiagnostic.makeMessage(
        constantMethodHandleApiLevel(), DESCRIPTOR, getPosition().toString());
  }
}

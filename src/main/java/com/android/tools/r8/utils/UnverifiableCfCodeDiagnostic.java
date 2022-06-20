// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.Keep;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.MethodReference;

@Keep
public class UnverifiableCfCodeDiagnostic implements Diagnostic {

  private final MethodReference methodReference;
  private final int instructionIndex;
  private final String message;
  private final Origin origin;

  public UnverifiableCfCodeDiagnostic(
      MethodReference methodReference, int instructionIndex, String message, Origin origin) {
    this.methodReference = methodReference;
    this.instructionIndex = instructionIndex;
    this.message = message;
    this.origin = origin;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return Position.UNKNOWN;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Unverifiable code in `"
        + MethodReferenceUtils.toSourceString(methodReference)
        + "` at instruction "
        + instructionIndex
        + ": "
        + message
        + ".";
  }
}

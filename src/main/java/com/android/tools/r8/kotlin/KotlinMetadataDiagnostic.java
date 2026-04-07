// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.StringUtils;

@KeepForApi
public class KotlinMetadataDiagnostic implements Diagnostic {

  private static final String LINK_TO_PAGE =
      "https://developer.android.com/studio/build/kotlin-d8-r8-versions";

  private final Origin origin;
  private final String message;

  private KotlinMetadataDiagnostic(Origin origin, String message) {
    this.origin = origin;
    this.message = message;
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
    return message;
  }

  static KotlinMetadataDiagnostic missingCompanionObject(
      DexClass clazz, String companionObjectName) {
    return new KotlinMetadataDiagnostic(
        clazz.getOrigin(),
        "The companion object "
            + companionObjectName
            + " could not be found in class "
            + clazz.getTypeName());
  }

  static KotlinMetadataDiagnostic unknownClassifier(String classifier) {
    return new KotlinMetadataDiagnostic(
        Origin.unknown(),
        "The classifier " + classifier + " is unknown and cannot be parsed");
  }

  static KotlinMetadataDiagnostic unexpectedErrorWhenRewriting(DexClass clazz, Throwable t) {
    return new KotlinMetadataDiagnostic(
        clazz.getOrigin(),
        "Unexpected error during rewriting of Kotlin metadata for class '"
            + clazz.getTypeName()
            + "':"
            + StringUtils.LINE_SEPARATOR
            + StringUtils.stacktraceAsString(t));
  }

  static KotlinMetadataDiagnostic unknownMetadataVersion() {
    return new KotlinMetadataDiagnostic(
        Origin.unknown(),
        "An error occurred when parsing kotlin metadata. This normally happens when using a newer"
            + " version of kotlin than the kotlin version released when this version of R8 was"
            + " created. To find compatible kotlin versions, please see: "
            + LINK_TO_PAGE);
  }
}

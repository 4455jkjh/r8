// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class D8R8DexFileMetadataImpl implements D8R8DexFileMetadata {

  @Expose
  @SerializedName("checksum")
  private final String checksum;

  @Expose
  @SerializedName("sizeInBytes")
  private final int sizeInBytes;

  @Expose
  @SerializedName("startup")
  private final boolean startup;

  D8R8DexFileMetadataImpl(String checksum, int sizeInBytes, boolean startup) {
    this.checksum = checksum;
    this.sizeInBytes = sizeInBytes;
    this.startup = startup;
  }

  @Override
  public String getChecksum() {
    return checksum;
  }

  @Override
  public int getSizeInBytes() {
    return sizeInBytes;
  }

  @Override
  public boolean isStartup() {
    return startup;
  }
}

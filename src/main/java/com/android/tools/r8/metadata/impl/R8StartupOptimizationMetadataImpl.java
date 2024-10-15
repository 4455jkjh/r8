// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.R8StartupOptimizationMetadata;
import com.android.tools.r8.profile.startup.StartupOptions;
import com.android.tools.r8.utils.InternalOptions;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class R8StartupOptimizationMetadataImpl implements R8StartupOptimizationMetadata {

  @Expose
  @SerializedName("isDexLayoutOptimizationEnabled")
  private final boolean isDexLayoutOptimizationEnabled;

  @Expose
  @SerializedName("isProfileGuidedOptimizationEnabled")
  private final boolean isProfileGuidedOptimizationEnabled;

  private R8StartupOptimizationMetadataImpl(StartupOptions startupOptions) {
    this.isDexLayoutOptimizationEnabled = startupOptions.isStartupLayoutOptimizationEnabled();
    this.isProfileGuidedOptimizationEnabled =
        !startupOptions.isStartupBoundaryOptimizationsEnabled();
  }

  public static R8StartupOptimizationMetadataImpl create(InternalOptions options) {
    StartupOptions startupOptions = options.getStartupOptions();
    if (startupOptions.getStartupProfileProviders().isEmpty()) {
      return null;
    }
    return new R8StartupOptimizationMetadataImpl(startupOptions);
  }

  @Override
  public boolean isDexLayoutOptimizationEnabled() {
    return isDexLayoutOptimizationEnabled;
  }

  @Override
  public boolean isProfileGuidedOptimizationEnabled() {
    return isProfileGuidedOptimizationEnabled;
  }
}

// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.D8LibraryDesugaringOptions;
import com.android.tools.r8.utils.InternalOptions;
import com.google.gson.annotations.SerializedName;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class D8LibraryDesugaringOptionsImpl extends D8R8LibraryDesugaringOptionsImpl
    implements D8LibraryDesugaringOptions {

  private D8LibraryDesugaringOptionsImpl(InternalOptions options) {
    super(options);
  }

  public static D8LibraryDesugaringOptionsImpl create(InternalOptions options) {
    return !options.machineDesugaredLibrarySpecification.isEmpty()
        ? new D8LibraryDesugaringOptionsImpl(options)
        : null;
  }
}

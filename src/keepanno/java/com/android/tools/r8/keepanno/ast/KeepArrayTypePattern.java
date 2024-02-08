// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.ast;

import com.google.common.base.Strings;
import java.util.Objects;

public class KeepArrayTypePattern {

  private static final KeepArrayTypePattern ANY =
      new KeepArrayTypePattern(KeepTypePattern.any(), 1);

  public static KeepArrayTypePattern getAny() {
    return ANY;
  }

  private final KeepTypePattern baseType;
  private final int dimensions;

  public KeepArrayTypePattern(KeepTypePattern baseType, int dimensions) {
    assert baseType != null;
    assert dimensions > 0;
    this.baseType = baseType;
    this.dimensions = dimensions;
  }

  public boolean isAny() {
    return ANY.equals(this);
  }

  public KeepTypePattern getBaseType() {
    return baseType;
  }

  public int getDimensions() {
    return dimensions;
  }

  public String getDescriptor() {
    if (isAny()) {
      throw new KeepEdgeException("No descriptor exists for 'any' array");
    }
    return Strings.repeat("[", dimensions)
        + baseType.match(
            () -> {
              throw new KeepEdgeException("No descriptor exists for 'any primitive' array");
            },
            primitive -> primitive.getDescriptor(),
            array -> {
              throw new KeepEdgeException("Unexpected nested array");
            },
            clazz -> clazz.getExactDescriptor());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof KeepArrayTypePattern)) {
      return false;
    }
    KeepArrayTypePattern that = (KeepArrayTypePattern) o;
    return dimensions == that.dimensions && Objects.equals(baseType, that.baseType);
  }

  @Override
  public int hashCode() {
    return Objects.hash(baseType, dimensions);
  }

  @Override
  public String toString() {
    return baseType + Strings.repeat("[]", dimensions);
  }
}

// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.DexField;

// TODO(b/296030319): Change DexField to implement InFlow and use DexField in all places instead of
//  FieldValue to avoid wrappers? This would also remove the need for the FieldValueFactory.
public class FieldValue implements BaseInFlow {

  private final DexField field;

  public FieldValue(DexField field) {
    this.field = field;
  }

  public DexField getField() {
    return field;
  }

  @Override
  public boolean isFieldValue() {
    return true;
  }

  @Override
  public boolean isFieldValue(DexField field) {
    return this.field.isIdenticalTo(field);
  }

  @Override
  public FieldValue asFieldValue() {
    return this;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    FieldValue fieldValue = (FieldValue) obj;
    return field.isIdenticalTo(fieldValue.field);
  }

  @Override
  public int hashCode() {
    return field.hashCode();
  }
}

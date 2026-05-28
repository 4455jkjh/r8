// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.internal.ObjectUtils;

/**
 * Reference to a field without its type.
 *
 * <p>A field reference is always fully qualified with a qualified holder type.
 *
 * <p>This class is only for Java-world contexts where fields are unique per name.
 */
public class FieldTypelessReference {
  private final ClassReference holderClass;
  private final String fieldName;

  public FieldTypelessReference(ClassReference holderClass, String fieldName) {
    assert holderClass != null;
    assert fieldName != null;
    this.holderClass = holderClass;
    this.fieldName = fieldName;
  }

  public ClassReference getHolderClass() {
    return holderClass;
  }

  public String getFieldName() {
    return fieldName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof FieldTypelessReference)) {
      return false;
    }
    FieldTypelessReference other = (FieldTypelessReference) o;
    return holderClass.equals(other.holderClass) && fieldName.equals(other.fieldName);
  }

  @Override
  public int hashCode() {
    return ObjectUtils.hashLL(holderClass, fieldName);
  }

  @Override
  public String toString() {
    return getHolderClass() + getFieldName();
  }

  public String toSourceString() {
    return getHolderClass().getTypeName() + "." + getFieldName();
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;

public class ExtraConstantByteParameter extends ExtraConstantParameter {

  private final long value;

  public ExtraConstantByteParameter(long value) {
    this.value = value;
  }

  @Override
  public DexType getType(DexItemFactory dexItemFactory) {
    return dexItemFactory.byteType;
  }

  @Override
  public TypeElement getTypeElement(AppView<?> appView, DexType argType) {
    assert argType.isIdenticalTo(appView.dexItemFactory().byteType);
    return TypeElement.getInt();
  }

  @Override
  public SingleNumberValue getValue(AppView<?> appView) {
    return appView.abstractValueFactory().createSingleNumberValue(value, TypeElement.getInt());
  }

  @Override
  public boolean isUnused() {
    return false;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ExtraConstantByteParameter other = (ExtraConstantByteParameter) obj;
    return value == other.value;
  }

  @Override
  public int hashCode() {
    return Long.hashCode(value);
  }
}

// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;

public class ExtraUnusedIntParameter extends ExtraUnusedPrimitiveParameter {

  @Override
  public DexType getType(DexItemFactory dexItemFactory) {
    return dexItemFactory.intType;
  }

  @Override
  public TypeElement getTypeElement(AppView<?> appView, DexType argType) {
    return TypeElement.getInt();
  }
}

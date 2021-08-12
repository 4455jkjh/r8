// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.DexMethod;
import java.util.Objects;

public class MethodParameter {

  private final DexMethod method;
  private final int index;

  public MethodParameter(DexMethod method, int index) {
    this.method = method;
    this.index = index;
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    MethodParameter methodParameter = (MethodParameter) obj;
    return method == methodParameter.method && index == methodParameter.index;
  }

  @Override
  public int hashCode() {
    return Objects.hash(method, index);
  }
}

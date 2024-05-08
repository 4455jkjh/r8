// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Collections;
import java.util.Objects;

public class CastAbstractFunction implements AbstractFunction {

  private final BaseInFlow inFlow;
  private final DexType type;

  public CastAbstractFunction(BaseInFlow inFlow, DexType type) {
    this.inFlow = inFlow;
    this.type = type;
  }

  @Override
  public ValueState apply(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState predecessorState) {
    return predecessorState.asReferenceState().cast(appView, type);
  }

  @Override
  public boolean containsBaseInFlow(BaseInFlow inFlow) {
    return inFlow.equals(this.inFlow);
  }

  @Override
  public Iterable<BaseInFlow> getBaseInFlow() {
    return Collections.singleton(inFlow);
  }

  @Override
  public boolean isCastAbstractFunction() {
    return true;
  }

  @Override
  public CastAbstractFunction asCastAbstractFunction() {
    return this;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof CastAbstractFunction)) {
      return false;
    }
    CastAbstractFunction fn = (CastAbstractFunction) obj;
    return inFlow.equals(fn.inFlow) && type.isIdenticalTo(fn.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(inFlow, type);
  }

  @Override
  public String toString() {
    return "Cast(" + inFlow + ", " + type.getTypeName() + ")";
  }
}

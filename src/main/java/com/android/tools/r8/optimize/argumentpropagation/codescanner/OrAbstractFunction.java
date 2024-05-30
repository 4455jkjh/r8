// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.analysis.value.arithmetic.AbstractCalculator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.IterableUtils;
import java.util.Objects;

/**
 * Encodes the `x | const` abstract function. This is currently used as part of the modeling of
 * updateChangedFlags, since the updateChangedFlags function is invoked with `changedFlags | 1` as
 * an argument.
 */
public class OrAbstractFunction implements AbstractFunction {

  public final BaseInFlow inFlow;
  public final SingleNumberValue constant;

  public OrAbstractFunction(BaseInFlow inFlow, SingleNumberValue constant) {
    this.inFlow = inFlow;
    this.constant = constant;
  }

  @Override
  public ValueState apply(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState inState) {
    ConcretePrimitiveTypeValueState inPrimitiveState = inState.asPrimitiveState();
    AbstractValue result =
        AbstractCalculator.orIntegers(appView, inPrimitiveState.getAbstractValue(), constant);
    return ConcretePrimitiveTypeValueState.create(result, inPrimitiveState.copyInFlow());
  }

  @Override
  public boolean containsBaseInFlow(BaseInFlow otherInFlow) {
    if (inFlow.isAbstractFunction()) {
      return inFlow.asAbstractFunction().containsBaseInFlow(otherInFlow);
    }
    assert inFlow.isBaseInFlow();
    return inFlow.equals(otherInFlow);
  }

  @Override
  public Iterable<BaseInFlow> getBaseInFlow() {
    if (inFlow.isAbstractFunction()) {
      return inFlow.asAbstractFunction().getBaseInFlow();
    }
    return IterableUtils.singleton(inFlow);
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
    OrAbstractFunction fn = (OrAbstractFunction) obj;
    return inFlow.equals(fn.inFlow) && constant.equals(fn.constant);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), inFlow, constant);
  }
}
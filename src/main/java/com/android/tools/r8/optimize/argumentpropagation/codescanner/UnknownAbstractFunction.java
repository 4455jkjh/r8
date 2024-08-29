// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.function.Function;

public class UnknownAbstractFunction implements AbstractFunction {

  private static final UnknownAbstractFunction INSTANCE = new UnknownAbstractFunction();

  private UnknownAbstractFunction() {}

  static UnknownAbstractFunction get() {
    return INSTANCE;
  }

  @Override
  public ValueState apply(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState inState,
      DexType outStaticType) {
    return ValueState.unknown();
  }

  @Override
  public boolean verifyContainsBaseInFlow(BaseInFlow inFlow) {
    throw new Unreachable();
  }

  @Override
  public <TB, TC> TraversalContinuation<TB, TC> traverseBaseInFlow(
      Function<? super BaseInFlow, TraversalContinuation<TB, TC>> fn) {
    throw new Unreachable();
  }

  @Override
  public InFlowKind getKind() {
    return InFlowKind.ABSTRACT_FUNCTION_UNKNOWN;
  }

  @Override
  public int internalCompareToSameKind(InFlow inFlow, InFlowComparator comparator) {
    assert this == inFlow;
    return 0;
  }

  @Override
  public boolean isUnknownAbstractFunction() {
    return true;
  }
}

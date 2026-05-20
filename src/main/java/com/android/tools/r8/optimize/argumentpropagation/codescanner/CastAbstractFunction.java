// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.internal.ObjectUtils;
import com.android.tools.r8.utils.internal.TraversalContinuation;
import java.util.Objects;
import java.util.function.Function;

/** Refines an in-flow to a given static type and nullability. */
public class CastAbstractFunction implements AbstractFunction {

  private final BaseInFlow inFlow;

  // The static type that inFlow should be strengthened to. May be null.
  private final DexType type;

  // The nullability that inFlow should be strengthened to.
  // Should always be one of MaybeNull and NotNull.
  private final Nullability nullability;

  public CastAbstractFunction(BaseInFlow inFlow, DexType type, Nullability nullability) {
    this.inFlow = inFlow;
    this.type = type;
    this.nullability = nullability;
  }

  @Override
  public ValueState apply(
      AppView<AppInfoWithLiveness> appView,
      FlowGraphStateProvider flowGraphStateProvider,
      ConcreteValueState predecessorState,
      DexType outStaticType) {
    return predecessorState.asReferenceState().cast(appView, type, nullability);
  }

  @Override
  public boolean hasBaseInFlow() {
    return true;
  }

  @Override
  public <TB, TC> TraversalContinuation<TB, TC> traverseBaseInFlow(
      Function<? super BaseInFlow, TraversalContinuation<TB, TC>> fn) {
    return fn.apply(inFlow);
  }

  @Override
  public InFlowKind getKind() {
    return InFlowKind.ABSTRACT_FUNCTION_CAST;
  }

  @Override
  public int internalCompareToSameKind(InFlow other, InFlowComparator comparator) {
    CastAbstractFunction fn = other.asCastAbstractFunction();
    if (inFlow != fn.inFlow) {
      int result = inFlow.compareTo(fn.inFlow, comparator);
      assert result != 0;
      return result;
    }
    if (Objects.isNull(type) != Objects.isNull(fn.type)) {
      return type == null ? 1 : -1;
    }
    if (!DexType.identical(type, fn.type)) {
      int result = type.compareTo(fn.type);
      assert result != 0;
      return result;
    }
    return getNullabilityIdForCompareTo(nullability) - getNullabilityIdForCompareTo(fn.nullability);
  }

  private static int getNullabilityIdForCompareTo(Nullability nullability) {
    if (nullability.isBottom()) {
      return 0;
    }
    if (nullability.isMaybeNull()) {
      return 1;
    }
    if (nullability.isDefinitelyNull()) {
      return 2;
    }
    assert nullability.isDefinitelyNotNull();
    return 3;
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
    return inFlow.equals(fn.inFlow)
        && DexType.identical(type, fn.type)
        && nullability.equals(fn.nullability);
  }

  @Override
  public int hashCode() {
    return ObjectUtils.hashLLL(inFlow, type, nullability);
  }

  @Override
  public String toString() {
    return "Cast("
        + inFlow
        + ", "
        + (type != null ? type.getTypeName() : "null")
        + ", "
        + nullability
        + ")";
  }
}

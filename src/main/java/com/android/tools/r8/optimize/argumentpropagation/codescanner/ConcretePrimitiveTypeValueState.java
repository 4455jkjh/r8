// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.SetUtils;
import java.util.Collections;
import java.util.Set;

public class ConcretePrimitiveTypeValueState extends ConcreteValueState {

  private AbstractValue abstractValue;

  public ConcretePrimitiveTypeValueState(AbstractValue abstractValue) {
    this(abstractValue, Collections.emptySet());
  }

  public ConcretePrimitiveTypeValueState(AbstractValue abstractValue, Set<InFlow> inFlow) {
    super(inFlow);
    this.abstractValue = abstractValue;
    assert !isEffectivelyBottom() : "Must use BottomPrimitiveTypeParameterState instead";
    assert !isEffectivelyUnknown() : "Must use UnknownParameterState instead";
  }

  public static NonEmptyValueState create(AbstractValue abstractValue) {
    return abstractValue.isUnknown()
        ? ValueState.unknown()
        : new ConcretePrimitiveTypeValueState(abstractValue);
  }

  public ConcretePrimitiveTypeValueState(InFlow inFlow) {
    this(AbstractValue.bottom(), SetUtils.newHashSet(inFlow));
  }

  @Override
  public ValueState clearInFlow() {
    if (hasInFlow()) {
      if (abstractValue.isBottom()) {
        return bottomPrimitiveTypeParameter();
      }
      internalClearInFlow();
    }
    assert !isEffectivelyBottom();
    return this;
  }

  @Override
  public ValueState mutableCopy() {
    return new ConcretePrimitiveTypeValueState(abstractValue, copyInFlow());
  }

  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView, ProgramField field, AbstractValue abstractValue) {
    mutableJoinAbstractValue(appView, abstractValue, field.getType());
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    return this;
  }

  public NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      ConcretePrimitiveTypeValueState state,
      DexType staticType,
      Action onChangedAction) {
    assert staticType.isPrimitiveType();
    boolean abstractValueChanged =
        mutableJoinAbstractValue(appView, state.getAbstractValue(), staticType);
    if (isEffectivelyUnknown()) {
      return unknown();
    }
    boolean inFlowChanged = mutableJoinInFlow(state);
    if (widenInFlow(appView)) {
      return unknown();
    }
    if (abstractValueChanged || inFlowChanged) {
      onChangedAction.execute();
    }
    return this;
  }

  private boolean mutableJoinAbstractValue(
      AppView<AppInfoWithLiveness> appView, AbstractValue otherAbstractValue, DexType staticType) {
    AbstractValue oldAbstractValue = abstractValue;
    abstractValue =
        appView
            .getAbstractValueParameterJoiner()
            .join(abstractValue, otherAbstractValue, staticType);
    return !abstractValue.equals(oldAbstractValue);
  }

  public AbstractValue getAbstractValue() {
    return abstractValue;
  }

  @Override
  public AbstractValue getAbstractValue(AppView<AppInfoWithLiveness> appView) {
    return abstractValue;
  }

  @Override
  public ConcreteParameterStateKind getKind() {
    return ConcreteParameterStateKind.PRIMITIVE;
  }

  @Override
  public boolean isEffectivelyBottom() {
    return abstractValue.isBottom() && !hasInFlow();
  }

  @Override
  public boolean isEffectivelyUnknown() {
    return abstractValue.isUnknown();
  }

  @Override
  public boolean isPrimitiveState() {
    return true;
  }

  @Override
  public ConcretePrimitiveTypeValueState asPrimitiveState() {
    return this;
  }
}

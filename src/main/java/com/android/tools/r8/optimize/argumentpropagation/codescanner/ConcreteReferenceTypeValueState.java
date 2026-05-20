// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.NotNullDynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValueJoiner;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.internal.Action;
import java.util.Set;

public abstract class ConcreteReferenceTypeValueState extends ConcreteValueState {

  ConcreteReferenceTypeValueState(Set<InFlow> inFlow) {
    super(inFlow);
  }

  public abstract ValueState cast(
      AppView<AppInfoWithLiveness> appView, DexType castType, Nullability castNullability);

  protected static DynamicType cast(
      AppView<AppInfoWithLiveness> appView,
      DexType castType,
      Nullability castNullability,
      DynamicType dynamicType) {
    if (dynamicType.isBottom()) {
      return dynamicType;
    }
    Nullability nullability = dynamicType.getNullability();
    Nullability meetNullability = nullability.meet(castNullability);
    if (meetNullability.isBottom()) {
      return DynamicType.bottom();
    }
    if (castType == null) {
      return narrowNullability(castNullability, dynamicType);
    }
    if (dynamicType.isNotNullType() || dynamicType.isUnknown()) {
      return DynamicType.create(appView, castType.toTypeElement(appView, meetNullability));
    }
    assert dynamicType.isDynamicTypeWithUpperBound();
    DynamicTypeWithUpperBound dynamicTypeWithUpperBound = dynamicType.asDynamicTypeWithUpperBound();
    // If this is an upcast, then return the more precise type.
    TypeElement typeElement = castType.toTypeElement(appView, meetNullability);
    if (dynamicTypeWithUpperBound
        .getDynamicUpperBoundType()
        .lessThanOrEqualUpToNullability(typeElement, appView)) {
      return dynamicType.withNullability(meetNullability);
    }
    // Otherwise this is a downcast.
    if (dynamicType.hasDynamicLowerBoundType()) {
      // There are three cases:
      // (1) the cast type is between the upper and lower bound,
      // (2) the cast type is below the lower bound, or
      // (3) the cast type is unrelated to the bounds.
      // In (2) and (3) the cast always fails unless the in-dynamic type can be null.
      ClassTypeElement lowerBound = dynamicTypeWithUpperBound.getDynamicLowerBoundType();
      if (typeElement.lessThanOrEqualUpToNullability(
              dynamicTypeWithUpperBound.getDynamicUpperBoundType(), appView)
          && lowerBound.lessThanOrEqualUpToNullability(typeElement, appView)) {
        return DynamicType.create(appView, typeElement, lowerBound);
      } else {
        return meetNullability.isMaybeNull() ? DynamicType.definitelyNull() : DynamicType.bottom();
      }
    }
    return DynamicType.create(appView, typeElement);
  }

  private static DynamicType narrowNullability(
      Nullability castNullability, DynamicType dynamicType) {
    Nullability nullability = dynamicType.getNullability();
    // If the existing nullability is stronger than the cast-nullability, then just return the
    // dynamic type.
    if (nullability.lessThanOrEqual(castNullability)) {
      return dynamicType;
    }
    // Otherwise, the cast-nullability is stronger.
    assert castNullability.isDefinitelyNotNull();
    assert castNullability.strictlyLessThan(nullability);
    assert !dynamicType.isNotNullType() : "Cast-nullability should be < nullability";
    if (dynamicType.isUnknown()) {
      return NotNullDynamicType.get();
    }
    assert dynamicType.isDynamicTypeWithUpperBound();
    return dynamicType.withNullability(castNullability);
  }

  public abstract DynamicType getDynamicType();

  public abstract Nullability getNullability();

  @Override
  public boolean isReferenceState() {
    return true;
  }

  @Override
  public ConcreteReferenceTypeValueState asReferenceState() {
    return this;
  }

  public abstract NonEmptyValueState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      AbstractValueJoiner abstractValueJoiner,
      ConcreteReferenceTypeValueState inState,
      DexType inStaticType,
      DexType outStaticType,
      Action onChangedAction);
}

// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.utils.IteratorUtils;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class OptimizationFeedbackDelayed implements OptimizationFeedback {

  // Caching of updated optimization info and processed status.
  private final Map<DexEncodedField, MutableFieldOptimizationInfo> fieldOptimizationInfos =
      new IdentityHashMap<>();
  private final Map<DexEncodedMethod, UpdatableMethodOptimizationInfo> methodOptimizationInfos =
      new IdentityHashMap<>();
  private final Map<DexEncodedMethod, ConstraintWithTarget> processed = new IdentityHashMap<>();

  private synchronized MutableFieldOptimizationInfo getFieldOptimizationInfoForUpdating(
      DexEncodedField field) {
    MutableFieldOptimizationInfo info = fieldOptimizationInfos.get(field);
    if (info != null) {
      return info;
    }
    info = field.getOptimizationInfo().mutableCopy();
    fieldOptimizationInfos.put(field, info);
    return info;
  }

  private synchronized UpdatableMethodOptimizationInfo getMethodOptimizationInfoForUpdating(
      DexEncodedMethod method) {
    UpdatableMethodOptimizationInfo info = methodOptimizationInfos.get(method);
    if (info != null) {
      return info;
    }
    info = method.getOptimizationInfo().mutableCopy();
    methodOptimizationInfos.put(method, info);
    return info;
  }

  public void updateVisibleOptimizationInfo() {
    // Remove methods that have become obsolete. A method may become obsolete, for example, as a
    // result of the class staticizer, which aims to transform virtual methods on companion classes
    // into static methods on the enclosing class of the companion class.
    IteratorUtils.removeIf(
        methodOptimizationInfos.entrySet().iterator(), entry -> entry.getKey().isObsolete());
    IteratorUtils.removeIf(processed.entrySet().iterator(), entry -> entry.getKey().isObsolete());

    // Update field optimization info.
    fieldOptimizationInfos.forEach(DexEncodedField::setOptimizationInfo);
    fieldOptimizationInfos.clear();

    // Update method optimization info.
    methodOptimizationInfos.forEach(DexEncodedMethod::setOptimizationInfo);
    methodOptimizationInfos.clear();

    // Mark the processed methods as processed.
    processed.forEach(DexEncodedMethod::markProcessed);
    processed.clear();
  }

  // FIELD OPTIMIZATION INFO:

  @Override
  public void markFieldCannotBeKept(DexEncodedField field) {
    getFieldOptimizationInfoForUpdating(field).cannotBeKept();
  }

  @Override
  public void markFieldAsPropagated(DexEncodedField field) {
    getFieldOptimizationInfoForUpdating(field).markAsPropagated();
  }

  @Override
  public void markFieldHasDynamicType(DexEncodedField field, TypeLatticeElement type) {
    getFieldOptimizationInfoForUpdating(field).setDynamicType(type);
  }

  // METHOD OPTIMIZATION INFO:

  @Override
  public synchronized void markInlinedIntoSingleCallSite(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markInlinedIntoSingleCallSite();
  }

  @Override
  public void markMethodCannotBeKept(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).cannotBeKept();
  }

  @Override
  public synchronized void methodInitializesClassesOnNormalExit(
      DexEncodedMethod method, Set<DexType> initializedClasses) {
    getMethodOptimizationInfoForUpdating(method)
        .markInitializesClassesOnNormalExit(initializedClasses);
  }

  @Override
  public synchronized void methodReturnsArgument(DexEncodedMethod method, int argument) {
    getMethodOptimizationInfoForUpdating(method).markReturnsArgument(argument);
  }

  @Override
  public synchronized void methodReturnsConstantNumber(DexEncodedMethod method, long value) {
    getMethodOptimizationInfoForUpdating(method).markReturnsConstantNumber(value);
  }

  @Override
  public synchronized void methodReturnsConstantString(DexEncodedMethod method, DexString value) {
    getMethodOptimizationInfoForUpdating(method).markReturnsConstantString(value);
  }

  @Override
  public synchronized void methodReturnsObjectOfType(
      DexEncodedMethod method, AppView<?> appView, TypeLatticeElement type) {
    getMethodOptimizationInfoForUpdating(method).markReturnsObjectOfType(appView, type);
  }

  @Override
  public synchronized void methodNeverReturnsNull(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markNeverReturnsNull();
  }

  @Override
  public synchronized void methodNeverReturnsNormally(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markNeverReturnsNormally();
  }

  @Override
  public synchronized void methodMayNotHaveSideEffects(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markMayNotHaveSideEffects();
  }

  @Override
  public synchronized void methodReturnValueOnlyDependsOnArguments(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markReturnValueOnlyDependsOnArguments();
  }

  @Override
  public synchronized void markAsPropagated(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markAsPropagated();
  }

  @Override
  public synchronized void markProcessed(DexEncodedMethod method, ConstraintWithTarget state) {
    processed.put(method, state);
  }

  @Override
  public synchronized void markUseIdentifierNameString(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markUseIdentifierNameString();
  }

  @Override
  public synchronized void markCheckNullReceiverBeforeAnySideEffect(
      DexEncodedMethod method, boolean mark) {
    getMethodOptimizationInfoForUpdating(method).markCheckNullReceiverBeforeAnySideEffect(mark);
  }

  @Override
  public synchronized void markTriggerClassInitBeforeAnySideEffect(
      DexEncodedMethod method, boolean mark) {
    getMethodOptimizationInfoForUpdating(method).markTriggerClassInitBeforeAnySideEffect(mark);
  }

  @Override
  public synchronized void setClassInlinerEligibility(
      DexEncodedMethod method, ClassInlinerEligibility eligibility) {
    getMethodOptimizationInfoForUpdating(method).setClassInlinerEligibility(eligibility);
  }

  @Override
  public synchronized void setTrivialInitializer(DexEncodedMethod method, TrivialInitializer info) {
    getMethodOptimizationInfoForUpdating(method).setTrivialInitializer(info);
  }

  @Override
  public synchronized void setInitializerEnablingJavaAssertions(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).setInitializerEnablingJavaAssertions();
  }

  @Override
  public synchronized void setParameterUsages(
      DexEncodedMethod method, ParameterUsagesInfo parameterUsagesInfo) {
    getMethodOptimizationInfoForUpdating(method).setParameterUsages(parameterUsagesInfo);
  }

  @Override
  public synchronized void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts) {
    getMethodOptimizationInfoForUpdating(method).setNonNullParamOrThrow(facts);
  }

  @Override
  public synchronized void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts) {
    getMethodOptimizationInfoForUpdating(method).setNonNullParamOnNormalExits(facts);
  }

  @Override
  public synchronized void classInitializerMayBePostponed(DexEncodedMethod method) {
    getMethodOptimizationInfoForUpdating(method).markClassInitializerMayBePostponed();
  }
}

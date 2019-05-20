// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ParameterUsagesInfo;
import com.android.tools.r8.graph.UpdatableMethodOptimizationInfo;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.utils.IteratorUtils;
import java.util.BitSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class OptimizationFeedbackDelayed implements OptimizationFeedback {

  // Caching of updated optimization info and processed status.
  private final Map<DexEncodedMethod, UpdatableMethodOptimizationInfo> optimizationInfos =
      new IdentityHashMap<>();
  private final Map<DexEncodedMethod, ConstraintWithTarget> processed = new IdentityHashMap<>();

  private synchronized UpdatableMethodOptimizationInfo getOptimizationInfoForUpdating(
      DexEncodedMethod method) {
    UpdatableMethodOptimizationInfo info = optimizationInfos.get(method);
    if (info != null) {
      return info;
    }
    info = method.getOptimizationInfo().mutableCopy();
    optimizationInfos.put(method, info);
    return info;
  }

  @Override
  public synchronized void methodInitializesClassesOnNormalExit(
      DexEncodedMethod method, Set<DexType> initializedClasses) {
    getOptimizationInfoForUpdating(method).markInitializesClassesOnNormalExit(initializedClasses);
  }

  @Override
  public synchronized void methodReturnsArgument(DexEncodedMethod method, int argument) {
    getOptimizationInfoForUpdating(method).markReturnsArgument(argument);
  }

  @Override
  public synchronized void methodReturnsConstantNumber(DexEncodedMethod method, long value) {
    getOptimizationInfoForUpdating(method).markReturnsConstantNumber(value);
  }

  @Override
  public synchronized void methodReturnsConstantString(DexEncodedMethod method, DexString value) {
    getOptimizationInfoForUpdating(method).markReturnsConstantString(value);
  }

  @Override
  public synchronized void methodReturnsObjectOfType(
      DexEncodedMethod method, TypeLatticeElement type) {
    getOptimizationInfoForUpdating(method).markReturnsObjectOfType(type);
  }

  @Override
  public synchronized void methodNeverReturnsNull(DexEncodedMethod method) {
    getOptimizationInfoForUpdating(method).markNeverReturnsNull();
  }

  @Override
  public synchronized void methodNeverReturnsNormally(DexEncodedMethod method) {
    getOptimizationInfoForUpdating(method).markNeverReturnsNormally();
  }

  @Override
  public synchronized void methodMayNotHaveSideEffects(DexEncodedMethod method) {
    getOptimizationInfoForUpdating(method).markMayNotHaveSideEffects();
  }

  @Override
  public synchronized void markAsPropagated(DexEncodedMethod method) {
    getOptimizationInfoForUpdating(method).markAsPropagated();
  }

  @Override
  public synchronized void markProcessed(DexEncodedMethod method, ConstraintWithTarget state) {
    processed.put(method, state);
  }

  @Override
  public synchronized void markUseIdentifierNameString(DexEncodedMethod method) {
    getOptimizationInfoForUpdating(method).markUseIdentifierNameString();
  }

  @Override
  public synchronized void markCheckNullReceiverBeforeAnySideEffect(
      DexEncodedMethod method, boolean mark) {
    getOptimizationInfoForUpdating(method).markCheckNullReceiverBeforeAnySideEffect(mark);
  }

  @Override
  public synchronized void markTriggerClassInitBeforeAnySideEffect(
      DexEncodedMethod method, boolean mark) {
    getOptimizationInfoForUpdating(method).markTriggerClassInitBeforeAnySideEffect(mark);
  }

  @Override
  public synchronized void setClassInlinerEligibility(
      DexEncodedMethod method, ClassInlinerEligibility eligibility) {
    getOptimizationInfoForUpdating(method).setClassInlinerEligibility(eligibility);
  }

  @Override
  public synchronized void setTrivialInitializer(DexEncodedMethod method, TrivialInitializer info) {
    getOptimizationInfoForUpdating(method).setTrivialInitializer(info);
  }

  @Override
  public synchronized void setInitializerEnablingJavaAssertions(DexEncodedMethod method) {
    getOptimizationInfoForUpdating(method).setInitializerEnablingJavaAssertions();
  }

  @Override
  public synchronized void setParameterUsages(
      DexEncodedMethod method, ParameterUsagesInfo parameterUsagesInfo) {
    getOptimizationInfoForUpdating(method).setParameterUsages(parameterUsagesInfo);
  }

  @Override
  public synchronized void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts) {
    getOptimizationInfoForUpdating(method).setNonNullParamOrThrow(facts);
  }

  @Override
  public synchronized void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts) {
    getOptimizationInfoForUpdating(method).setNonNullParamOnNormalExits(facts);
  }

  public void updateVisibleOptimizationInfo() {
    // Remove methods that have become obsolete. A method may become obsolete, for example, as a
    // result of the class staticizer, which aims to transform virtual methods on companion classes
    // into static methods on the enclosing class of the companion class.
    IteratorUtils.removeIf(
        optimizationInfos.entrySet().iterator(), entry -> entry.getKey().isObsolete());
    IteratorUtils.removeIf(processed.entrySet().iterator(), entry -> entry.getKey().isObsolete());
    optimizationInfos.forEach(DexEncodedMethod::setOptimizationInfo);
    processed.forEach(DexEncodedMethod::markProcessed);
    optimizationInfos.clear();
    processed.clear();
  }
}

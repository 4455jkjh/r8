// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.classinliner.constraint.ClassInlinerMethodConstraint;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfoCollection;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.AppInfoWithLivenessModifier;
import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.internal.StringUtils;
import java.util.BitSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class OptimizationFeedbackDelayed extends OptimizationFeedback {

  // Caching of updated optimization info and processed status.
  private final AppInfoWithLivenessModifier appInfoWithLivenessModifier =
      AppInfoWithLiveness.modifier();
  private Map<DexEncodedField, MutableFieldOptimizationInfo> fieldOptimizationInfos =
      new ConcurrentHashMap<>();
  private Map<DexEncodedMethod, MutableMethodOptimizationInfo> methodOptimizationInfos =
      new ConcurrentHashMap<>();
  private Map<DexEncodedMethod, ConstraintWithTarget> processed = new ConcurrentHashMap<>();

  private MutableFieldOptimizationInfo getFieldOptimizationInfoForUpdating(DexEncodedField field) {
    return fieldOptimizationInfos.computeIfAbsent(
        field, k -> k.getOptimizationInfo().toMutableOptimizationInfo().mutableCopy());
  }

  private void updatePendingFieldOptimizationInfo(
      DexEncodedField field, Consumer<MutableFieldOptimizationInfo> consumer) {
    MutableFieldOptimizationInfo info = getFieldOptimizationInfoForUpdating(field);
    synchronized (info) {
      consumer.accept(info);
    }
  }

  private MutableMethodOptimizationInfo getMethodOptimizationInfoForUpdating(
      DexEncodedMethod method) {
    return methodOptimizationInfos.computeIfAbsent(
        method, k -> k.getOptimizationInfo().toMutableOptimizationInfo().mutableCopy());
  }

  public MutableMethodOptimizationInfo getMethodOptimizationInfoForUpdating(ProgramMethod method) {
    return getMethodOptimizationInfoForUpdating(method.getDefinition());
  }

  private void updatePendingMethodOptimizationInfo(
      DexEncodedMethod method, Consumer<MutableMethodOptimizationInfo> consumer) {
    MutableMethodOptimizationInfo info = getMethodOptimizationInfoForUpdating(method);
    synchronized (info) {
      consumer.accept(info);
    }
  }

  private void updatePendingMethodOptimizationInfo(
      ProgramMethod method, Consumer<MutableMethodOptimizationInfo> consumer) {
    updatePendingMethodOptimizationInfo(method.getDefinition(), consumer);
  }

  public boolean hasPendingOptimizationInfo(ProgramMethod method) {
    return methodOptimizationInfos.containsKey(method.getDefinition());
  }

  @Override
  public void fixupOptimizationInfos(
      AppView<?> appView,
      ThreadingModule threadingModule,
      ExecutorService executorService,
      OptimizationInfoFixer fixer)
      throws ExecutionException {
    updateVisibleOptimizationInfo();
    super.fixupOptimizationInfos(appView, threadingModule, executorService, fixer);
  }

  @Override
  public void modifyAppInfoWithLiveness(Consumer<AppInfoWithLivenessModifier> consumer) {
    consumer.accept(appInfoWithLivenessModifier);
  }

  public void refineAppInfoWithLiveness(AppInfoWithLiveness appInfo) {
    appInfoWithLivenessModifier.modify(appInfo);
  }

  public void updateVisibleOptimizationInfo() {
    // Update field optimization info.
    if (!fieldOptimizationInfos.isEmpty()) {
      fieldOptimizationInfos.forEach(DexEncodedField::setOptimizationInfo);
      if (fieldOptimizationInfos.size() > 64) {
        // Create a new map to ensure that small waves do not unnecessarily scan large empty hash
        // tables.
        fieldOptimizationInfos = new ConcurrentHashMap<>();
      } else {
        fieldOptimizationInfos.clear();
      }
    }

    // Update method optimization info, skipping methods that have become obsolete. A method may
    // become obsolete, for example, as a result of the class staticizer, which aims to transform
    // virtual methods on companion classes into static methods on the enclosing class of the
    // companion class.
    if (!methodOptimizationInfos.isEmpty()) {
      methodOptimizationInfos.forEach(
          (method, info) -> {
            if (!method.isObsolete()) {
              method.setOptimizationInfo(info);
            }
          });
      if (methodOptimizationInfos.size() > 64) {
        // Create a new map to ensure that small waves do not unnecessarily scan large empty hash
        // tables.
        methodOptimizationInfos = new ConcurrentHashMap<>();
      } else {
        methodOptimizationInfos.clear();
      }
    }

    // Mark the processed methods as processed.
    if (!processed.isEmpty()) {
      processed.forEach(
          (method, state) -> {
            if (!method.isObsolete()) {
              method.markProcessed(state);
            }
          });
      if (processed.size() > 64) {
        // Create a new map to ensure that small waves do not unnecessarily scan large empty hash
        // tables.
        processed = new ConcurrentHashMap<>();
      } else {
        processed.clear();
      }
    }
  }

  public boolean noUpdatesLeft() {
    assert appInfoWithLivenessModifier.isEmpty();
    assert fieldOptimizationInfos.isEmpty()
        : StringUtils.join(", ", fieldOptimizationInfos.keySet());
    assert methodOptimizationInfos.isEmpty()
        : StringUtils.join(", ", methodOptimizationInfos.keySet());
    assert processed.isEmpty() : StringUtils.join(", ", processed.keySet());
    return true;
  }

  // FIELD OPTIMIZATION INFO:

  @Override
  public void markFieldCannotBeKept(DexEncodedField field) {
    updatePendingFieldOptimizationInfo(field, MutableFieldOptimizationInfo::cannotBeKept);
  }

  @Override
  public void markFieldAsDead(DexEncodedField field) {
    updatePendingFieldOptimizationInfo(field, MutableFieldOptimizationInfo::markAsDead);
  }

  @Override
  public void markFieldAsPropagated(DexEncodedField field) {
    updatePendingFieldOptimizationInfo(field, MutableFieldOptimizationInfo::markAsPropagated);
  }

  @Override
  public void markFieldHasDynamicType(DexEncodedField field, DynamicType dynamicType) {
    updatePendingFieldOptimizationInfo(field, info -> info.setDynamicType(dynamicType));
  }

  @Override
  public void markFieldBitsRead(DexEncodedField field, int bitsRead) {
    updatePendingFieldOptimizationInfo(field, info -> info.joinReadBits(bitsRead));
  }

  @Override
  public void recordFieldHasAbstractValue(
      DexEncodedField field, AppView<AppInfoWithLiveness> appView, AbstractValue abstractValue) {
    assert appView.appInfo().getFieldAccessInfoCollection().contains(field.getReference());
    assert !appView
        .appInfo()
        .getFieldAccessInfoCollection()
        .get(field.getReference())
        .hasReflectiveAccess();
    assert verifyValuePropagationIsAllowed(field, appView);
    updatePendingFieldOptimizationInfo(field, info -> info.setAbstractValue(abstractValue, field));
  }

  // METHOD OPTIMIZATION INFO:

  @Override
  public void markInlinedIntoSingleCallSite(DexEncodedMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::markInlinedIntoSingleCallSite);
  }

  @Override
  public void markMethodCannotBeKept(DexEncodedMethod method) {
    updatePendingMethodOptimizationInfo(method, MutableMethodOptimizationInfo::cannotBeKept);
  }

  @Override
  public void methodInitializesClassesOnNormalExit(
      DexEncodedMethod method, Set<DexType> initializedClasses) {
    updatePendingMethodOptimizationInfo(
        method, info -> info.markInitializesClassesOnNormalExit(initializedClasses));
  }

  @Override
  public void methodReturnsArgument(DexEncodedMethod method, int argument) {
    updatePendingMethodOptimizationInfo(method, info -> info.markReturnsArgument(argument));
  }

  @Override
  public void methodReturnsAbstractValue(
      DexEncodedMethod definition, AppView<AppInfoWithLiveness> appView, AbstractValue value) {
    assert verifyValuePropagationIsAllowed(definition, appView);
    updatePendingMethodOptimizationInfo(
        definition, info -> info.setAbstractReturnValue(value, definition));
  }

  @Override
  public void setAbstractFunction(DexEncodedMethod method, AbstractFunction abstractFunction) {
    updatePendingMethodOptimizationInfo(method, info -> info.setAbstractFunction(abstractFunction));
  }

  @Override
  public void setDynamicReturnType(
      DexEncodedMethod method, AppView<?> appView, DynamicType dynamicType) {
    updatePendingMethodOptimizationInfo(
        method, info -> info.setDynamicType(appView, dynamicType, method));
  }

  @Override
  public void methodNeverReturnsNormally(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::markNeverReturnsNormally);
  }

  @Override
  public void methodMayNotHaveSideEffects(DexEncodedMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::markMayNotHaveSideEffects);
  }

  @Override
  public void methodReturnValueOnlyDependsOnArguments(DexEncodedMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::markReturnValueOnlyDependsOnArguments);
  }

  @Override
  public void markAsPropagated(DexEncodedMethod method) {
    updatePendingMethodOptimizationInfo(method, MutableMethodOptimizationInfo::markAsPropagated);
  }

  @Override
  public void markProcessed(DexEncodedMethod method, ConstraintWithTarget state) {
    processed.put(method, state);
  }

  @Override
  public void setBridgeInfo(ProgramMethod method, BridgeInfo bridgeInfo) {
    updatePendingMethodOptimizationInfo(method, info -> info.setBridgeInfo(bridgeInfo));
  }

  @Override
  public void setClassInlinerMethodConstraint(
      ProgramMethod method, ClassInlinerMethodConstraint classInlinerConstraint) {
    updatePendingMethodOptimizationInfo(
        method, info -> info.setClassInlinerMethodConstraint(classInlinerConstraint));
  }

  @Override
  public void setEnumUnboxerMethodClassification(
      ProgramMethod method, EnumUnboxerMethodClassification enumUnboxerMethodClassification) {
    updatePendingMethodOptimizationInfo(
        method, info -> info.setEnumUnboxerMethodClassification(enumUnboxerMethodClassification));
  }

  @Override
  public void setInstanceInitializerInfoCollection(
      DexEncodedMethod method,
      InstanceInitializerInfoCollection instanceInitializerInfoCollection) {
    updatePendingMethodOptimizationInfo(
        method,
        info -> info.setInstanceInitializerInfoCollection(instanceInitializerInfoCollection));
  }

  @Override
  public void setInitializerEnablingJavaVmAssertions(DexEncodedMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::setInitializerEnablingJavaAssertions);
  }

  @Override
  public void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts) {
    updatePendingMethodOptimizationInfo(method, info -> info.setNonNullParamOrThrow(facts));
  }

  @Override
  public void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts) {
    updatePendingMethodOptimizationInfo(method, info -> info.setNonNullParamOnNormalExits(facts));
  }

  @Override
  public void setNopInliningConstraint(ProgramMethod method, SimpleInliningConstraint constraint) {
    updatePendingMethodOptimizationInfo(method, info -> info.setNopInliningConstraint(constraint));
  }

  @Override
  public void setSimpleInliningConstraint(
      ProgramMethod method, SimpleInliningConstraint constraint) {
    updatePendingMethodOptimizationInfo(
        method, info -> info.setSimpleInliningConstraint(constraint));
  }

  @Override
  public void classInitializerMayBePostponed(DexEncodedMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::markClassInitializerMayBePostponed);
  }

  @Override
  public void setParametersWithBitwiseOperations(
      ProgramMethod method, BitSet parametersWithBitwiseOperations) {
    updatePendingMethodOptimizationInfo(
        method, info -> info.setParametersWithBitwiseOperations(parametersWithBitwiseOperations));
  }

  @Override
  public void setUnusedArguments(ProgramMethod method, BitSet unusedArguments) {
    updatePendingMethodOptimizationInfo(method, info -> info.setUnusedArguments(unusedArguments));
  }

  // Unset methods.

  @Override
  public void unsetAbstractReturnValue(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetAbstractReturnValue);
  }

  @Override
  public void unsetBridgeInfo(DexEncodedMethod method) {
    updatePendingMethodOptimizationInfo(method, MutableMethodOptimizationInfo::unsetBridgeInfo);
  }

  @Override
  public void unsetClassInitializerMayBePostponed(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetClassInitializerMayBePostponed);
  }

  @Override
  public void unsetClassInlinerMethodConstraint(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetClassInlinerMethodConstraint);
  }

  @Override
  public void unsetDynamicReturnType(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(method, MutableMethodOptimizationInfo::unsetDynamicType);
  }

  @Override
  public void unsetEnumUnboxerMethodClassification(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetEnumUnboxerMethodClassification);
  }

  @Override
  public void unsetForceInline(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(method, MutableMethodOptimizationInfo::unsetForceInline);
  }

  @Override
  public void unsetInitializedClassesOnNormalExit(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetInitializedClassesOnNormalExit);
  }

  @Override
  public void unsetInitializerEnablingJavaVmAssertions(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetInitializerEnablingJavaVmAssertions);
  }

  @Override
  public void unsetInlinedIntoSingleCallSite(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetInlinedIntoSingleCallSite);
  }

  @Override
  public void unsetInstanceInitializerInfoCollection(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetInstanceInitializerInfoCollection);
  }

  @Override
  public void unsetMayNotHaveSideEffects(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetMayNotHaveSideEffects);
  }

  @Override
  public void unsetNeverReturnsNormally(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetNeverReturnsNormally);
  }

  @Override
  public void unsetNonNullParamOnNormalExits(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetNonNullParamOnNormalExits);
  }

  @Override
  public void unsetNonNullParamOrThrow(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetNonNullParamOrThrow);
  }

  @Override
  public void unsetReturnedArgument(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetReturnedArgument);
  }

  @Override
  public void unsetReturnValueOnlyDependsOnArguments(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetReturnValueOnlyDependsOnArguments);
  }

  @Override
  public void unsetNopInliningConstraint(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetNopInliningConstraint);
  }

  @Override
  public void unsetSimpleInliningConstraint(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetSimpleInliningConstraint);
  }

  @Override
  public void unsetUnusedArguments(ProgramMethod method) {
    updatePendingMethodOptimizationInfo(
        method, MutableMethodOptimizationInfo::unsetUnusedArguments);
  }
}

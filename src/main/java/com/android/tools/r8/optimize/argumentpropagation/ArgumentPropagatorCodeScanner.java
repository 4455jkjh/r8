// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow.asBaseInFlowOrNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectStateAnalysis;
import com.android.tools.r8.ir.code.AbstractValueSupplier;
import com.android.tools.r8.ir.code.AliasedValueConfiguration;
import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.FieldGet;
import com.android.tools.r8.ir.code.FieldPut;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteArrayTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteClassTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodStateOrBottom;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodStateOrUnknown;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePolymorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePolymorphicMethodStateOrBottom;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteReceiverValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldStateCollection;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.FieldValueFactory;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.InstanceFieldReadAbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameterFactory;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.NonEmptyValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.StateCloner;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.UnknownMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria.ArgumentPropagatorReprocessingCriteriaCollection;
import com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria.MethodReprocessingCriteria;
import com.android.tools.r8.optimize.argumentpropagation.reprocessingcriteria.ParameterReprocessingCriteria;
import com.android.tools.r8.optimize.argumentpropagation.utils.WideningUtils;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Analyzes each {@link IRCode} during the primary optimization to collect information about the
 * arguments passed to method parameters.
 *
 * <p>State pruning is applied on-the-fly to avoid storing redundant information.
 */
// TODO(b/330130322): Consider extending the flow graph with method-return nodes.
public class ArgumentPropagatorCodeScanner {

  private static AliasedValueConfiguration aliasedValueConfiguration =
      AssumeAndCheckCastAliasedValueConfiguration.getInstance();

  private final AppView<AppInfoWithLiveness> appView;

  private final ArgumentPropagatorCodeScannerModeling modeling;

  private final FieldValueFactory fieldValueFactory = new FieldValueFactory();

  private final MethodParameterFactory methodParameterFactory = new MethodParameterFactory();

  private final Set<DexMethod> monomorphicVirtualMethods = Sets.newIdentityHashSet();

  private final ArgumentPropagatorReprocessingCriteriaCollection reprocessingCriteriaCollection;

  /**
   * Maps each non-private virtual method to the upper most method in the class hierarchy with the
   * same method signature. Virtual methods that do not override other virtual methods are mapped to
   * themselves.
   */
  private final Map<DexMethod, DexMethod> virtualRootMethods = new IdentityHashMap<>();

  /**
   * The abstract program state for this optimization. Intuitively maps each field to its abstract
   * value and dynamic type.
   */
  private final FieldStateCollection fieldStates = FieldStateCollection.createConcurrent();

  /**
   * The abstract program state for this optimization. Intuitively maps each parameter to its
   * abstract value and dynamic type.
   */
  private final MethodStateCollectionByReference methodStates =
      MethodStateCollectionByReference.createConcurrent();

  public ArgumentPropagatorCodeScanner(AppView<AppInfoWithLiveness> appView) {
    this(appView, new ArgumentPropagatorReprocessingCriteriaCollection(appView));
  }

  ArgumentPropagatorCodeScanner(
      AppView<AppInfoWithLiveness> appView,
      ArgumentPropagatorReprocessingCriteriaCollection reprocessingCriteriaCollection) {
    this.appView = appView;
    this.modeling = new ArgumentPropagatorCodeScannerModeling(appView);
    this.reprocessingCriteriaCollection = reprocessingCriteriaCollection;
  }

  public synchronized void addMonomorphicVirtualMethods(Set<DexMethod> extension) {
    monomorphicVirtualMethods.addAll(extension);
  }

  public synchronized void addVirtualRootMethods(Map<DexMethod, DexMethod> extension) {
    virtualRootMethods.putAll(extension);
  }

  public FieldStateCollection getFieldStates() {
    return fieldStates;
  }

  public MethodStateCollectionByReference getMethodStates() {
    return methodStates;
  }

  DexMethod getVirtualRootMethod(ProgramMethod method) {
    return virtualRootMethods.get(method.getReference());
  }

  // TODO(b/296030319): Allow lookups in the FieldStateCollection using DexField keys to avoid the
  //  need for definitionFor here.
  private boolean isFieldValueAlreadyUnknown(DexField field) {
    return isFieldValueAlreadyUnknown(appView.definitionFor(field).asProgramField());
  }

  private boolean isFieldValueAlreadyUnknown(ProgramField field) {
    return fieldStates.get(field).isUnknown();
  }

  protected boolean isMethodParameterAlreadyUnknown(
      MethodParameter methodParameter, ProgramMethod method) {
    assert methodParameter.getMethod().isIdenticalTo(method.getReference());
    MethodState methodState =
        methodStates.get(
            method.getDefinition().belongsToDirectPool() || isMonomorphicVirtualMethod(method)
                ? method.getReference()
                : getVirtualRootMethod(method));
    if (methodState.isPolymorphic()) {
      methodState = methodState.asPolymorphic().getMethodStateForBounds(DynamicType.unknown());
    }
    if (methodState.isMonomorphic()) {
      ValueState parameterState =
          methodState.asMonomorphic().getParameterState(methodParameter.getIndex());
      return parameterState.isUnknown();
    }
    assert methodState.isBottom() || methodState.isUnknown();
    return methodState.isUnknown();
  }

  boolean isMonomorphicVirtualMethod(ProgramMethod method) {
    boolean isMonomorphicVirtualMethod = isMonomorphicVirtualMethod(method.getReference());
    assert method.getDefinition().belongsToVirtualPool() || !isMonomorphicVirtualMethod;
    return isMonomorphicVirtualMethod;
  }

  boolean isMonomorphicVirtualMethod(DexMethod method) {
    return monomorphicVirtualMethods.contains(method);
  }

  public void scan(
      ProgramMethod method,
      IRCode code,
      AbstractValueSupplier abstractValueSupplier,
      Timing timing) {
    timing.begin("Argument propagation scanner");
    for (Instruction instruction : code.instructions()) {
      if (instruction.isFieldPut()) {
        scan(instruction.asFieldPut(), abstractValueSupplier, method, timing);
      } else if (instruction.isInvokeMethod()) {
        scan(instruction.asInvokeMethod(), abstractValueSupplier, method, timing);
      } else if (instruction.isInvokeCustom()) {
        scan(instruction.asInvokeCustom());
      }
    }
    timing.end();
  }

  private void scan(
      FieldPut fieldPut,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context,
      Timing timing) {
    ProgramField field = fieldPut.resolveField(appView, context).getProgramField();
    if (field == null) {
      // Nothing to propagate.
      return;
    }
    addTemporaryFieldState(fieldPut, field, abstractValueSupplier, context, timing);
  }

  private void addTemporaryFieldState(
      FieldPut fieldPut,
      ProgramField field,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context,
      Timing timing) {
    timing.begin("Add field state");
    fieldStates.addTemporaryFieldState(
        field,
        () -> computeFieldState(fieldPut, field, abstractValueSupplier, context, timing),
        timing,
        (existingFieldState, fieldStateToAdd) -> {
          NonEmptyValueState newFieldState =
              existingFieldState.mutableJoin(
                  appView,
                  fieldStateToAdd,
                  field.getType(),
                  StateCloner.getCloner(),
                  Action.empty());
          return narrowFieldState(field, newFieldState);
        });
    timing.end();
  }

  private NonEmptyValueState computeFieldState(
      FieldPut fieldPut,
      ProgramField resolvedField,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context,
      Timing timing) {
    timing.begin("Compute field state for field-put");
    NonEmptyValueState result =
        computeFieldState(fieldPut, resolvedField, abstractValueSupplier, context);
    timing.end();
    return result;
  }

  private NonEmptyValueState computeFieldState(
      FieldPut fieldPut,
      ProgramField field,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context) {
    NonEmptyValueState inFlowState = computeInFlowState(field.getType(), fieldPut.value(), context);
    if (inFlowState != null) {
      return inFlowState;
    }

    if (field.getType().isArrayType()) {
      Nullability nullability = fieldPut.value().getType().nullability();
      return ConcreteArrayTypeValueState.create(nullability);
    }

    AbstractValue abstractValue = abstractValueSupplier.getAbstractValue(fieldPut.value());
    if (abstractValue.isUnknown()) {
      abstractValue =
          getFallbackAbstractValueForField(
              field,
              () -> ObjectStateAnalysis.computeObjectState(fieldPut.value(), appView, context));
    }
    if (field.getType().isClassType()) {
      DynamicType dynamicType =
          WideningUtils.widenDynamicNonReceiverType(
              appView, fieldPut.value().getDynamicType(appView), field.getType());
      return ConcreteClassTypeValueState.create(abstractValue, dynamicType);
    } else {
      assert field.getType().isPrimitiveType();
      return ConcretePrimitiveTypeValueState.create(abstractValue);
    }
  }

  // If the value is an argument of the enclosing method or defined by a field-get, then clearly we
  // have no information about its abstract value (yet). Instead of treating this as having an
  // unknown runtime value, we instead record a flow constraint.
  private InFlow computeInFlow(Value value, ProgramMethod context) {
    Value valueRoot = value.getAliasedValue(aliasedValueConfiguration);
    if (valueRoot.isArgument()) {
      MethodParameter inParameter =
          methodParameterFactory.create(context, valueRoot.getDefinition().asArgument().getIndex());
      return widenBaseInFlow(inParameter, context);
    } else if (valueRoot.isDefinedByInstructionSatisfying(Instruction::isFieldGet)) {
      FieldGet fieldGet = valueRoot.getDefinition().asFieldGet();
      ProgramField field = fieldGet.resolveField(appView, context).getProgramField();
      if (field == null) {
        return null;
      }
      if (fieldGet.isInstanceGet()) {
        Value receiverValue = fieldGet.asInstanceGet().object();
        BaseInFlow receiverInFlow = asBaseInFlowOrNull(computeInFlow(receiverValue, context));
        if (receiverInFlow != null
            && receiverInFlow.equals(widenBaseInFlow(receiverInFlow, context))) {
          return new InstanceFieldReadAbstractFunction(receiverInFlow, field.getReference());
        }
      }
      return widenBaseInFlow(fieldValueFactory.create(field), context);
    }
    return null;
  }

  private InFlow widenBaseInFlow(BaseInFlow inFlow, ProgramMethod context) {
    if (inFlow.isFieldValue()) {
      if (isFieldValueAlreadyUnknown(inFlow.asFieldValue().getField())) {
        return AbstractFunction.unknown();
      }
    } else {
      assert inFlow.isMethodParameter();
      if (isMethodParameterAlreadyUnknown(inFlow.asMethodParameter(), context)) {
        return AbstractFunction.unknown();
      }
    }
    return inFlow;
  }

  private NonEmptyValueState computeInFlowState(
      DexType staticType, Value value, ProgramMethod context) {
    InFlow inFlow = computeInFlow(value, context);
    if (inFlow == null) {
      return null;
    }
    if (inFlow.isUnknownAbstractFunction()) {
      return ValueState.unknown();
    }
    assert inFlow.isBaseInFlow() || inFlow.isInstanceFieldReadAbstractFunction();
    return ConcreteValueState.create(staticType, inFlow);
  }

  // Strengthens the abstract value of static final fields to a (self-)SingleFieldValue when the
  // abstract value is unknown. The soundness of this is based on the fact that static final fields
  // will never have their value changed after the <clinit> finishes, so value in a static final
  // field can always be rematerialized by reading the field.
  private NonEmptyValueState narrowFieldState(ProgramField field, NonEmptyValueState fieldState) {
    AbstractValue fallbackAbstractValue =
        getFallbackAbstractValueForField(field, ObjectState::empty);
    if (!fallbackAbstractValue.isUnknown()) {
      AbstractValue abstractValue = fieldState.getAbstractValue(appView);
      if (!abstractValue.isUnknown()) {
        return fieldState;
      }
      if (field.getType().isArrayType()) {
        // We do not track an abstract value for array types.
        return fieldState;
      }
      if (field.getType().isClassType()) {
        DynamicType dynamicType =
            fieldState.isReferenceState()
                ? fieldState.asReferenceState().getDynamicType()
                : DynamicType.unknown();
        return new ConcreteClassTypeValueState(fallbackAbstractValue, dynamicType);
      } else {
        assert field.getType().isPrimitiveType();
        return new ConcretePrimitiveTypeValueState(fallbackAbstractValue);
      }
    }
    return fieldState;
  }

  // TODO(b/296030319): Also handle effectively final fields.
  private AbstractValue getFallbackAbstractValueForField(
      ProgramField field, Supplier<ObjectState> objectStateSupplier) {
    if (field.getAccessFlags().isFinal() && field.getAccessFlags().isStatic()) {
      return appView
          .abstractValueFactory()
          .createSingleFieldValue(field.getReference(), objectStateSupplier.get());
    }
    return AbstractValue.unknown();
  }

  private void scan(
      InvokeMethod invoke,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context,
      Timing timing) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.getHolderType().isArrayType()) {
      // Nothing to propagate; the targeted method is not a program method.
      return;
    }

    if (appView.options().testing.checkReceiverAlwaysNullInCallSiteOptimization
        && invoke.isInvokeMethodWithReceiver()
        && invoke.asInvokeMethodWithReceiver().getReceiver().isAlwaysNull(appView)) {
      // Nothing to propagate; the invoke instruction always fails.
      return;
    }

    SingleResolutionResult<?> resolutionResult =
        invoke.resolveMethod(appView, context).asSingleResolution();
    if (resolutionResult == null) {
      // Nothing to propagate; the invoke instruction fails.
      return;
    }

    if (!resolutionResult.getResolvedHolder().isProgramClass()) {
      // Nothing to propagate; this could dispatch to a program method, but we cannot optimize
      // methods that override non-program methods.
      return;
    }

    ProgramMethod resolvedMethod = resolutionResult.getResolvedProgramMethod();
    if (resolvedMethod.getDefinition().isLibraryMethodOverride().isPossiblyTrue()) {
      assert resolvedMethod.getDefinition().isLibraryMethodOverride().isTrue();
      // Nothing to propagate; we don't know anything about methods that can be called from outside
      // the program.
      return;
    }

    if (invoke.arguments().size() != resolvedMethod.getDefinition().getNumberOfArguments()
        || invoke.isInvokeStatic() != resolvedMethod.getAccessFlags().isStatic()) {
      // Nothing to propagate; the invoke instruction fails.
      return;
    }

    if (invoke.isInvokeInterface()) {
      if (!resolutionResult.getInitialResolutionHolder().isInterface()) {
        // Nothing to propagate; the invoke instruction fails.
        return;
      }
    }

    if (invoke.isInvokeSuper()) {
      // Use the super target instead of the resolved method to ensure that we propagate the
      // argument information to the targeted method.
      DexClassAndMethod target =
          resolutionResult.lookupInvokeSuperTarget(context.getHolder(), appView);
      if (target == null) {
        // Nothing to propagate; the invoke instruction fails.
        return;
      }
      if (!target.isProgramMethod()) {
        throw new Unreachable(
            "Expected super target of a non-library override to be a program method ("
                + "resolved program method: "
                + resolvedMethod
                + ", "
                + "super non-program method: "
                + target
                + ")");
      }
      resolvedMethod = target.asProgramMethod();
    }

    // Find the method where to store the information about the arguments from this invoke.
    // If the invoke may dispatch to more than one method, we intentionally do not compute all
    // possible dispatch targets and propagate the information to these methods (this is expensive).
    // Instead we record the information in one place and then later propagate the information to
    // all dispatch targets.
    addTemporaryMethodState(invoke, resolvedMethod, abstractValueSupplier, context, timing);
  }

  protected void addTemporaryMethodState(
      InvokeMethod invoke,
      ProgramMethod resolvedMethod,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context,
      Timing timing) {
    timing.begin("Add method state");
    methodStates.addTemporaryMethodState(
        appView,
        getRepresentative(invoke, resolvedMethod),
        existingMethodState ->
            computeMethodState(
                invoke,
                resolvedMethod,
                abstractValueSupplier,
                context,
                existingMethodState,
                timing),
        timing);
    timing.end();
  }

  private MethodState computeMethodState(
      InvokeMethod invoke,
      ProgramMethod resolvedMethod,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context,
      MethodState existingMethodState,
      Timing timing) {
    assert !existingMethodState.isUnknown();

    // If this invoke may target at most one method, then we compute a state that maps each
    // parameter to the abstract value and dynamic type provided by this call site. Otherwise, we
    // compute a polymorphic method state, which includes information about the receiver's dynamic
    // type bounds.
    timing.begin("Compute method state for invoke");
    MethodState result;
    if (shouldUsePolymorphicMethodState(invoke, resolvedMethod)) {
      assert existingMethodState.isBottom() || existingMethodState.isPolymorphic();
      result =
          computePolymorphicMethodState(
              invoke.asInvokeMethodWithReceiver(),
              resolvedMethod,
              abstractValueSupplier,
              context,
              existingMethodState.asPolymorphicOrBottom());
    } else {
      assert existingMethodState.isBottom() || existingMethodState.isMonomorphic();
      result =
          computeMonomorphicMethodState(
              invoke,
              resolvedMethod,
              invoke.lookupSingleProgramTarget(appView, context),
              abstractValueSupplier,
              context,
              existingMethodState.asMonomorphicOrBottom());
    }
    timing.end();
    return result;
  }

  // TODO(b/190154391): Add a strategy that widens the dynamic receiver type to allow easily
  //  experimenting with the performance/size trade-off between precise/imprecise handling of
  //  dynamic dispatch.
  private MethodState computePolymorphicMethodState(
      InvokeMethodWithReceiver invoke,
      ProgramMethod resolvedMethod,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context,
      ConcretePolymorphicMethodStateOrBottom existingMethodState) {
    DynamicTypeWithUpperBound dynamicReceiverType = invoke.getReceiver().getDynamicType(appView);
    // TODO(b/331587404): Investigate if we can replace the receiver by null before entering this
    //  pass, so that this special case is not needed.
    if (dynamicReceiverType.isNullType()) {
      assert appView.testing().allowNullDynamicTypeInCodeScanner : "b/250634405";
      // This can happen if we were unable to determine that the receiver is a phi value where null
      // information has not been propagated down. Ideally this case would never happen as it should
      // be possible to replace the receiver by the null constant in this case.
      //
      // Since the receiver is known to be null, no argument information should be propagated to the
      // callees, so we return bottom here.
      return MethodState.bottom();
    }

    ProgramMethod singleTarget = invoke.lookupSingleProgramTarget(appView, context);
    DynamicTypeWithUpperBound bounds =
        computeBoundsForPolymorphicMethodState(resolvedMethod, singleTarget, dynamicReceiverType);
    MethodState existingMethodStateForBounds =
        existingMethodState.isPolymorphic()
            ? existingMethodState.asPolymorphic().getMethodStateForBounds(bounds)
            : MethodState.bottom();

    if (existingMethodStateForBounds.isPolymorphic()) {
      assert false;
      return MethodState.unknown();
    }

    // If we already don't know anything about the parameters for the given type bounds, then don't
    // compute a method state.
    if (existingMethodStateForBounds.isUnknown()) {
      return MethodState.bottom();
    }

    ConcreteMonomorphicMethodStateOrUnknown methodStateForBounds =
        computeMonomorphicMethodState(
            invoke,
            resolvedMethod,
            singleTarget,
            abstractValueSupplier,
            context,
            existingMethodStateForBounds.asMonomorphicOrBottom(),
            dynamicReceiverType);
    return ConcretePolymorphicMethodState.create(bounds, methodStateForBounds);
  }

  private DynamicTypeWithUpperBound computeBoundsForPolymorphicMethodState(
      ProgramMethod resolvedMethod,
      ProgramMethod singleTarget,
      DynamicTypeWithUpperBound dynamicReceiverType) {
    DynamicTypeWithUpperBound bounds =
        singleTarget != null
            ? DynamicType.createExact(
                singleTarget.getHolderType().toTypeElement(appView).asClassType())
            : dynamicReceiverType.withNullability(Nullability.maybeNull());

    // We intentionally drop the nullability for the type bounds. This increases the number of
    // collisions in the polymorphic method states, which does not change the precision (since the
    // nullability does not have any impact on the possible dispatch targets) and is good for state
    // pruning.
    assert bounds.getDynamicUpperBoundType().nullability().isMaybeNull();

    // If the bounds are trivial (i.e., the upper bound is equal to the holder of the virtual root
    // method), then widen the type bounds to 'unknown'.
    DexMethod virtualRootMethod = getVirtualRootMethod(resolvedMethod);
    if (virtualRootMethod == null) {
      assert false : "Unexpected virtual method without root: " + resolvedMethod;
      return bounds;
    }

    DynamicType trivialBounds =
        DynamicType.create(
            appView, virtualRootMethod.getHolderType().toTypeElement(appView).asClassType());
    if (bounds.equals(trivialBounds)) {
      return DynamicType.unknown();
    }
    return bounds;
  }

  private ConcreteMonomorphicMethodStateOrUnknown computeMonomorphicMethodState(
      InvokeMethod invoke,
      ProgramMethod resolvedMethod,
      ProgramMethod singleTarget,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context,
      ConcreteMonomorphicMethodStateOrBottom existingMethodState) {
    return computeMonomorphicMethodState(
        invoke,
        resolvedMethod,
        singleTarget,
        abstractValueSupplier,
        context,
        existingMethodState,
        invoke.isInvokeMethodWithReceiver()
            ? invoke.getFirstArgument().getDynamicType(appView)
            : null);
  }

  @SuppressWarnings("UnusedVariable")
  private ConcreteMonomorphicMethodStateOrUnknown computeMonomorphicMethodState(
      InvokeMethod invoke,
      ProgramMethod resolvedMethod,
      ProgramMethod singleTarget,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context,
      ConcreteMonomorphicMethodStateOrBottom existingMethodState,
      DynamicType dynamicReceiverType) {
    List<ValueState> parameterStates = new ArrayList<>(invoke.arguments().size());

    MethodReprocessingCriteria methodReprocessingCriteria =
        singleTarget != null
            ? reprocessingCriteriaCollection.getReprocessingCriteria(singleTarget)
            : MethodReprocessingCriteria.alwaysReprocess();

    int argumentIndex = 0;
    if (invoke.isInvokeMethodWithReceiver()) {
      assert dynamicReceiverType != null;
      parameterStates.add(
          computeParameterStateForReceiver(
              resolvedMethod,
              dynamicReceiverType,
              existingMethodState,
              methodReprocessingCriteria.getParameterReprocessingCriteria(0)));
      argumentIndex++;
    }

    for (; argumentIndex < invoke.arguments().size(); argumentIndex++) {
      parameterStates.add(
          computeParameterStateForNonReceiver(
              invoke,
              singleTarget,
              argumentIndex,
              invoke.getArgument(argumentIndex),
              abstractValueSupplier,
              context,
              existingMethodState));
    }

    // We simulate that the return value is used for methods with void return type. This ensures
    // that we will widen the method state to unknown if/when all parameter states become unknown.
    boolean isReturnValueUsed = invoke.getReturnType().isVoidType() || invoke.hasUsedOutValue();
    return ConcreteMonomorphicMethodState.create(isReturnValueUsed, parameterStates);
  }

  // For receivers there is not much point in trying to track an abstract value. Therefore we only
  // track the dynamic type for receivers.
  // TODO(b/190154391): Consider validating the above hypothesis by using
  //  computeParameterStateForNonReceiver() for receivers.
  private ValueState computeParameterStateForReceiver(
      ProgramMethod resolvedMethod,
      DynamicType dynamicReceiverType,
      ConcreteMonomorphicMethodStateOrBottom existingMethodState,
      ParameterReprocessingCriteria parameterReprocessingCriteria) {
    // Don't compute a state for this parameter if the stored state is already unknown.
    if (existingMethodState.isMonomorphic()
        && existingMethodState.asMonomorphic().getParameterState(0).isUnknown()) {
      return ValueState.unknown();
    }

    // For receivers we only track the dynamic type. Therefore, if there is no need to track the
    // dynamic type of the receiver of the targeted method, then just return unknown.
    if (!parameterReprocessingCriteria.shouldReprocessDueToDynamicType()) {
      return ValueState.unknown();
    }

    DynamicType widenedDynamicReceiverType =
        WideningUtils.widenDynamicReceiverType(appView, resolvedMethod, dynamicReceiverType);
    return widenedDynamicReceiverType.isUnknown()
        ? ValueState.unknown()
        : new ConcreteReceiverValueState(dynamicReceiverType);
  }

  @SuppressWarnings("UnusedVariable")
  private ValueState computeParameterStateForNonReceiver(
      InvokeMethod invoke,
      ProgramMethod singleTarget,
      int argumentIndex,
      Value argument,
      AbstractValueSupplier abstractValueSupplier,
      ProgramMethod context,
      ConcreteMonomorphicMethodStateOrBottom existingMethodState) {
    ValueState modeledState =
        modeling.modelParameterStateForArgumentToFunction(
            invoke, singleTarget, argumentIndex, argument, context);
    if (modeledState != null) {
      return modeledState;
    }

    // Don't compute a state for this parameter if the stored state is already unknown.
    if (existingMethodState.isMonomorphic()
        && existingMethodState.asMonomorphic().getParameterState(argumentIndex).isUnknown()) {
      return ValueState.unknown();
    }

    DexType parameterType =
        invoke.getInvokedMethod().getArgumentType(argumentIndex, invoke.isInvokeStatic());

    // If the value is an argument of the enclosing method, then clearly we have no information
    // about its abstract value. Instead of treating this as having an unknown runtime value, we
    // instead record a flow constraint that specifies that all values that flow into the parameter
    // of this enclosing method also flows into the corresponding parameter of the methods
    // potentially called from this invoke instruction.
    NonEmptyValueState inFlowState = computeInFlowState(parameterType, argument, context);
    if (inFlowState != null) {
      return inFlowState;
    }

    // Only track the nullability for array types.
    if (parameterType.isArrayType()) {
      Nullability nullability = argument.getType().nullability();
      return ConcreteArrayTypeValueState.create(nullability);
    }

    AbstractValue abstractValue = abstractValueSupplier.getAbstractValue(argument);

    // For class types, we track both the abstract value and the dynamic type. If both are unknown,
    // then use UnknownParameterState.
    if (parameterType.isClassType()) {
      DynamicType dynamicType = argument.getDynamicType(appView);
      DynamicType widenedDynamicType =
          WideningUtils.widenDynamicNonReceiverType(appView, dynamicType, parameterType);
      return ConcreteClassTypeValueState.create(abstractValue, widenedDynamicType);
    } else {
      // For primitive types, we only track the abstract value, thus if the abstract value is
      // unknown,
      // we use UnknownParameterState.
      assert parameterType.isPrimitiveType();
      return ConcretePrimitiveTypeValueState.create(abstractValue);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  private DexMethod getRepresentative(InvokeMethod invoke, ProgramMethod resolvedMethod) {
    if (resolvedMethod.getDefinition().belongsToDirectPool()) {
      return resolvedMethod.getReference();
    }

    if (invoke.isInvokeInterface()) {
      assert !isMonomorphicVirtualMethod(resolvedMethod);
      return getVirtualRootMethod(resolvedMethod);
    }

    assert invoke.isInvokeSuper() || invoke.isInvokeVirtual();

    if (isMonomorphicVirtualMethod(resolvedMethod)) {
      return resolvedMethod.getReference();
    }

    DexMethod rootMethod = getVirtualRootMethod(resolvedMethod);
    assert rootMethod != null;
    assert !isMonomorphicVirtualMethod(resolvedMethod)
        || rootMethod == resolvedMethod.getReference();
    return rootMethod;
  }

  private boolean shouldUsePolymorphicMethodState(
      InvokeMethod invoke, ProgramMethod resolvedMethod) {
    return !resolvedMethod.getDefinition().belongsToDirectPool()
        && !isMonomorphicVirtualMethod(getRepresentative(invoke, resolvedMethod));
  }

  private void scan(InvokeCustom invoke) {
    // If the bootstrap method is program declared it will be called. The call is with runtime
    // provided arguments so ensure that the argument information is unknown.
    DexMethodHandle bootstrapMethod = invoke.getCallSite().bootstrapMethod;
    SingleResolutionResult<?> resolution =
        appView
            .appInfo()
            .resolveMethodLegacy(bootstrapMethod.asMethod(), bootstrapMethod.isInterface)
            .asSingleResolution();
    if (resolution != null && resolution.getResolvedHolder().isProgramClass()) {
      methodStates.set(resolution.getResolvedProgramMethod(), UnknownMethodState.get());
    }
  }
}

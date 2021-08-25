// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.AliasedValueConfiguration;
import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteArrayTypeParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteClassTypeParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodStateOrBottom;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteMonomorphicMethodStateOrUnknown;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePolymorphicMethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePolymorphicMethodStateOrBottom;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcretePrimitiveTypeParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteReceiverParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodStateCollectionByReference;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ParameterState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.UnknownMethodState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Analyzes each {@link IRCode} during the primary optimization to collect information about the
 * arguments passed to method parameters.
 *
 * <p>State pruning is applied on-the-fly to avoid storing redundant information.
 */
public class ArgumentPropagatorCodeScanner {

  private static AliasedValueConfiguration aliasedValueConfiguration =
      AssumeAndCheckCastAliasedValueConfiguration.getInstance();

  private final AppView<AppInfoWithLiveness> appView;

  /**
   * Maps each non-private virtual method to the upper most method in the class hierarchy with the
   * same method signature. Virtual methods that do not override other virtual methods are mapped to
   * themselves.
   */
  private final Map<DexMethod, DexMethod> virtualRootMethods = new IdentityHashMap<>();

  /**
   * The abstract program state for this optimization. Intuitively maps each parameter to its
   * abstract value and dynamic type.
   */
  private final MethodStateCollectionByReference methodStates =
      MethodStateCollectionByReference.createConcurrent();

  ArgumentPropagatorCodeScanner(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public synchronized void addVirtualRootMethods(Map<DexMethod, DexMethod> extension) {
    virtualRootMethods.putAll(extension);
  }

  MethodStateCollectionByReference getMethodStates() {
    return methodStates;
  }

  void scan(ProgramMethod method, IRCode code, Timing timing) {
    timing.begin("Argument propagation scanner");
    for (Invoke invoke : code.<Invoke>instructions(Instruction::isInvoke)) {
      if (invoke.isInvokeMethod()) {
        scan(invoke.asInvokeMethod(), method, timing);
      } else if (invoke.isInvokeCustom()) {
        scan(invoke.asInvokeCustom(), method);
      }
    }
    timing.end();
  }

  private void scan(InvokeMethod invoke, ProgramMethod context, Timing timing) {
    List<Value> arguments = invoke.arguments();
    if (arguments.isEmpty()) {
      // Nothing to propagate.
      return;
    }

    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.getHolderType().isArrayType()) {
      // Nothing to propagate; the targeted method is not a program method.
      return;
    }

    if (invoke.isInvokeMethodWithReceiver()
        && invoke.asInvokeMethodWithReceiver().getReceiver().isAlwaysNull(appView)) {
      // Nothing to propagate; the invoke instruction always fails.
      return;
    }

    SingleResolutionResult resolutionResult =
        appView.appInfo().unsafeResolveMethodDueToDexFormat(invokedMethod).asSingleResolution();
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

    if (arguments.size() != resolvedMethod.getDefinition().getNumberOfArguments()
        || invoke.isInvokeStatic() != resolvedMethod.getAccessFlags().isStatic()) {
      // Nothing to propagate; the invoke instruction fails.
      return;
    }

    if (invoke.isInvokeSuper()) {
      // Use the super target instead of the resolved method to ensure that we propagate the
      // argument information to the targeted method.
      DexClassAndMethod target =
          resolutionResult.lookupInvokeSuperTarget(context.getHolder(), appView.appInfo());
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
    DexMethod representativeMethodReference =
        getRepresentativeForPolymorphicInvokeOrElse(
            invoke, resolvedMethod, resolvedMethod.getReference());
    ProgramMethod finalResolvedMethod = resolvedMethod;
    timing.begin("Add method state");
    methodStates.addTemporaryMethodState(
        appView,
        representativeMethodReference,
        existingMethodState ->
            computeMethodState(invoke, finalResolvedMethod, context, existingMethodState, timing),
        timing);
    timing.end();
  }

  private MethodState computeMethodState(
      InvokeMethod invoke,
      ProgramMethod resolvedMethod,
      ProgramMethod context,
      MethodState existingMethodState,
      Timing timing) {
    assert !existingMethodState.isUnknown();

    // If this invoke may target at most one method, then we compute a state that maps each
    // parameter to the abstract value and dynamic type provided by this call site. Otherwise, we
    // compute a polymorphic method state, which includes information about the receiver's dynamic
    // type bounds.
    timing.begin("Compute method state for invoke");
    boolean isPolymorphicInvoke =
        getRepresentativeForPolymorphicInvokeOrElse(invoke, resolvedMethod, null) != null;
    MethodState result;
    if (isPolymorphicInvoke) {
      assert existingMethodState.isBottom() || existingMethodState.isPolymorphic();
      result =
          computePolymorphicMethodState(
              invoke.asInvokeMethodWithReceiver(),
              resolvedMethod,
              context,
              existingMethodState.asPolymorphicOrBottom());
    } else {
      assert existingMethodState.isBottom() || existingMethodState.isMonomorphic();
      result =
          computeMonomorphicMethodState(
              invoke, context, existingMethodState.asMonomorphicOrBottom());
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
      ProgramMethod context,
      ConcretePolymorphicMethodStateOrBottom existingMethodState) {
    DynamicType dynamicReceiverType = invoke.getReceiver().getDynamicType(appView);
    assert !dynamicReceiverType.getDynamicUpperBoundType().nullability().isDefinitelyNull();

    // TODO(b/190154391): Consider using an exact bound if we have a single target (these are cached
    //  and should therefore not be too expensive to compute). Doing so should have the same
    //  precision, but lead to less state propagation in the subsequent top-down class
    //  hierarchy traversals.
    DynamicType bounds =
        invoke.isInvokeSuper()
            ? DynamicType.createExact(
                resolvedMethod.getHolderType().toTypeElement(appView).asClassType())
            : dynamicReceiverType;

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
      return MethodState.unknown();
    }

    // TODO(b/190154391): If the receiver type is effectively unknown, and the computed monomorphic
    //  method state is also unknown (i.e., we have "unknown receiver type" -> "unknown method
    //  state"), then return the canonicalized UnknownMethodState instance instead.
    return new ConcretePolymorphicMethodState(
        bounds,
        computeMonomorphicMethodState(
            invoke,
            context,
            existingMethodStateForBounds.asMonomorphicOrBottom(),
            dynamicReceiverType));
  }

  private ConcreteMonomorphicMethodStateOrUnknown computeMonomorphicMethodState(
      InvokeMethod invoke,
      ProgramMethod context,
      ConcreteMonomorphicMethodStateOrBottom existingMethodState) {
    return computeMonomorphicMethodState(
        invoke,
        context,
        existingMethodState,
        invoke.isInvokeMethodWithReceiver()
            ? invoke.getFirstArgument().getDynamicType(appView)
            : null);
  }

  private ConcreteMonomorphicMethodStateOrUnknown computeMonomorphicMethodState(
      InvokeMethod invoke,
      ProgramMethod context,
      ConcreteMonomorphicMethodStateOrBottom existingMethodState,
      DynamicType dynamicReceiverType) {
    List<ParameterState> parameterStates = new ArrayList<>(invoke.arguments().size());

    int argumentIndex = 0;
    if (invoke.isInvokeMethodWithReceiver()) {
      assert dynamicReceiverType != null;
      parameterStates.add(
          computeParameterStateForReceiver(
              invoke.asInvokeMethodWithReceiver(), dynamicReceiverType, existingMethodState));
      argumentIndex++;
    }

    for (; argumentIndex < invoke.arguments().size(); argumentIndex++) {
      parameterStates.add(
          computeParameterStateForNonReceiver(
              invoke,
              argumentIndex,
              invoke.getArgument(argumentIndex),
              context,
              existingMethodState));
    }

    // If all parameter states are unknown, then return a canonicalized unknown method state that
    // has this property.
    if (Iterables.all(parameterStates, ParameterState::isUnknown)) {
      return MethodState.unknown();
    }

    return new ConcreteMonomorphicMethodState(parameterStates);
  }

  // For receivers there is not much point in trying to track an abstract value. Therefore we only
  // track the dynamic type for receivers.
  // TODO(b/190154391): Consider validating the above hypothesis by using
  //  computeParameterStateForNonReceiver() for receivers.
  private ParameterState computeParameterStateForReceiver(
      InvokeMethodWithReceiver invoke,
      DynamicType dynamicReceiverType,
      ConcreteMonomorphicMethodStateOrBottom existingMethodState) {
    // Don't compute a state for this parameter if the stored state is already unknown.
    if (existingMethodState.isMonomorphic()
        && existingMethodState.asMonomorphic().getParameterState(0).isUnknown()) {
      return ParameterState.unknown();
    }

    ClassTypeElement staticReceiverType =
        invoke
            .getInvokedMethod()
            .getHolderType()
            .toTypeElement(appView)
            .asClassType()
            .asMeetWithNotNull();
    return dynamicReceiverType.isTrivial(staticReceiverType)
        ? ParameterState.unknown()
        : new ConcreteReceiverParameterState(dynamicReceiverType);
  }

  private ParameterState computeParameterStateForNonReceiver(
      InvokeMethod invoke,
      int argumentIndex,
      Value argument,
      ProgramMethod context,
      ConcreteMonomorphicMethodStateOrBottom existingMethodState) {
    // Don't compute a state for this parameter if the stored state is already unknown.
    if (existingMethodState.isMonomorphic()
        && existingMethodState.asMonomorphic().getParameterState(argumentIndex).isUnknown()) {
      return ParameterState.unknown();
    }

    Value argumentRoot = argument.getAliasedValue(aliasedValueConfiguration);
    TypeElement parameterType =
        invoke
            .getInvokedMethod()
            .getArgumentType(argumentIndex, invoke.isInvokeStatic())
            .toTypeElement(appView);

    // If the value is an argument of the enclosing method, then clearly we have no information
    // about its abstract value. Instead of treating this as having an unknown runtime value, we
    // instead record a flow constraint that specifies that all values that flow into the parameter
    // of this enclosing method also flows into the corresponding parameter of the methods
    // potentially called from this invoke instruction.
    if (argumentRoot.isArgument()) {
      MethodParameter forwardedParameter =
          new MethodParameter(
              context.getReference(), argumentRoot.getDefinition().asArgument().getIndex());
      if (parameterType.isClassType()) {
        return new ConcreteClassTypeParameterState(forwardedParameter);
      } else if (parameterType.isArrayType()) {
        return new ConcreteArrayTypeParameterState(forwardedParameter);
      } else {
        assert parameterType.isPrimitiveType();
        return new ConcretePrimitiveTypeParameterState(forwardedParameter);
      }
    }

    // Only track the nullability for array types.
    if (parameterType.isArrayType()) {
      Nullability nullability = argument.getType().nullability();
      return nullability.isMaybeNull()
          ? ParameterState.unknown()
          : new ConcreteArrayTypeParameterState(nullability);
    }

    AbstractValue abstractValue = argument.getAbstractValue(appView, context);

    // For class types, we track both the abstract value and the dynamic type. If both are unknown,
    // then use UnknownParameterState.
    if (parameterType.isClassType()) {
      DynamicType dynamicType = argument.getDynamicType(appView);
      return abstractValue.isUnknown() && dynamicType.isTrivial(parameterType)
          ? ParameterState.unknown()
          : new ConcreteClassTypeParameterState(abstractValue, dynamicType);
    }

    // For primitive types, we only track the abstract value, thus if the abstract value is unknown,
    // we use UnknownParameterState.
    assert parameterType.isPrimitiveType();
    return abstractValue.isUnknown()
        ? ParameterState.unknown()
        : new ConcretePrimitiveTypeParameterState(abstractValue);
  }

  private DexMethod getRepresentativeForPolymorphicInvokeOrElse(
      InvokeMethod invoke, ProgramMethod resolvedMethod, DexMethod defaultValue) {
    DexMethod resolvedMethodReference = resolvedMethod.getReference();
    if (invoke.isInvokeInterface()) {
      if (resolvedMethod.getDefinition().belongsToVirtualPool()) {
        return resolvedMethodReference;
      }
      // An invoke-interface can also target private methods, in which case this is a monomorphic
      // invoke.
      return defaultValue;
    }
    if (invoke.isInvokeSuper()) {
      return resolvedMethodReference;
    }
    DexMethod rootMethod = virtualRootMethods.get(resolvedMethodReference);
    if (rootMethod != null) {
      assert invoke.isInvokeVirtual();
      return rootMethod;
    }
    return defaultValue;
  }

  private void scan(InvokeCustom invoke, ProgramMethod context) {
    // If the bootstrap method is program declared it will be called. The call is with runtime
    // provided arguments so ensure that the argument information is unknown.
    DexMethodHandle bootstrapMethod = invoke.getCallSite().bootstrapMethod;
    SingleResolutionResult resolution =
        appView
            .appInfo()
            .resolveMethod(bootstrapMethod.asMethod(), bootstrapMethod.isInterface)
            .asSingleResolution();
    if (resolution != null && resolution.getResolvedHolder().isProgramClass()) {
      methodStates.set(resolution.getResolvedProgramMethod(), UnknownMethodState.get());
    }
  }
}

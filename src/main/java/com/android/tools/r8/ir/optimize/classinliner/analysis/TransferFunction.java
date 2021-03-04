// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner.analysis;

import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.MONITOR;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractTransferFunction;
import com.android.tools.r8.ir.code.AliasedValueConfiguration;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.AssumeAndCheckCastAliasedValueConfiguration;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeInterface;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Monitor;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Set;

class TransferFunction implements AbstractTransferFunction<ParameterUsages> {

  private static final AliasedValueConfiguration aliasedValueConfiguration =
      AssumeAndCheckCastAliasedValueConfiguration.getInstance();

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final ProgramMethod method;

  // Caches the parent or forwarding constructor call (only used in constructors).
  private InvokeDirect constructorInvoke;

  // The arguments that are considered by the analysis. We don't consider primitive arguments since
  // they cannot be class inlined.
  private Set<Value> argumentsOfInterest = Sets.newIdentityHashSet();

  // Instructions that use one of the arguments. Instructions that don't use any of the arguments
  // do not have any impact on the ability to class inline the arguments, therefore they are
  // skipped.
  private Set<Instruction> instructionsOfInterest = Sets.newIdentityHashSet();

  TransferFunction(AppView<AppInfoWithLiveness> appView, ProgramMethod method) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.method = method;
  }

  @Override
  public ParameterUsages apply(Instruction instruction, ParameterUsages state) {
    if (instruction.isArgument()) {
      return analyzeArgument(instruction.asArgument(), state);
    }
    if (!instructionsOfInterest.contains(instruction)) {
      // The instruction does not use any of the argument values that we are analyzing, so for the
      // purpose of class inlining we can ignore this instruction.
      return state;
    }
    assert !state.isBottom();
    assert !state.isTop();
    return apply(instruction, state.asNonEmpty());
  }

  private ParameterUsages apply(Instruction instruction, NonEmptyParameterUsages state) {
    switch (instruction.opcode()) {
      case ASSUME:
        return analyzeAssume(instruction.asAssume(), state);
      case CHECK_CAST:
        return analyzeCheckCast(instruction.asCheckCast(), state);
      case IF:
        return analyzeIf(instruction.asIf(), state);
      case INSTANCE_GET:
        return analyzeInstanceGet(instruction.asInstanceGet(), state);
      case INSTANCE_PUT:
        return analyzeInstancePut(instruction.asInstancePut(), state);
      case INVOKE_DIRECT:
        return analyzeInvokeDirect(instruction.asInvokeDirect(), state);
      case INVOKE_INTERFACE:
        return analyzeInvokeInterface(instruction.asInvokeInterface(), state);
      case INVOKE_STATIC:
        return analyzeInvokeStatic(instruction.asInvokeStatic(), state);
      case INVOKE_VIRTUAL:
        return analyzeInvokeVirtual(instruction.asInvokeVirtual(), state);
      case MONITOR:
        return analyzeMonitor(instruction.asMonitor(), state);
      case RETURN:
        return analyzeReturn(instruction.asReturn(), state);
      default:
        return fail(instruction, state);
    }
  }

  @Override
  public ParameterUsages computeBlockEntryState(
      BasicBlock block, BasicBlock predecessor, ParameterUsages predecessorExitState) {
    // TODO(b/173337498): Fork a new `FIELD=x` analysis context for the successor block if the
    //  predecessor ends with an if or switch instruction, and the successor block is the
    //  `FIELD=x` target of the predecessor. To avoid an excessive number of contexts being
    //  created, only allow forking new contexts for $r8$classId fields synthesized by the
    //  horizontal class merger.
    return predecessorExitState;
  }

  private ParameterUsages analyzeArgument(Argument argument, ParameterUsages state) {
    // Only consider arguments that could store an instance eligible for class inlining. Note that
    // we can't ignore parameters with a library type, since instances of program classes could
    // still flow into such parameters.
    Value value = argument.outValue();
    if (!value.getType().isClassType() || value.hasPhiUsers()) {
      return state.put(argument.getIndex(), ParameterUsagePerContext.top());
    }

    // Mark the users of this argument for analysis, and fork the analysis of this argument in the
    // default analysis context.
    argumentsOfInterest.add(value);
    instructionsOfInterest.addAll(value.aliasedUsers(aliasedValueConfiguration));
    return state.put(argument.getIndex(), NonEmptyParameterUsagePerContext.createInitial());
  }

  private ParameterUsages analyzeAssume(Assume assume, NonEmptyParameterUsages state) {
    // Mark the value as ineligible for class inlining if it has phi users.
    return assume.outValue().hasPhiUsers() ? fail(assume, state) : state;
  }

  private ParameterUsages analyzeCheckCast(CheckCast checkCast, NonEmptyParameterUsages state) {
    // Mark the value as ineligible for class inlining if it has phi users.
    return checkCast.outValue().hasPhiUsers() ? fail(checkCast, state) : state;
  }

  private ParameterUsages analyzeIf(If theIf, NonEmptyParameterUsages state) {
    // Null/not-null tests are ok.
    if (theIf.isZeroTest()) {
      assert argumentsOfInterest.contains(theIf.lhs().getAliasedValue(aliasedValueConfiguration));
      return state;
    }

    // For non-null tests, mark the inputs as ineligible for class inlining.
    return fail(theIf, state);
  }

  private ParameterUsages analyzeInstanceGet(
      InstanceGet instanceGet, NonEmptyParameterUsages state) {
    // Instance field reads are OK, as long as the field resolves, since the class inliner will
    // just replace the field read by the value of the field.
    FieldResolutionResult resolutionResult = appView.appInfo().resolveField(instanceGet.getField());
    if (resolutionResult.isSuccessfulResolution()) {
      // Record that the field is read from the parameter. For class inlining of singletons, this
      // parameter is only eligible for class inlining if the singleton's field value is known.
      return state.rebuildParameter(
          instanceGet.object(),
          (context, usage) -> usage.addFieldReadFromParameter(instanceGet.getField()));
    }

    return fail(instanceGet, state);
  }

  private ParameterUsages analyzeInstancePut(
      InstancePut instancePut, NonEmptyParameterUsages state) {
    // Instance field writes are OK, as long as the field resolves and the receiver is not being
    // assigned (in that case the receiver escapes, and thus it is not eligible for class
    // inlining).
    Value valueRoot = instancePut.value().getAliasedValue(aliasedValueConfiguration);
    if (isArgumentOfInterest(valueRoot)) {
      state = state.abandonClassInliningInCurrentContexts(valueRoot);
    }

    Value objectRoot = instancePut.object().getAliasedValue(aliasedValueConfiguration);
    if (!isArgumentOfInterest(objectRoot)) {
      return state;
    }

    FieldResolutionResult resolutionResult = appView.appInfo().resolveField(instancePut.getField());
    if (resolutionResult.isSuccessfulResolution()) {
      return state.rebuildParameter(objectRoot, (context, usage) -> usage.setParameterMutated());
    } else {
      return state.abandonClassInliningInCurrentContexts(objectRoot);
    }
  }

  private ParameterUsages analyzeInvokeDirect(InvokeDirect invoke, NonEmptyParameterUsages state) {
    // We generally don't class inline instances that escape through invoke-direct calls, but we
    // make an exception for forwarding/parent constructor calls that does not leak the receiver.
    state =
        state.abandonClassInliningInCurrentContexts(
            invoke.getNonReceiverArguments(), this::isArgumentOfInterest);

    Value receiverRoot = invoke.getReceiver().getAliasedValue(aliasedValueConfiguration);
    if (!isArgumentOfInterest(receiverRoot)) {
      return state;
    }

    if (!receiverRoot.isThis()
        || !method.getDefinition().isInstanceInitializer()
        || !invoke.isInvokeConstructor(dexItemFactory)) {
      return state.abandonClassInliningInCurrentContexts(receiverRoot);
    }

    SingleResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnClass(invoke.getInvokedMethod()).asSingleResolution();
    if (resolutionResult == null) {
      return state.abandonClassInliningInCurrentContexts(receiverRoot);
    }

    InstanceInitializerInfo instanceInitializerInfo =
        resolutionResult
            .getResolvedMethod()
            .getOptimizationInfo()
            .getInstanceInitializerInfo(invoke);
    if (instanceInitializerInfo.receiverMayEscapeOutsideConstructorChain()) {
      return state.abandonClassInliningInCurrentContexts(receiverRoot);
    }

    // We require that there is exactly one forwarding/parent constructor call.
    if (constructorInvoke != null && constructorInvoke != invoke) {
      return state.abandonClassInliningInCurrentContexts(receiverRoot);
    }

    constructorInvoke = invoke;
    return state;
  }

  private ParameterUsages analyzeInvokeInterface(
      InvokeInterface invoke, NonEmptyParameterUsages state) {
    // We only allow invoke-interface instructions where the parameter is in the receiver position.
    state =
        state.abandonClassInliningInCurrentContexts(
            invoke.getNonReceiverArguments(), this::isArgumentOfInterest);

    Value receiverRoot = invoke.getReceiver().getAliasedValue(aliasedValueConfiguration);
    if (!isArgumentOfInterest(receiverRoot)) {
      return state;
    }

    SingleResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnInterface(invoke.getInvokedMethod()).asSingleResolution();
    if (resolutionResult == null) {
      return state.abandonClassInliningInCurrentContexts(receiverRoot);
    }

    return state.rebuildParameter(
        receiverRoot, (context, usage) -> usage.addMethodCallWithParameterAsReceiver(invoke));
  }

  private ParameterUsages analyzeInvokeStatic(InvokeStatic invoke, NonEmptyParameterUsages state) {
    // We generally don't class inline instances that escape through invoke-static calls, but we
    // make an exception for calls to Objects.requireNonNull().
    SingleResolutionResult resolutionResult =
        appView
            .appInfo()
            .unsafeResolveMethodDueToDexFormat(invoke.getInvokedMethod())
            .asSingleResolution();
    if (resolutionResult != null
        && resolutionResult.getResolvedMethod().getReference()
            == dexItemFactory.objectsMethods.requireNonNull) {
      return state;
    }

    return fail(invoke, state);
  }

  private ParameterUsages analyzeInvokeVirtual(
      InvokeVirtual invoke, NonEmptyParameterUsages state) {
    // We only allow invoke-virtual instructions where the parameter is in the receiver position.
    state =
        state.abandonClassInliningInCurrentContexts(
            invoke.getNonReceiverArguments(), this::isArgumentOfInterest);

    Value receiverRoot = invoke.getReceiver().getAliasedValue(aliasedValueConfiguration);
    if (!isArgumentOfInterest(receiverRoot)) {
      return state;
    }

    SingleResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnClass(invoke.getInvokedMethod()).asSingleResolution();
    if (resolutionResult == null) {
      return state.abandonClassInliningInCurrentContexts(receiverRoot);
    }

    return state.rebuildParameter(
        receiverRoot, (context, usage) -> usage.addMethodCallWithParameterAsReceiver(invoke));
  }

  private ParameterUsages analyzeMonitor(Monitor monitor, NonEmptyParameterUsages state) {
    // Record that the receiver is used as a lock in each context that may reach this monitor
    // instruction.
    return state.rebuildParameter(
        monitor.object(), (context, usage) -> usage.setParameterUsedAsLock());
  }

  private ParameterUsages analyzeReturn(Return theReturn, NonEmptyParameterUsages state) {
    return state.rebuildParameter(
        theReturn.returnValue(), (context, usage) -> usage.setParameterReturned());
  }

  private ParameterUsages fail(Instruction instruction, NonEmptyParameterUsages state) {
    return state.abandonClassInliningInCurrentContexts(
        instruction.inValues(), this::isArgumentOfInterest);
  }

  private boolean isArgumentOfInterest(Value value) {
    assert value.getAliasedValue(aliasedValueConfiguration) == value;
    return value.isArgument() && argumentsOfInterest.contains(value);
  }
}

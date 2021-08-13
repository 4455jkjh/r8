// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo.abandoned;
import static com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo.top;
import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.PostOptimization;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ForEachable;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.CallSiteOptimizationOptions;
import com.android.tools.r8.utils.LazyBox;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class CallSiteOptimizationInfoPropagator implements PostOptimization {

  // TODO(b/139246447): should we revisit new targets over and over again?
  //   Maybe piggy-back on MethodProcessor's wave/batch processing?
  // For now, before revisiting methods with more precise argument info, we switch the mode.
  // Then, revisiting a target at a certain level will not improve call site information of
  // callees in lower levels.
  private enum Mode {
    COLLECT, // Set until the end of the 1st round of IR processing. CallSiteOptimizationInfo will
             // be updated in this mode only.
    REVISIT  // Set once the all methods are processed. IRBuilder will add other instructions that
             // reflect collected CallSiteOptimizationInfo.
  }

  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;
  private final CallSiteOptimizationOptions optimizationOptions;
  private ProgramMethodSet revisitedMethods = null;
  private Mode mode = Mode.COLLECT;

  public CallSiteOptimizationInfoPropagator(AppView<AppInfoWithLiveness> appView) {
    assert appView.enableWholeProgramOptimizations();
    this.appView = appView;
    this.options = appView.options();
    this.optimizationOptions = appView.options().callSiteOptimizationOptions();
    if (Log.isLoggingEnabledFor(CallSiteOptimizationInfoPropagator.class)) {
      revisitedMethods = ProgramMethodSet.create();
    }
  }

  public void logResults() {
    assert Log.ENABLED;
    if (revisitedMethods != null) {
      Log.info(getClass(), "# of methods to revisit: %s", revisitedMethods.size());
      for (ProgramMethod m : revisitedMethods) {
        Log.info(
            getClass(),
            "%s: %s",
            m.toSourceString(),
            m.getDefinition().getCallSiteOptimizationInfo().toString());
      }
    }
  }

  public void collectCallSiteOptimizationInfo(IRCode code, Timing timing) {
    // TODO(b/139246447): we could collect call site optimization during REVISIT mode as well,
    //   but that may require a separate copy of CallSiteOptimizationInfo.
    if (mode != Mode.COLLECT) {
      return;
    }

    ProgramMethod context = code.context();
    for (Instruction instruction : code.instructions()) {
      if (instruction.isInvokeMethod()) {
        collectCallSiteOptimizationInfoForInvokeMethod(
            instruction.asInvokeMethod(), context, timing);
      } else if (instruction.isInvokeCustom()) {
        collectCallSiteOptimizationInfoForInvokeCustom(instruction.asInvokeCustom());
      }
    }
  }

  private void collectCallSiteOptimizationInfoForInvokeMethod(
      InvokeMethod invoke, ProgramMethod context, Timing timing) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    SingleResolutionResult resolutionResult =
        appView
            .appInfo()
            .resolveMethod(invokedMethod, invoke.getInterfaceBit())
            .asSingleResolution();
    if (resolutionResult == null) {
      return;
    }
    // For virtual and interface calls, proceed on valid results only (since it's enforced).
    if (invoke.isInvokeMethodWithDynamicDispatch() && !resolutionResult.isVirtualTarget()) {
      return;
    }
    ProgramMethod resolutionTarget =
        resolutionResult.asSingleResolution().getResolutionPair().asProgramMethod();
    if (resolutionTarget == null || isMaybeClasspathOrLibraryMethodOverride(resolutionTarget)) {
      return;
    }
    propagateArgumentsToDispatchTargets(invoke, resolutionResult, context, timing);
  }

  private void collectCallSiteOptimizationInfoForInvokeCustom(InvokeCustom invoke) {
    // If the bootstrap method is program declared it will be called. The call is with runtime
    // provided arguments so ensure that the call-site info is TOP.
    DexMethodHandle bootstrapMethod = invoke.getCallSite().bootstrapMethod;
    SingleResolutionResult resolution =
        appView
            .appInfo()
            .resolveMethod(bootstrapMethod.asMethod(), bootstrapMethod.isInterface)
            .asSingleResolution();
    if (resolution != null && resolution.getResolvedHolder().isProgramClass()) {
      resolution.getResolvedMethod().joinCallSiteOptimizationInfo(top(), appView);
    }
  }

  private boolean isMaybeClasspathOrLibraryMethodOverride(ProgramMethod target) {
    // If the method overrides a library method, it is unsure how the method would be invoked by
    // that library.
    return target.getDefinition().isLibraryMethodOverride().isPossiblyTrue();
  }

  // Propagate information about the arguments to all possible dispatch targets of the invoke.
  private void propagateArgumentsToDispatchTargets(
      InvokeMethod invoke,
      SingleResolutionResult resolutionResult,
      ProgramMethod context,
      Timing timing) {
    if (invoke.arguments().isEmpty()) {
      // Nothing to propagate.
      return;
    }

    if (invoke.arguments().size()
        != invoke.getInvokedMethod().getArity()
            + BooleanUtils.intValue(invoke.isInvokeMethodWithReceiver())) {
      // Verification error.
      assert false;
      return;
    }

    if (resolutionResult.getResolvedMethod().getCallSiteOptimizationInfo().isAbandoned()) {
      // We stopped tracking the arguments to all possible dispatch targets.
      assert verifyAllProgramDispatchTargetsHaveBeenAbandoned(invoke, context);
      return;
    }

    timing.begin("Lookup possible dispatch targets");
    ProgramMethodSet targets = invoke.lookupProgramDispatchTargets(appView, context);
    timing.end();

    assert invoke.isInvokeMethodWithDynamicDispatch()
        // For other invocation types, the size of targets should be at most one.
        || targets == null
        || targets.size() <= 1;

    if (targets == null || targets.isEmpty()) {
      return;
    }

    if (targets.size() > optimizationOptions.getMaxNumberOfDispatchTargetsBeforeAbandoning()) {
      // If the number of targets exceed the threshold, abandon call site optimization for all
      // targets.
      abandonCallSitePropagation(invoke, resolutionResult, targets, context);
      return;
    }

    timing.begin("Record arguments");
    // Lazily computed piece of information that needs to be propagated to all dispatch targets.
    LazyBox<CallSiteOptimizationInfo> callSiteOptimizationInfo =
        new LazyBox<>(() -> computeCallSiteOptimizationInfoFromArguments(invoke, context, timing));
    for (ProgramMethod target : targets) {
      CallSiteOptimizationInfo newCallSiteOptimizationInfo =
          propagateArgumentsToDispatchTarget(target, callSiteOptimizationInfo, timing);

      // If one of the targets is abandoned or ends up being abandoned, then abandon call site
      // optimization for all targets.
      if (newCallSiteOptimizationInfo.isAbandoned()) {
        abandonCallSitePropagation(invoke, resolutionResult, targets, context);
        break;
      }
    }
    timing.end();
  }

  private CallSiteOptimizationInfo propagateArgumentsToDispatchTarget(
      ProgramMethod target,
      LazyBox<CallSiteOptimizationInfo> lazyCallSiteOptimizationInfo,
      Timing timing) {
    CallSiteOptimizationInfo existingCallSiteOptimizationInfo =
        target.getDefinition().getCallSiteOptimizationInfo();
    if (existingCallSiteOptimizationInfo.isAbandoned()
        || existingCallSiteOptimizationInfo.isTop()) {
      return existingCallSiteOptimizationInfo;
    }
    if (!appView.appInfo().mayPropagateArgumentsTo(target)) {
      return top();
    }
    timing.begin("Join argument info");
    CallSiteOptimizationInfo callSiteOptimizationInfo =
        lazyCallSiteOptimizationInfo.computeIfAbsent();
    target
        .getDefinition()
        .joinCallSiteOptimizationInfo(
            callSiteOptimizationInfo.hasUsefulOptimizationInfo(appView, target)
                ? callSiteOptimizationInfo
                : top(),
            appView);
    timing.end();
    return target.getDefinition().getCallSiteOptimizationInfo();
  }

  private void abandonCallSitePropagation(
      InvokeMethod invoke,
      SingleResolutionResult resolutionResult,
      ProgramMethodSet targets,
      ProgramMethod context) {
    if (invoke.isInvokeMethodWithDynamicDispatch()) {
      // When there is a dynamic dispatch, we may have used dynamic type information to reduce the
      // set of possible dispatch targets. However, it is an invariant that a method is marked as
      // abandoned if-and-only-if that method and all of its overrides have been marked as
      // abandoned. Therefore, we need to find all the overrides of the targeted method and mark
      // them as abandoned, which we accomplish by performing a lookup without any dynamic type
      // information.
      InvokeMethodWithReceiver invokeMethodWithReceiver = invoke.asInvokeMethodWithReceiver();
      if (invokeMethodWithReceiver.hasRefinedReceiverUpperBoundType(appView)
          || invokeMethodWithReceiver.hasRefinedReceiverLowerBoundType(appView)) {
        LookupResult lookupResult =
            resolutionResult.lookupVirtualDispatchTargets(context.getHolder(), appView.appInfo());
        // This should always succeed since we already looked up `targets` successfully.
        assert lookupResult.isLookupResultSuccess();
        abandonCallSitePropagation(
            consumer ->
                lookupResult.forEach(
                    methodTarget -> {
                      if (methodTarget.isProgramMethod()) {
                        consumer.accept(methodTarget.asProgramMethod());
                      } else {
                        // This may happen if an interface method in the program is implemented
                        // by a method in the classpath or library.
                        assert invoke.isInvokeInterface();
                      }
                    },
                    emptyConsumer()));
        return;
      }
    }
    abandonCallSitePropagation(targets::forEach);
  }

  private void abandonCallSitePropagation(ForEachable<ProgramMethod> methods) {
    if (InternalOptions.assertionsEnabled()) {
      synchronized (this) {
        methods.forEach(method -> method.getDefinition().abandonCallSiteOptimizationInfo());
      }
    } else {
      methods.forEach(method -> method.getDefinition().abandonCallSiteOptimizationInfo());
    }
  }

  public void abandonCallSitePropagationForLambdaImplementationMethods(
      ExecutorService executorService, Timing timing) throws ExecutionException {
    if (appView.options().isGeneratingClassFiles()) {
      timing.begin("Call site optimization: abandon lambda implementation methods");
      ForEachable<ProgramMethod> lambdaImplementationMethods =
          consumer ->
              appView
                  .appInfo()
                  .forEachMethod(
                      method -> {
                        if (appView
                            .appInfo()
                            .isMethodTargetedByInvokeDynamic(method.getReference())) {
                          consumer.accept(method);
                        }
                      });
      ThreadUtils.processItems(
          lambdaImplementationMethods,
          this::abandonCallSitePropagationForMethodAndOverrides,
          executorService);
      timing.end();
    }
  }

  public void abandonCallSitePropagationForPinnedMethodsAndOverrides(
      ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Call site optimization: abandon pinned methods");
    ThreadUtils.processItems(
        this::forEachPinnedNonPrivateVirtualMethod,
        this::abandonCallSitePropagationForMethodAndOverrides,
        executorService);
    timing.end();
  }

  private void forEachPinnedNonPrivateVirtualMethod(Consumer<ProgramMethod> consumer) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (ProgramMethod virtualProgramMethod : clazz.virtualProgramMethods()) {
        if (virtualProgramMethod.getDefinition().isNonPrivateVirtualMethod()
            && appView
                .getKeepInfo()
                .isPinned(virtualProgramMethod.getReference(), appView, options)) {
          consumer.accept(virtualProgramMethod);
        }
      }
    }
  }

  private void abandonCallSitePropagationForMethodAndOverrides(ProgramMethod method) {
    Set<ProgramMethod> abandonSet = Sets.newIdentityHashSet();
    if (method.getDefinition().isNonPrivateVirtualMethod()) {
      SingleResolutionResult resolutionResult =
          new SingleResolutionResult(
              method.getHolder(), method.getHolder(), method.getDefinition());
      resolutionResult
          .lookupVirtualDispatchTargets(method.getHolder(), appView.appInfo())
          .forEach(
              methodTarget -> {
                if (methodTarget.isProgramMethod()) {
                  abandonSet.add(methodTarget.asProgramMethod());
                }
              },
              lambdaTarget -> {
                if (lambdaTarget.getImplementationMethod().isProgramMethod()) {
                  abandonSet.add(lambdaTarget.getImplementationMethod().asProgramMethod());
                }
              });
    } else {
      abandonSet.add(method);
    }
    abandonCallSitePropagation(abandonSet::forEach);
  }

  private CallSiteOptimizationInfo computeCallSiteOptimizationInfoFromArguments(
      InvokeMethod invoke, ProgramMethod context, Timing timing) {
    timing.begin("Compute argument info");
    CallSiteOptimizationInfo callSiteOptimizationInfo =
        ConcreteCallSiteOptimizationInfo.fromArguments(
            appView, invoke.getInvokedMethod(), invoke.arguments(), context);
    if (callSiteOptimizationInfo.isTop()) {
      // If we are propagating unknown information to all call sites, then mark them as abandoned
      // such that we bail out before looking up the possible dispatch targets if we see any future
      // invokes to these methods.
      callSiteOptimizationInfo = abandoned();
    }
    timing.end();
    return callSiteOptimizationInfo;
  }

  @Override
  public ProgramMethodSet methodsToRevisit() {
    mode = Mode.REVISIT;
    ProgramMethodSet targetsToRevisit = ProgramMethodSet.create();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethodMatching(
          definition -> {
            assert !definition.isObsolete();
            if (definition.shouldNotHaveCode()
                || !definition.hasCode()
                || definition.getCode().isEmptyVoidMethod()) {
              return false;
            }
            // TODO(b/139246447): Assert no BOTTOM left.
            CallSiteOptimizationInfo callSiteOptimizationInfo =
                definition.getCallSiteOptimizationInfo();
            return callSiteOptimizationInfo.hasUsefulOptimizationInfo(appView, definition);
          },
          method -> {
            targetsToRevisit.add(method);
            if (appView.options().testing.callSiteOptimizationInfoInspector != null) {
              appView.options().testing.callSiteOptimizationInfoInspector.accept(method);
            }
          });
    }
    if (revisitedMethods != null) {
      revisitedMethods.addAll(targetsToRevisit);
    }
    return targetsToRevisit;
  }

  private synchronized boolean verifyAllProgramDispatchTargetsHaveBeenAbandoned(
      InvokeMethod invoke, ProgramMethod context) {
    ProgramMethodSet targets = invoke.lookupProgramDispatchTargets(appView, context);
    if (targets != null) {
      for (ProgramMethod target : targets) {
        assert target.getDefinition().getCallSiteOptimizationInfo().isAbandoned()
            : "Expected method `"
                + target.toSourceString()
                + "` to be marked as abandoned (called from `"
                + invoke.toString()
                + "` in `"
                + context.toSourceString()
                + "`)";
      }
    }
    return true;
  }
}

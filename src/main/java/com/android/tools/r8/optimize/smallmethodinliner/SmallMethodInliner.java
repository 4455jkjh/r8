// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.smallmethodinliner;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;
import static com.android.tools.r8.utils.internal.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.MutableFieldAccessInfo;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.conversion.IRFinalizer;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.MethodProcessorEventConsumer;
import com.android.tools.r8.ir.conversion.OneTimeMethodProcessor;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.Inliner.InvokeSupplier;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.inliner.InliningIRProvider;
import com.android.tools.r8.ir.optimize.inliner.InliningReasonStrategy;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirConstant;
import com.android.tools.r8.lightir.LirInstructionView;
import com.android.tools.r8.lightir.LirOpcodeUtils;
import com.android.tools.r8.optimize.singlecaller.SingleCallerInliner;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

/**
 * A simple inliner pass that aggressively inlines small methods such as getters and setters early
 * in the pipeline. All small methods that are inlined into all call sites are then deleted from the
 * app.
 */
public class SmallMethodInliner extends Inliner implements InliningReasonStrategy, InvokeSupplier {

  private final ProgramMethodSet methodsToInline = ProgramMethodSet.createConcurrent();
  private final ProgramMethodSet failedToInline = ProgramMethodSet.createConcurrent();

  private final ProgramMethodSet needsFinalization = ProgramMethodSet.createConcurrent();

  private SmallMethodInliner(AppView<AppInfoWithLiveness> appView) {
    super(appView);
  }

  public static void run(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    InternalOptions options = appView.options();
    if (options.isOptimizing() && options.isShrinking()) {
      timing.begin("SmallMethodInliner");
      new SmallMethodInliner(appView).runInternal(executorService, timing);
      appView.getTypeElementFactory().clearTypeElementsCache();
      timing.end();
    }
  }

  private void runInternal(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    computeMethodsToInline(executorService, timing);
    if (methodsToInline.isEmpty()) {
      return;
    }

    ProgramMethodSet callers = computeCallers(executorService, timing);
    processCallers(callers, executorService, timing);
    pruneInlinedMethods(executorService, timing);
  }

  private void computeMethodsToInline(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    try (Timing t0 = timing.begin("Compute methods to inline")) {
      ProgramMethodSet monomorphicVirtualMethods =
          SingleCallerInliner.computeMonomorphicVirtualRootMethods(appView, executorService);
      ThreadUtils.processItems(
          appView.appInfo().classes(),
          clazz ->
              clazz.forEachProgramMethodMatching(
                  method -> {
                    if (!method.hasLirCode() || method.isClassInitializer()) {
                      return false;
                    }
                    if (method.isLibraryMethodOverride().isPossiblyTrue()) {
                      // We cannot delete this method as the library may dispatch to it.
                      return false;
                    }
                    if (method.isInstanceInitializer()
                        && !options.canInitNewInstanceUsingSuperclassConstructor()) {
                      return false;
                    }
                    if (method.getAccessFlags().belongsToVirtualPool()
                        && !monomorphicVirtualMethods.contains(method)) {
                      return false;
                    }
                    return hasAtMostOneInstruction(method.getLirCode());
                  },
                  method -> {
                    KeepMethodInfo keepInfo = appView.getKeepInfo(method);
                    if (!keepInfo.isClosedWorldReasoningAllowed(options)
                        || !keepInfo.isOptimizationAllowed(options)
                        || !keepInfo.isShrinkingAllowed(options)) {
                      return;
                    }
                    IRCode code = method.buildIR(appView, MethodConversionOptions.nonConverting());
                    ConstraintWithTarget constraint = computeInliningConstraint(code);
                    if (constraint.isAlways()) {
                      methodsToInline.add(method);
                      method.getDefinition().markProcessed(constraint);
                    }
                  }),
          options.getThreadingModule(),
          executorService);
    }
  }

  private boolean hasAtMostOneInstruction(LirCode<?> lirCode) {
    int count = 0;
    for (LirInstructionView view : lirCode) {
      int opcode = view.getOpcode();
      if (!LirOpcodeUtils.isReturn(opcode)) {
        count++;
        if (count > 1) {
          return false;
        }
      }
    }
    return true;
  }

  private ProgramMethodSet computeCallers(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    try (Timing t0 = timing.begin("Compute callers")) {
      DexMethodSignatureSet methodSignaturesOfInterest = DexMethodSignatureSet.create();
      for (ProgramMethod method : methodsToInline) {
        methodSignaturesOfInterest.add(method);
      }

      // Find all methods that call a method that needs to be inlined.
      ProgramMethodSet callers = ProgramMethodSet.createConcurrent();
      ThreadUtils.processItems(
          appView.appInfo().classes(),
          clazz ->
              clazz.forEachProgramMethodMatching(
                  method -> {
                    if (method.hasLirCode()) {
                      for (LirConstant constant : method.getCode().asLirCode().getConstantPool()) {
                        if (constant instanceof DexMethod) {
                          DexMethod methodConstant = (DexMethod) constant;
                          if (!methodSignaturesOfInterest.contains(methodConstant)) {
                            continue;
                          }
                          ProgramMethod resolvedMethod =
                              appView
                                  .appInfo()
                                  .unsafeResolveMethodDueToDexFormat(methodConstant)
                                  .getResolvedProgramMethod();
                          if (resolvedMethod != null && methodsToInline.contains(resolvedMethod)) {
                            return true;
                          }
                        }
                      }
                    }
                    return false;
                  },
                  callers::add),
          options.getThreadingModule(),
          executorService);
      return callers;
    }
  }

  private void processCallers(
      ProgramMethodSet callers, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    try (Timing t0 = timing.begin("Process callers")) {
      MethodProcessor methodProcessor = createMethodProcessor();
      ThreadUtils.processItems(
          callers,
          method -> {
            IRCode code = method.buildIR(appView);
            DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView);
            Timing threadTiming = Timing.empty();
            performInlining(
                method,
                code,
                OptimizationFeedbackSimple.getInstance(),
                methodProcessor,
                threadTiming,
                this,
                this);

            // Only convert IR back to LIR if any methods were inlined.
            if (needsFinalization.remove(method)) {
              IRFinalizer<?> finalizer =
                  code.getConversionOptions().getFinalizer(deadCodeRemover, appView);
              Code newCode =
                  finalizer.finalizeCode(code, BytecodeMetadataProvider.empty(), threadTiming);
              method.setCode(newCode, appView);
            }
          },
          options.getThreadingModule(),
          executorService);
      assert needsFinalization.isEmpty();
    }
  }

  private void pruneInlinedMethods(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    try (Timing t0 = timing.begin("Prune inlined methods")) {
      PrunedItems.Builder prunedItemsBuilder = PrunedItems.builder().setPrunedApp(appView.app());
      Map<DexProgramClass, Set<DexEncodedMethod>> methodsToRemoveByClass = new IdentityHashMap<>();
      SmallMethodInlinerResult result = new SmallMethodInlinerResult();
      for (ProgramMethod method : methodsToInline) {
        if (failedToInline.contains(method)) {
          method.getDefinition().markNotProcessed();
        } else {
          prunedItemsBuilder.addRemovedMethod(method.getReference());
          methodsToRemoveByClass
              .computeIfAbsent(method.getHolder(), ignoreKey(Sets::newIdentityHashSet))
              .add(method.getDefinition());
          if (method.getDefinition().isInstanceInitializer()) {
            result.classesWithFullyInlinedInstanceInitializers.add(method.getHolderType());
          }
        }
      }
      // Remove all methods for each class at once to avoid quadratic behavior.
      methodsToRemoveByClass.forEach(
          (clazz, methodsToRemove) -> clazz.getMethodCollection().removeMethods(methodsToRemove));
      appView.pruneItems(prunedItemsBuilder.build(), executorService, timing);
      appView.setSmallMethodInlinerResult(result);
    }
  }

  private MethodProcessor createMethodProcessor() {
    return OneTimeMethodProcessor.create(
        ProgramMethodSet.empty(), MethodProcessorEventConsumer.empty(), appView);
  }

  @Override
  public Reason computeInliningReason(
      InvokeMethod invoke,
      ProgramMethod target,
      ProgramMethod context,
      DefaultInliningOracle oracle,
      InliningIRProvider inliningIRProvider,
      MethodProcessor methodProcessor,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    return methodsToInline.contains(target) ? Reason.ALWAYS : Reason.NEVER;
  }

  @Override
  public void forEachInvoke(
      ProgramMethod method,
      BasicBlock block,
      BiConsumer<InvokeMethod, InstructionListIterator> consumer) {
    InstructionListIterator iterator = block.listIterator();
    while (iterator.hasNext()) {
      InvokeMethod invoke = iterator.next().asInvokeMethod();
      if (invoke == null) {
        continue;
      }
      SingleResolutionResult<?> resolutionResult =
          invoke.resolveMethod(appView, method).asSingleResolution();
      if (resolutionResult == null) {
        continue;
      }
      ProgramMethod target =
          asProgramMethodOrNull(
              resolutionResult
                  .lookupDispatchTarget(appView, invoke, method)
                  .getSingleDispatchTarget());
      if (target != null && methodsToInline.contains(target)) {
        consumer.accept(invoke, iterator);
      } else {
        ProgramMethod resolvedMethod = resolutionResult.getResolvedProgramMethod();
        if (resolvedMethod != null && methodsToInline.contains(resolvedMethod)) {
          failedToInline.add(resolvedMethod);
        }
      }
    }
  }

  @Override
  protected void inlineInvoke(
      InstructionListIterator iterator,
      IRCode code,
      IRCode inlinee,
      ListIterator<BasicBlock> blockIterator,
      Set<BasicBlock> blocksToRemove,
      DexProgramClass downcast) {
    for (FieldInstruction fieldInstruction :
        inlinee.<FieldInstruction>instructions(Instruction::isFieldInstruction)) {
      ProgramField resolvedField =
          fieldInstruction.resolveField(appView, inlinee.context()).getProgramField();
      if (resolvedField != null) {
        MutableFieldAccessInfo info =
            appView
                .appInfo()
                .getMutableFieldAccessInfoCollection()
                .get(resolvedField.getReference());
        if (info != null) {
          synchronized (info) {
            if (fieldInstruction.isFieldGet()) {
              info.recordRead(fieldInstruction.getField(), code.context());
            } else {
              info.recordWrite(fieldInstruction.getField(), code.context());
            }
          }
        }
      }
    }
    super.inlineInvoke(iterator, code, inlinee, blockIterator, blocksToRemove, downcast);
    needsFinalization.add(code.context());
  }

  @Override
  protected void notifyInvokeNotInlined(
      InvokeMethod invoke, MethodResolutionResult resolutionResult) {
    if (resolutionResult.isSingleResolution()) {
      ProgramMethod target = resolutionResult.getResolvedProgramMethod();
      if (target != null && methodsToInline.contains(target)) {
        failedToInline.add(target);
      }
    }
  }

  @Override
  protected boolean shouldApplyInliningToInlinee(
      AppView<?> appView, ProgramMethod singleTarget, int inliningDepth) {
    return true;
  }
}

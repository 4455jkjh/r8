// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.shaking.ObjectAllocationInfoCollectionUtils.mayHaveFinalizeMethodDirectlyOrIndirectly;
import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRFinalizer;
import com.android.tools.r8.ir.conversion.IRToCfFinalizer;
import com.android.tools.r8.ir.conversion.IRToDexFinalizer;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfoLookup;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessKind;
import com.android.tools.r8.shaking.Enqueuer.FieldAccessMetadata;
import com.android.tools.r8.shaking.Enqueuer.Mode;
import com.android.tools.r8.shaking.EnqueuerWorklist.EnqueuerAction;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramFieldMap;
import com.android.tools.r8.utils.collections.ProgramFieldSet;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class EnqueuerDeferredTracing {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Enqueuer enqueuer;
  private final Mode mode;
  private final InternalOptions options;

  // Helper for rewriting code instances at the end of tree shaking.
  private final EnqueuerDeferredTracingRewriter rewriter;

  // Maps each field to the tracing actions that have been deferred for that field. This allows
  // enqueuing previously deferred tracing actions into the worklist if a given field cannot be
  // optimized after all.
  private final ProgramFieldMap<Set<EnqueuerAction>> deferredEnqueuerActions =
      ProgramFieldMap.create();

  // A set of fields that are never eligible for pruning.
  private final ProgramFieldSet ineligibleForPruning = ProgramFieldSet.create();

  EnqueuerDeferredTracing(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer, Mode mode) {
    this.appView = appView;
    this.enqueuer = enqueuer;
    this.mode = mode;
    this.options = appView.options();
    this.rewriter = new EnqueuerDeferredTracingRewriter(appView);
  }

  /**
   * Returns true if the {@link Enqueuer} should not trace the given field reference.
   *
   * <p>If for some reason the field reference should be traced after all, a worklist item can be
   * enqueued upon reaching a (preliminary) fixpoint in {@link
   * #enqueueWorklistActions(EnqueuerWorklist)}, which will cause tracing to continue.
   */
  public boolean deferTracingOfFieldAccess(
      DexField fieldReference,
      FieldResolutionResult resolutionResult,
      ProgramMethod context,
      FieldAccessKind accessKind,
      FieldAccessMetadata metadata) {
    if (!enqueuer.getMode().isTreeShaking()) {
      return false;
    }

    ProgramField field = resolutionResult.getSingleProgramField();
    if (field == null) {
      return false;
    }

    // Check if field access is consistent with the field access flags.
    if (field.getAccessFlags().isStatic() != accessKind.isStatic()) {
      return enqueueDeferredEnqueuerActions(field);
    }

    // If the access is from a reachability sensitive method, then bail out.
    if (context.getHolder().getOrComputeReachabilitySensitive(appView)) {
      return enqueueDeferredEnqueuerActions(field);
    }

    if (accessKind.isRead()) {
      // If the value of the field is not guaranteed to be the default value, even if it is never
      // assigned, then give up.
      // TODO(b/205810841): Allow this by handling this in the corresponding IR rewriter.
      AssumeInfo assumeInfo = AssumeInfoLookup.lookupAssumeInfo(appView, field);
      if (assumeInfo != null && assumeInfo.hasReturnInfo()) {
        return enqueueDeferredEnqueuerActions(field);
      }
      if (field.getAccessFlags().isStatic() && field.getDefinition().hasExplicitStaticValue()) {
        return enqueueDeferredEnqueuerActions(field);
      }
    }

    if (!isEligibleForPruning(field)) {
      return enqueueDeferredEnqueuerActions(field);
    }

    // Field can be removed unless some other field access that has not yet been seen prohibits it.
    // Record an EnqueuerAction that must be traced if that should happen.
    EnqueuerAction deferredEnqueuerAction =
        accessKind.toEnqueuerAction(fieldReference, context, metadata.toDeferred());
    deferredEnqueuerActions
        .computeIfAbsent(field, ignoreKey(LinkedHashSet::new))
        .add(deferredEnqueuerAction);

    // If the field is static, then the field access will trigger the class initializer of the
    // field's holder. Therefore, we unconditionally trace the class initializer in this case.
    // The corresponding IR rewriter will rewrite the field access into an init-class instruction.
    if (accessKind.isStatic()) {
      KeepReason reason =
          enqueuer.getGraphReporter().reportClassReferencedFrom(field.getHolder(), context);
      enqueuer.getWorklist().enqueueTraceTypeReferenceAction(field.getHolder(), reason);
      enqueuer.getWorklist().enqueueTraceDirectAndIndirectClassInitializers(field.getHolder());
    }

    return true;
  }

  public void notifyReflectiveFieldAccess(ProgramField field, ProgramMethod context) {
    enqueueDeferredEnqueuerActions(field);
  }

  private boolean isEligibleForPruning(ProgramField field) {
    FieldAccessInfo info = enqueuer.getFieldAccessInfoCollection().get(field.getReference());
    if (info.hasReflectiveAccess()
        || info.isAccessedFromMethodHandle()
        || info.isReadFromAnnotation()
        || info.isReadFromRecordInvokeDynamic()
        || enqueuer.getKeepInfo(field).isPinned(options)) {
      return false;
    }

    if (info.isWritten()) {
      // If the assigned value may have an override of Object#finalize() then give up.
      // Note that this check depends on the set of instantiated types, and must therefore be rerun
      // when the enqueuer's fixpoint is reached.
      if (field.getType().isReferenceType()) {
        DexType fieldBaseType = field.getType().toBaseType(appView.dexItemFactory());
        if (fieldBaseType.isClassType()
            && mayHaveFinalizeMethodDirectlyOrIndirectly(
                appView, fieldBaseType, enqueuer.getObjectAllocationInfoCollection())) {
          return false;
        }
      }
    }

    // We always have precise knowledge of field accesses during tracing.
    assert info.hasKnownReadContexts();
    assert info.hasKnownWriteContexts();

    DexType fieldType = field.getType();

    // If the field is now both read and written, then we cannot optimize the field unless the field
    // type is an uninstantiated class type.
    if (info.getReadsWithContexts().hasAccesses() && info.getWritesWithContexts().hasAccesses()) {
      if (!fieldType.isClassType()) {
        return false;
      }
      DexProgramClass fieldTypeDefinition = asProgramClassOrNull(appView.definitionFor(fieldType));
      if (fieldTypeDefinition == null
          || enqueuer
              .getObjectAllocationInfoCollection()
              .isInstantiatedDirectlyOrHasInstantiatedSubtype(fieldTypeDefinition)) {
        return false;
      }
    }

    return !ineligibleForPruning.contains(field);
  }

  private boolean enqueueDeferredEnqueuerActions(ProgramField field) {
    Set<EnqueuerAction> actions = deferredEnqueuerActions.remove(field);
    if (actions != null) {
      enqueuer.getWorklist().enqueueAll(actions);
    }
    ineligibleForPruning.add(field);
    return false;
  }

  /**
   * Called when the {@link EnqueuerWorklist} is empty, to allow additional tracing before ending
   * tree shaking.
   */
  public boolean enqueueWorklistActions(EnqueuerWorklist worklist) {
    return deferredEnqueuerActions.removeIf(
        (field, worklistActions) -> {
          if (isEligibleForPruning(field)) {
            return false;
          }
          worklist.enqueueAll(worklistActions);
          return true;
        });
  }

  /**
   * Called when tree shaking has ended, to allow rewriting the application according to the tracing
   * that has not been performed (e.g., rewriting of dead field instructions).
   */
  public void rewriteApplication(ExecutorService executorService) throws ExecutionException {
    FieldAccessInfoCollectionImpl fieldAccessInfoCollection =
        enqueuer.getFieldAccessInfoCollection();
    ProgramMethodSet methodsToProcess = ProgramMethodSet.create();
    Map<DexField, ProgramField> prunedFields = new IdentityHashMap<>();
    deferredEnqueuerActions.forEach(
        (field, ignore) -> {
          FieldAccessInfo accessInfo = fieldAccessInfoCollection.get(field.getReference());
          prunedFields.put(field.getReference(), field);
          accessInfo.forEachAccessContext(methodsToProcess::add);
          accessInfo.forEachIndirectAccess(reference -> prunedFields.put(reference, field));
        });
    deferredEnqueuerActions.clear();

    // Rewrite application.
    Map<DexProgramClass, ProgramMethodSet> initializedClassesWithContexts =
        new ConcurrentHashMap<>();
    ThreadUtils.processItems(
        methodsToProcess,
        method -> rewriteMethod(method, initializedClassesWithContexts, prunedFields),
        executorService);

    // Register new InitClass instructions.
    initializedClassesWithContexts.forEach(
        (clazz, contexts) ->
            contexts.forEach(context -> enqueuer.traceInitClass(clazz.getType(), context)));
    assert enqueuer.getWorklist().isEmpty();

    // Prune field access info collection.
    prunedFields.values().forEach(field -> fieldAccessInfoCollection.remove(field.getReference()));
  }

  private void rewriteMethod(
      ProgramMethod method,
      Map<DexProgramClass, ProgramMethodSet> initializedClassesWithContexts,
      Map<DexField, ProgramField> prunedFields) {
    // Build IR.
    MutableMethodConversionOptions conversionOptions =
        mode.isInitialTreeShaking()
            ? new MutableMethodConversionOptions(options).setIsGeneratingClassFiles(true)
            : new MutableMethodConversionOptions(options);
    conversionOptions.disableStringSwitchConversion();

    IRCode ir = method.buildIR(appView, conversionOptions);

    // Rewrite the IR according to the tracing that has been deferred.
    rewriter.rewriteCode(ir, initializedClassesWithContexts, prunedFields);

    // Run dead code elimination.
    rewriter.getCodeRewriter().optimizeAlwaysThrowingInstructions(ir);
    rewriter.getDeadCodeRemover().run(ir, Timing.empty());

    // Finalize to class files or dex.
    IRFinalizer<?> finalizer =
        conversionOptions.isGeneratingClassFiles()
            ? new IRToCfFinalizer(appView, rewriter.getDeadCodeRemover())
            : new IRToDexFinalizer(appView, rewriter.getDeadCodeRemover());
    Code newCode = finalizer.finalizeCode(ir, BytecodeMetadataProvider.empty(), Timing.empty());
    method.setCode(newCode, appView);
  }
}

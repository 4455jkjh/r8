// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility.Reporter.reportInfo;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeInstructionListIterator;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AtomicFieldUpdaterInstrumentor.AtomicFieldUpdaterInstrumentorInfo;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizations;
import com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility.Event;
import com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility.Reason;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Map;

/**
 * This pass uses the instrumentation of {@code AtomicFieldUpdaterInstrumentor} to find calls to
 * AtomicReferenceFieldUpdater and replace them by their underlying unsafe operation.
 *
 * <BlockQuote>
 *
 * <pre>
 *  class Example {
 *    volatile SomeType someField;
 *    static final AtomicReferenceFieldUpdater updater;
 *    // Synthetic field produced by AtomicFieldUpdaterInstrumentor.
 *    public static final long updater$offset;
 *
 *    static {
 *      .. initializes both updater and updater$offset ..
 *    }
 *
 *    ReturnType exampleFunction(..) {
 *      ..
 *      updater.compareAndSet(instance, expect, update);
 *      // If static information permits, translate into:
 *      checkNull(updater) // If necessary.
 *      checkNull(instance) // If necessary.
 *      SyntheticUnsafeClass.unsafe.compareAndSet(instance, updater$offset, expect, update)
 *      ..
 *    }
 *  }
 * </pre>
 *
 * </BlockQuote>
 */
public class AtomicFieldUpdaterOptimizer extends CodeRewriterPass<AppInfoWithClassHierarchy> {

  protected AtomicFieldUpdaterOptimizer(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "AtomicFieldUpdaterOptimizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    // TODO(b/453628974): Test with compare calls in class initializer.
    return !isDebugMode(code.context())
        && methodProcessor.isPrimaryMethodProcessor()
        // TODO(b/453628974): Consider running in second pass (must maintain appView data).
        && appView
            .getAtomicFieldUpdaterInstrumentorInfo()
            .isInstrumented(code.context().getHolderType())
        && code.metadata().mayHaveInvokeMethodWithReceiver();
  }

  @Override
  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    AtomicFieldUpdaterInstrumentorInfo info = appView.getAtomicFieldUpdaterInstrumentorInfo();
    var atomicUpdaterFields = info.getInstrumentationsOrNull(code.context().getHolderType());
    assert atomicUpdaterFields != null;

    var it = code.instructionListIterator();
    var context =
        new OptimizationContext(
            methodProcessor, methodProcessingContext, code, it, atomicUpdaterFields);
    var changed = false;
    while (it.hasNext()) {
      var next = it.nextUntil(Instruction::isInvokeVirtual);
      if (next == null) {
        continue;
      }
      var invoke = next.asInvokeVirtual();
      assert invoke != null;
      DexMethod invokedMethod = invoke.getInvokedMethod();
      DexType invocationHolder = invokedMethod.holder;
      if (!(invocationHolder.isIdenticalTo(
              dexItemFactory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater)
          || invocationHolder.isIdenticalTo(
              dexItemFactory.javaUtilConcurrentAtomicAtomicIntegerFieldUpdater)
          || invocationHolder.isIdenticalTo(
              dexItemFactory.javaUtilConcurrentAtomicAtomicLongFieldUpdater))) {
        continue;
      }

      // TODO(b/453628974): implement and test optimization under handlers.
      if (invoke.getBlock().hasCatchHandlers()) {
        reportInfo(appView, new Event.CannotOptimize(invoke), Reason.UNDER_CATCH_HANDLER);
        continue;
      }

      // If this assert fails then check these things before updating the assert:
      //   * Check if the below AtomicReferenceFieldUpdater methods have changed implementation.
      //     * If so, verify/correct the static checks to match the runtime checks.
      assert AndroidApiLevel.LATEST.isEqualTo(AndroidApiLevel.CINNAMON_BUN);

      if (invokedMethod.isIdenticalTo(dexItemFactory.atomicReferenceUpdaterMethods.compareAndSet)) {
        if (visitCompareAndSet(
            context, invoke, dexItemFactory.sunMiscUnsafeMethods.compareAndSwapObject)) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(dexItemFactory.atomicReferenceUpdaterMethods.get)) {
        if (visitGet(context, invoke, dexItemFactory.sunMiscUnsafeMethods.getObjectVolatile)) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(dexItemFactory.atomicReferenceUpdaterMethods.set)) {
        if (visitSet(context, invoke, dexItemFactory.sunMiscUnsafeMethods.putObjectVolatile)) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(
          dexItemFactory.atomicReferenceUpdaterMethods.getAndSet)) {
        if (visitGetAndSet(context, invoke)) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(
          dexItemFactory.atomicIntUpdaterMethods.compareAndSet)) {
        if (visitCompareAndSet(
            context, invoke, dexItemFactory.sunMiscUnsafeMethods.compareAndSwapInt)) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(dexItemFactory.atomicIntUpdaterMethods.get)) {
        if (visitGet(context, invoke, dexItemFactory.sunMiscUnsafeMethods.getIntVolatile)) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(dexItemFactory.atomicIntUpdaterMethods.set)) {
        if (visitSet(context, invoke, dexItemFactory.sunMiscUnsafeMethods.putIntVolatile)) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(
          dexItemFactory.atomicLongUpdaterMethods.compareAndSet)) {
        if (visitCompareAndSet(
            context, invoke, dexItemFactory.sunMiscUnsafeMethods.compareAndSwapLong)) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(dexItemFactory.atomicLongUpdaterMethods.get)) {
        if (visitGet(context, invoke, dexItemFactory.sunMiscUnsafeMethods.getLongVolatile)) {
          changed = true;
        }
      } else if (invokedMethod.isIdenticalTo(dexItemFactory.atomicLongUpdaterMethods.set)) {
        if (visitSet(context, invoke, dexItemFactory.sunMiscUnsafeMethods.putLongVolatile)) {
          changed = true;
        }
      } else {
        reportInfo(appView, new Event.CannotOptimize(invoke), Reason.NOT_SUPPORTED);
      }
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  /** Returns true if {@code invoke} was rewritten. */
  private boolean visitCompareAndSet(
      OptimizationContext context, InvokeVirtual invoke, DexMethod target) {
    var resolvedUpdater = resolveUpdater(context, invoke);
    if (resolvedUpdater == null) {
      return false;
    }

    var expectedHolder = resolvedUpdater.updaterFieldInfo.holder;
    var resolvedHolder = resolveHolder(invoke, expectedHolder);
    if (resolvedHolder == null) {
      return false;
    }

    var expectValue = invoke.getSecondNonReceiverArgument();

    var updateValue = invoke.getThirdNonReceiverArgument();
    if (!isNewValueValid(resolvedUpdater, updateValue, invoke)) {
      return false;
    }

    reportInfo(
        appView,
        new Event.CanOptimize(invoke, resolvedUpdater.isNullable, resolvedHolder.isNullable));
    rewriteCompareAndSet(
        context, invoke, resolvedUpdater, resolvedHolder, expectValue, updateValue, target);
    return true;
  }

  /** Returns true if {@code invoke} was rewritten. */
  private boolean visitGet(OptimizationContext context, InvokeVirtual invoke, DexMethod target) {
    var resolvedUpdater = resolveUpdater(context, invoke);
    if (resolvedUpdater == null) {
      return false;
    }

    var expectedHolder = resolvedUpdater.updaterFieldInfo.holder;
    var resolvedHolder = resolveHolder(invoke, expectedHolder);
    if (resolvedHolder == null) {
      return false;
    }

    reportInfo(
        appView,
        new Event.CanOptimize(invoke, resolvedUpdater.isNullable, resolvedHolder.isNullable));
    rewriteGet(context, invoke, resolvedUpdater, resolvedHolder, target);
    return true;
  }

  /** Returns true if {@code invoke} was rewritten. */
  private boolean visitSet(OptimizationContext context, InvokeVirtual invoke, DexMethod target) {
    var resolvedUpdater = resolveUpdater(context, invoke);
    if (resolvedUpdater == null) {
      return false;
    }

    var expectedHolder = resolvedUpdater.updaterFieldInfo.holder;
    var resolvedHolder = resolveHolder(invoke, expectedHolder);
    if (resolvedHolder == null) {
      return false;
    }

    var newValueValue = invoke.getSecondNonReceiverArgument();
    if (!isNewValueValid(resolvedUpdater, newValueValue, invoke)) {
      return false;
    }

    reportInfo(
        appView,
        new Event.CanOptimize(invoke, resolvedUpdater.isNullable, resolvedHolder.isNullable));
    rewriteSet(context, invoke, resolvedUpdater, resolvedHolder, newValueValue, target);
    return true;
  }

  /** Returns true if {@code invoke} was rewritten. */
  private boolean visitGetAndSet(OptimizationContext context, InvokeVirtual invoke) {
    var resolvedUpdater = resolveUpdater(context, invoke);
    if (resolvedUpdater == null) {
      return false;
    }

    var expectedHolder = resolvedUpdater.updaterFieldInfo.holder;
    var resolvedHolder = resolveHolder(invoke, expectedHolder);
    if (resolvedHolder == null) {
      return false;
    }

    var newValueValue = invoke.getSecondNonReceiverArgument();
    if (!isNewValueValid(resolvedUpdater, newValueValue, invoke)) {
      return false;
    }

    reportInfo(
        appView,
        new Event.CanOptimize(invoke, resolvedUpdater.isNullable, resolvedHolder.isNullable));
    rewriteGetAndSet(context, invoke, resolvedUpdater, resolvedHolder, newValueValue);
    return true;
  }

  /** Returns null if the updater cannot be resolved. */
  private ResolvedUpdater resolveUpdater(OptimizationContext context, InvokeVirtual invoke) {
    var updaterValue = invoke.getReceiver();
    var updaterMightBeNull = updaterValue.getType().isNullable();
    DexField updaterField;
    var updaterAbstractValue =
        updaterValue.getAbstractValue(appView, context.code.context()).removeNullOrAbstractValue();
    if (updaterAbstractValue.isSingleFieldValue()) {
      updaterField = updaterAbstractValue.asSingleFieldValue().getField();
    } else {
      reportInfo(
          appView,
          new Event.CannotOptimize(invoke),
          new Reason.StaticallyUnclearUpdater(updaterAbstractValue));
      return null;
    }
    var updaterInfo = context.instrumentations.get(updaterField);
    if (updaterInfo == null) {
      reportInfo(appView, new Event.CannotOptimize(invoke), Reason.UPDATER_NOT_INSTRUMENTED);
      return null;
    }
    return new ResolvedUpdater(updaterMightBeNull, updaterInfo, updaterValue);
  }

  private static class ResolvedUpdater {

    final boolean isNullable;
    final AtomicFieldUpdaterInfo updaterFieldInfo;
    final Value value;

    private ResolvedUpdater(
        boolean isNullable, AtomicFieldUpdaterInfo updaterFieldInfo, Value value) {
      this.isNullable = isNullable;
      this.updaterFieldInfo = updaterFieldInfo;
      this.value = value;
    }
  }

  /** Returns null if the holder cannot be resolved. */
  private ResolvedHolder resolveHolder(InvokeVirtual invoke, DexType expectedHolder) {
    Value holderValue = invoke.getFirstNonReceiverArgument();
    TypeElement holderType = holderValue.getType();
    TypeElement expectedHolderType = expectedHolder.toTypeElement(appView);
    if (!holderType.lessThanOrEqual(expectedHolderType, appView)) {
      reportInfo(
          appView,
          new Event.CannotOptimize(invoke),
          new Reason.WrongHolderType(holderType, expectedHolderType));
      return null;
    }
    var isNullable = holderType.isNullable();
    return new ResolvedHolder(isNullable, holderValue);
  }

  private static class ResolvedHolder {

    final boolean isNullable;
    final Value value;

    private ResolvedHolder(boolean isNullable, Value value) {
      this.isNullable = isNullable;
      this.value = value;
    }
  }

  private boolean isNewValueValid(
      ResolvedUpdater resolvedUpdater, Value newValueValue, InvokeMethod invokeForLogging) {
    var expectedType = resolvedUpdater.updaterFieldInfo.reflectedFieldType.toTypeElement(appView);
    TypeElement newValueValueType = newValueValue.getType();
    if (!newValueValueType.lessThanOrEqual(expectedType, appView)) {
      reportInfo(
          appView,
          new Event.CannotOptimize(invokeForLogging),
          new Reason.WrongValueType(newValueValueType, expectedType));
      return false;
    }
    return true;
  }

  /**
   * Rewrites {@code updater.compareAndSet(holder, expect, update)} into {@code
   * SyntheticUnsafeClass.unsafe.<target>(holder, this.offsetField, expect, update)}.
   */
  private void rewriteCompareAndSet(
      OptimizationContext context,
      InvokeVirtual invoke,
      ResolvedUpdater resolvedUpdater,
      ResolvedHolder resolvedHolder,
      Value expectValue,
      Value updateValue,
      DexMethod target) {
    var position = invoke.getPosition();
    var instructions = new ArrayList<Instruction>(4);

    if (resolvedUpdater.isNullable) {
      instructions.add(createNullCheck(context, position, resolvedUpdater.value));
    }

    if (resolvedHolder.isNullable) {
      instructions.add(
          createNullCheckWithClassCastException(context, position, resolvedHolder.value));
    }

    Instruction unsafeInstance = createUnsafeGet(context, position);
    instructions.add(unsafeInstance);

    Instruction offset =
        createOffsetGet(context, position, resolvedUpdater.updaterFieldInfo.offsetField);
    instructions.add(offset);

    // Add instructions BEFORE the compareAndSet instruction.
    insertInstructionsBeforeCurrentInstruction(context.it, instructions);

    // Call underlying unsafe method.
    Instruction unsafeCompareAndSet =
        new InvokeVirtual(
            target,
            invoke.outValue(),
            ImmutableList.of(
                unsafeInstance.outValue(),
                resolvedHolder.value,
                offset.outValue(),
                expectValue,
                updateValue));
    unsafeCompareAndSet.setPosition(position);
    context.it.replaceCurrentInstruction(unsafeCompareAndSet);
  }

  /**
   * Rewrites {@code updater.get(holder)} into {@code SyntheticUnsafeClass.unsafe.<target>(holder,
   * this.offset)}.
   */
  private void rewriteGet(
      OptimizationContext context,
      InvokeVirtual invoke,
      ResolvedUpdater resolvedUpdater,
      ResolvedHolder resolvedHolder,
      DexMethod target) {
    var position = invoke.getPosition();
    var instructions = new ArrayList<Instruction>(4);

    if (resolvedUpdater.isNullable) {
      instructions.add(createNullCheck(context, position, resolvedUpdater.value));
    }

    if (resolvedHolder.isNullable) {
      instructions.add(
          createNullCheckWithClassCastException(context, position, resolvedHolder.value));
    }

    Instruction unsafeInstance = createUnsafeGet(context, position);
    instructions.add(unsafeInstance);

    Instruction offset =
        createOffsetGet(context, position, resolvedUpdater.updaterFieldInfo.offsetField);
    instructions.add(offset);

    // Add instructions BEFORE the get instruction.
    insertInstructionsBeforeCurrentInstruction(context.it, instructions);

    // Call underlying unsafe method.
    Instruction unsafeGet =
        new InvokeVirtual(
            target,
            invoke.outValue(),
            ImmutableList.of(unsafeInstance.outValue(), resolvedHolder.value, offset.outValue()));
    unsafeGet.setPosition(position);
    context.it.replaceCurrentInstruction(unsafeGet);
  }

  /**
   * Rewrites {@code updater.set(holder, newValue)} into {@code
   * SyntheticUnsafeClass.unsafe.<target>(holder, this.offset, newValue)}.
   */
  private void rewriteSet(
      OptimizationContext context,
      InvokeVirtual invoke,
      ResolvedUpdater resolvedUpdater,
      ResolvedHolder resolvedHolder,
      Value newValueValue,
      DexMethod target) {
    var position = invoke.getPosition();
    var instructions = new ArrayList<Instruction>(4);

    if (resolvedUpdater.isNullable) {
      instructions.add(createNullCheck(context, position, resolvedUpdater.value));
    }

    if (resolvedHolder.isNullable) {
      instructions.add(
          createNullCheckWithClassCastException(context, position, resolvedHolder.value));
    }

    Instruction unsafeInstance = createUnsafeGet(context, position);
    instructions.add(unsafeInstance);

    Instruction offset =
        createOffsetGet(context, position, resolvedUpdater.updaterFieldInfo.offsetField);
    instructions.add(offset);

    // Add instructions BEFORE the get instruction.
    insertInstructionsBeforeCurrentInstruction(context.it, instructions);

    // Call underlying unsafe method.
    Instruction unsafeSet =
        new InvokeVirtual(
            target,
            invoke.outValue(),
            ImmutableList.of(
                unsafeInstance.outValue(), resolvedHolder.value, offset.outValue(), newValueValue));
    unsafeSet.setPosition(position);
    context.it.replaceCurrentInstruction(unsafeSet);
  }

  /**
   * Rewrites {@code updater.getAndSet(holder, newValue)} into {@code
   * SyntheticUnsafeClass.unsafe.getAndSetObject(holder, this.offset, newValue)}.
   */
  private void rewriteGetAndSet(
      OptimizationContext context,
      InvokeVirtual invoke,
      ResolvedUpdater resolvedUpdater,
      ResolvedHolder resolvedHolder,
      Value newValueValue) {
    var position = invoke.getPosition();

    var instructions = new ArrayList<Instruction>(4);

    if (resolvedUpdater.isNullable) {
      instructions.add(createNullCheck(context, position, resolvedUpdater.value));
    }

    if (resolvedHolder.isNullable) {
      instructions.add(
          createNullCheckWithClassCastException(context, position, resolvedHolder.value));
    }

    boolean isGetAndSetDefined =
        !appView.options().isGeneratingDex()
            || appView.options().getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N);
    Instruction unsafeInstance;
    if (isGetAndSetDefined) {
      unsafeInstance = createUnsafeGet(context, position);
      instructions.add(unsafeInstance);
    } else {
      unsafeInstance = null;
    }

    Instruction offset =
        createOffsetGet(context, position, resolvedUpdater.updaterFieldInfo.offsetField);
    instructions.add(offset);

    // Add instructions BEFORE the get instruction.
    insertInstructionsBeforeCurrentInstruction(context.it, instructions);

    // Call underlying unsafe method directly or backport if necessary.
    Instruction getAndSet;
    if (isGetAndSetDefined) {
      DexMethod unsafeGetAndSetMethod =
          dexItemFactory.createMethod(
              dexItemFactory.sunMiscUnsafeType,
              dexItemFactory.createProto(
                  dexItemFactory.objectType,
                  dexItemFactory.objectType,
                  dexItemFactory.longType,
                  dexItemFactory.objectType),
              "getAndSetObject");
      getAndSet =
          new InvokeVirtual(
              unsafeGetAndSetMethod,
              invoke.outValue(),
              ImmutableList.of(
                  unsafeInstance.outValue(),
                  resolvedHolder.value,
                  offset.outValue(),
                  newValueValue));
    } else {
      DexMethod backportedGetAndSet = appView.getSyntheticUnsafeClass().getGetAndSetMethod();
      getAndSet =
          new InvokeStatic(
              backportedGetAndSet,
              invoke.outValue(),
              ImmutableList.of(
                  resolvedHolder.value,
                  offset.outValue(),
                  newValueValue));
      context
          .methodProcessor
          .getEventConsumer()
          .acceptUnsafeGetAndSetContext(backportedGetAndSet, context.code.context());
    }
    getAndSet.setPosition(position);
    context.it.replaceCurrentInstruction(getAndSet);
  }

  /** Returns an instruction to retrieve offsetField and updates the profile correspondingly. */
  private Instruction createOffsetGet(
      OptimizationContext context, Position position, DexField offsetField) {
    assert offsetField.type.isIdenticalTo(dexItemFactory.longType);
    Instruction offset =
        new StaticGet(
            context.code.createValue(dexItemFactory.longType.toTypeElement(appView)), offsetField);
    offset.setPosition(position);
    // Update profile on the behalf of the offset initialization in clinit.
    context
        .methodProcessor
        .getEventConsumer()
        .acceptUnsafeInstanceContext(
            appView.getSyntheticUnsafeClass(),
            offsetField.holder.asProgramClass(appView).getProgramClassInitializer());
    return offset;
  }

  private InvokeVirtual createNullCheck(
      OptimizationContext context, Position position, Value value) {
    var nullCheck =
        new InvokeVirtual(
            dexItemFactory.objectMembers.getClass,
            context.code.createValue(dexItemFactory.classType.toTypeElement(appView)),
            ImmutableList.of(value));
    nullCheck.setPosition(position);
    return nullCheck;
  }

  private InvokeStatic createNullCheckWithClassCastException(
      OptimizationContext context, Position position, Value value) {
    var optimizations =
        UtilityMethodsForCodeOptimizations.synthesizeThrowClassCastExceptionIfNullMethod(
            appView, context.methodProcessor.getEventConsumer(), context.methodProcessingContext);
    optimizations.optimize(context.methodProcessor);
    InvokeStatic invokeStatic =
        new InvokeStatic(optimizations.getMethod().getReference(), null, ImmutableList.of(value));
    invokeStatic.setPosition(position);
    return invokeStatic;
  }

  /**
   * Returns an instruction to retrieve an unsafe instance and updates the profile correspondingly.
   */
  private Instruction createUnsafeGet(OptimizationContext context, Position position) {
    assert appView
        .getSyntheticUnsafeClass()
        .getInstanceField()
        .getType()
        .isIdenticalTo(dexItemFactory.sunMiscUnsafeType);
    Instruction unsafeInstance =
        new StaticGet(
            context.code.createValue(dexItemFactory.sunMiscUnsafeType.toTypeElement(appView)),
            appView.getSyntheticUnsafeClass().getInstanceField());
    unsafeInstance.setPosition(position);
    context
        .methodProcessor
        .getEventConsumer()
        .acceptUnsafeInstanceContext(appView.getSyntheticUnsafeClass(), context.code.context());
    return unsafeInstance;
  }

  private void insertInstructionsBeforeCurrentInstruction(
      IRCodeInstructionListIterator it, ArrayList<Instruction> instructions) {
    it.previous();
    it.addAll(instructions);
    it.next();
  }

  /**
   * Creation information about an AtomicReferenceFieldUpdater.
   *
   * <blockquote>
   *
   * <pre>
   * class holder {
   *   volatile reflectedFieldType reflectedField;
   *   static final AtomicReferenceFieldUpdater updater = AtomicReferenceFieldUpdater.newUpdater(holder.class, reflectedFieldType, "reflectedField");
   *   public static final long offsetField = ..
   * }
   * </pre>
   *
   * </blockquote>
   */
  public static class AtomicFieldUpdaterInfo {

    public final DexType holder;

    /**
     * A reflected type of the primitives int or long indicate an Atomic(Integer|Long)FieldUpdater.
     */
    public final DexType reflectedFieldType;

    public final DexField offsetField;

    public AtomicFieldUpdaterInfo(
        DexType holder, DexType reflectedFieldType, DexField offsetField) {
      this.holder = holder;
      this.reflectedFieldType = reflectedFieldType;
      this.offsetField = offsetField;
    }
  }

  private static class OptimizationContext {

    final MethodProcessor methodProcessor;
    final MethodProcessingContext methodProcessingContext;
    final IRCode code;
    final IRCodeInstructionListIterator it;
    final Map<DexField, AtomicFieldUpdaterInfo> instrumentations;

    public OptimizationContext(
        MethodProcessor methodProcessor,
        MethodProcessingContext methodProcessingContext,
        IRCode code,
        IRCodeInstructionListIterator it,
        Map<DexField, AtomicFieldUpdaterInfo> instrumentations) {
      this.methodProcessor = methodProcessor;
      this.methodProcessingContext = methodProcessingContext;
      this.code = code;
      this.it = it;
      this.instrumentations = instrumentations;
    }
  }
}

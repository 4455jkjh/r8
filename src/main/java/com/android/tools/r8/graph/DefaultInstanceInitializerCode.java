// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.dex.code.DexInvokeDirect;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.ir.conversion.SyntheticStraightLineSourceCode;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirEncodingStrategy;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.IteratorUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.google.common.collect.ImmutableList;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * A piece of code on the form:
 *
 * <pre>
 *   aload_0
 *   invoke-special LSuperClass;-><init>()V
 *   return
 * </pre>
 *
 * <p>Note that (i) {@code SuperClass} may be different from {@link java.lang.Object} and (ii) the
 * method holding this code object may have a non-empty proto.
 */
public class DefaultInstanceInitializerCode extends Code
    implements CfWritableCode, DexWritableCode {

  private static final DefaultInstanceInitializerCode INSTANCE =
      new DefaultInstanceInitializerCode();

  private DefaultInstanceInitializerCode() {}

  public static DefaultInstanceInitializerCode get() {
    return INSTANCE;
  }

  public static boolean canonicalizeCodeIfPossible(AppView<?> appView, ProgramMethod method) {
    if (hasDefaultInstanceInitializerCode(method, appView)) {
      method.setCode(get(), appView);
      return true;
    }
    return false;
  }

  public static void uncanonicalizeCode(AppView<?> appView, ProgramMethod method) {
    uncanonicalizeCode(appView, method, method.getHolder().getSuperType());
  }

  public static void uncanonicalizeCode(
      AppView<?> appView, ProgramMethod method, DexType superType) {
    DexEncodedMethod definition = method.getDefinition();
    assert definition.getCode().isDefaultInstanceInitializerCode();
    Position position = method.getDefinition().getAndClearPendingInlineFrameAsPosition();
    Code newCode;
    if (appView.testing().isSupportedLirPhase()) {
      newCode = get().toLirCode(appView, method, superType, position);
    } else {
      assert appView.testing().isPreLirPhase();
      newCode = get().toCfCode(method, appView.dexItemFactory(), superType, position);
    }
    method.setCode(newCode, appView);
  }

  @Override
  public boolean supportsPendingInlineFrame() {
    return true;
  }

  private static boolean hasDefaultInstanceInitializerCode(
      ProgramMethod method, AppView<?> appView) {
    if (!method.getDefinition().isInstanceInitializer()) {
      return false;
    }
    Code code = method.getDefinition().getCode();
    if (!code.isCfCode()) {
      return false;
    }
    CfCode cfCode = code.asCfCode();
    if (!method.getDefinition().isInstanceInitializer()
        || !cfCode.getLocalVariables().isEmpty()
        || !cfCode.getTryCatchRanges().isEmpty()) {
      return false;
    }
    if (cfCode.getInstructionCount() > 6) {
      // Default instance initializers typically have the following instruction sequence:
      // [CfLabel, CfPosition, CfLoad, CfInvoke, CfReturnVoid, CfLabel].
      return false;
    }
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    Iterator<CfInstruction> instructionIterator = cfCode.getInstructions().iterator();
    // Allow skipping CfPosition instructions in instance initializers that only call Object.<init>.
    Predicate<CfInstruction> instructionOfInterest =
        method.getHolder().getSuperType().isIdenticalTo(dexItemFactory.objectType)
            ? instruction -> !instruction.isLabel() && !instruction.isPosition()
            : instruction -> !instruction.isLabel();
    CfLoad load = IteratorUtils.nextUntil(instructionIterator, instructionOfInterest).asLoad();
    if (load == null || load.getLocalIndex() != 0) {
      return false;
    }
    CfInvoke invoke = instructionIterator.next().asInvoke();
    if (invoke == null
        || !invoke.isInvokeConstructor(dexItemFactory)
        || invoke.getMethod().isNotIdenticalTo(getParentConstructor(method, dexItemFactory))) {
      return false;
    }
    return instructionIterator.next().isReturnVoid();
  }

  @Override
  public Code asCode() {
    return this;
  }

  @Override
  public void acceptHashing(HashingVisitor visitor) {
    visitor.visitInt(getCfWritableCodeKind().hashCode());
  }

  @Override
  public IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      MutableMethodConversionOptions conversionOptions) {
    DefaultInstanceInitializerSourceCode source =
        new DefaultInstanceInitializerSourceCode(
            method.getReference(),
            method.getDefinition().isD8R8Synthesized(),
            method.getDefinition().getPendingInlineFrameAsPosition());
    return IRBuilder.create(method, appView, source).build(method, conversionOptions);
  }

  @Override
  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      RewrittenPrototypeDescription protoChanges) {
    DefaultInstanceInitializerSourceCode source =
        new DefaultInstanceInitializerSourceCode(
            method.getReference(),
            method.getDefinition().isD8R8Synthesized(),
            method
                .getDefinition()
                .getPendingInlineFrameAsPositionWithCallerPosition(callerPosition));
    return IRBuilder.createForInlining(
            method, appView, codeLens, source, valueNumberGenerator, protoChanges)
        .build(context, MethodConversionOptions.nonConverting());
  }

  @Override
  public int codeSizeInBytes() {
    return DexInvokeDirect.SIZE + DexReturnVoid.SIZE;
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      GraphLens codeLens,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    DexMethod parentConstructor = getParentConstructor(context, rewriter.dexItemFactory());
    MethodLookupResult lookupResult =
        appView.graphLens().lookupInvokeDirect(parentConstructor, context, codeLens);
    lookupResult.getReference().collectIndexedItems(appView, indexedItems);
  }

  @Override
  public void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    // Intentionally empty.
  }

  @Override
  protected int computeHashCode() {
    return System.identityHashCode(this);
  }

  @Override
  protected boolean computeEquals(Object other) {
    return this == other;
  }

  @Override
  public int estimatedDexCodeSizeUpperBoundInBytes() {
    return codeSizeInBytes();
  }

  @Override
  public CfWritableCodeKind getCfWritableCodeKind() {
    return CfWritableCodeKind.DEFAULT_INSTANCE_INITIALIZER;
  }

  @Override
  public DexWritableCodeKind getDexWritableCodeKind() {
    return DexWritableCodeKind.DEFAULT_INSTANCE_INITIALIZER;
  }

  @Override
  public DexDebugInfoForWriting getDebugInfoForWriting() {
    return null;
  }

  @Override
  public int getEstimatedSizeForInliningIfLessThanOrEquals(int threshold) {
    int estimatedSizeForInlining = estimatedDexCodeSizeUpperBoundInBytes();
    if (estimatedSizeForInlining <= threshold) {
      return estimatedSizeForInlining;
    }
    return -1;
  }

  @Override
  public TryHandler[] getHandlers() {
    return TryHandler.EMPTY_ARRAY;
  }

  @Override
  public DexString getHighestSortingString() {
    return null;
  }

  @Override
  public int getIncomingRegisterSize(ProgramMethod method) {
    return getMaxLocals(method);
  }

  public static DexMethod getParentConstructor(
      DexClassAndMethod method, DexItemFactory dexItemFactory) {
    return dexItemFactory.createInstanceInitializer(method.getHolder().getSuperType());
  }

  private int getMaxLocals(ProgramMethod method) {
    int maxLocals = method.getAccessFlags().isStatic() ? 0 : 1;
    for (DexType parameter : method.getParameters()) {
      maxLocals += parameter.getRequiredRegisters();
    }
    return maxLocals;
  }

  private int getMaxStack() {
    return 1;
  }

  @Override
  public int getOutgoingRegisterSize() {
    return 1;
  }

  @Override
  public int getRegisterSize(ProgramMethod method) {
    return getIncomingRegisterSize(method);
  }

  @Override
  public Try[] getTries() {
    return Try.EMPTY_ARRAY;
  }

  @Override
  public boolean isCfWritableCode() {
    return true;
  }

  @Override
  public CfWritableCode asCfWritableCode() {
    return this;
  }

  @Override
  public boolean isDexWritableCode() {
    return true;
  }

  @Override
  public DexWritableCode asDexWritableCode() {
    return this;
  }

  @Override
  public boolean isEmptyVoidMethod() {
    return false;
  }

  @Override
  public boolean isDefaultInstanceInitializerCode() {
    return true;
  }

  @Override
  public DefaultInstanceInitializerCode asDefaultInstanceInitializerCode() {
    return this;
  }

  @Override
  public boolean isSharedCodeObject() {
    return true;
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    internalRegisterCodeReferences(method, registry);
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    internalRegisterCodeReferences(method, registry);
  }

  private void internalRegisterCodeReferences(DexClassAndMethod method, UseRegistry<?> registry) {
    registry.registerInvokeDirect(getParentConstructor(method, registry.dexItemFactory()));
  }

  @Override
  public DexWritableCode rewriteCodeWithJumboStrings(
      ProgramMethod method, ObjectToOffsetMapping mapping, AppView<?> appView, boolean force) {
    // Intentionally empty. This piece of code does not have any const-string instructions.
    return this;
  }

  @Override
  public void setCallSiteContexts(ProgramMethod method) {
    // Intentionally empty. This piece of code does not have any call sites.
  }

  public CfCode toCfCode(
      ProgramMethod method, DexItemFactory dexItemFactory, DexType supertype, Position position) {
    List<CfInstruction> instructions = new ArrayList<>(position != null ? 5 : 3);
    if (position != null) {
      CfLabel entryLabel = new CfLabel();
      instructions.add(entryLabel);
      instructions.add(new CfPosition(entryLabel, position));
    }
    instructions.add(new CfLoad(ValueType.OBJECT, 0));
    instructions.add(
        new CfInvoke(
            Opcodes.INVOKESPECIAL, dexItemFactory.createInstanceInitializer(supertype), false));
    instructions.add(new CfReturnVoid());
    return new CfCode(method.getHolderType(), getMaxStack(), getMaxLocals(method), instructions);
  }

  public LirCode<?> toLirCode(
      AppView<?> appView, ProgramMethod method, DexType supertype, Position position) {
    TypeElement receiverType =
        method.getHolder().getType().toTypeElement(appView, Nullability.definitelyNotNull());
    Value receiver = new Value(0, receiverType, null);
    DexMethod invokedMethod = appView.dexItemFactory().createInstanceInitializer(supertype);
    LirEncodingStrategy<Value, Integer> strategy =
        LirStrategy.getDefaultStrategy().getEncodingStrategy();
    strategy.defineValue(receiver, 0);
    LirBuilder<Value, Integer> builder =
        LirCode.builder(
            method.getReference(),
            method.getDefinition().isD8R8Synthesized(),
            strategy,
            appView.options());
    if (position != null) {
      builder.setCurrentPosition(position);
    }
    return builder
        .addArgument(0, false)
        .addInvokeDirect(invokedMethod, ImmutableList.of(receiver), false)
        .addReturnVoid()
        .build();
  }

  @Override
  public void writeCf(
      ProgramMethod method,
      CfVersion classFileVersion,
      AppView<?> appView,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    visitor.visitVarInsn(Opcodes.ALOAD, 0);
    visitor.visitMethodInsn(
        Opcodes.INVOKESPECIAL,
        namingLens.lookupInternalName(method.getHolder().getSuperType()),
        "<init>",
        "()V",
        false);
    visitor.visitInsn(Opcodes.RETURN);
    visitor.visitEnd();
    visitor.visitMaxs(getMaxStack(), getMaxLocals(method));
  }

  @Override
  public void writeDex(
      ShortBuffer shortBuffer,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      LensCodeRewriterUtils lensCodeRewriter,
      ObjectToOffsetMapping mapping) {
    DexMethod parentConstructor = getParentConstructor(context, mapping.dexItemFactory());
    new DexInvokeDirect(1, parentConstructor, 0, 0, 0, 0, 0)
        .write(shortBuffer, context, graphLens, codeLens, mapping, lensCodeRewriter);
    new DexReturnVoid().write(shortBuffer, context, graphLens, codeLens, mapping, lensCodeRewriter);
  }

  @Override
  public String toString() {
    return "DefaultInstanceInitializerCode";
  }

  @Override
  public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
    return toString();
  }

  @Override
  public DexWritableCacheKey getCacheLookupKey(ProgramMethod method, DexItemFactory factory) {
    return new AmendedDexWritableCodeKey<>(
        this,
        getParentConstructor(method, factory),
        getIncomingRegisterSize(method),
        getRegisterSize(method));
  }

  static class DefaultInstanceInitializerSourceCode extends SyntheticStraightLineSourceCode {

    DefaultInstanceInitializerSourceCode(
        DexMethod method, boolean isD8R8Synthesized, Position callerPosition) {
      super(getInstructionBuilders(), getPosition(method, isD8R8Synthesized, callerPosition));
    }

    private static Position getPosition(
        DexMethod method, boolean isD8R8Synthesized, Position callerPosition) {
      SyntheticPosition calleePosition =
          SyntheticPosition.builder()
              .setLine(0)
              .setMethod(method)
              .setIsD8R8Synthesized(isD8R8Synthesized)
              .build();
      return callerPosition == null
          ? calleePosition
          : Code.newInlineePosition(callerPosition, calleePosition, isD8R8Synthesized);
    }

    private static List<Consumer<IRBuilder>> getInstructionBuilders() {
      return ImmutableList.of(
          builder ->
              builder.add(
                  InvokeDirect.builder()
                      .setMethod(
                          getParentConstructor(
                              builder.getProgramMethod(), builder.dexItemFactory()))
                      .setSingleArgument(builder.getReceiverValue())
                      .build()),
          IRBuilder::addReturn);
    }
  }
}

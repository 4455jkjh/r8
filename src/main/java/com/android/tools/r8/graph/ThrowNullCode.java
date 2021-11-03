// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.Throw;
import com.android.tools.r8.dex.CodeToKeep;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.SourceCode;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ConsumerUtils;
import com.google.common.collect.ImmutableList;
import java.nio.ShortBuffer;
import java.util.List;
import java.util.function.Consumer;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

public class ThrowNullCode extends Code implements CfWritableCode, DexWritableCode {

  private static final ThrowNullCode INSTANCE = new ThrowNullCode();

  private ThrowNullCode() {}

  public static ThrowNullCode get() {
    return INSTANCE;
  }

  @Override
  public IRCode buildIR(ProgramMethod method, AppView<?> appView, Origin origin) {
    ThrowNullSourceCode source = new ThrowNullSourceCode(method);
    return IRBuilder.create(method, appView, source, origin).build(method);
  }

  @Override
  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      RewrittenPrototypeDescription protoChanges) {
    ThrowNullSourceCode source = new ThrowNullSourceCode(method, callerPosition);
    return IRBuilder.createForInlining(
            method, appView, codeLens, source, origin, valueNumberGenerator, protoChanges)
        .build(context);
  }

  @Override
  public int codeSizeInBytes() {
    return Const4.SIZE + Throw.SIZE;
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
  public DexDebugInfoForWriting getDebugInfoForWriting() {
    return null;
  }

  @Override
  public TryHandler[] getHandlers() {
    return new TryHandler[0];
  }

  @Override
  public DexString getHighestSortingString() {
    return null;
  }

  @Override
  public int getIncomingRegisterSize(ProgramMethod method) {
    return getMaxLocals(method);
  }

  private int getMaxLocals(ProgramMethod method) {
    int maxLocals = method.getAccessFlags().isStatic() ? 0 : 1;
    for (DexType parameter : method.getParameters()) {
      maxLocals += parameter.getRequiredRegisters();
    }
    return maxLocals;
  }

  @Override
  public int getOutgoingRegisterSize() {
    return 0;
  }

  @Override
  public int getRegisterSize(ProgramMethod method) {
    return Math.max(getIncomingRegisterSize(method), 1);
  }

  @Override
  public Try[] getTries() {
    return new Try[0];
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
  public boolean isSharedCodeObject() {
    return true;
  }

  @Override
  public boolean isThrowNullCode() {
    return true;
  }

  @Override
  public ThrowNullCode asThrowNullCode() {
    return this;
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    // Intentionally empty.
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    // Intentionally empty.
  }

  @Override
  public DexWritableCode rewriteCodeWithJumboStrings(
      ProgramMethod method, ObjectToOffsetMapping mapping, DexItemFactory factory, boolean force) {
    // Intentionally empty. This piece of code does not have any const-string instructions.
    return this;
  }

  @Override
  public void setCallSiteContexts(ProgramMethod method) {
    // Intentionally empty. This piece of code does not have any call sites.
  }

  @Override
  public void writeCf(
      ProgramMethod method,
      CfVersion classFileVersion,
      AppView<?> appView,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    int maxStack = 1;
    visitor.visitInsn(Opcodes.ACONST_NULL);
    visitor.visitInsn(Opcodes.ATHROW);
    visitor.visitEnd();
    visitor.visitMaxs(maxStack, getMaxLocals(method));
  }

  @Override
  public void writeDex(
      ShortBuffer shortBuffer,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils lensCodeRewriter,
      ObjectToOffsetMapping mapping) {
    int register = 0;
    new Const4(register, 0).write(shortBuffer, context, graphLens, mapping, lensCodeRewriter);
    new Throw(register).write(shortBuffer, context, graphLens, mapping, lensCodeRewriter);
  }

  @Override
  public void writeKeepRulesForDesugaredLibrary(CodeToKeep codeToKeep) {
    // Intentionally empty.
  }

  @Override
  public String toString() {
    return "ThrowNullCode";
  }

  @Override
  public String toString(DexEncodedMethod method, ClassNameMapper naming) {
    return "ThrowNullCode";
  }

  static class ThrowNullSourceCode implements SourceCode {

    private static final List<Consumer<IRBuilder>> instructionBuilders =
        ImmutableList.of(builder -> builder.addNullConst(0), builder -> builder.addThrow(0));

    private final ProgramMethod method;
    private final Position position;

    ThrowNullSourceCode(ProgramMethod method) {
      this(method, null);
    }

    ThrowNullSourceCode(ProgramMethod method, Position callerPosition) {
      this.method = method;
      this.position =
          SyntheticPosition.builder()
              .setLine(0)
              .setMethod(method.getReference())
              .setCallerPosition(callerPosition)
              .build();
    }

    @Override
    public int instructionCount() {
      return instructionBuilders.size();
    }

    @Override
    public int instructionIndex(int instructionOffset) {
      return instructionOffset;
    }

    @Override
    public int instructionOffset(int instructionIndex) {
      return instructionIndex;
    }

    @Override
    public void buildPrelude(IRBuilder builder) {
      int firstArgumentRegister = 0;
      builder.buildArgumentsWithRewrittenPrototypeChanges(
          firstArgumentRegister, method.getDefinition(), ConsumerUtils.emptyBiConsumer());
    }

    @Override
    public void buildInstruction(
        IRBuilder builder, int instructionIndex, boolean firstBlockInstruction) {
      instructionBuilders.get(instructionIndex).accept(builder);
    }

    @Override
    public void buildPostlude(IRBuilder builder) {
      // Intentionally empty.
    }

    @Override
    public void clear() {
      // Intentionally empty.
    }

    @Override
    public Position getCanonicalDebugPositionAtOffset(int offset) {
      return null;
    }

    @Override
    public CatchHandlers<Integer> getCurrentCatchHandlers(IRBuilder builder) {
      return null;
    }

    @Override
    public Position getCurrentPosition() {
      return position;
    }

    @Override
    public DebugLocalInfo getIncomingLocal(int register) {
      return null;
    }

    @Override
    public DebugLocalInfo getIncomingLocalAtBlock(int register, int blockOffset) {
      return null;
    }

    @Override
    public DebugLocalInfo getOutgoingLocal(int register) {
      return null;
    }

    @Override
    public void setUp() {
      // Intentionally empty.
    }

    @Override
    public int traceInstruction(int instructionIndex, IRBuilder builder) {
      // This instruction does not close the block.
      return -1;
    }

    @Override
    public boolean verifyCurrentInstructionCanThrow() {
      return true;
    }

    @Override
    public boolean verifyRegister(int register) {
      return true;
    }

    @Override
    public void buildBlockTransfer(
        IRBuilder builder, int predecessorOffset, int successorOffset, boolean isExceptional) {
      throw new Unreachable();
    }

    @Override
    public int getMoveExceptionRegister(int instructionIndex) {
      throw new Unreachable();
    }

    @Override
    public void resolveAndBuildNewArrayFilledData(
        int arrayRef, int payloadOffset, IRBuilder builder) {
      throw new Unreachable();
    }

    @Override
    public void resolveAndBuildSwitch(
        int value, int fallthroughOffset, int payloadOffset, IRBuilder builder) {
      throw new Unreachable();
    }

    @Override
    public boolean verifyLocalInScope(DebugLocalInfo local) {
      throw new Unreachable();
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.optimize.compose.ComposeReferences;
import com.google.common.collect.Sets;
import java.util.Set;

public class ComposableBranchSimplifier {

  private final AppView<?> appView;
  private final BranchSimplifier branchSimplifier;
  private final ComposeReferences composeReferences;
  private final DexItemFactory dexItemFactory;

  private ComposableBranchSimplifier(AppView<?> appView, BranchSimplifier branchSimplifier) {
    this.appView = appView;
    this.branchSimplifier = branchSimplifier;
    this.composeReferences = appView.getComposeReferences();
    this.dexItemFactory = appView.dexItemFactory();
  }

  public static ComposableBranchSimplifier create(
      AppView<?> appView, BranchSimplifier branchSimplifier) {
    return appView.options().getComposeOptions().isSimplifyDefaultsEnabled()
        ? new ComposableBranchSimplifier(appView, branchSimplifier)
        : null;
  }

  /**
   * Simplifies the Compose defaults pattern when all default value assignments have been removed:
   *
   * <pre>
   *   composer.startDefaults()
   *   if ((changed & 1) != 0 && !composer.getDefaultsInvalid()) {
   *     composer.skipToGroupEnd()
   *   }
   *   composer.endDefaults()
   * </pre>
   */
  public boolean simplifyComposeDefaults(IRCode code, BasicBlock block) {
    AnalysisResult analysisResult = analyze(code, block);
    if (analysisResult == null) {
      return false;
    }
    analysisResult.startDefaultsInvoke.removeOrReplaceByDebugLocalRead();
    analysisResult.endDefaultsInvoke.removeOrReplaceByDebugLocalRead();
    branchSimplifier.rewriteIfToGoto(
        block, analysisResult.theIf, analysisResult.target, analysisResult.deadTarget);
    if (analysisResult.getDefaultsInvalidInvoke != null) {
      analysisResult.getDefaultsInvalidInvoke.removeOrReplaceByDebugLocalRead();
    }
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private AnalysisResult analyze(IRCode code, BasicBlock block) {
    if (!code.metadata().mayHaveInvokeMethodWithReceiver()
        || block.hasCatchHandlers()
        || code.context().isReachabilitySensitive()) {
      return null;
    }
    If theIf = block.exit().asIf();
    If firstIf = null;
    BasicBlock defaultsInvalidBlock;
    BasicBlock bypassBlock = null;
    InvokeMethodWithReceiver getDefaultsInvalidInvoke = getComposeDefaultsInvalidInvoke(theIf);
    Instruction startDefaultsSearchFrom;
    if (getDefaultsInvalidInvoke != null && getDefaultsInvalidInvoke.getBlock() == block) {
      // 1-If pattern: `(changed & 1) != 0` was folded to true.
      defaultsInvalidBlock = block;
      startDefaultsSearchFrom = getDefaultsInvalidInvoke;
    } else {
      // 2-If pattern: `theIf` checks `(changed & 1) != 0` and branches to `defaultsInvalidBlock`.
      firstIf = theIf;
      BasicBlock fallthroughBlock = firstIf.fallthroughBlock();
      BasicBlock trueTarget = firstIf.getTrueTarget();
      if (fallthroughBlock == trueTarget) {
        return null;
      }
      getDefaultsInvalidInvoke = getComposeDefaultsInvalidInvokeFromBlock(fallthroughBlock);
      if (getDefaultsInvalidInvoke != null) {
        defaultsInvalidBlock = fallthroughBlock;
        bypassBlock = trueTarget;
      } else {
        getDefaultsInvalidInvoke = getComposeDefaultsInvalidInvokeFromBlock(trueTarget);
        if (getDefaultsInvalidInvoke == null) {
          return null;
        }
        defaultsInvalidBlock = trueTarget;
        bypassBlock = fallthroughBlock;
      }
      startDefaultsSearchFrom = firstIf;
    }

    Instruction instructionBeforeIf = startDefaultsSearchFrom.getPrev();
    while (instructionBeforeIf != null
        && !instructionBeforeIf.instructionMayHaveSideEffects(appView, code.context())) {
      instructionBeforeIf = instructionBeforeIf.getPrev();
    }
    if (instructionBeforeIf == null
        || !instructionBeforeIf.isInvokeMethodWithReceiver()
        || instructionBeforeIf.hasOutValue()) {
      return null;
    }
    InvokeMethodWithReceiver startDefaultsInvoke = instructionBeforeIf.asInvokeMethodWithReceiver();
    Value composerValue = getDefaultsInvalidInvoke.getReceiver().getAliasedValue();
    if (!isComposeInvoke(
        startDefaultsInvoke,
        composeReferences.startDefaultsName,
        dexItemFactory.voidType,
        composerValue)) {
      return null;
    }

    If secondIf = defaultsInvalidBlock.exit().asIf();
    BasicBlock skipToGroupEndBlock = secondIf.targetFromCondition(0);
    BasicBlock defaultsInvalidTrueTarget = secondIf.targetFromCondition(1);
    if (!isComposeSkipToGroupEndBlock(skipToGroupEndBlock, composerValue)) {
      return null;
    }

    Set<BasicBlock> intermediateBlocks = Sets.newIdentityHashSet();
    BasicBlock endDefaultsBlock =
        followComposeGotoChain(
            skipToGroupEndBlock.getUniqueNormalSuccessor(), null, intermediateBlocks);
    if (endDefaultsBlock == null
        || endDefaultsBlock == block
        || endDefaultsBlock == defaultsInvalidBlock
        || endDefaultsBlock == skipToGroupEndBlock) {
      return null;
    }
    InvokeMethodWithReceiver endDefaultsInvoke =
        getComposeEndDefaultsInvokeFromBlock(code, endDefaultsBlock, composerValue);
    if (endDefaultsInvoke == null) {
      return null;
    }

    if (followComposeGotoChain(defaultsInvalidTrueTarget, endDefaultsBlock, intermediateBlocks)
        != endDefaultsBlock) {
      return null;
    }
    if (bypassBlock != null
        && followComposeGotoChain(bypassBlock, endDefaultsBlock, intermediateBlocks)
            != endDefaultsBlock) {
      return null;
    }

    for (BasicBlock intermediateBlock : intermediateBlocks) {
      if (!verifyComposePredecessors(
          intermediateBlock,
          block,
          defaultsInvalidBlock,
          skipToGroupEndBlock,
          intermediateBlocks)) {
        return null;
      }
    }
    if (!verifyComposePredecessors(
        endDefaultsBlock, block, defaultsInvalidBlock, skipToGroupEndBlock, intermediateBlocks)) {
      return null;
    }

    if (firstIf != null) {
      return new AnalysisResult(
          startDefaultsInvoke, null, endDefaultsInvoke, firstIf, bypassBlock, defaultsInvalidBlock);
    }
    return new AnalysisResult(
        startDefaultsInvoke,
        getDefaultsInvalidInvoke,
        endDefaultsInvoke,
        secondIf,
        defaultsInvalidTrueTarget,
        skipToGroupEndBlock);
  }

  private InvokeMethodWithReceiver getComposeDefaultsInvalidInvoke(If theIf) {
    if (!theIf.isZeroTest() || !theIf.getType().isEqualsOrNotEquals()) {
      return null;
    }
    Value lhs = theIf.lhs();
    if (!lhs.hasSingleUniqueUser()
        || lhs.hasPhiUsers()
        || !lhs.isDefinedByInstructionSatisfying(Instruction::isInvokeMethodWithReceiver)) {
      return null;
    }
    InvokeMethodWithReceiver invoke = lhs.getDefinition().asInvokeMethodWithReceiver();
    if (!isComposeInvoke(
        invoke, composeReferences.getDefaultsInvalidName, dexItemFactory.booleanType, null)) {
      return null;
    }
    return invoke;
  }

  @SuppressWarnings("ReferenceEquality")
  private InvokeMethodWithReceiver getComposeDefaultsInvalidInvokeFromBlock(BasicBlock block) {
    if (!block.hasUniquePredecessor()
        || block.hasPhis()
        || block.hasCatchHandlers()
        || !block.exit().isIf()) {
      return null;
    }
    If theIf = block.exit().asIf();
    InvokeMethodWithReceiver invoke = getComposeDefaultsInvalidInvoke(theIf);
    if (invoke == null || block.entry() != invoke || invoke.getNext() != theIf) {
      return null;
    }
    return invoke;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isComposeSkipToGroupEndBlock(BasicBlock block, Value composerValue) {
    if (!block.hasUniquePredecessor()
        || block.hasPhis()
        || block.hasCatchHandlers()
        || !block.exit().isGoto()) {
      return false;
    }
    Instruction firstInstruction = block.entry();
    if (!firstInstruction.isInvokeMethodWithReceiver()
        || firstInstruction.hasOutValue()
        || firstInstruction.getNext() != block.exit()) {
      return false;
    }
    return isComposeInvoke(
        firstInstruction.asInvokeMethodWithReceiver(),
        composeReferences.skipToGroupEndName,
        dexItemFactory.voidType,
        composerValue);
  }

  private InvokeMethodWithReceiver getComposeEndDefaultsInvokeFromBlock(
      IRCode code, BasicBlock block, Value composerValue) {
    if (block.hasPhis() || block.hasCatchHandlers()) {
      return null;
    }
    Instruction instruction = block.entry();
    while (instruction != null
        && !instruction.instructionMayHaveSideEffects(appView, code.context())) {
      instruction = instruction.getNext();
    }
    if (instruction == null
        || !instruction.isInvokeMethodWithReceiver()
        || instruction.hasOutValue()) {
      return null;
    }
    InvokeMethodWithReceiver invoke = instruction.asInvokeMethodWithReceiver();
    if (!isComposeInvoke(
        invoke, composeReferences.endDefaultsName, dexItemFactory.voidType, composerValue)) {
      return null;
    }
    return invoke;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean isComposeInvoke(
      InvokeMethodWithReceiver invoke,
      DexString methodName,
      DexType returnType,
      Value expectedReceiver) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    return invokedMethod.getName().isIdenticalTo(methodName)
        && invokedMethod.getArity() == 0
        && invokedMethod.getReturnType().isIdenticalTo(returnType)
        && (expectedReceiver == null || invoke.getReceiver().getAliasedValue() == expectedReceiver);
  }

  @SuppressWarnings("ReferenceEquality")
  private static BasicBlock followComposeGotoChain(
      BasicBlock current, BasicBlock expectedTarget, Set<BasicBlock> intermediateBlocks) {
    Set<BasicBlock> visited = Sets.newIdentityHashSet();
    while (true) {
      if (expectedTarget != null
          && (current == expectedTarget || intermediateBlocks.contains(current))) {
        intermediateBlocks.addAll(visited);
        return expectedTarget;
      }
      if (current.hasPhis()
          || current.hasCatchHandlers()
          || !current.exit().isGoto()
          || current.entry() != current.exit()) {
        break;
      }
      if (!visited.add(current)) {
        return null;
      }
      current = current.getUniqueNormalSuccessor();
    }
    if (expectedTarget == null) {
      intermediateBlocks.addAll(visited);
      return current;
    }
    return null;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean verifyComposePredecessors(
      BasicBlock block,
      BasicBlock startBlock,
      BasicBlock defaultsInvalidBlock,
      BasicBlock skipToGroupEndBlock,
      Set<BasicBlock> intermediateBlocks) {
    for (BasicBlock predecessor : block.getPredecessors()) {
      if (predecessor != startBlock
          && predecessor != defaultsInvalidBlock
          && predecessor != skipToGroupEndBlock
          && !intermediateBlocks.contains(predecessor)) {
        return false;
      }
    }
    return true;
  }

  private static class AnalysisResult {

    private final InvokeMethodWithReceiver startDefaultsInvoke;
    private final InvokeMethodWithReceiver getDefaultsInvalidInvoke;
    private final InvokeMethodWithReceiver endDefaultsInvoke;
    private final If theIf;
    private final BasicBlock target;
    private final BasicBlock deadTarget;

    private AnalysisResult(
        InvokeMethodWithReceiver startDefaultsInvoke,
        InvokeMethodWithReceiver getDefaultsInvalidInvoke,
        InvokeMethodWithReceiver endDefaultsInvoke,
        If theIf,
        BasicBlock target,
        BasicBlock deadTarget) {
      this.startDefaultsInvoke = startDefaultsInvoke;
      this.getDefaultsInvalidInvoke = getDefaultsInvalidInvoke;
      this.endDefaultsInvoke = endDefaultsInvoke;
      this.theIf = theIf;
      this.target = target;
      this.deadTarget = deadTarget;
    }
  }
}

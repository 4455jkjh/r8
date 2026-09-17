// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.FieldGet;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.internal.IterableUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;

public class ShareFieldGetInstructions extends CodeRewriterPass<AppInfo> {

  public ShareFieldGetInstructions(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "ShareFieldGetInstructions";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return options.isRelease()
        && (code.metadata().mayHaveInstanceGet() || code.metadata().mayHaveStaticGet());
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean changed = false;
    for (BasicBlock block : ImmutableList.copyOf(code.getBlocks())) {
      List<BasicBlock> successors = block.getNormalSuccessors();
      if (successors.size() == 2 && matchingCatchHandlers(successors.get(0), successors.get(1))) {
        assert IterableUtils.all(successors, succ -> succ.getPredecessors().size() == 1)
            : "critical blocks should be split";
        changed |= hoistFieldGet(code, block, successors);
      }
      List<BasicBlock> predecessors = block.getPredecessors();
      if (predecessors.size() == 2) {
        BasicBlock firstPredecessor = getEffectivePredecessor(predecessors.get(0));
        BasicBlock secondPredecessor = getEffectivePredecessor(predecessors.get(1));
        if (matchingCatchHandlers(firstPredecessor, secondPredecessor)) {
          assert IterableUtils.all(predecessors, pred -> pred.getNormalSuccessors().size() == 1)
              : "critical blocks should be split";
          changed |= sinkFieldGet(code, block, firstPredecessor, secondPredecessor);
        }
      }
    }
    if (changed) {
      code.removeUnreachableBlocks();
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  private BasicBlock getEffectivePredecessor(BasicBlock pred) {
    if (pred.getInstructions().size() == 1
        && pred.exit().isGoto()
        && pred.getPhis().isEmpty()
        && pred.getPredecessors().size() == 1
        && pred.getUniquePredecessor().getNormalSuccessors().size() == 1) {
      return pred.getUniquePredecessor();
    }
    return pred;
  }

  private boolean matchingCatchHandlers(BasicBlock block1, BasicBlock block2) {
    return block1.hasEquivalentCatchHandlers(block2, true);
  }

  private boolean sinkFieldGet(
      IRCode code, BasicBlock block, BasicBlock firstPredecessor, BasicBlock secondPredecessor) {
    FieldGet firstFieldGet = getLastFieldGetInstruction(code, firstPredecessor);
    FieldGet secondFieldGet = getLastFieldGetInstruction(code, secondPredecessor);
    if (invalidCandidates(firstFieldGet, secondFieldGet)
        || hasPhisThatWillBecomeInvalid(
            block, firstFieldGet.outValue(), secondFieldGet.outValue())) {
      return false;
    }
    DexField field = firstFieldGet.getField();
    Value outValue = code.createValue(firstFieldGet.outValue().getType());
    Instruction sunkInstruction;
    if (firstFieldGet.isStaticGet()) {
      if (invalidStaticGetCandidates(code, firstFieldGet, secondFieldGet)) {
        return false;
      }
      sunkInstruction = new StaticGet(outValue, field);
    } else {
      InstanceGet firstInstanceGet = firstFieldGet.asInstanceGet();
      InstanceGet secondInstanceGet = secondFieldGet.asInstanceGet();
      Value firstReceiver = firstInstanceGet.object();
      Value secondReceiver = secondInstanceGet.object();
      if (firstReceiver.isMaybeNull() || secondReceiver.isMaybeNull()) {
        return false;
      }
      Value receiver;
      if (firstReceiver == secondReceiver) {
        receiver = firstReceiver;
      } else {
        Value firstReceiverRoot = firstReceiver.getAliasedValue();
        if (firstReceiverRoot == secondReceiver.getAliasedValue()) {
          receiver = firstReceiverRoot;
        } else {
          TypeElement type = firstReceiver.getType().join(secondReceiver.getType(), appView);
          Phi phi = code.createPhi(block, type);
          phi.appendOperand(firstReceiver);
          phi.appendOperand(secondReceiver);
          receiver = phi;
        }
      }
      sunkInstruction = new InstanceGet(outValue, receiver, field);
    }
    updatePosition(sunkInstruction, firstFieldGet);
    insertSunkInstruction(code, block, firstPredecessor, sunkInstruction);
    removeOldInstructions(outValue, firstFieldGet, secondFieldGet);
    return true;
  }

  private static void updatePosition(Instruction sunkInstruction, FieldGet anyFieldGet) {
    Position position = anyFieldGet.asFieldInstruction().getPosition();
    // The two input positions may differ. We determinisically pick the first one.
    sunkInstruction.setPosition(position);
  }

  private boolean hoistFieldGet(IRCode code, BasicBlock block, List<BasicBlock> successors) {
    BasicBlock firstSuccessor = successors.get(0);
    BasicBlock secondSuccessor = successors.get(1);
    FieldGet firstFieldGet = findFirstFieldGetInstruction(code, firstSuccessor);
    FieldGet secondFieldGet = findFirstFieldGetInstruction(code, secondSuccessor);
    if (invalidCandidates(firstFieldGet, secondFieldGet)) {
      return false;
    }
    DexField field = firstFieldGet.getField();
    Value outValue = code.createValue(firstFieldGet.outValue().getType());
    Instruction hoistedInstruction;
    if (firstFieldGet.isStaticGet()) {
      if (invalidStaticGetCandidates(code, firstFieldGet, secondFieldGet)) {
        return false;
      }
      hoistedInstruction = new StaticGet(outValue, field);
    } else {
      InstanceGet firstInstanceGet = firstFieldGet.asInstanceGet();
      InstanceGet secondInstanceGet = secondFieldGet.asInstanceGet();
      Value firstReceiver = firstInstanceGet.object();
      Value firstReceiverRoot = firstReceiver.getAliasedValue();
      Value secondReceiver = secondInstanceGet.object();
      if (firstReceiverRoot != secondReceiver.getAliasedValue()
          || firstReceiver.isMaybeNull()
          || secondReceiver.isMaybeNull()) {
        return false;
      }
      Value newReceiver =
          firstReceiver.getBlock() == firstInstanceGet.getBlock()
              ? firstReceiverRoot
              : firstReceiver;
      hoistedInstruction = new InstanceGet(outValue, newReceiver, field);
    }
    updatePosition(hoistedInstruction, firstFieldGet);
    insertHoistedInstruction(code, block, firstSuccessor, hoistedInstruction);
    removeOldInstructions(outValue, firstFieldGet, secondFieldGet);
    return true;
  }

  private boolean invalidStaticGetCandidates(
      IRCode code, FieldGet firstFieldGet, FieldGet secondFieldGet) {
    StaticGet firstStaticGet = firstFieldGet.asStaticGet();
    StaticGet secondStaticGet = secondFieldGet.asStaticGet();
    return firstStaticGet.instructionInstanceCanThrow(appView, code.context())
        || secondStaticGet.instructionInstanceCanThrow(appView, code.context());
  }

  private static boolean invalidCandidates(FieldGet firstFieldGet, FieldGet secondFieldGet) {
    if (firstFieldGet == null
        || secondFieldGet == null
        || firstFieldGet.isStaticGet() != secondFieldGet.isStaticGet()) {
      return true;
    }
    DexField field1 = firstFieldGet.getField();
    if (field1.isNotIdenticalTo(secondFieldGet.getField())) {
      return true;
    }
    Value firstOutValue = firstFieldGet.outValue();
    Value secondOutValue = secondFieldGet.outValue();
    return firstOutValue.hasLocalInfo() || secondOutValue.hasLocalInfo();
  }

  private void insertHoistedInstruction(
      IRCode code, BasicBlock block, BasicBlock firstSuccessor, Instruction hoistedInstruction) {
    Instruction lastInstruction = block.getLastInstruction();
    block.getInstructions().addBefore(hoistedInstruction, lastInstruction);
    if (firstSuccessor.hasCatchHandlers()) {
      BasicBlock hoistBlock =
          hoistedInstruction != block.entry()
              ? block.split(code, false, hoistedInstruction)
              : block;
      hoistBlock.split(code, false, lastInstruction);
      hoistBlock.copyCatchHandlers(code, null, firstSuccessor, options);
    }
  }

  private void insertSunkInstruction(
      IRCode code, BasicBlock block, BasicBlock firstPredecessor, Instruction sunkInstruction) {
    block.getInstructions().addFirst(sunkInstruction);
    if (firstPredecessor.hasCatchHandlers() || block.hasCatchHandlers()) {
      if (sunkInstruction.getNext() != null) {
        block.split(code, false, sunkInstruction.getNext());
      }
      if (firstPredecessor.hasCatchHandlers()) {
        block.copyCatchHandlers(code, null, firstPredecessor, options);
      }
    }
  }

  private static void removeOldInstructions(
      Value outValue, FieldGet firstFieldGet, FieldGet secondFieldGet) {
    BasicBlock firstBlock = firstFieldGet.asFieldInstruction().getBlock();
    BasicBlock secondBlock = secondFieldGet.asFieldInstruction().getBlock();
    firstFieldGet.outValue().replaceUsers(outValue);
    secondFieldGet.outValue().replaceUsers(outValue);
    outValue.uniquePhiUsers().forEach(Phi::removeTrivialPhi);
    firstFieldGet.removeOrReplaceByDebugLocalRead();
    secondFieldGet.removeOrReplaceByDebugLocalRead();
    unlinkCatchHandlersIfNotThrowing(firstBlock);
    unlinkCatchHandlersIfNotThrowing(secondBlock);
  }

  private static void unlinkCatchHandlersIfNotThrowing(BasicBlock block) {
    if (block.hasCatchHandlers() && !block.canThrow()) {
      for (BasicBlock catchHandler : block.getCatchHandlers().getUniqueTargets()) {
        catchHandler.unlinkCatchHandler();
      }
    }
  }

  private boolean hasPhisThatWillBecomeInvalid(
      BasicBlock block, Value firstOutValue, Value secondOutValue) {
    for (Phi phi : block.getPhis()) {
      if (phi.getOperands().contains(firstOutValue)) {
        if (phi.getOperands().size() != 2 || !phi.getOperands().contains(secondOutValue)) {
          return true;
        }
      } else if (phi.getOperands().contains(secondOutValue)) {
        if (phi.getOperands().size() != 2 || !phi.getOperands().contains(firstOutValue)) {
          return true;
        }
      }
    }
    return false;
  }

  private FieldGet getLastFieldGetInstruction(IRCode code, BasicBlock block) {
    Set<Value> seenValues = Sets.newIdentityHashSet();
    for (Instruction instruction = block.getLastInstruction();
        instruction != null;
        instruction = instruction.getPrev()) {
      if (instruction.isFieldGet() && !seenValues.contains(instruction.outValue())) {
        return instruction.asFieldGet();
      }
      if (instruction.instructionMayHaveSideEffects(appView, code.context())) {
        return null;
      }
      seenValues.addAll(instruction.inValues());
    }
    return null;
  }

  private FieldGet findFirstFieldGetInstruction(IRCode code, BasicBlock block) {
    for (Instruction instruction = block.entry();
        instruction != null;
        instruction = instruction.getNext()) {
      if (instruction.isFieldGet()) {
        return instruction.asFieldGet();
      }
      if (instruction.instructionMayHaveSideEffects(appView, code.context())) {
        return null;
      }
    }
    return null;
  }
}

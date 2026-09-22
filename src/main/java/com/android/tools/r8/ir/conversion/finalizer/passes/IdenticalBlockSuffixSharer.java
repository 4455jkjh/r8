// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

import static com.android.tools.r8.ir.regalloc.LiveIntervals.NO_REGISTER;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionList;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Identify common suffixes in predecessor blocks and share them. */
public class IdenticalBlockSuffixSharer extends FinalizerRewriterPass<AppInfo> {

  private final int overhead;

  public IdenticalBlockSuffixSharer(AppView<?> appView) {
    this(appView, 0);
  }

  public IdenticalBlockSuffixSharer(AppView<?> appView, int overhead) {
    super(appView);
    this.overhead = overhead;
  }

  @Override
  protected String getRewriterId() {
    return "IdenticalBlockSuffixSharer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return code.blocks.size() >= 2;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code, RegisterAllocator allocator) {
    boolean hasChanged = false;
    Collection<BasicBlock> blocks = code.blocks;
    List<BasicBlock> normalExits = code.computeNormalExitBlocks();
    Set<BasicBlock> syntheticNormalExits = Sets.newIdentityHashSet();
    if (normalExits.size() > 1) {
      if (code.context().getReturnType().isVoidType()
          || code.getConversionOptions().isGeneratingClassFiles()) {
        BasicBlock syntheticNormalExit = new BasicBlock(code.metadata());
        syntheticNormalExit.getMutablePredecessors().addAll(normalExits);
        syntheticNormalExits.add(syntheticNormalExit);
      } else {
        Int2ReferenceMap<List<BasicBlock>> normalExitPartitioning =
            new Int2ReferenceOpenHashMap<>();
        for (BasicBlock block : normalExits) {
          int returnRegister =
              block
                  .exit()
                  .asReturn()
                  .returnValue()
                  .getLiveIntervals()
                  .getSplitCovering(block.exit().getNumber())
                  .getRegister();
          assert returnRegister != NO_REGISTER;
          List<BasicBlock> blocksWithReturnRegister;
          if (normalExitPartitioning.containsKey(returnRegister)) {
            blocksWithReturnRegister = normalExitPartitioning.get(returnRegister);
          } else {
            blocksWithReturnRegister = new ArrayList<>();
            normalExitPartitioning.put(returnRegister, blocksWithReturnRegister);
          }
          blocksWithReturnRegister.add(block);
        }
        for (List<BasicBlock> blocksWithSameReturnRegister : normalExitPartitioning.values()) {
          BasicBlock syntheticNormalExit = new BasicBlock(code.metadata());
          syntheticNormalExit.getMutablePredecessors().addAll(blocksWithSameReturnRegister);
          syntheticNormalExits.add(syntheticNormalExit);
        }
      }
      blocks = new ArrayList<>(code.getBlocks().size() + syntheticNormalExits.size());
      blocks.addAll(code.getBlocks());
      blocks.addAll(syntheticNormalExits);
    }
    do {
      Map<BasicBlock, BasicBlock> newBlocks = new IdentityHashMap<>();
      InstructionEquivalence equivalence = new InstructionEquivalence(allocator, code);
      for (BasicBlock block : blocks) {
        if (block.getPredecessors().size() < 2) {
          continue;
        }
        // Group interesting predecessor blocks by their last instruction.
        Map<Wrapper<Instruction>, List<BasicBlock>> lastInstructionToBlocks = new HashMap<>();
        for (BasicBlock pred : block.getPredecessors()) {
          if (pred.exit().isGoto()
              && pred.getSuccessors().size() == 1
              && pred.getInstructions().size() > 1) {
            Instruction lastInstruction = pred.getLastInstruction().getPrev();
            List<BasicBlock> value =
                lastInstructionToBlocks.computeIfAbsent(
                    equivalence.wrap(lastInstruction), (k) -> new ArrayList<>());
            value.add(pred);
          } else if (pred.exit().isReturn()
              && pred.getSuccessors().isEmpty()
              && pred.getInstructions().size() > 2) {
            Instruction lastInstruction = pred.exit();
            List<BasicBlock> value =
                lastInstructionToBlocks.computeIfAbsent(
                    equivalence.wrap(lastInstruction), (k) -> new ArrayList<>());
            value.add(pred);
          }
        }
        // For each group of predecessors of size 2 or more, find the largest common suffix and
        // move that to a separate block.
        for (List<BasicBlock> predsWithSameLastInstruction : lastInstructionToBlocks.values()) {
          if (predsWithSameLastInstruction.size() < 2) {
            continue;
          }
          BasicBlock firstPred = predsWithSameLastInstruction.get(0);
          int commonSuffixSize = firstPred.getInstructions().size();
          for (int i = 1; i < predsWithSameLastInstruction.size(); i++) {
            BasicBlock pred = predsWithSameLastInstruction.get(i);
            assert pred.exit().isGoto() || pred.exit().isReturn();
            commonSuffixSize =
                Math.min(commonSuffixSize, sharedSuffixSize(firstPred, pred, allocator, code));
          }

          int sizeDelta = overhead - (predsWithSameLastInstruction.size() - 1) * commonSuffixSize;

          // Don't share a suffix that is just a single goto or return instruction.
          if (commonSuffixSize <= 1 || sizeDelta >= 0) {
            continue;
          }
          BasicBlock newBlock =
              createAndInsertBlockForSuffix(
                  code,
                  commonSuffixSize,
                  predsWithSameLastInstruction,
                  syntheticNormalExits.contains(block) ? null : block,
                  allocator);
          newBlocks.put(predsWithSameLastInstruction.get(0), newBlock);
          hasChanged = true;
        }
      }
      ListIterator<BasicBlock> blockIterator = code.listIterator();
      while (blockIterator.hasNext()) {
        BasicBlock block = blockIterator.next();
        if (newBlocks.containsKey(block)) {
          blockIterator.add(newBlocks.get(block));
        }
      }
      // Go through all the newly introduced blocks to find more common suffixes to share.
      blocks = newBlocks.values();
    } while (!blocks.isEmpty());
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  private static BasicBlock createAndInsertBlockForSuffix(
      IRCode code,
      int suffixSize,
      List<BasicBlock> preds,
      BasicBlock successorBlock,
      RegisterAllocator allocator) {
    BasicBlock first = preds.get(0);
    assert (successorBlock != null && first.exit().isGoto())
        || (successorBlock == null && first.exit().isReturn());
    BasicBlock newBlock = new BasicBlock(code.metadata());
    newBlock.setNumber(code.getNextBlockNumber());
    Int2ReferenceMap<DebugLocalInfo> newBlockEntryLocals = null;
    if (first.getLocalsAtEntry() != null) {
      newBlockEntryLocals = new Int2ReferenceOpenHashMap<>(first.getLocalsAtEntry());
      int prefixSize = first.getInstructions().size() - suffixSize;
      Instruction instruction = first.entry();
      for (int i = 0; i < prefixSize; i++) {
        if (instruction.isDebugLocalsChange()) {
          instruction.asDebugLocalsChange().apply(newBlockEntryLocals);
        }
        instruction = instruction.getNext();
      }
    }

    allocator.addNewBlockToShareIdenticalSuffix(newBlock, suffixSize, preds);

    boolean movedThrowingInstruction = false;
    Instruction instruction = first.getLastInstruction();
    for (int i = 0; i < suffixSize; i++) {
      movedThrowingInstruction = movedThrowingInstruction || instruction.instructionTypeCanThrow();
      instruction = instruction.getPrev();
    }
    newBlock
        .getInstructions()
        .severFrom(instruction == null ? first.entry() : instruction.getNext());
    if (movedThrowingInstruction && first.hasCatchHandlers()) {
      newBlock.transferCatchHandlers(first);
    }

    for (BasicBlock pred : preds) {
      Position lastPosition;
      InstructionList instructions = pred.getInstructions();
      if (pred == first) {
        // Already removed from first via severFrom().
        lastPosition = newBlock.getPosition();
      } else {
        lastPosition = pred.getPosition();
        for (int i = 0; i < suffixSize; i++) {
          instructions.removeIgnoreValues(instructions.getLast());
        }
      }
      for (Instruction ins = instructions.getLastOrNull(); ins != null; ins = ins.getPrev()) {
        if (ins.getPosition().isSome()) {
          lastPosition = ins.getPosition();
          break;
        }
      }
      Goto jump = new Goto();
      jump.setPosition(lastPosition);
      instructions.addLast(jump);
      newBlock.getMutablePredecessors().add(pred);
      if (successorBlock != null) {
        pred.replaceSuccessor(successorBlock, newBlock);
        successorBlock.getMutablePredecessors().remove(pred);
      } else {
        pred.getMutableSuccessors().add(newBlock);
      }
      if (movedThrowingInstruction) {
        pred.clearCatchHandlers();
      }
    }
    newBlock.close(null);
    if (newBlockEntryLocals != null) {
      newBlock.setLocalsAtEntry(newBlockEntryLocals);
    }
    if (successorBlock != null) {
      newBlock.link(successorBlock);
    }
    return newBlock;
  }

  private static Int2ReferenceMap<DebugLocalInfo> localsAtBlockExit(BasicBlock block) {
    if (block.getLocalsAtEntry() == null) {
      return null;
    }
    Int2ReferenceMap<DebugLocalInfo> locals =
        new Int2ReferenceOpenHashMap<>(block.getLocalsAtEntry());
    for (Instruction instruction : block.getInstructions()) {
      if (instruction.isDebugLocalsChange()) {
        instruction.asDebugLocalsChange().apply(locals);
      }
    }
    return locals;
  }

  private static int sharedSuffixSize(
      BasicBlock block0, BasicBlock block1, RegisterAllocator allocator, IRCode code) {
    assert block0.exit().isGoto() || block0.exit().isReturn();
    // If the blocks do not agree on locals at exit then they don't have any shared suffix.
    if (!Objects.equals(localsAtBlockExit(block0), localsAtBlockExit(block1))) {
      return 0;
    }
    Instruction i0 = block0.getLastInstruction();
    Instruction i1 = block1.getLastInstruction();
    int suffixSize = 0;
    while (i0 != null && i1 != null) {
      if (!i0.identicalAfterRegisterAllocation(i1, allocator, code.getConversionOptions())) {
        return suffixSize;
      }
      i0 = i0.getPrev();
      i1 = i1.getPrev();
      suffixSize++;
    }
    return suffixSize;
  }
}

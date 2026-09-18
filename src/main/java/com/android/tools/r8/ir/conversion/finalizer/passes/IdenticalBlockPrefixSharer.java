// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DebugLocalsChange;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Identify common prefixes in successor blocks and share them. */
public class IdenticalBlockPrefixSharer extends FinalizerRewriterPass<AppInfo> {

  public IdenticalBlockPrefixSharer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "IdenticalBlockPrefixSharer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return true;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code, RegisterAllocator allocator) {
    boolean hasChanged = false;
    InstructionEquivalence equivalence = new InstructionEquivalence(allocator, code);
    Set<BasicBlock> blocksToBeRemoved = Sets.newIdentityHashSet();
    for (BasicBlock block : code.blocks) {
      hasChanged |=
          shareIdenticalBlockPrefixFromNormalSuccessors(
              block, blocksToBeRemoved, equivalence, allocator);
    }
    code.blocks.removeAll(blocksToBeRemoved);
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  private boolean shareIdenticalBlockPrefixFromNormalSuccessors(
      BasicBlock block,
      Set<BasicBlock> blocksToBeRemoved,
      InstructionEquivalence equivalence,
      RegisterAllocator allocator) {
    if (blocksToBeRemoved.contains(block)) {
      // This block has already been removed entirely.
      return false;
    }

    if (!mayShareIdenticalBlockPrefix(block)) {
      // Not eligible.
      return false;
    }

    boolean hasChanged = false;
    // Share instructions from the normal successors one-by-one.
    List<BasicBlock> normalSuccessors = block.getNormalSuccessors();
    while (true) {
      BasicBlock firstNormalSuccessor = normalSuccessors.get(0);

      // Check if all instructions were already removed from the normal successors.
      for (BasicBlock normalSuccessor : normalSuccessors) {
        if (normalSuccessor.isEmpty()) {
          assert blocksToBeRemoved.containsAll(normalSuccessors);
          return hasChanged;
        }
      }

      // If the first instruction in all successors is not the same, we cannot merge them into the
      // predecessor.
      Instruction instruction = firstNormalSuccessor.entry();
      for (int i = 1; i < normalSuccessors.size(); i++) {
        BasicBlock otherNormalSuccessor = normalSuccessors.get(i);
        Instruction otherInstruction = otherNormalSuccessor.entry();
        if (!equivalence.equivalent(instruction, otherInstruction)) {
          return hasChanged;
        }
      }

      if (instruction.instructionTypeCanThrow()) {
        // Each block with one or more catch handlers may have at most one throwing instruction.
        if (block.hasCatchHandlers()) {
          return hasChanged;
        }

        // If the instruction can throw and one of the normal successor blocks has a catch handler,
        // then we cannot merge the instruction into the predecessor, since this would change the
        // exceptional control flow.
        for (BasicBlock normalSuccessor : normalSuccessors) {
          if (normalSuccessor.hasCatchHandlers()) {
            return hasChanged;
          }
        }
      }

      // Check for commutativity (semantics). If the instruction writes to a register, then we
      // need to make sure that the instruction commutes with the last instruction in the
      // predecessor.
      if (instruction.outValue() != null && instruction.outValue().needsRegister()) {
        int destinationRegister =
            allocator.getRegisterForValue(instruction.outValue(), instruction.getNumber());
        boolean commutative =
            block.exit().inValues().stream()
                .allMatch(
                    inValue -> {
                      int operandRegister =
                          allocator.getRegisterForValue(inValue, block.exit().getNumber());
                      for (int i = 0; i < instruction.outValue().requiredRegisters(); i++) {
                        for (int j = 0; j < inValue.requiredRegisters(); j++) {
                          if (destinationRegister + i == operandRegister + j) {
                            // Overlap detected, the two instructions do not commute.
                            return false;
                          }
                        }
                      }
                      return true;
                    });
        if (!commutative) {
          return hasChanged;
        }
      }

      // Check for commutativity (debug info).
      if (!instruction.getPosition().equals(block.exit().getPosition())
          && !(block.exit().getPosition().isNone() && !block.exit().getDebugValues().isEmpty())) {
        return hasChanged;
      }

      // Remove the instruction from the normal successors.
      for (BasicBlock normalSuccessor : normalSuccessors) {
        normalSuccessor.entry().removeIgnoreValues();
      }
      hasChanged = true;

      // Move the instruction into the predecessor.
      if (instruction.isJumpInstruction()) {
        // Replace jump instruction in predecessor with the jump instruction from the normal
        // successors.
        block.getLastInstruction().removeIgnoreValues();
        block.getInstructions().addLast(instruction);

        // Take a copy of the old normal successors before performing a destructive update.
        List<BasicBlock> oldNormalSuccessors = new ArrayList<>(normalSuccessors);

        // Update successors of predecessor block.
        block.detachAllSuccessors();
        for (BasicBlock newNormalSuccessor : firstNormalSuccessor.getNormalSuccessors()) {
          block.link(newNormalSuccessor);
        }

        // Detach the previous normal successors from the rest of the graph.
        for (BasicBlock oldNormalSuccessor : oldNormalSuccessors) {
          oldNormalSuccessor.detachAllSuccessors();
        }

        // Record that the previous normal successors should be removed entirely.
        blocksToBeRemoved.addAll(oldNormalSuccessors);

        if (!mayShareIdenticalBlockPrefix(block)) {
          return hasChanged;
        }
      } else {
        // Insert instruction before the jump instruction in the predecessor.
        block.getInstructions().addBefore(instruction, block.getLastInstruction());

        // Update locals-at-entry if needed.
        if (instruction.isDebugLocalsChange()) {
          DebugLocalsChange localsChange = instruction.asDebugLocalsChange();
          for (BasicBlock normalSuccessor : normalSuccessors) {
            localsChange.apply(normalSuccessor.getLocalsAtEntry());
          }
        }
      }
    }
  }

  private static boolean mayShareIdenticalBlockPrefix(BasicBlock block) {
    List<BasicBlock> normalSuccessors = block.getNormalSuccessors();
    if (normalSuccessors.size() <= 1) {
      // Nothing to share.
      return false;
    }

    // Ensure that the current block is on all paths to the successor blocks.
    for (BasicBlock normalSuccessor : normalSuccessors) {
      if (normalSuccessor.getPredecessors().size() != 1) {
        return false;
      }
    }

    // Only try to share instructions if the normal successors have the same locals state on entry.
    BasicBlock firstNormalSuccessor = normalSuccessors.get(0);
    for (int i = 1; i < normalSuccessors.size(); i++) {
      BasicBlock otherNormalSuccessor = normalSuccessors.get(i);
      if (!Objects.equals(
          firstNormalSuccessor.getLocalsAtEntry(), otherNormalSuccessor.getLocalsAtEntry())) {
        return false;
      }
    }
    return true;
  }
}

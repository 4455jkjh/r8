// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

/**
 * Post-register allocation pass that removes {@code Move} and {@code ConstNumber} instructions
 * writing to dead registers, using a backward register liveness analysis.
 *
 * <p>The live registers are represented as a {@code long} bitmask where bit {@code r} is set if
 * register {@code r} is live. Only registers below 64 are tracked, so writes to higher registers
 * are never removed. Wide values are tracked through their low register only, since verifiable code
 * never accesses the high register of a wide value on its own.
 *
 * <p>The in-registers of a dead store are not marked live, so chains of dead stores, including dead
 * move cycles in loops, are removed at once.
 */
public class DeadRegisterStoreEliminator extends FinalizerRewriterPass<AppInfo> {

  private static final long NO_REGISTERS = 0L;

  public DeadRegisterStoreEliminator(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "DeadRegisterStoreEliminator";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    // In debug mode and in reachability sensitive methods, a register that is not read by
    // subsequent instructions may still hold a local, which must remain visible to the debugger or
    // keep its object alive until the end of its scope.
    return appView.options().shouldCompileMethodInReleaseMode(appView, code.context());
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code, RegisterAllocator registerAllocator) {
    assert code.assertBasicBlockNumbersReset();
    // TODO(b/566097174): Track more than just the lowest 64 registers.
    long[] blockLiveInRegisters = new long[code.blocks.size()];
    if (hasBackwardEdge(code)) {
      // Propagate liveness across the backward edges until fixed point (bounded by the number of
      // nested loops C, complexity is O(n * C)).
      while (updateLiveInRegisters(code, blockLiveInRegisters, registerAllocator)) {}
    }
    // Without backward edges (most methods), each block is swept after all of its successors, so
    // this single sweep computes the liveness while removing the dead stores.
    return CodeRewriterResult.hasChanged(
        removeDeadStores(code, blockLiveInRegisters, registerAllocator));
  }

  /** Returns true if a block jumps to itself or to a preceding block, e.g., a loop back-edge. */
  private static boolean hasBackwardEdge(IRCode code) {
    for (BasicBlock block : code.blocks) {
      for (BasicBlock successor : block.getSuccessors()) {
        if (successor.getNumber() <= block.getNumber()) {
          return true;
        }
      }
    }
    return false;
  }

  /** Recomputes the live-in registers of all blocks and returns true if any of them changed. */
  private static boolean updateLiveInRegisters(
      IRCode code, long[] blockLiveInRegisters, RegisterAllocator registerAllocator) {
    boolean changed = false;
    Iterator<BasicBlock> blockIterator = code.blocks.descendingIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      long previousLiveInRegisters = blockLiveInRegisters[block.getNumber()];
      sweepBlockBackward(block, blockLiveInRegisters, registerAllocator, false);
      changed |= blockLiveInRegisters[block.getNumber()] != previousLiveInRegisters;
    }
    return changed;
  }

  /** Removes the dead stores of all blocks and returns true if any was removed. */
  private static boolean removeDeadStores(
      IRCode code, long[] blockLiveInRegisters, RegisterAllocator registerAllocator) {
    boolean changed = false;
    ListIterator<BasicBlock> blockIterator = code.blocks.listIterator(code.blocks.size());
    while (blockIterator.hasPrevious()) {
      changed |=
          sweepBlockBackward(
              blockIterator.previous(), blockLiveInRegisters, registerAllocator, true);
    }
    assert blockLiveInRegisters[0] == NO_REGISTERS;
    return changed;
  }

  private static boolean sweepBlockBackward(
      BasicBlock block,
      long[] blockLiveInRegisters,
      RegisterAllocator registerAllocator,
      boolean removeDeadStores) {
    long liveRegisters = getNormalLiveOutRegisters(block, blockLiveInRegisters);
    long catchLiveOutRegisters = getCatchLiveOutRegisters(block, blockLiveInRegisters);
    boolean changed = false;
    for (Instruction instruction = block.exit(), previousInstruction;
        instruction != null;
        instruction = previousInstruction) {
      previousInstruction = instruction.getPrev();
      long outRegisterMask = getOutRegisterMask(instruction, registerAllocator);
      boolean isDead =
          outRegisterMask != NO_REGISTERS && (liveRegisters & outRegisterMask) == NO_REGISTERS;
      if (isDead && isRemovableInstruction(instruction)) {
        if (removeDeadStores) {
          instruction.removeIgnoreValues();
          changed = true;
        }
        continue;
      }
      liveRegisters &= ~outRegisterMask;
      if (instruction.instructionTypeCanThrow()) {
        // Throwing jumps to the catch handlers without writing the out-register.
        liveRegisters |= catchLiveOutRegisters;
      }
      liveRegisters |= getInRegistersMask(instruction, registerAllocator);
    }
    blockLiveInRegisters[block.getNumber()] = liveRegisters;
    return changed;
  }

  private static long getNormalLiveOutRegisters(BasicBlock block, long[] blockLiveInRegisters) {
    long liveOutRegisters = NO_REGISTERS;
    List<BasicBlock> successors = block.getSuccessors();
    for (int i = block.numberOfExceptionalSuccessors(); i < successors.size(); i++) {
      liveOutRegisters |= blockLiveInRegisters[successors.get(i).getNumber()];
    }
    return liveOutRegisters;
  }

  private static long getCatchLiveOutRegisters(BasicBlock block, long[] blockLiveInRegisters) {
    long liveOutRegisters = NO_REGISTERS;
    List<BasicBlock> successors = block.getSuccessors();
    for (int i = 0; i < block.numberOfExceptionalSuccessors(); i++) {
      liveOutRegisters |= blockLiveInRegisters[successors.get(i).getNumber()];
    }
    return liveOutRegisters;
  }

  private static boolean isRemovableInstruction(Instruction instruction) {
    return instruction.isMove() || instruction.isConstNumber();
  }

  private static long getOutRegisterMask(
      Instruction instruction, RegisterAllocator registerAllocator) {
    Value outValue = instruction.outValue();
    if (outValue == null || !outValue.needsRegister()) {
      return NO_REGISTERS;
    }
    return getRegisterMask(registerAllocator, outValue, instruction.getNumber());
  }

  private static long getInRegistersMask(
      Instruction instruction, RegisterAllocator registerAllocator) {
    long inRegistersMask = NO_REGISTERS;
    for (Value inValue : instruction.inValues()) {
      if (inValue.needsRegister()) {
        inRegistersMask |= getRegisterMask(registerAllocator, inValue, instruction.getNumber());
      }
    }
    return inRegistersMask;
  }

  private static long getRegisterMask(
      RegisterAllocator registerAllocator, Value value, int instructionNumber) {
    int register = registerAllocator.getRegisterForValue(value, instructionNumber);
    return register < 64 ? 1L << register : NO_REGISTERS;
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.regalloc;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Binop;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCode.LiveAtEntrySets;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Removes moves by changing the register assignment after linear scan.
 *
 * <p>Linear scan assigns registers in the order of the live interval starts and never revisits a
 * decision. Two live intervals connected by a move therefore often end up in different registers,
 * even when one of them could have used the register of the other. The moves are:
 *
 * <ul>
 *   <li>split moves, between two consecutive splits of a value,
 *   <li>resolution moves, between the splits of a value at the two ends of a control-flow edge,
 *   <li>phi moves, between a phi operand at the end of a predecessor and the phi.
 * </ul>
 *
 * <p>Most phi moves are already removed before linear scan, by coalescing the live intervals of
 * each phi with the live intervals of its operands. The remaining phi moves are from operands that
 * overlap the phi, such as the operand of a loop phi flowing in from the back edge, from operands
 * that are not coalesced, such as constants, and between the splits of coalesced live intervals
 * that linear scan assigned to different registers.
 *
 * <p>This pass moves live intervals to the register of the live intervals they are connected to by
 * a move (their move partners), when that register is free during their entire live range. The move
 * then becomes a move from a register to itself, which is not inserted.
 *
 * <p>The out value of a check-cast and its object, and the out value of a binop and its operands,
 * are also move partners: a check-cast whose out value is in the register of its object needs no
 * move, and a binop whose out value is in the register of an operand can use the 2addr form.
 */
class MoveCoalescer {

  // Changing the register of a live intervals can free a register for another live intervals, so
  // we iterate. Most moves are removed in the first round, and a third round is rarely useful.
  private static final int MAX_ROUNDS = 2;

  private final LinearScanRegisterAllocator allocator;

  // For each live intervals, the live intervals it is connected to by a move.
  private final Map<LiveIntervals, Set<LiveIntervals>> movePartners = new LinkedHashMap<>();

  // For each register, the live intervals assigned to it.
  private final Int2ReferenceMap<Set<LiveIntervals>> intervalsByRegister =
      new Int2ReferenceOpenHashMap<>();

  private MoveCoalescer(LinearScanRegisterAllocator allocator) {
    this.allocator = allocator;
  }

  static void run(
      LinearScanRegisterAllocator allocator,
      IRCode code,
      List<LiveIntervals> liveIntervals,
      Map<BasicBlock, LiveAtEntrySets> liveAtEntrySets,
      int maxRegisterNumber) {
    MoveCoalescer coalescer = new MoveCoalescer(allocator);
    coalescer.addSplitMovePartners(liveIntervals);
    coalescer.addControlFlowMovePartners(code, liveAtEntrySets);
    coalescer.addSameRegisterOperandPartners(code);
    if (!coalescer.movePartners.isEmpty()) {
      coalescer.computeIntervalsByRegister(liveIntervals);
      coalescer.coalesce();
    }
  }

  private void addSplitMovePartners(List<LiveIntervals> liveIntervals) {
    for (LiveIntervals parent : liveIntervals) {
      if (!parent.hasSplits()) {
        continue;
      }
      LiveIntervals previousSplit = parent;
      for (LiveIntervals split : parent.getSplitChildren()) {
        // Mirrors LinearScanRegisterAllocator.insertMoves, which only inserts a move in this case.
        if (split.getStart() == previousSplit.getEnd()) {
          addMovePartners(previousSplit, split);
        }
        previousSplit = split;
      }
    }
  }

  private void addControlFlowMovePartners(
      IRCode code, Map<BasicBlock, LiveAtEntrySets> liveAtEntrySets) {
    for (BasicBlock block : code.blocks) {
      for (BasicBlock successor : block.getSuccessors()) {
        // The moves of an exceptional edge are at the throwing instruction.
        int from =
            (block.hasCatchSuccessor(successor) ? block.exceptionalExit() : block.exit())
                .getNumber();
        int to = successor.entry().getNumber();
        // Resolution moves.
        for (Value value : liveAtEntrySets.get(successor).liveValues) {
          LiveIntervals parent = value.getLiveIntervals();
          if (parent.hasSplits()) {
            addMovePartners(parent.getSplitCovering(from), parent.getSplitCovering(to));
          }
        }
        // Phi moves.
        if (!successor.hasPhis()) {
          continue;
        }
        int predecessorIndex = successor.getPredecessors().indexOf(block);
        for (Phi phi : successor.getPhis()) {
          Value operand = phi.getOperand(predecessorIndex);
          LiveIntervals operandSplit = operand.getLiveIntervals().getSplitCovering(from);
          LiveIntervals phiSplit = phi.getLiveIntervals().getSplitCovering(to);
          // Identical phi moves on different edges share a block, so they count once. Otherwise the
          // phi follows the operand of most edges.
          addMovePartners(operandSplit, phiSplit);
        }
      }
    }
  }

  // Check-cast and 2addr binop partners, see the class documentation.
  @SuppressWarnings("ReferenceEquality")
  private void addSameRegisterOperandPartners(IRCode code) {
    for (BasicBlock block : code.blocks) {
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.isCheckCast()) {
          CheckCast checkCast = instruction.asCheckCast();
          if (checkCast.hasOutValue()) {
            addOperandPartners(checkCast, checkCast.object());
          }
        } else if (instruction.isArithmeticBinop()
            || (allocator.options().getTestingOptions().enable2AddrHintsForLogicalBinop
                && instruction.isLogicalBinop())) {
          Binop binop = instruction.asBinop();
          if (binop.canBeTwoAddr(allocator.options())) {
            addOperandPartners(binop, binop.leftValue());
            if (binop.isCommutative() && binop.rightValue() != binop.leftValue()) {
              addOperandPartners(binop, binop.rightValue());
            }
          }
        }
      }
    }
  }

  private void addOperandPartners(Instruction instruction, Value operand) {
    Value out = instruction.outValue();
    if (out == null || out.getLiveIntervals() == null || operand.getLiveIntervals() == null) {
      return;
    }
    addMovePartners(
        out.getLiveIntervals().getSplitCovering(instruction),
        operand.getLiveIntervals().getSplitCovering(instruction));
  }

  @SuppressWarnings("ReferenceEquality")
  private void addMovePartners(LiveIntervals a, LiveIntervals b) {
    // We register in both directions because when changing the register of intervals,
    // we iterate over all partners to find a shared register.
    if (a != b && a.hasRegister() && b.hasRegister()) {
      movePartners.computeIfAbsent(a, key -> new LinkedHashSet<>()).add(b);
      movePartners.computeIfAbsent(b, key -> new LinkedHashSet<>()).add(a);
    }
  }

  private void computeIntervalsByRegister(List<LiveIntervals> liveIntervals) {
    for (LiveIntervals parent : liveIntervals) {
      addToIntervalsByRegister(parent);
      parent.getSplitChildren().forEach(this::addToIntervalsByRegister);
    }
  }

  private void addToIntervalsByRegister(LiveIntervals intervals) {
    if (intervals.hasRegister()) {
      intervals.forEachRegister(
          register ->
              intervalsByRegister
                  .computeIfAbsent(register, key -> new LinkedHashSet<>())
                  .add(intervals));
    }
  }

  private void coalesce() {
    for (int round = 0; round < MAX_ROUNDS; round++) {
      boolean changed = false;
      for (LiveIntervals intervals : movePartners.keySet()) {
        if (!canChangeRegisterAfterLinearScan(intervals)) {
          continue;
        }
        Set<LiveIntervals> partners = movePartners.get(intervals);
        if (moveToPartnerRegister(intervals, partners)
            || moveWithPartnerToFreeRegister(intervals, partners)) {
          changed = true;
        }
      }
      if (!changed) {
        return;
      }
    }
  }

  // Moves the live intervals to the register of its partners, if that register is free. When the
  // partners are in different registers, we pick the register used by most partners.
  private boolean moveToPartnerRegister(LiveIntervals intervals, Set<LiveIntervals> partners) {
    Int2IntMap counts = new Int2IntOpenHashMap();
    for (LiveIntervals partner : partners) {
      counts.put(partner.getRegister(), counts.getOrDefault(partner.getRegister(), 0) + 1);
    }
    int bestRegister = intervals.getRegister();
    int bestCount = counts.getOrDefault(bestRegister, 0);
    for (Int2IntMap.Entry entry : counts.int2IntEntrySet()) {
      int register = entry.getIntKey();
      if (register == intervals.getRegister()) {
        continue;
      }
      int count = entry.getIntValue();
      if (count > bestCount && isRegisterFree(intervals, register)) {
        bestRegister = register;
        bestCount = count;
      }
    }
    if (bestRegister == intervals.getRegister()) {
      return false;
    }
    changeRegister(intervals, bestRegister);
    return true;
  }

  // When the live intervals and some of its partners do not share their register with any partner,
  // moves the live intervals and one of these partners to a register that is free for both.
  private boolean moveWithPartnerToFreeRegister(
      LiveIntervals intervals, Set<LiveIntervals> partners) {
    if (hasPartnerInSameRegister(intervals, partners)) {
      return false;
    }
    List<LiveIntervals> candidates = new ArrayList<>();
    for (LiveIntervals partner : partners) {
      // A partner can be live at the same time as the live intervals, for example a loop phi and
      // the operand flowing into it from the back edge. They cannot share a register.
      if (!conflicts(intervals, partner)
          && canChangeRegisterAfterLinearScan(partner)
          && !hasPartnerInSameRegister(partner, movePartners.get(partner))) {
        candidates.add(partner);
      }
    }
    if (candidates.isEmpty()) {
      return false;
    }
    int maxRegister = allocator.getRegisterConstraint(intervals);
    for (int register = allocator.numberOfArgumentRegisters; register <= maxRegister; register++) {
      if (isRegisterFree(intervals, register)) {
        for (LiveIntervals partner : candidates) {
          if (isRegisterFree(partner, register)
              && canUseRegisterAfterLinearScan(partner, register)) {
            changeRegister(intervals, register);
            changeRegister(partner, register);
            allocator.maxRegisterNumber =
                Math.max(
                    allocator.maxRegisterNumber,
                    register
                        + Math.max(intervals.requiredRegisters(), partner.requiredRegisters())
                        - 1);
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean hasPartnerInSameRegister(LiveIntervals intervals, Set<LiveIntervals> partners) {
    for (LiveIntervals partner : partners) {
      if (partner.getRegister() == intervals.getRegister()) {
        return true;
      }
    }
    return false;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean isRegisterFree(LiveIntervals intervals, int register) {
    if (!canUseRegisterAfterLinearScan(intervals, register)) {
      return false;
    }
    if (register > allocator.maxRegisterNumber) {
      return true;
    }
    for (int r = register; r < register + intervals.requiredRegisters(); r++) {
      for (LiveIntervals other : intervalsByRegister.getOrDefault(r, Collections.emptySet())) {
        if (other != intervals && conflicts(intervals, other)) {
          return false;
        }
      }
    }
    return true;
  }

  // A split of length zero [p; p[ does not overlap any live intervals, but it holds its value at p:
  // it conflicts with the live intervals that start at p or are live at p.
  private static boolean conflicts(LiveIntervals a, LiveIntervals b) {
    if (a.getStart() == b.getStart()) {
      return true;
    }
    if (a.getStart() == a.getEnd()) {
      return b.overlapsPosition(a.getStart());
    }
    if (b.getStart() == b.getEnd()) {
      return a.overlapsPosition(b.getStart());
    }
    return a.overlaps(b);
  }

  private boolean canChangeRegisterAfterLinearScan(LiveIntervals intervals) {
    return intervals.hasRegister()
        && !intervals.isArgumentInterval()
        && !intervals.isInvokeRangeIntervals()
        && !intervals.getValue().isDefinedByInstructionSatisfying(Instruction::isMoveException)
        && !intervals.isNewStringInstanceDisallowingSpilling()
        && !isOperandOfRuntimeWorkaround(intervals);
  }

  private boolean canUseRegisterAfterLinearScan(LiveIntervals intervals, int register) {
    int registerEnd = register + intervals.requiredRegisters() - 1;
    return registerEnd <= allocator.getRegisterConstraint(intervals)
        && !allocator.isArgumentRegister(register)
        && !allocator.isDedicatedMoveExceptionRegister(register)
        && !allocator.isRegisterRejectedByRuntimeWorkaround(register, intervals);
  }

  private boolean isOperandOfRuntimeWorkaround(LiveIntervals intervals) {
    if (allocator.options().canUseSameArrayAndResultRegisterInArrayGetWide()
        && !allocator.options().canHaveOverlappingLongRegisterBug()
        && !allocator.options().canHaveCmpLongBug()
        && !allocator.options().canHaveLongToIntBug()) {
      return false;
    }
    for (Value value : intervals.getValues()) {
      for (Instruction user : value.uniqueUsers()) {
        Value outValue = user.outValue();
        if (outValue != null
            && outValue.getLiveIntervals() != null
            && allocator.needsRuntimeWorkaround(outValue.getLiveIntervals())) {
          return true;
        }
      }
    }
    return false;
  }

  private void changeRegister(LiveIntervals intervals, int register) {
    intervals.forEachRegister(r -> intervalsByRegister.get(r).remove(intervals));
    intervals.clearRegisterAssignment();
    intervals.setRegister(register);
    addToIntervalsByRegister(intervals);
  }
}

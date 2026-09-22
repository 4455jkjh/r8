// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DebugLocalsChange;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Move;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Set;

/**
 * Remove moves that are not actually used by instructions in exiting paths. These moves can arise
 * due to debug local info needing a particular value and the live-interval for it then moves it
 * back into the properly assigned register. If the register is only used for debug purposes, it is
 * safe to just remove the move and update the local information accordingly.
 */
public class DebugLocalUpdater extends FinalizerRewriterPass<AppInfo> {

  public DebugLocalUpdater(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "DebugLocalUpdater";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return appView.options().debug;
  }

  /**
   * Updates the debug local values so they are correct, the pass is required in debug mode. In
   * addition, removes moves that are not actually used by instructions in exiting paths. These
   * moves can arise due to debug local info needing a particular value and the live-interval for it
   * then moves it back into the properly assigned register. If the register is only used for debug
   * purposes, it is safe to just remove the move and update the local information accordingly.
   */
  @Override
  protected CodeRewriterResult rewriteCode(IRCode code, RegisterAllocator allocator) {
    boolean changed = false;
    for (BasicBlock block : code.blocks) {
      // Skip non-exit blocks.
      if (!block.getSuccessors().isEmpty()) {
        continue;
      }
      // Skip blocks with no locals at entry.
      Int2ReferenceMap<DebugLocalInfo> localsAtEntry = block.getLocalsAtEntry();
      if (localsAtEntry == null || localsAtEntry.isEmpty()) {
        continue;
      }
      // Find the locals state after spill moves.
      DebugLocalsChange postSpillLocalsChange = null;
      for (Instruction instruction : block.getInstructions()) {
        if (instruction.getNumber() != -1 || postSpillLocalsChange != null) {
          break;
        }
        postSpillLocalsChange = instruction.asDebugLocalsChange();
      }
      // Skip if the locals state did not change.
      if (postSpillLocalsChange == null
          || !postSpillLocalsChange.apply(new Int2ReferenceOpenHashMap<>(localsAtEntry))) {
        continue;
      }
      // Collect the moves that can safely be removed.
      Set<Move> unneededMoves = computeUnneededMoves(block, postSpillLocalsChange, allocator);
      if (unneededMoves.isEmpty()) {
        continue;
      }
      changed = true;
      Int2IntMap previousMapping = new Int2IntOpenHashMap();
      Int2IntMap mapping = new Int2IntOpenHashMap();
      InstructionListIterator it = block.listIterator();
      while (it.hasNext()) {
        Instruction instruction = it.next();
        if (instruction.isMove()) {
          Move move = instruction.asMove();
          int dst = allocator.getRegisterForValue(move.dest(), move.getNumber());
          if (unneededMoves.contains(move)) {
            int src = allocator.getRegisterForValue(move.src(), move.getNumber());
            int mappedSrc = mapping.getOrDefault(src, src);
            mapping.put(dst, mappedSrc);
            it.removeInstructionIgnoreOutValue();
          } else {
            mapping.remove(dst);
          }
        } else if (instruction.isDebugLocalsChange()) {
          DebugLocalsChange change = instruction.asDebugLocalsChange();
          updateDebugLocalsRegisterMap(previousMapping, change.getEnding());
          updateDebugLocalsRegisterMap(mapping, change.getStarting());
          previousMapping = mapping;
          mapping = new Int2IntOpenHashMap(previousMapping);
        }
      }
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  private Set<Move> computeUnneededMoves(
      BasicBlock block, DebugLocalsChange postSpillLocalsChange, RegisterAllocator allocator) {
    Set<Move> unneededMoves = Sets.newIdentityHashSet();
    IntSet usedRegisters = new IntOpenHashSet();
    IntSet clobberedRegisters = new IntOpenHashSet();
    // Backwards instruction scan collecting the registers used by actual instructions.
    boolean inEntrySpillMoves = false;
    for (Instruction ins = block.getLastInstruction(); ins != null; ins = ins.getPrev()) {
      if (ins == postSpillLocalsChange) {
        inEntrySpillMoves = true;
      }
      // If this is a move in the block-entry spill moves check if it is unneeded.
      if (inEntrySpillMoves && ins.isMove()) {
        Move move = ins.asMove();
        int dst = allocator.getRegisterForValue(move.dest(), move.getNumber());
        int src = allocator.getRegisterForValue(move.src(), move.getNumber());
        if (!usedRegisters.contains(dst) && !clobberedRegisters.contains(src)) {
          unneededMoves.add(move);
          continue;
        }
      }
      if (ins.outValue() != null && ins.outValue().needsRegister()) {
        int register = allocator.getRegisterForValue(ins.outValue(), ins.getNumber());
        // The register is defined anew, so uses before this are on distinct values.
        usedRegisters.remove(register);
        // Mark it clobbered to avoid any uses in locals after this point to become invalid.
        clobberedRegisters.add(register);
      }
      if (!ins.inValues().isEmpty()) {
        for (Value inValue : ins.inValues()) {
          if (inValue.needsRegister()) {
            int register = allocator.getRegisterForValue(inValue, ins.getNumber());
            // Record the register as being used.
            usedRegisters.add(register);
          }
        }
      }
    }
    return unneededMoves;
  }

  private void updateDebugLocalsRegisterMap(
      Int2IntMap mapping, Int2ReferenceMap<DebugLocalInfo> locals) {
    // If nothing is mapped nothing needs to be changed.
    if (mapping.isEmpty()) {
      return;
    }
    // Locals is final, so we copy and clear it during update.
    Int2ReferenceMap<DebugLocalInfo> copy = new Int2ReferenceOpenHashMap<>(locals);
    locals.clear();
    for (Entry<DebugLocalInfo> entry : copy.int2ReferenceEntrySet()) {
      int oldRegister = entry.getIntKey();
      int newRegister = mapping.getOrDefault(oldRegister, oldRegister);
      locals.put(newRegister, entry.getValue());
    }
  }
}

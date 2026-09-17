// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.DebugLocalsChange;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Move;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.conversion.finalizer.passes.PeepholeOptimizer;
import com.android.tools.r8.ir.conversion.finalizer.passes.TrivialGotosCollapser;
import com.android.tools.r8.ir.desugar.nest.D8NestBasedAccessDesugaring;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.optimize.RuntimeWorkaroundCodeRewriter;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap.Entry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Set;

public class IRToDexFinalizer extends IRFinalizer<DexCode> {

  private static final int PEEPHOLE_OPTIMIZATION_PASSES = 2;

  private final DeadCodeRemover deadCodeRemover;
  private final InternalOptions options;

  public IRToDexFinalizer(AppView<?> appView, DeadCodeRemover deadCodeRemover) {
    super(appView);
    this.deadCodeRemover = deadCodeRemover;
    this.options = appView.options();
  }

  @Override
  public DexCode finalizeCode(
      IRCode code,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      Timing timing,
      String previousPrintString) {
    if (options.canUseNestBasedAccess()) {
      D8NestBasedAccessDesugaring.checkAndFailOnIncompleteNests(appView);
    }
    DexEncodedMethod method = code.method();
    workaroundBugs(code, timing);
    code.traceBlocks();
    // Perform register allocation.
    LinearScanRegisterAllocator registerAllocator = performRegisterAllocation(code, method, timing);
    return new DexBuilder(code, bytecodeMetadataProvider, registerAllocator, options).build();
  }

  private void workaroundBugs(IRCode code, Timing timing) {
    RuntimeWorkaroundCodeRewriter.workaroundNumberConversionRegisterAllocationBug(appView, code);
    RuntimeWorkaroundCodeRewriter.workaroundDex2OatInliningIssue(appView, code);
    if (RuntimeWorkaroundCodeRewriter.workaroundInstanceOfTypeWeakeningInVerifier(appView, code)) {
      deadCodeRemover.run(code, timing);
    }
    RuntimeWorkaroundCodeRewriter.workaroundSwitchMaxIntBug(code, appView);
    RuntimeWorkaroundCodeRewriter.workaroundDex2OatLinkedListBug(code, options);
    RuntimeWorkaroundCodeRewriter.workaroundForwardingInitializerBug(code, options);
    RuntimeWorkaroundCodeRewriter.workaroundExceptionTargetingLoopHeaderBug(code, options);
    RuntimeWorkaroundCodeRewriter.rewriteJdk8272564Fix(code, options, appView);
    RuntimeWorkaroundCodeRewriter.reportInvokeSuperToInterfaceOnDalvik(code, options, appView);
    assert code.isConsistentSSA(appView);
  }

  @SuppressWarnings("UnusedVariable")
  private LinearScanRegisterAllocator performRegisterAllocation(
      IRCode code, DexEncodedMethod method, Timing timing) {
    // Always perform dead code elimination before register allocation. The register allocator
    // does not allow dead code (to make sure that we do not waste registers for unneeded values).
    assert deadCodeRemover.verifyNoDeadCode(code);
    timing.begin("Allocate registers");
    LinearScanRegisterAllocator registerAllocator =
        new LinearScanRegisterAllocator(appView, code, timing);
    registerAllocator.allocateRegisters();
    timing.end();
    TrivialGotosCollapser trivialGotosCollapser = new TrivialGotosCollapser(appView);
    timing.begin("Peephole optimize");
    for (int i = 0; i < PEEPHOLE_OPTIMIZATION_PASSES; i++) {
      trivialGotosCollapser.run(code, timing);
      PeepholeOptimizer.optimize(appView, code, registerAllocator);
    }
    timing.end();
    timing.begin("Clean up");
    removeUnneededMovesOnExitingPaths(code, registerAllocator);
    trivialGotosCollapser.run(code, timing);
    timing.end();
    return registerAllocator;
  }

  /**
   * Remove moves that are not actually used by instructions in exiting paths. These moves can arise
   * due to debug local info needing a particular value and the live-interval for it then moves it
   * back into the properly assigned register. If the register is only used for debug purposes, it
   * is safe to just remove the move and update the local information accordingly.
   */
  private void removeUnneededMovesOnExitingPaths(
      IRCode code, LinearScanRegisterAllocator allocator) {
    if (!allocator.options().debug) {
      return;
    }
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
  }

  private Set<Move> computeUnneededMoves(
      BasicBlock block,
      DebugLocalsChange postSpillLocalsChange,
      LinearScanRegisterAllocator allocator) {
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

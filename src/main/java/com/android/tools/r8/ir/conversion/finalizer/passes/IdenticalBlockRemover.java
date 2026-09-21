// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

import static com.android.tools.r8.ir.conversion.finalizer.passes.TrivialGotosCollapser.isFallthroughBlock;
import static com.android.tools.r8.ir.conversion.finalizer.passes.TrivialGotosCollapser.unlinkTrivialGotoBlock;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Sets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * If two blocks have the same code and successors, replace one of them with an empty block with a
 * goto to the other.
 */
public class IdenticalBlockRemover extends FinalizerRewriterPass<AppInfo> {

  public IdenticalBlockRemover(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "IdenticalBlockRemover";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return code.blocks.size() > 2;
  }

  /**
   * Iterates basic blocks in reverse order (bottom-up) to find blocks with identical instructions,
   * catch handlers, and outgoing successors (including exit blocks such as {@code Return} and
   * {@code Throw} that have no successors).
   *
   * <p>When a duplicate block {@code block} matches an earlier seen block {@code otherBlock},
   * {@code block}'s instructions are replaced with a single {@code Goto} to {@code otherBlock}. If
   * {@code block} is not targeted by a fallthrough (or if {@code otherBlock} starts with {@code
   * move-exception}), {@code block} is immediately unlinked and scheduled for removal so that its
   * predecessors now target {@code otherBlock} directly, allowing the bottom-up pass to cascade and
   * merge identical predecessor blocks as well.
   */
  @Override
  protected CodeRewriterResult rewriteCode(IRCode code, RegisterAllocator allocator) {
    boolean hasChanged = false;
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    BasicBlockInstructionsEquivalence equivalence =
        new BasicBlockInstructionsEquivalence(code, allocator);
    Map<Wrapper<BasicBlock>, BasicBlock> seenBlocks = new HashMap<>();
    Iterator<BasicBlock> iterator = code.blocks.descendingIterator();
    while (iterator.hasNext()) {
      BasicBlock block = iterator.next();
      if (block == code.entryBlock() || block.getInstructions().size() == 1) {
        continue;
      }
      BasicBlock otherBlock = seenBlocks.putIfAbsent(equivalence.wrap(block), block);
      if (otherBlock != null) {
        hasChanged = true;
        mergeBlocks(otherBlock, block, allocator, equivalence, seenBlocks, blocksToRemove);
      }
    }
    code.removeBlocks(blocksToRemove);
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  private static void mergeBlocks(
      BasicBlock kept,
      BasicBlock removed,
      RegisterAllocator allocator,
      BasicBlockInstructionsEquivalence equivalence,
      Map<Wrapper<BasicBlock>, BasicBlock> seenBlocks,
      Set<BasicBlock> blocksToRemove) {
    assert !allocator.options().debug || Objects.equals(removed.getPosition(), kept.getPosition());
    allocator.mergeBlocks(kept, removed);
    removed.clearCatchHandlers();
    removed.getInstructions().clear();
    for (BasicBlock succ : removed.getSuccessors()) {
      succ.removePredecessor(removed, null);
    }
    removed.getMutableSuccessors().clear();
    removed.link(kept);
    Goto exit = new Goto();
    exit.setPosition(kept.getPosition());
    removed.getInstructions().addLast(exit);

    if (kept.entry().isMoveException() || !isFallthroughBlock(removed)) {
      for (BasicBlock pred : removed.getPredecessors()) {
        seenBlocks.remove(equivalence.wrap(pred));
        equivalence.clearComputedHash(pred);
      }
      unlinkTrivialGotoBlock(removed, kept);
      blocksToRemove.add(removed);
    }
  }
}

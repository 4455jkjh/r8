// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

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
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * If two predecessors have the same code and successors, replace one of them with an empty block
 * with a goto to the other.
 */
public class IdenticalPredecessorBlocksRemover extends FinalizerRewriterPass<AppInfo> {

  public IdenticalPredecessorBlocksRemover(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "IdenticalPredecessorBlocksRemover";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return true;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code, RegisterAllocator allocator) {
    boolean hasChanged = false;
    Set<BasicBlock> blocksToRemove = Sets.newIdentityHashSet();
    BasicBlockInstructionsEquivalence equivalence =
        new BasicBlockInstructionsEquivalence(code, allocator);
    // Locate one block at a time that has identical predecessors. Rewrite those predecessors and
    // then start over. Restarting when one block's predecessors have been rewritten simplifies
    // the rewriting and reduces the size of the data structures.
    boolean changed;
    do {
      changed = false;
      for (BasicBlock block : code.blocks) {
        Map<Wrapper<BasicBlock>, Integer> blockToIndex = new HashMap<>();
        for (int predIndex = 0; predIndex < block.getPredecessors().size(); predIndex++) {
          BasicBlock pred = block.getPredecessors().get(predIndex);
          if (pred.getInstructions().size() == 1) {
            continue;
          }
          Wrapper<BasicBlock> wrapper = equivalence.wrap(pred);
          if (blockToIndex.containsKey(wrapper)) {
            changed = true;
            hasChanged = true;
            int otherPredIndex = blockToIndex.get(wrapper);
            BasicBlock otherPred = block.getPredecessors().get(otherPredIndex);
            assert !allocator.options().debug
                || Objects.equals(pred.getPosition(), otherPred.getPosition());
            allocator.mergeBlocks(otherPred, pred);
            pred.clearCatchHandlers();
            pred.getInstructions().clear();
            equivalence.clearComputedHash(pred);
            for (BasicBlock succ : pred.getSuccessors()) {
              succ.removePredecessor(pred, null);
            }
            pred.getMutableSuccessors().clear();
            pred.getMutableSuccessors().add(otherPred);
            assert !otherPred.getPredecessors().contains(pred);
            otherPred.getMutablePredecessors().add(pred);
            Goto exit = new Goto();
            exit.setPosition(otherPred.getPosition());
            pred.getInstructions().addLast(exit);

            // If `otherPred` is a catch handler with a `move-exception`, then we cannot
            // `goto otherPred`. In this case we eliminate the goto and remove the `pred` block.
            if (otherPred.entry().isMoveException()) {
              unlinkTrivialGotoBlock(pred, otherPred);
              blocksToRemove.add(pred);
            }
          } else {
            blockToIndex.put(wrapper, predIndex);
          }
        }
      }
    } while (changed);
    code.removeBlocks(blocksToRemove);
    return CodeRewriterResult.hasChanged(hasChanged);
  }
}

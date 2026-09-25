// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;

/**
 * Simplifies conditional branches when a block ending with an {@code If} instruction falls through
 * to a single-predecessor trivial {@code Goto} block {@code fallthroughBlock} positioned
 * immediately before {@code block}'s {@code trueTargetBlock}. Inverts {@code block}'s {@code If}
 * condition so {@code trueTargetBlock} becomes the fallthrough and redirects the branch directly to
 * {@code fallthroughBlock}'s target, removing {@code fallthroughBlock}.
 */
public class BranchDiamondInverter extends FinalizerRewriterPass<AppInfo> {

  public BranchDiamondInverter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "BranchDiamondInverter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return code.blocks.size() >= 2;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code, RegisterAllocator allocator) {
    boolean hasChanged = false;
    BasicBlockIterator iterator = code.listIterator();
    while (iterator.hasNext()) {
      BasicBlock block = iterator.next();
      if (block.exit().isIf()) {
        hasChanged |= tryInvertBranch(block, block.exit().asIf(), iterator, allocator);
      }
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  private boolean tryInvertBranch(
      BasicBlock block, If theIf, BasicBlockIterator iterator, RegisterAllocator allocator) {
    BasicBlock trueTargetBlock = theIf.getTrueTarget();
    BasicBlock originalFallthroughBlock = theIf.fallthroughBlock();
    assert iterator.peekNext() == originalFallthroughBlock;
    if (!originalFallthroughBlock.isTrivialGoto()
        || !originalFallthroughBlock.hasUniquePredecessor()
        || (originalFallthroughBlock.exit().getPosition().isSome()
            && !originalFallthroughBlock.exit().identicalPosition(theIf, allocator))) {
      return false;
    }
    iterator.next();
    if (!iterator.hasNext() || iterator.peekNext() != trueTargetBlock) {
      return false;
    }

    BasicBlock redirectTarget = originalFallthroughBlock.exit().asGoto().getTarget();
    if (!canRedirectToTarget(redirectTarget, block) || redirectTarget == trueTargetBlock) {
      return false;
    }

    theIf.invert();
    block.replaceSuccessor(originalFallthroughBlock, redirectTarget);
    if (!redirectTarget.getPredecessors().contains(block)) {
      redirectTarget.getMutablePredecessors().add(block);
    }
    originalFallthroughBlock.getMutablePredecessors().clear();
    originalFallthroughBlock.detachAllSuccessors();
    BasicBlock removedBlock = iterator.previous();
    assert removedBlock == originalFallthroughBlock;
    iterator.remove();
    return true;
  }

  private static boolean canRedirectToTarget(BasicBlock redirectTarget, BasicBlock sourceBlock) {
    return redirectTarget != sourceBlock
        && !redirectTarget.entry().isMoveException()
        && !sourceBlock.hasCatchSuccessor(redirectTarget);
  }
}

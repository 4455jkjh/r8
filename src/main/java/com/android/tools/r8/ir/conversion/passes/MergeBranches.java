// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.LazyDominatorTree;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.google.common.collect.Sets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Optimization pass that eliminates redundant conditional branches (If instructions).
 *
 * <p>When control flow splits at a conditional branch and later converges at a block containing
 * only a second If instruction evaluating the same condition, the second condition check is
 * redundant because its outcome is already determined along each incoming path.
 *
 * <p>This pass identifies candidate blocks with two predecessors, determines the known branch
 * outcome for each predecessor, updates affected phi instructions to preserve SSA form, and
 * retargets the predecessors' Goto instructions directly to the known branch targets, bypassing the
 * redundant If block.
 */
public class MergeBranches extends CodeRewriterPass<AppInfo> {

  public MergeBranches(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "MergeBranches";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return code.metadata().mayHaveIf();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    // Collects the branches to merge in one pass over the blocks.
    Map<BasicBlock, BranchToMerge> branchesToMerge = new LinkedHashMap<>();
    // Collects the set of "target join blocks" that will receive new phis as a result of
    // eliminating branches earlier in the code.
    Set<BasicBlock> blocksReceivingPhis = Sets.newIdentityHashSet();
    for (BasicBlock block : code.topologicallySortedBlocks()) {
      // Check if the block starts with an If instruction. This rules out that the block contains
      // any other instructions that would be skipped when bypassing this block.
      if (!block.entry().isIf()) {
        continue;
      }
      If ifInstruction = block.exit().asIf();

      // Ensure the candidate block has exactly two predecessors, both ending with a Goto.
      if (block.getPredecessors().size() != 2) {
        continue;
      }

      BasicBlock pred0 = block.getPredecessor(0);
      BasicBlock pred1 = block.getPredecessor(1);
      if (!pred0.exit().isGoto() || !pred1.exit().isGoto()) {
        continue;
      }

      // Determine the known branch target for each predecessor by checking if an earlier If
      // instruction along their control flow paths evaluated the same condition.
      BasicBlock target0 = getKnownTargetForPredecessor(ifInstruction, pred0);
      if (target0 == null) {
        continue;
      }

      BasicBlock target1 = getKnownTargetForPredecessor(ifInstruction, pred1);
      if (target1 == null) {
        continue;
      }

      // In the presence of phis, check that there is a target join block where we can move each phi
      // if necessary.
      BranchToMerge branchToMerge =
          new BranchToMerge(ifInstruction, pred0, pred1, target0, target1);
      if (canUpdatePhis(branchToMerge, blocksReceivingPhis)) {
        branchesToMerge.put(block, branchToMerge);
        if (hasOrWillHavePhis(branchToMerge, blocksReceivingPhis)) {
          blocksReceivingPhis.add(branchToMerge.getTargetJoinBlock());
        }
      }
    }

    if (branchesToMerge.isEmpty()) {
      return CodeRewriterResult.NO_CHANGE;
    }

    // Update phi uses before making any changes to the control flow, so that the same dominator
    // tree can be used for all dominance queries.
    AffectedValues affectedValues = new AffectedValues();
    LazyDominatorTree dominatorTree = new LazyDominatorTree(code);
    for (BranchToMerge branchToMerge : branchesToMerge.values()) {
      updatePhisBeforeRetargeting(code, branchToMerge, affectedValues, dominatorTree);
    }

    // Finally rewrite the control flow to skip the If instructions.
    for (BranchToMerge branchToMerge : branchesToMerge.values()) {
      retargetGotos(branchToMerge);
    }

    // Clean up unreachable blocks, dead phis, and restore SSA invariants.
    code.removeUnreachableBlocks(affectedValues);
    code.removeAllDeadAndTrivialPhis(affectedValues);
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    code.removeRedundantBlocks();
    return CodeRewriterResult.HAS_CHANGED;
  }

  /**
   * Walks backward along single-predecessor blocks starting from {@param pred} to find an earlier
   * {@link If} instruction that evaluates the same condition as {@param ifInstruction}.
   *
   * @return the target basic block that {@param ifInstruction} is guaranteed to jump to when coming
   *     from {@param pred}, or {@code null} if the outcome cannot be determined.
   */
  private BasicBlock getKnownTargetForPredecessor(If ifInstruction, BasicBlock pred) {
    BasicBlock current = pred;
    while (current != null) {
      // TODO(b/527421462): Consider looking further back even when the control flow splits.
      if (!current.hasUniquePredecessor()) {
        break;
      }
      BasicBlock prev = current.getUniquePredecessor();
      If prevIfInstruction = prev.exit().asIf();
      if (prevIfInstruction != null) {
        ConditionMatch match = matchCondition(ifInstruction, prevIfInstruction);
        if (match != null) {
          if (current == prevIfInstruction.getTrueTarget()
              && current != prevIfInstruction.fallthroughBlock()) {
            return match.isSame()
                ? ifInstruction.getTrueTarget()
                : ifInstruction.fallthroughBlock();
          } else if (current == prevIfInstruction.fallthroughBlock()
              && current != prevIfInstruction.getTrueTarget()) {
            return match.isSame()
                ? ifInstruction.fallthroughBlock()
                : ifInstruction.getTrueTarget();
          }
        }
      }
      current = prev;
    }
    return null;
  }

  /** Determines whether the two If instructions evaluate the same (or inverted) condition. */
  private ConditionMatch matchCondition(If ifInstruction, If prevIfInstruction) {
    if (ifInstruction.isZeroTest() != prevIfInstruction.isZeroTest()) {
      return null;
    }
    Value lhs = ifInstruction.lhs().getAliasedValue();
    Value prevLhs = prevIfInstruction.lhs().getAliasedValue();
    if (ifInstruction.isZeroTest()) {
      if (lhs == prevLhs) {
        return matchType(ifInstruction, prevIfInstruction);
      }
    } else {
      Value rhs = ifInstruction.rhs().getAliasedValue();
      Value prevRhs = prevIfInstruction.rhs().getAliasedValue();
      if (lhs == prevLhs && rhs == prevRhs) {
        return matchType(ifInstruction, prevIfInstruction);
      }
      if (lhs == prevRhs && rhs == prevLhs) {
        return matchType(ifInstruction, prevIfInstruction.getType().forSwappedOperands());
      }
    }
    return null;
  }

  private ConditionMatch matchType(If ifInstruction, If prevIfInstruction) {
    return matchType(ifInstruction, prevIfInstruction.getType());
  }

  private ConditionMatch matchType(If ifInstruction, IfType prevType) {
    if (ifInstruction.getType() == prevType) {
      return ConditionMatch.SAME;
    }
    if (ifInstruction.getType() == prevType.inverted()) {
      return ConditionMatch.INVERTED;
    }
    return null;
  }

  private enum ConditionMatch {
    SAME,
    INVERTED;

    public boolean isSame() {
      return this == SAME;
    }
  }

  private boolean canUpdatePhis(BranchToMerge branchToMerge, Set<BasicBlock> blocksReceivingPhis) {
    if (hasOrWillHavePhis(branchToMerge, blocksReceivingPhis)) {
      BasicBlock targetJoinBlock = branchToMerge.getTargetJoinBlock();
      return targetJoinBlock != null && targetJoinBlock.getPredecessors().size() == 2;
    }
    return true;
  }

  private boolean hasOrWillHavePhis(
      BranchToMerge branchToMerge, Set<BasicBlock> blocksReceivingPhis) {
    BasicBlock ifBlock = branchToMerge.getIfBlock();
    return !ifBlock.getPhis().isEmpty() || blocksReceivingPhis.contains(ifBlock);
  }

  /**
   * Updates or rewrites phis in {@param block} and {@param targetJoinBlock} before retargeting
   * predecessors' {@link Goto} instructions to bypass {@param block}.
   */
  private void updatePhisBeforeRetargeting(
      IRCode code,
      BranchToMerge branchToMerge,
      AffectedValues affectedValues,
      LazyDominatorTree dominatorTree) {
    BasicBlock ifBlock = branchToMerge.getIfBlock();
    for (Phi phi : ifBlock.getPhis()) {
      if (!phi.hasUsers() && !phi.hasPhiUsers()) {
        continue;
      }

      Value operand0 = phi.getOperand(0);
      Value operand1 = phi.getOperand(1);
      if (operand0 == operand1 && dominatorTree.dominatedBy(ifBlock, operand0.getBlock())) {
        phi.replaceUsers(operand0, affectedValues);
        continue;
      }

      // All uses of the phi inside the "then" or "else" branch can be updated to use the phi
      // operand instead.
      BasicBlock targetJoinBlock = branchToMerge.getTargetJoinBlock();
      for (Instruction user : phi.uniqueUsers()) {
        if (!dominatorTree.dominatedBy(user.getBlock(), targetJoinBlock)) {
          if (dominatorTree.dominatedBy(user.getBlock(), branchToMerge.getTarget0())) {
            user.replaceValue(phi, operand0, affectedValues);
          } else {
            assert dominatorTree.dominatedBy(user.getBlock(), branchToMerge.getTarget1());
            user.replaceValue(phi, operand1, affectedValues);
          }
        }
      }

      // Similarly all phi uses of the phi in the target join block can be updated to use the phi
      // operand instead.
      for (Phi user : phi.uniquePhiUsers()) {
        if (user.getBlock() == targetJoinBlock) {
          for (int operandIndex = 0; operandIndex < user.getOperands().size(); operandIndex++) {
            if (user.getOperand(operandIndex) == phi) {
              BasicBlock operandPredBlock = user.getBlock().getPredecessor(operandIndex);
              Value operand =
                  getOperandForTargetJoinBlockPredecessor(
                      operandPredBlock, branchToMerge, dominatorTree, operand0, operand1);
              user.replaceOperandAt(operandIndex, operand, affectedValues);
            }
          }
        }
      }

      // If phi uses remain, then there exists uses of the phi that come after the target join
      // block. In this case we materialize a new phi in the target join block and replace all
      // remaining uses by this new phi.
      if (phi.hasUsers() || phi.hasPhiUsers()) {
        Phi newPhi = code.createPhi(targetJoinBlock, TypeElement.getBottom());
        for (BasicBlock pred : targetJoinBlock.getPredecessors()) {
          Value operand =
              getOperandForTargetJoinBlockPredecessor(
                  pred, branchToMerge, dominatorTree, operand0, operand1);
          newPhi.appendOperand(operand);
        }
        newPhi.setType(newPhi.computePhiType(appView));
        phi.replaceUsers(newPhi, affectedValues);
      }
    }
  }

  private Value getOperandForTargetJoinBlockPredecessor(
      BasicBlock operandPredBlock,
      BranchToMerge branchToMerge,
      LazyDominatorTree dominatorTree,
      Value operand0,
      Value operand1) {
    if (operandPredBlock == branchToMerge.getIfBlock()) {
      if (branchToMerge.getTarget0() == branchToMerge.getTargetJoinBlock()) {
        return operand0;
      }
      assert branchToMerge.getTarget1() == branchToMerge.getTargetJoinBlock();
      return operand1;
    }
    if (dominatorTree.dominatedBy(operandPredBlock, branchToMerge.getTarget0())) {
      return operand0;
    } else {
      assert dominatorTree.dominatedBy(operandPredBlock, branchToMerge.getTarget1());
      return operand1;
    }
  }

  private void retargetGotos(BranchToMerge branchToMerge) {
    // Redirect the Gotos in pred0 and pred1 directly to target0 and target1, bypassing the block
    // containing the If instruction.
    retargetGoto(branchToMerge.getPred0().exit().asGoto(), branchToMerge.getTarget0());
    retargetGoto(branchToMerge.getPred1().exit().asGoto(), branchToMerge.getTarget1());
  }

  private void retargetGoto(Goto gotoInstruction, BasicBlock newTarget) {
    BasicBlock initialTarget = gotoInstruction.getTarget();
    int predIndex = initialTarget.getPredecessors().indexOf(gotoInstruction.getBlock());
    for (Phi phi : initialTarget.getPhis()) {
      phi.removeOperand(predIndex);
    }
    gotoInstruction.setTarget(newTarget);
  }

  /**
   * Stores the blocks relevant for a given merge candidate.
   *
   * <pre>
   *    pred0  pred1
   *       \    /
   *    ifInstruction
   *       /    \
   *   target0 target1
   *         ...
   *       \    /
   *   targetJoinBlock
   * </pre>
   *
   * <p>This optimization aims to retarget pred0 -> target0 and pred1 -> target1.
   */
  private static class BranchToMerge {

    private final If ifInstruction;
    private final BasicBlock pred0;
    private final BasicBlock pred1;
    private final BasicBlock target0;
    private final BasicBlock target1;
    private final BasicBlock targetJoinBlock;

    BranchToMerge(
        If ifInstruction,
        BasicBlock pred0,
        BasicBlock pred1,
        BasicBlock target0,
        BasicBlock target1) {
      this.ifInstruction = ifInstruction;
      this.pred0 = pred0;
      this.pred1 = pred1;
      this.target0 = target0;
      this.target1 = target1;
      this.targetJoinBlock = findTargetJoinBlock(target0, target1);
    }

    /**
     * Finds the downstream basic block where the true and false targets of an If instruction
     * rejoin.
     */
    private static BasicBlock findTargetJoinBlock(BasicBlock target0, BasicBlock target1) {
      while (target0.exit().isGoto() && target0.hasUniqueSuccessorWithUniquePredecessor()) {
        target0 = target0.getUniqueSuccessor();
      }
      while (target1.exit().isGoto() && target1.hasUniqueSuccessorWithUniquePredecessor()) {
        target1 = target1.getUniqueSuccessor();
      }
      if (target0 == target1) {
        return target0;
      }
      if (target0.exit().isGoto() && target1.exit().isGoto()) {
        if (target0.getUniqueNormalSuccessor() == target1.getUniqueNormalSuccessor()) {
          return target0.getUniqueNormalSuccessor();
        }
      }
      if (target1.exit().isGoto() && target1.getUniqueNormalSuccessor() == target0) {
        return target0;
      }
      if (target0.exit().isGoto() && target0.getUniqueNormalSuccessor() == target1) {
        return target1;
      }
      return null;
    }

    public BasicBlock getIfBlock() {
      return ifInstruction.getBlock();
    }

    public BasicBlock getPred0() {
      return pred0;
    }

    public BasicBlock getPred1() {
      return pred1;
    }

    public BasicBlock getTarget0() {
      return target0;
    }

    public BasicBlock getTarget1() {
      return target1;
    }

    public BasicBlock getTargetJoinBlock() {
      return targetJoinBlock;
    }
  }
}

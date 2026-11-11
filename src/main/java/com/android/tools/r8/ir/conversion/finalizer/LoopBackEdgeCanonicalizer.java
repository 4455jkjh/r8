// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.finalizer;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BackTo;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.timing.Timing;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Objects;
import java.util.Set;

/**
 * Canonicalizes loops such that each loop has a single back edge, which is a {@link BackTo} from
 * the loop latch to the loop header. All other branches can then be emitted as forward branches.
 *
 * <p>ART performs hotness counting and suspend checks on backward branches. When a loop has several
 * backward branches, this work is duplicated on each of them. Having a single backward branch per
 * loop, and no other backward branches, avoids this duplication and avoids doing this work outside
 * of loops.
 *
 * <p>The back edges are the normal edges that go backwards in the reverse postorder computed by
 * {@link IRCode#topologicallySortedBlocks()}, which is the order that {@link IRCode#traceBlocks()}
 * lays out the blocks in. For reducible control flow these are exactly the loop back edges. For
 * irreducible control flow they depend on the order of the depth first search, but every cycle
 * still has exactly one back edge.
 *
 * <p>When a loop header has multiple back edges, or a back edge that is not a goto, the back edges
 * are redirected to a new latch block ending with a {@link BackTo} to the loop header. The phis of
 * the loop header are split such that the latch block has phis that merge the values flowing in
 * from the back edges.
 */
public class LoopBackEdgeCanonicalizer extends CodeRewriterPass<AppInfo> {

  public LoopBackEdgeCanonicalizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "LoopBackEdgeCanonicalizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return options.getTestingOptions().enableLoopBackEdgeCanonicalizer
        && code.getBlocks().size() >= 2;
  }

  /**
   * Merges each join block that only has phis and a goto into its target, when the block or the
   * target is a loop header. Such blocks are, e.g., the header of an outer loop that directly
   * enters an inner loop, or the join of the paths that resume and continue a loop in a coroutine
   * state machine. Without merging, the back edges through these blocks cannot share a latch with
   * the other back edges of the loop, which keeps identical code on these back edges apart
   * (b/577294978).
   *
   * <p>This runs before the runtime workarounds, since the removal of trivial phis and dead code
   * may expose new patterns for these, whereas the latches are created after the runtime
   * workarounds, since these are not aware of {@link BackTo}.
   */
  public void mergeTrivialGotosIntoLoopHeaders(
      IRCode code, DeadCodeRemover deadCodeRemover, Timing timing) {
    if (!shouldRewriteCode(code, null)) {
      return;
    }
    timing.begin("Merge trivial gotos into loop headers");
    Set<BasicBlock> loopHeaders = computeLoopHeaders(code);
    boolean hasMergedBlocks = false;
    while (!loopHeaders.isEmpty() && mergeTrivialGotoBlocks(code, loopHeaders)) {
      hasMergedBlocks = true;
    }
    if (hasMergedBlocks) {
      // Merging can make phis trivial, e.g., phi_T = phi(v, phi_B) with phi_B = phi(v, phi_T), and
      // removing these can make code dead.
      AffectedValues affectedValues = new AffectedValues();
      code.removeAllDeadAndTrivialPhis(affectedValues);
      affectedValues.narrowingWithAssumeRemoval(appView, code);
      deadCodeRemover.run(code, timing);
      assert code.isConsistentSSA(appView);
    }
    timing.end();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    Set<BasicBlock> loopHeaders = computeLoopHeaders(code);
    if (loopHeaders.isEmpty()) {
      return noChange();
    }
    boolean needsCriticalEdgeSplitting = false;
    for (BasicBlock header : loopHeaders) {
      List<BasicBlock> backEdgeSources = new ArrayList<>();
      for (BasicBlock predecessor : header.getPredecessors()) {
        if (predecessor.getNumber() >= header.getNumber()) {
          backEdgeSources.add(predecessor);
        }
      }
      if (backEdgeSources.size() == 1) {
        BasicBlock backEdgeSource = backEdgeSources.get(0);
        if (backEdgeSource.exit().isGoto()) {
          backEdgeSource.exit().replace(new BackTo());
          continue;
        }
      }
      createLatch(code, header, backEdgeSources);
      needsCriticalEdgeSplitting = true;
    }
    if (needsCriticalEdgeSplitting) {
      code.splitCriticalEdges();
    }
    return CodeRewriterResult.HAS_CHANGED;
  }

  @SuppressWarnings("JdkObsolete")
  private static Set<BasicBlock> computeLoopHeaders(IRCode code) {
    // Number the blocks in reverse postorder such that the back edges are the edges to a block
    // with a number smaller than or equal to the number of the source block.
    code.blocks = new LinkedList<>(code.topologicallySortedBlocks());
    code.resetNumbers();
    Set<BasicBlock> loopHeaders = new LinkedHashSet<>();
    for (BasicBlock block : code.getBlocks()) {
      for (BasicBlock successor : block.getNormalSuccessors()) {
        if (successor.getNumber() <= block.getNumber()) {
          loopHeaders.add(successor);
        }
      }
    }
    return loopHeaders;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean mergeTrivialGotoBlocks(IRCode code, Set<BasicBlock> loopHeaders) {
    boolean changed = false;
    ListIterator<BasicBlock> blockIterator = code.getBlocks().listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      if (block == code.entryBlock()
          || !block.isTrivialGoto()
          || block.getPredecessors().size() < 2) {
        continue;
      }
      BasicBlock target = block.exit().asGoto().getTarget();
      if ((loopHeaders.contains(block) || loopHeaders.contains(target))
          && canMergeIntoTarget(block, target)) {
        mergeIntoTarget(block, target);
        blockIterator.remove();
        if (loopHeaders.remove(block)) {
          loopHeaders.add(target);
        }
        changed = true;
      }
    }
    return changed;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean canMergeIntoTarget(BasicBlock block, BasicBlock target) {
    if (target == block
        || target.getPredecessors().size() < 2
        || !block.exit().getDebugValues().isEmpty()) {
      return false;
    }
    // The predecessors of the block become predecessors of the target, which has multiple
    // predecessors. They must not introduce critical edges. As they only have the block as
    // successor, they are not already predecessors of the target.
    for (BasicBlock predecessor : block.getPredecessors()) {
      if (predecessor == target || !predecessor.hasOneNormalExit()) {
        return false;
      }
    }
    if (!block.hasPhis()) {
      return true;
    }
    int predIndex = target.getPredecessors().indexOf(block);
    for (Phi targetPhi : target.getPhis()) {
      Value operand = targetPhi.getOperand(predIndex);
      if (operand.isPhi()
          && operand.getBlock() == block
          && !Objects.equals(targetPhi.getLocalInfo(), operand.getLocalInfo())) {
        return false;
      }
    }
    for (Phi phi : block.getPhis()) {
      if (phi.hasDebugUsers() || hasUseOtherThanAsTargetPhiOperand(phi, target, predIndex)) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean hasUseOtherThanAsTargetPhiOperand(
      Phi phi, BasicBlock target, int predIndex) {
    if (phi.hasUsers()) {
      return true;
    }
    for (Phi user : phi.uniquePhiUsers()) {
      if (user.getBlock() != target) {
        return true;
      }
      List<Value> operands = user.getOperands();
      for (int i = 0; i < operands.size(); i++) {
        if (i != predIndex && operands.get(i) == phi) {
          return true;
        }
      }
    }
    return false;
  }

  @SuppressWarnings("ReferenceEquality")
  private static void mergeIntoTarget(BasicBlock block, BasicBlock target) {
    assert canMergeIntoTarget(block, target);
    assert !block.hasCatchHandlers();
    // Replace the block by its predecessors in the predecessors of the target, and the operand for
    // the block in the phis of the target by the operands for the predecessors of the block.
    int predIndex = target.getPredecessors().indexOf(block);
    List<Integer> predIndexList = Collections.singletonList(predIndex);
    List<BasicBlock> blockPredecessors = block.getPredecessors();
    for (Phi targetPhi : target.getPhis()) {
      Value operand = targetPhi.getOperand(predIndex);
      targetPhi.removeOperandsByIndex(predIndexList);
      for (int i = 0; i < blockPredecessors.size(); i++) {
        targetPhi.appendOperand(
            operand.isPhi() && operand.getBlock() == block
                ? operand.asPhi().getOperand(i)
                : operand);
      }
    }
    target.removePredecessorsByIndex(predIndexList);
    for (BasicBlock predecessor : blockPredecessors) {
      predecessor.replaceSuccessor(block, target);
      target.getMutablePredecessors().add(predecessor);
    }
    for (Phi phi : new ArrayList<>(block.getPhis())) {
      assert !phi.isUsed();
      phi.removeDeadPhi();
    }
    block.getMutablePredecessors().clear();
    block.getMutableSuccessors().clear();
    block.getInstructions().clear();
  }

  private void createLatch(IRCode code, BasicBlock header, List<BasicBlock> backEdgeSources) {
    BasicBlock latch = new BasicBlock(code.metadata());
    latch.setNumber(code.getNextBlockNumber());
    BackTo backTo = new BackTo();
    // The latch merges back edges from different source lines. Give it the position of the loop
    // header, which is where all the back edges go, so that stepping does not show the line of an
    // unrelated back edge.
    backTo.setPosition(header.getPosition());
    latch.add(backTo, code.metadata());
    latch.close(null);
    // Redirect the back edges to the latch, in the order of the predecessors of the header.
    List<BasicBlock> headerPredecessors = header.getPredecessors();
    List<Integer> backEdgeIndices = new ArrayList<>(backEdgeSources.size());
    for (int i = 0; i < headerPredecessors.size(); i++) {
      BasicBlock predecessor = headerPredecessors.get(i);
      if (backEdgeSources.contains(predecessor)) {
        backEdgeIndices.add(i);
        predecessor.replaceSuccessor(header, latch);
        latch.getMutablePredecessors().add(predecessor);
      }
    }
    assert backEdgeIndices.size() == backEdgeSources.size();
    // Move the phi operands of the back edges to new phis in the latch.
    List<Phi> latchPhis = new ArrayList<>(header.getPhis().size());
    for (Phi headerPhi : header.getPhis()) {
      // In debug mode, the operands of a phi with local info must have the same local info.
      Phi latchPhi = code.createPhi(latch, TypeElement.getBottom(), headerPhi.getLocalInfo());
      for (int index : backEdgeIndices) {
        latchPhi.appendOperand(headerPhi.getOperand(index));
      }
      headerPhi.removeOperandsByIndex(backEdgeIndices);
      headerPhi.appendOperand(latchPhi);
      latchPhis.add(latchPhi);
    }
    header.removePredecessorsByIndex(backEdgeIndices);
    latch.link(header);
    for (Phi latchPhi : latchPhis) {
      latchPhi.setType(latchPhi.computePhiType(appView));
      latchPhi.removeTrivialPhi();
    }
    code.blocks.add(latch);
  }
}

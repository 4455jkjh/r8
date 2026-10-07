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
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.InternalOptions;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
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

  public static boolean isEnabled(InternalOptions options, IRCode code) {
    return options.getTestingOptions().enableLoopBackEdgeCanonicalizer
        && !options.shouldCompileMethodInDebugMode(code.context());
  }

  @Override
  protected String getRewriterId() {
    return "LoopBackEdgeCanonicalizer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return isEnabled(options, code) && code.getBlocks().size() >= 2;
  }

  @Override
  @SuppressWarnings("JdkObsolete")
  protected CodeRewriterResult rewriteCode(IRCode code) {
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
      Phi latchPhi = code.createPhi(latch, TypeElement.getBottom());
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

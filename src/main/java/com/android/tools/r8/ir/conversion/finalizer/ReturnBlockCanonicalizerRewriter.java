// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.finalizer;

import static com.android.tools.r8.utils.internal.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Canonicalize return blocks for oat size since ART return instructions are expensive (they need to
 * restore callee saved registers).
 */
public class ReturnBlockCanonicalizerRewriter extends CodeRewriterPass<AppInfo> {

  public ReturnBlockCanonicalizerRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "ReturnBlockCanonicalizerRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return !options.shouldCompileMethodInDebugMode(code.context()) && code.getBlocks().size() >= 3;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean changed = false;
    for (List<BasicBlock> exitBlocks : computeMergeableExitBlockGroups(code)) {
      if (exitBlocks.size() > 1) {
        canonicalizeReturnBlocks(code, exitBlocks);
        changed = true;
      }
    }
    if (!changed) {
      return noChange();
    }
    code.splitCriticalEdges();
    code.removeRedundantBlocks();
    return CodeRewriterResult.HAS_CHANGED;
  }

  /**
   * Returns groups of normal exit blocks that can be merged into a single return block. See {@link
   * Return#shouldOnlyMergeIdenticalReturnValues}.
   */
  private Collection<List<BasicBlock>> computeMergeableExitBlockGroups(IRCode code) {
    List<BasicBlock> normalExits = code.computeNormalExitBlocks();
    if (!Return.shouldOnlyMergeIdenticalReturnValues(options, code.context())) {
      return Collections.singletonList(normalExits);
    }
    Map<Value, List<BasicBlock>> returnValueToExitBlocks = new LinkedHashMap<>();
    for (BasicBlock exitBlock : normalExits) {
      returnValueToExitBlocks
          .computeIfAbsent(getReturnValue(exitBlock), ignoreKey(ArrayList::new))
          .add(exitBlock);
    }
    return returnValueToExitBlocks.values();
  }

  private void canonicalizeReturnBlocks(IRCode code, List<BasicBlock> exitBlocks) {
    Return firstReturn = exitBlocks.get(0).exit().asReturn();
    BasicBlock newExitBlock = new BasicBlock(code.metadata());
    newExitBlock.setNumber(code.getNextBlockNumber());
    Phi phi =
        firstReturn.isReturnVoid() ? null : code.createPhi(newExitBlock, TypeElement.getBottom());
    newExitBlock.add(
        Return.builder().setReturnValue(phi).setPosition(firstReturn.getPosition()).build(),
        code.metadata());
    newExitBlock.close(null);
    for (BasicBlock exitBlock : exitBlocks) {
      if (phi != null) {
        phi.appendOperand(getReturnValue(exitBlock));
      }
      exitBlock.exit().replace(new Goto());
      exitBlock.link(newExitBlock);
    }
    if (phi != null) {
      phi.setType(phi.computePhiType(appView));
      phi.removeTrivialPhi();
    }
    code.blocks.add(newExitBlock);
  }

  private static Value getReturnValue(BasicBlock exitBlock) {
    return exitBlock.exit().asReturn().returnValue();
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

/**
 * Post-register-allocation pass that decomposes {@code code.blocks} into maximal fallthrough chains
 * and reorders chains in O(n) time to coalesce adjacent {@code TryItem} ranges and create 0-byte
 * {@code Goto} fallthrough transitions.
 */
public class BasicBlockReorderer extends FinalizerRewriterPass<AppInfo> {

  public BasicBlockReorderer(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "BasicBlockReorderer";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code) {
    return code.blocks.size() >= 2;
  }

  /** A maximal fallthrough chain of consecutive blocks that must stay adjacent and in order. */
  private static class Chain {
    final List<BasicBlock> blocks = new ArrayList<>();
    // The CatchHandlers of the first throwing block in the chain, or null if the first throwing
    // block is not covered by any catch handler.
    CatchHandlers<BasicBlock> entryTryHandler = null;
    boolean entryTryHandlerResolved = false;

    void addBlock(BasicBlock block) {
      blocks.add(block);
      if (!entryTryHandlerResolved && (!block.getCatchHandlers().isEmpty() || block.canThrow())) {
        entryTryHandlerResolved = true;
        if (!block.getCatchHandlers().isEmpty()) {
          entryTryHandler = block.getCatchHandlers();
        }
      }
    }

    BasicBlock getHeadBlock() {
      return blocks.get(0);
    }

    CatchHandlers<BasicBlock> computeTryHandlerAtChainExit(
        CatchHandlers<BasicBlock> activeTryHandler) {
      ListIterator<BasicBlock> iterator = blocks.listIterator(blocks.size());
      while (iterator.hasPrevious()) {
        BasicBlock block = iterator.previous();
        if (!block.getCatchHandlers().isEmpty()) {
          return block.getCatchHandlers();
        }
        if (block.canThrow()) {
          return null;
        }
      }
      return activeTryHandler;
    }

    boolean preservesTryHandlerAtExit() {
      assert entryTryHandler != null;
      return entryTryHandler.equals(computeTryHandlerAtChainExit(entryTryHandler));
    }
  }

  /**
   * Reorders basic block layout in O(n) time without breaking existing fallthrough edges:
   *
   * <ol>
   *   <li><b>Maximal fallthrough chain decomposition</b>: Groups consecutive blocks in {@code
   *       code.blocks} that are connected by {@code If} fallthroughs, {@code Switch} fallthroughs,
   *       or already-adjacent {@code Goto} jumps into atomic chains, indexed by their head block
   *       ({@code unplacedChains}, in original order) and by their entry {@code CatchHandlers}
   *       ({@code chainsByEntryTryHandler}).
   *   <li><b>Linear-time {@code TryItem} and {@code Goto} chain ordering</b>: Starting from the
   *       entry chain, repeatedly selects the next chain with {@link #selectNextChain}, preferring
   *       chains that keep the active {@code CatchHandlers} range open (saving an 8-byte {@code
   *       TryItem} entry in the DEX exception table) and chains targeted by the previous chain's
   *       {@code Goto} (allowing {@code TrivialGotosCollapser} to elide the branch).
   * </ol>
   */
  @Override
  protected CodeRewriterResult rewriteCode(IRCode code, RegisterAllocator allocator) {
    Map<BasicBlock, Chain> unplacedChains = new LinkedHashMap<>();
    Map<CatchHandlers<BasicBlock>, Deque<Chain>> chainsByEntryTryHandler = new HashMap<>();
    computeFallthroughChains(code, unplacedChains, chainsByEntryTryHandler);
    boolean orderChanged = orderFallthroughChains(code, unplacedChains, chainsByEntryTryHandler);
    return CodeRewriterResult.hasChanged(orderChanged);
  }

  private static void computeFallthroughChains(
      IRCode code,
      Map<BasicBlock, Chain> unplacedChains,
      Map<CatchHandlers<BasicBlock>, Deque<Chain>> chainsByEntryTryHandler) {
    Chain currentChain = null;
    BasicBlock previousBlock = null;
    for (BasicBlock block : code.blocks) {
      if (previousBlock == null || !isConnectedByFallthrough(previousBlock, block)) {
        currentChain = new Chain();
        unplacedChains.put(block, currentChain);
      }
      currentChain.addBlock(block);
      previousBlock = block;
    }
    assert previousBlock == null || previousBlock.exit().fallthroughBlock() == null;
    // Enqueue chains that also exit with the same try handler before chains that end with a
    // different try handler so that C2 -> C3 -> C1 is preferred when C1 changes handler on exit.
    for (Chain chain : unplacedChains.values()) {
      if (chain.entryTryHandler != null && chain.preservesTryHandlerAtExit()) {
        chainsByEntryTryHandler
            .computeIfAbsent(chain.entryTryHandler, k -> new ArrayDeque<>())
            .addLast(chain);
      }
    }
    for (Chain chain : unplacedChains.values()) {
      if (chain.entryTryHandler != null && !chain.preservesTryHandlerAtExit()) {
        chainsByEntryTryHandler
            .computeIfAbsent(chain.entryTryHandler, k -> new ArrayDeque<>())
            .addLast(chain);
      }
    }
  }

  private static boolean orderFallthroughChains(
      IRCode code,
      Map<BasicBlock, Chain> unplacedChains,
      Map<CatchHandlers<BasicBlock>, Deque<Chain>> chainsByEntryTryHandler) {
    LinkedList<BasicBlock> orderedBlocks = new LinkedList<>();
    CatchHandlers<BasicBlock> activeTryHandler = null;
    Chain selectedChain = unplacedChains.get(code.entryBlock());
    boolean orderChanged = false;
    while (selectedChain != null) {
      orderChanged |= selectedChain != unplacedChains.values().iterator().next();
      unplacedChains.remove(selectedChain.getHeadBlock());
      orderedBlocks.addAll(selectedChain.blocks);
      activeTryHandler = selectedChain.computeTryHandlerAtChainExit(activeTryHandler);
      selectedChain =
          selectNextChain(
              orderedBlocks.getLast(), activeTryHandler, unplacedChains, chainsByEntryTryHandler);
    }
    assert orderChanged != code.blocks.equals(orderedBlocks);
    code.blocks = orderedBlocks;
    return orderChanged;
  }

  private static Chain selectNextChain(
      BasicBlock lastBlock,
      CatchHandlers<BasicBlock> activeTryHandler,
      Map<BasicBlock, Chain> unplacedChains,
      Map<CatchHandlers<BasicBlock>, Deque<Chain>> chainsByEntryTryHandler) {
    if (unplacedChains.isEmpty()) {
      return null;
    }
    // The chain targeted by the trailing Goto, which becomes a 0-byte fallthrough if selected.
    Chain gotoChain =
        lastBlock.exit().isGoto()
            ? unplacedChains.get(lastBlock.exit().asGoto().getTarget())
            : null;
    // Prefer the Goto chain if it also keeps the active try range open.
    if (gotoChain != null
        && (activeTryHandler == null || activeTryHandler.equals(gotoChain.entryTryHandler))) {
      return gotoChain;
    }
    // Otherwise prefer the first unplaced chain that keeps the active try range open (chains that
    // also exit with activeTryHandler are ordered first in matchingChains).
    if (activeTryHandler != null) {
      Deque<Chain> matchingChains = chainsByEntryTryHandler.get(activeTryHandler);
      while (matchingChains != null && !matchingChains.isEmpty()) {
        Chain candidate = matchingChains.pollFirst();
        if (unplacedChains.containsKey(candidate.getHeadBlock())) {
          return candidate;
        }
      }
    }
    // Otherwise take the Goto chain, or the next unplaced chain in original order.
    return gotoChain != null ? gotoChain : unplacedChains.values().iterator().next();
  }

  private static boolean isConnectedByFallthrough(BasicBlock currentBlock, BasicBlock nextBlock) {
    JumpInstruction exit = currentBlock.exit();
    if (exit.isIf() || exit.isSwitch()) {
      assert exit.fallthroughBlock() == nextBlock;
      return true;
    }
    return exit.isGoto() && exit.asGoto().getTarget() == nextBlock;
  }
}

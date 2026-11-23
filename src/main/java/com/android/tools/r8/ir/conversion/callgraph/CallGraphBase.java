// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.ToIntFunction;

public abstract class CallGraphBase<N extends NodeBase<N>> {

  final Map<DexMethod, N> nodes;
  private int remainingNodes;

  // The nodes that became leaves (or roots) when the previous wave was extracted.
  private List<N> nextWave;

  public CallGraphBase(Map<DexMethod, N> nodes) {
    this.nodes = nodes;
    this.remainingNodes = nodes.size();
  }

  public boolean isEmpty() {
    return remainingNodes == 0;
  }

  public N getNode(ProgramMethod method) {
    return nodes.get(method.getReference());
  }

  public Collection<N> getNodes() {
    return nodes.values();
  }

  /**
   * Extracts the next wave of nodes whose remaining degree is zero. Extraction tracks progress
   * using per-node degree counters instead of mutating node edge sets, and clears {@link #nodes}
   * once all nodes have been extracted.
   */
  protected ProgramMethodSet extractNodes(
      ToIntFunction<N> initialDegree, BiConsumer<N, List<N>> clean) {
    // Only the first wave is found by scanning all nodes.
    List<N> removed;
    if (nextWave != null) {
      removed = nextWave;
    } else {
      removed = new ArrayList<>();
      for (N node : nodes.values()) {
        int degree = initialDegree.applyAsInt(node);
        node.setRemainingDegree(degree);
        if (degree == 0) {
          removed.add(node);
        }
      }
    }
    ProgramMethodSet result =
        InternalOptions.DETERMINISTIC_DEBUGGING
            ? SortedProgramMethodSet.create()
            : ProgramMethodSet.create(removed.size());
    nextWave = new ArrayList<>();
    remainingNodes -= removed.size();
    for (N node : removed) {
      assert node.getRemainingDegree() == 0;
      result.add(node.getProgramMethod());
      clean.accept(node, nextWave);
    }
    assert !result.isEmpty();
    assert !nextWave.isEmpty() || remainingNodes == 0;
    if (remainingNodes == 0) {
      nodes.clear();
      nextWave = null;
    }
    return result;
  }
}

// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.MethodProcessorWithWave;
import com.android.tools.r8.ir.conversion.callgraph.CallSiteInformation.CallGraphBasedCallSiteInformation;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.android.tools.r8.utils.internal.ListUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Call graph representation.
 *
 * <p>Each node in the graph contain the methods called and the calling methods. For virtual and
 * interface calls all potential calls from subtypes are recorded.
 *
 * <p>Only methods in the program - not library methods - are
 * represented. @SuppressWarnings("UnusedVariable")
 *
 * <p>The directional edges are represented as sets of nodes in each node (called methods and
 * callees).
 *
 * <p>A call from method <code>a</code> to method <code>b</code> is only present once no matter how
 * many calls of <code>a</code> there are in <code>a</code>.
 *
 * <p>Recursive calls are not present.
 */
public class CallGraph extends CallGraphBase<Node> {

  // The nodes that became leaves (or roots) when the previous wave was extracted.
  private List<Node> nextWave;

  CallGraph(Map<DexMethod, Node> nodes) {
    super(nodes);
  }

  public static CallGraphBuilder builder(AppView<AppInfoWithLiveness> appView) {
    return new CallGraphBuilder(appView);
  }

  public static CallGraph createForTesting(Collection<Node> nodes) {
    return new CallGraph(
        nodes.stream()
            .collect(
                Collectors.toMap(
                    node -> node.getProgramMethod().getReference(), Function.identity())));
  }

  public CallSiteInformation createCallSiteInformation(
      AppView<AppInfoWithLiveness> appView, MethodProcessorWithWave methodProcessor) {
    return needsCallSiteInformation(appView)
        ? new CallGraphBasedCallSiteInformation(appView, this, methodProcessor)
        : CallSiteInformation.empty();
  }

  private boolean needsCallSiteInformation(AppView<AppInfoWithLiveness> appView) {
    InternalOptions options = appView.options();
    if (options.debug) {
      return false;
    }
    if (options.isOptimizing() && options.isShrinking()) {
      return true;
    }
    // When we are neither optimizing nor shrinking, we still allow inlining of javac synthetic
    // lambda methods into R8 generated accessor methods.
    return options.isGeneratingDex();
  }

  public ProgramMethodSet extractLeaves() {
    return extractNodes(Node::isLeaf, Node::cleanCallersAndReadersForRemoval);
  }

  public ProgramMethodSet extractLeaves(Consumer<Node> nodeRemovalConsumer) {
    return extractNodes(
        Node::isLeaf,
        (node, newLeaves) -> {
          nodeRemovalConsumer.accept(node);
          node.cleanCallersAndReadersForRemoval(newLeaves);
        });
  }

  public ProgramMethodSet extractRoots() {
    return extractNodes(Node::isRoot, Node::cleanCalleesAndWritersForRemoval);
  }

  private ProgramMethodSet extractNodes(
      Predicate<Node> predicate, BiConsumer<Node, List<Node>> clean) {
    ProgramMethodSet result =
        InternalOptions.DETERMINISTIC_DEBUGGING
            ? SortedProgramMethodSet.create()
            : ProgramMethodSet.create();
    // Only the first wave is found by scanning all nodes.
    List<Node> removed = nextWave != null ? nextWave : ListUtils.filter(nodes.values(), predicate);
    nextWave = new ArrayList<>();
    for (Node node : removed) {
      assert predicate.test(node);
      ProgramMethod method = node.getProgramMethod();
      result.add(method);
      nodes.remove(method.getReference());
      clean.accept(node, nextWave);
    }
    assert !result.isEmpty();
    assert !nextWave.isEmpty() || nodes.isEmpty();
    return result;
  }
}

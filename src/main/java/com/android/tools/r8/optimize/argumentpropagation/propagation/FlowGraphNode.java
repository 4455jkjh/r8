// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.propagation;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.AbstractFunction;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ConcreteValueState;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.StateCloner;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.ValueState;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Action;
import com.google.common.collect.Sets;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;

public abstract class FlowGraphNode {

  private final Set<FlowGraphNode> predecessors = Sets.newIdentityHashSet();
  private final Map<FlowGraphNode, Set<AbstractFunction>> successors = new IdentityHashMap<>();

  private boolean inWorklist = true;

  void addState(
      AppView<AppInfoWithLiveness> appView, ConcreteValueState stateToAdd, Action onChangedAction) {
    ValueState oldState = getState();
    ValueState newState =
        oldState.mutableJoin(
            appView, stateToAdd, getStaticType(), StateCloner.getCloner(), onChangedAction);
    if (newState != oldState) {
      setState(newState);
      onChangedAction.execute();
    }
  }

  abstract ValueState getState();

  abstract DexType getStaticType();

  abstract void setState(ValueState valueState);

  void setStateToUnknown() {
    setState(ValueState.unknown());
  }

  void addPredecessor(FlowGraphNode predecessor, AbstractFunction abstractFunction) {
    predecessor.successors.computeIfAbsent(this, ignoreKey(HashSet::new)).add(abstractFunction);
    predecessors.add(predecessor);
  }

  void clearPredecessors() {
    for (FlowGraphNode predecessor : predecessors) {
      predecessor.successors.remove(this);
    }
    predecessors.clear();
  }

  void clearPredecessors(FlowGraphNode cause) {
    for (FlowGraphNode predecessor : predecessors) {
      if (predecessor != cause) {
        predecessor.successors.remove(this);
      }
    }
    predecessors.clear();
  }

  Set<FlowGraphNode> getPredecessors() {
    return predecessors;
  }

  boolean hasPredecessors() {
    return !predecessors.isEmpty();
  }

  void clearDanglingSuccessors() {
    assert successors.keySet().stream()
        .noneMatch(successor -> successor.getPredecessors().contains(this));
    successors.clear();
  }

  Set<FlowGraphNode> getSuccessors() {
    return successors.keySet();
  }

  public void forEachSuccessor(BiConsumer<FlowGraphNode, Set<AbstractFunction>> consumer) {
    successors.forEach(consumer);
  }

  public void removeSuccessorIf(BiPredicate<FlowGraphNode, Set<AbstractFunction>> predicate) {
    successors.entrySet().removeIf(entry -> predicate.test(entry.getKey(), entry.getValue()));
  }

  boolean hasSuccessors() {
    return !successors.isEmpty();
  }

  boolean isBottom() {
    return getState().isBottom();
  }

  boolean isFieldNode() {
    return false;
  }

  FlowGraphFieldNode asFieldNode() {
    return null;
  }

  boolean isParameterNode() {
    return false;
  }

  FlowGraphParameterNode asParameterNode() {
    return null;
  }

  boolean isReceiverNode() {
    return false;
  }

  boolean isEffectivelyUnknown() {
    return getState().isConcrete() && getState().asConcrete().isEffectivelyUnknown();
  }

  boolean isUnknown() {
    return getState().isUnknown();
  }

  // No need to enqueue the affected node if it is already in the worklist or if it does not have
  // any successors (i.e., the successor is a leaf).
  void addToWorkList(Deque<FlowGraphNode> worklist) {
    if (!inWorklist && hasSuccessors()) {
      worklist.add(this);
      inWorklist = true;
    }
  }

  void unsetInWorklist() {
    assert inWorklist;
    inWorklist = false;
  }
}

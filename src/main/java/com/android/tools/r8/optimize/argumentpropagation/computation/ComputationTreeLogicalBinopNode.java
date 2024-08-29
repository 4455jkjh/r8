// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.BaseInFlow;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.function.Function;

public abstract class ComputationTreeLogicalBinopNode extends ComputationTreeBaseNode {

  final ComputationTreeNode left;
  final ComputationTreeNode right;

  ComputationTreeLogicalBinopNode(ComputationTreeNode left, ComputationTreeNode right) {
    assert !left.isUnknown() || !right.isUnknown();
    this.left = left;
    this.right = right;
  }

  @Override
  public boolean contains(ComputationTreeNode node) {
    return equals(node) || left.contains(node) || right.contains(node);
  }

  @Override
  public <TB, TC> TraversalContinuation<TB, TC> traverseBaseInFlow(
      Function<? super BaseInFlow, TraversalContinuation<TB, TC>> fn) {
    TraversalContinuation<TB, TC> traversalContinuation = left.traverseBaseInFlow(fn);
    if (traversalContinuation.shouldContinue()) {
      traversalContinuation = right.traverseBaseInFlow(fn);
    }
    return traversalContinuation;
  }

  public NumericType getNumericType() {
    return NumericType.INT;
  }

  @Override
  public final MethodParameter getSingleOpenVariable() {
    MethodParameter openVariable = left.getSingleOpenVariable();
    if (openVariable != null) {
      return right.getSingleOpenVariable() == null ? openVariable : null;
    }
    return right.getSingleOpenVariable();
  }

  boolean internalIsEqualTo(ComputationTreeLogicalBinopNode node) {
    return left.equals(node.left) && right.equals(node.right);
  }

  @Override
  public boolean verifyContainsBaseInFlow(BaseInFlow inFlow) {
    assert inFlow.isMethodParameter();
    MethodParameter methodParameter = inFlow.asMethodParameter();
    assert left.contains(methodParameter) || right.verifyContainsBaseInFlow(inFlow);
    return true;
  }
}

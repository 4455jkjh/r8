// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.computation;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.arithmetic.AbstractCalculator;
import com.android.tools.r8.optimize.argumentpropagation.codescanner.MethodParameter;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.Objects;
import java.util.function.Function;

public class ComputationTreeLogicalBinopAndNode extends ComputationTreeLogicalBinopNode {

  private ComputationTreeLogicalBinopAndNode(ComputationTreeNode left, ComputationTreeNode right) {
    super(left, right);
  }

  public static ComputationTreeNode create(ComputationTreeNode left, ComputationTreeNode right) {
    if (left.isUnknown() && right.isUnknown()) {
      return AbstractValue.unknown();
    }
    return new ComputationTreeLogicalBinopAndNode(left, right);
  }

  @Override
  public AbstractValue evaluate(
      AppView<AppInfoWithLiveness> appView,
      Function<MethodParameter, AbstractValue> argumentAssignment) {
    assert getNumericType().isInt();
    AbstractValue leftValue = left.evaluate(appView, argumentAssignment);
    if (leftValue.isBottom()) {
      return leftValue;
    }
    AbstractValue rightValue = right.evaluate(appView, argumentAssignment);
    if (rightValue.isBottom()) {
      return rightValue;
    }
    return AbstractCalculator.andIntegers(appView, leftValue, rightValue);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ComputationTreeLogicalBinopAndNode)) {
      return false;
    }
    ComputationTreeLogicalBinopAndNode node = (ComputationTreeLogicalBinopAndNode) obj;
    return internalIsEqualTo(node);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getClass(), left, right);
  }

  @Override
  public String toString() {
    return left.toStringWithParenthesis() + " & " + right.toStringWithParenthesis();
  }
}

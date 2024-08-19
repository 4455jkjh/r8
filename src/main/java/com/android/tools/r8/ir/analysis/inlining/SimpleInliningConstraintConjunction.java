// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.inlining;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.ListUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.List;

public class SimpleInliningConstraintConjunction extends SimpleInliningConstraint {

  private static final int MAX_SIZE = 3;

  private final List<SimpleInliningConstraint> constraints;

  private SimpleInliningConstraintConjunction(List<SimpleInliningConstraint> constraints) {
    assert constraints.size() > 1;
    assert constraints.stream().noneMatch(SimpleInliningConstraint::isAlways);
    assert constraints.stream().noneMatch(SimpleInliningConstraint::isConjunction);
    assert constraints.stream().noneMatch(SimpleInliningConstraint::isNever);
    this.constraints = constraints;
  }

  public static SimpleInliningConstraint create(List<SimpleInliningConstraint> constraints) {
    return constraints.size() <= MAX_SIZE
        ? new SimpleInliningConstraintConjunction(constraints)
        : NeverSimpleInliningConstraint.getInstance();
  }

  SimpleInliningConstraint add(SimpleInliningConstraint constraint) {
    assert !constraint.isAlways();
    assert !constraint.isNever();
    if (constraint.isConjunction()) {
      return addAll(constraint.asConjunction());
    }
    assert constraint.isArgumentConstraint() || constraint.isDisjunction();
    return create(
        ImmutableList.<SimpleInliningConstraint>builder()
            .addAll(constraints)
            .add(constraint)
            .build());
  }

  public SimpleInliningConstraint addAll(SimpleInliningConstraintConjunction conjunction) {
    return create(
        ImmutableList.<SimpleInliningConstraint>builder()
            .addAll(constraints)
            .addAll(conjunction.constraints)
            .build());
  }

  @Override
  public boolean isConjunction() {
    return true;
  }

  @Override
  public SimpleInliningConstraintConjunction asConjunction() {
    return this;
  }

  @Override
  public boolean isSatisfied(InvokeMethod invoke) {
    for (SimpleInliningConstraint constraint : constraints) {
      if (!constraint.isSatisfied(invoke)) {
        return false;
      }
    }
    return true;
  }

  @Override
  public SimpleInliningConstraint fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView,
      ArgumentInfoCollection changes,
      SimpleInliningConstraintFactory factory) {
    List<SimpleInliningConstraint> rewrittenConstraints =
        ListUtils.mapOrElse(
            constraints,
            constraint -> {
              SimpleInliningConstraint rewrittenConstraint =
                  constraint.fixupAfterParametersChanged(appView, changes, factory);
              if (rewrittenConstraint.isAlways()) {
                // Remove 'always' from conjunctions.
                return null;
              }
              return rewrittenConstraint;
            },
            null);

    if (rewrittenConstraints == null) {
      return this;
    }

    if (rewrittenConstraints.isEmpty()) {
      return AlwaysSimpleInliningConstraint.getInstance();
    }

    if (rewrittenConstraints.size() == 1) {
      return ListUtils.first(rewrittenConstraints);
    }

    if (Iterables.any(rewrittenConstraints, SimpleInliningConstraint::isNever)) {
      return NeverSimpleInliningConstraint.getInstance();
    }

    return create(rewrittenConstraints);
  }
}

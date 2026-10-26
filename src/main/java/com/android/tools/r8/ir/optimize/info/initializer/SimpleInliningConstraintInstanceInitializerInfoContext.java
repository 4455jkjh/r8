// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.initializer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/**
 * A context that is satisfied by the invocations whose arguments satisfy a given constraint, e.g.,
 * invocations where a given argument is non-null.
 */
public class SimpleInliningConstraintInstanceInitializerInfoContext
    extends InstanceInitializerInfoContext {

  private final SimpleInliningConstraint constraint;

  public SimpleInliningConstraintInstanceInitializerInfoContext(
      SimpleInliningConstraint constraint) {
    assert !constraint.isAlways();
    assert !constraint.isNever();
    this.constraint = constraint;
  }

  @Override
  public boolean isSatisfiedBy(InvokeMethod invoke) {
    return constraint.isSatisfied(invoke);
  }

  @Override
  public InstanceInitializerInfoContext fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView, ArgumentInfoCollection argumentInfoCollection) {
    SimpleInliningConstraint rewrittenConstraint =
        constraint.fixupAfterParametersChanged(
            appView, argumentInfoCollection, appView.simpleInliningConstraintFactory());
    if (rewrittenConstraint.isAlways()) {
      return AlwaysTrueInstanceInitializerInfoContext.getInstance();
    }
    if (rewrittenConstraint.isNever()) {
      return null;
    }
    if (rewrittenConstraint.equals(constraint)) {
      return this;
    }
    return new SimpleInliningConstraintInstanceInitializerInfoContext(rewrittenConstraint);
  }
}

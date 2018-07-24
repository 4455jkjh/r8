// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;

public class OptimizationFeedbackSimple implements OptimizationFeedback {

  @Override
  public void methodReturnsArgument(DexEncodedMethod method, int argument) {
    // Ignored.
  }

  @Override
  public void methodReturnsConstant(DexEncodedMethod method, long value) {
    // Ignored.
  }

  @Override
  public void methodNeverReturnsNull(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void methodNeverReturnsNormally(DexEncodedMethod method) {
    // Ignored.
  }

  @Override
  public void markProcessed(DexEncodedMethod method, Constraint state) {
    // Just as processed, don't provide any inlining constraints.
    method.markProcessed(Constraint.NEVER);
  }

  @Override
  public void markCheckNullReceiverBeforeAnySideEffect(DexEncodedMethod method, boolean mark) {
    // Ignored.
  }

  @Override
  public void markTriggerClassInitBeforeAnySideEffect(DexEncodedMethod method, boolean mark) {
    // Ignored.
  }

  @Override
  public void setClassInlinerEligibility(
      DexEncodedMethod method, ClassInlinerEligibility eligibility) {
    // Ignored.
  }

  @Override
  public void setInitializerEnablingJavaAssertions(DexEncodedMethod method) {
    method.setInitializerEnablingJavaAssertions();
  }
}

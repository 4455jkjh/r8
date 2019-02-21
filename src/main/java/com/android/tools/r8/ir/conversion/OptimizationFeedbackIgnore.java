// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexEncodedMethod.ClassInlinerEligibility;
import com.android.tools.r8.graph.DexEncodedMethod.TrivialInitializer;
import com.android.tools.r8.graph.ParameterUsagesInfo;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import java.util.BitSet;

public class OptimizationFeedbackIgnore implements OptimizationFeedback {

  @Override
  public void methodReturnsArgument(DexEncodedMethod method, int argument) {}

  @Override
  public void methodReturnsConstant(DexEncodedMethod method, long value) {}

  @Override
  public void methodMayNotHaveSideEffects(DexEncodedMethod method) {}

  @Override
  public void methodNeverReturnsNull(DexEncodedMethod method) {}

  @Override
  public void methodNeverReturnsNormally(DexEncodedMethod method) {}

  @Override
  public void markProcessed(DexEncodedMethod method, ConstraintWithTarget state) {}

  @Override
  public void markUseIdentifierNameString(DexEncodedMethod method) {}

  @Override
  public void markCheckNullReceiverBeforeAnySideEffect(DexEncodedMethod method, boolean mark) {}

  @Override
  public void markTriggerClassInitBeforeAnySideEffect(DexEncodedMethod method, boolean mark) {}

  @Override
  public void setClassInlinerEligibility(
      DexEncodedMethod method, ClassInlinerEligibility eligibility) {
  }

  @Override
  public void setTrivialInitializer(DexEncodedMethod method, TrivialInitializer info) {
  }

  @Override
  public void setInitializerEnablingJavaAssertions(DexEncodedMethod method) {
  }

  @Override
  public void setParameterUsages(DexEncodedMethod method, ParameterUsagesInfo parameterUsagesInfo) {
  }

  @Override
  public void setNonNullParamOrThrow(DexEncodedMethod method, BitSet facts) {
  }

  @Override
  public void setNonNullParamOnNormalExits(DexEncodedMethod method, BitSet facts) {
  }
}

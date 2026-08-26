// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.PostMethodProcessor;
import com.android.tools.r8.ir.conversion.PrimaryR8IRConverter;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.timing.Timing;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public interface ReprocessingOptimization {

  void prepareForPrimaryOptimizationPass(
      GraphLens graphLensForPrimaryOptimizationPass, ExecutorService executorService, Timing timing)
      throws ExecutionException;

  void irAnalysis(
      ProgramMethod method, IRCode code, MethodProcessor methodProcessor, Timing timing);

  void rewriteWithLens();

  default void classInitializerAnalysis(ProgramMethod method, StaticFieldValues staticFieldValues) {
    // Intentionally empty.
  }

  default void apply(
      AppView<AppInfoWithLiveness> appView,
      PrimaryR8IRConverter converter,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      Timing timing)
      throws ExecutionException {
    assert shouldApplyAfterSecondRoundOfIrProcessing();
  }

  default boolean shouldApplyAfterSecondRoundOfIrProcessing() {
    return false;
  }

  default void applyAfterSecondRoundOfIrProcessing(
      PrimaryR8IRConverter converter,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      Timing timing)
      throws ExecutionException {
    assert !shouldApplyAfterSecondRoundOfIrProcessing();
  }

  void onMethodPruned(ProgramMethod method);

  void onMethodCodePruned(ProgramMethod method);

  void waveDone();

  GraphLens getAppliedGraphLens();

  // When graphLenses are pruned, R8 needs to update the appliedGraphLens so it is not a pruned one.
  default void updateAppliedLens(List<GraphLens> prunedGraphLenses) {
    if (prunedGraphLenses.contains(getAppliedGraphLens())) {
      GraphLens newAppliedGraphLens =
          getAppliedGraphLens()
              .asNonIdentityLens()
              .find(l -> !l.isClearCodeRewritingLens() && !l.isMemberRebindingIdentityLens());
      updateAppliedLens(newAppliedGraphLens);
    }
  }

  void updateAppliedLens(GraphLens newAppliedLens);
}

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
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.timing.Timing;
import java.util.List;
import java.util.ListIterator;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public class ReprocessingOptimizationCollectionImpl implements ReprocessingOptimizationCollection {

  private final AppView<AppInfoWithLiveness> appView;
  private ArgumentPropagator argumentPropagator;
  private final OutlinerImpl outliner;
  private final List<ReprocessingOptimization> optimizationsToApply;

  public ReprocessingOptimizationCollectionImpl(
      AppView<AppInfoWithLiveness> appView,
      ArgumentPropagator argumentPropagator,
      OutlinerImpl outliner,
      List<ReprocessingOptimization> optimizationsToApply) {
    this.appView = appView;
    // ArgumentPropagator is special since it has to be run first.
    this.argumentPropagator = argumentPropagator;
    this.outliner = outliner;
    this.optimizationsToApply = optimizationsToApply;
    assert argumentPropagator == null || optimizationsToApply.get(0) == argumentPropagator;
  }

  private boolean assertValidAppliedLensOnAllOptimizations() {
    optimizationsToApply.forEach(
        opt -> {
          assert appView.graphLens() == opt.getAppliedGraphLens();
        });
    return true;
  }

  @Override
  public void prepareForPrimaryOptimizationPass(
      GraphLens graphLensForPrimaryOptimizationPass, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    for (ReprocessingOptimization opt : optimizationsToApply) {
      opt.prepareForPrimaryOptimizationPass(
          graphLensForPrimaryOptimizationPass, executorService, timing);
    }
  }

  @Override
  public void irAnalysis(
      ProgramMethod method, IRCode code, MethodProcessor methodProcessor, Timing timing) {
    optimizationsToApply.forEach(opt -> opt.irAnalysis(method, code, methodProcessor, timing));
  }

  @Override
  public void outlinerIrAnalysis(IRCode code, MethodProcessor methodProcessor, Timing timing) {
    // TODO(b/552291036): This should be part of irAnalysis.
    outliner.collectOutlineSites(code, timing);
  }

  @Override
  public void classInitializerAnalysis(ProgramMethod method, StaticFieldValues staticFieldValues) {
    optimizationsToApply.forEach(opt -> opt.classInitializerAnalysis(method, staticFieldValues));
  }

  @Override
  public void applyArgumentPropagator(
      PrimaryR8IRConverter converter,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      Timing timing)
      throws ExecutionException {
    if (argumentPropagator == null) {
      return;
    }
    rewriteRemainingOptimizationsWithLens();
    argumentPropagator.apply(
        appView, converter, postMethodProcessorBuilder, executorService, feedback, timing);
    assert optimizationsToApply.get(0) == argumentPropagator;
    optimizationsToApply.remove(argumentPropagator);
    argumentPropagator = null;
  }

  @Override
  public void apply(
      PrimaryR8IRConverter converter,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      Timing timing)
      throws ExecutionException {
    assert argumentPropagator == null : "Should apply first";
    ListIterator<ReprocessingOptimization> iterator = optimizationsToApply.listIterator();
    while (iterator.hasNext()) {
      ReprocessingOptimization opt = iterator.next();
      if (!opt.shouldApplyAfterSecondRoundOfIrProcessing()) {
        rewriteRemainingOptimizationsWithLens();
        opt.apply(
            appView, converter, postMethodProcessorBuilder, executorService, feedback, timing);
        iterator.remove();
      }
    }
    rewriteRemainingOptimizationsWithLens();
  }

  @Override
  public void applyAfterSecondRoundOfIrProcessing(
      PrimaryR8IRConverter converter,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      Timing timing)
      throws ExecutionException {
    ListIterator<ReprocessingOptimization> iterator = optimizationsToApply.listIterator();
    while (iterator.hasNext()) {
      ReprocessingOptimization opt = iterator.next();
      assert opt.shouldApplyAfterSecondRoundOfIrProcessing();
      rewriteRemainingOptimizationsWithLens();
      opt.applyAfterSecondRoundOfIrProcessing(converter, executorService, feedback, timing);
      iterator.remove();
    }
    // All optimizations should have been applied.
    assert optimizationsToApply.isEmpty();
  }

  @Override
  public void rewriteRemainingOptimizationsWithLens() {
    optimizationsToApply.forEach(ReprocessingOptimization::rewriteWithLens);
    assert assertValidAppliedLensOnAllOptimizations();
  }

  @Override
  public void onMethodPruned(ProgramMethod method) {
    assert assertValidAppliedLensOnAllOptimizations();
    optimizationsToApply.forEach(opt -> opt.onMethodPruned(method));
  }

  @Override
  public void onMethodCodePruned(ProgramMethod method) {
    assert assertValidAppliedLensOnAllOptimizations();
    optimizationsToApply.forEach(opt -> opt.onMethodCodePruned(method));
  }

  @Override
  public void waveDone() {
    optimizationsToApply.forEach(ReprocessingOptimization::waveDone);
  }

  @Override
  public void updateAppliedLens(List<GraphLens> prunedGraphLens) {
    optimizationsToApply.forEach(opt -> opt.updateAppliedLens(prunedGraphLens));
  }

  @Override
  public void withArgumentPropagator(Consumer<ArgumentPropagator> consumer) {
    if (argumentPropagator != null) {
      consumer.accept(argumentPropagator);
    }
  }
}

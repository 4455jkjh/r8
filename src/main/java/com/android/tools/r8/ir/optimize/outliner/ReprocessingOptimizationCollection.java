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
import com.android.tools.r8.ir.optimize.enums.EnumUnboxerImpl;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackDelayed;
import com.android.tools.r8.ir.optimize.numberunboxer.NumberUnboxerImpl;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.timing.Timing;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

public interface ReprocessingOptimizationCollection {

  void prepareForPrimaryOptimizationPass(
      GraphLens graphLensForPrimaryOptimizationPass, ExecutorService executorService, Timing timing)
      throws ExecutionException;

  void irAnalysis(
      ProgramMethod method, IRCode code, MethodProcessor methodProcessor, Timing timing);

  void outlinerIrAnalysis(IRCode code, MethodProcessor methodProcessor, Timing timing);

  void classInitializerAnalysis(ProgramMethod method, StaticFieldValues staticFieldValues);

  void applyArgumentPropagator(
      PrimaryR8IRConverter converter,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      Timing timing)
      throws ExecutionException;

  void apply(
      PrimaryR8IRConverter converter,
      PostMethodProcessor.Builder postMethodProcessorBuilder,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      Timing timing)
      throws ExecutionException;

  void applyAfterSecondRoundOfIrProcessing(
      PrimaryR8IRConverter converter,
      ExecutorService executorService,
      OptimizationFeedbackDelayed feedback,
      Timing timing)
      throws ExecutionException;

  void rewriteRemainingOptimizationsWithLens();

  void onMethodPruned(ProgramMethod method);

  void onMethodCodePruned(ProgramMethod method);

  void waveDone();

  void updateAppliedLens(List<GraphLens> prunedGraphLens);

  void withArgumentPropagator(Consumer<ArgumentPropagator> consumer);

  static ReprocessingOptimizationCollection create(AppView<AppInfoWithLiveness> appView) {
    ArgumentPropagator argumentPropagator = ArgumentPropagator.create(appView);
    EnumUnboxerImpl enumUnboxer = EnumUnboxerImpl.create(appView);
    NumberUnboxerImpl numberUnboxer = NumberUnboxerImpl.create(appView);
    OutlinerImpl outliner = OutlinerImpl.create(appView);
    List<ReprocessingOptimization> optimizations =
        ListUtils.newArrayListExcludingNullItems(
            argumentPropagator, enumUnboxer, numberUnboxer, outliner);
    return optimizations.isEmpty()
        ? createEmpty()
        : new ReprocessingOptimizationCollectionImpl(
            appView, argumentPropagator, outliner, optimizations);
  }

  static ReprocessingOptimizationCollection createEmpty() {
    return new ReprocessingOptimizationCollection() {

      @Override
      public void prepareForPrimaryOptimizationPass(
          GraphLens graphLensForPrimaryOptimizationPass,
          ExecutorService executorService,
          Timing timing)
          throws ExecutionException {
        // Intentionally empty.
      }

      @Override
      public void irAnalysis(
          ProgramMethod method, IRCode code, MethodProcessor methodProcessor, Timing timing) {
        // Intentionally empty.
      }

      @Override
      public void outlinerIrAnalysis(IRCode code, MethodProcessor methodProcessor, Timing timing) {
        // Intentionally empty.
      }

      @Override
      public void classInitializerAnalysis(
          ProgramMethod method, StaticFieldValues staticFieldValues) {
        // Intentionally empty.
      }

      @Override
      public void applyArgumentPropagator(
          PrimaryR8IRConverter converter,
          PostMethodProcessor.Builder postMethodProcessorBuilder,
          ExecutorService executorService,
          OptimizationFeedbackDelayed feedback,
          Timing timing)
          throws ExecutionException {
        // Intentionally empty.
      }

      @Override
      public void apply(
          PrimaryR8IRConverter converter,
          PostMethodProcessor.Builder postMethodProcessorBuilder,
          ExecutorService executorService,
          OptimizationFeedbackDelayed feedback,
          Timing timing)
          throws ExecutionException {
        // Intentionally empty.
      }

      @Override
      public void applyAfterSecondRoundOfIrProcessing(
          PrimaryR8IRConverter converter,
          ExecutorService executorService,
          OptimizationFeedbackDelayed feedback,
          Timing timing)
          throws ExecutionException {
        // Intentionally empty.
      }

      @Override
      public void rewriteRemainingOptimizationsWithLens() {
        // Intentionally empty.
      }

      @Override
      public void onMethodPruned(ProgramMethod method) {
        // Intentionally empty.
      }

      @Override
      public void onMethodCodePruned(ProgramMethod method) {
        // Intentionally empty.
      }

      @Override
      public void waveDone() {
        // Intentionally empty.
      }

      @Override
      public void updateAppliedLens(List<GraphLens> prunedGraphLens) {
        // Intentionally empty.
      }

      @Override
      public void withArgumentPropagator(Consumer<ArgumentPropagator> consumer) {
        // Intentionally empty.
      }
    };
  }
}

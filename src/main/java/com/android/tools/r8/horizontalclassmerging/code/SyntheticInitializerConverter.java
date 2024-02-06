// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.code;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.IRCodeProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Converts synthetic class initializers that have been created as a result of merging class
 * initializers into a single class initializer to DEX.
 */
public class SyntheticInitializerConverter {

  private final AppView<?> appView;
  private final IRCodeProvider codeProvider;

  private final List<ProgramMethod> classInitializers;

  private SyntheticInitializerConverter(
      AppView<?> appView, IRCodeProvider codeProvider, List<ProgramMethod> classInitializers) {
    this.appView = appView;
    this.codeProvider = codeProvider;
    this.classInitializers = classInitializers;
  }

  public static Builder builder(AppView<?> appView, IRCodeProvider codeProvider) {
    return new Builder(appView, codeProvider);
  }

  public void convertClassInitializers(ExecutorService executorService) throws ExecutionException {
    if (!classInitializers.isEmpty()) {
      assert appView.dexItemFactory().verifyNoCachedTypeElements();
      IRConverter converter = new IRConverter(createAppViewForConversion());
      ThreadUtils.processItems(
          classInitializers,
          method -> processMethod(method, converter),
          appView.options().getThreadingModule(),
          executorService);
      appView.dexItemFactory().clearTypeElementsCache();
    }
  }

  private AppView<AppInfo> createAppViewForConversion() {
    assert appView.enableWholeProgramOptimizations();
    assert appView.hasClassHierarchy();

    // At this point the code rewritings described by repackaging and synthetic finalization have
    // not been applied to the code objects. These code rewritings will be applied in the
    // application writer. We therefore simulate that we are in D8, to allow building IR for each of
    // the class initializers without applying the unapplied code rewritings, to avoid that we apply
    // the lens more than once to the same piece of code.

    // Since we are now running in D8 mode clear type elements cache.
    appView.dexItemFactory().clearTypeElementsCache();

    AppView<AppInfo> appViewForConversion =
        AppView.createForSimulatingD8InR8(
            AppInfo.createInitialAppInfo(
                appView.appInfo().app(), GlobalSyntheticsStrategy.forNonSynthesizing()));
    appViewForConversion.setGraphLens(appView.graphLens());
    appViewForConversion.setCodeLens(appView.codeLens());
    return appViewForConversion;
  }

  private void processMethod(ProgramMethod method, IRConverter converter) {
    IRCode code = codeProvider.buildIR(method);
    converter.removeDeadCodeAndFinalizeIR(
        code, OptimizationFeedbackIgnore.getInstance(), Timing.empty());
  }

  public boolean isEmpty() {
    return classInitializers.isEmpty();
  }

  public static class Builder {

    private final AppView<?> appView;
    private final IRCodeProvider codeProvider;

    private final List<ProgramMethod> classInitializers = new ArrayList<>();

    private Builder(AppView<?> appView, IRCodeProvider codeProvider) {
      this.appView = appView;
      this.codeProvider = codeProvider;
    }

    public Builder addClassInitializer(ProgramMethod method) {
      this.classInitializers.add(method);
      return this;
    }

    public SyntheticInitializerConverter build() {
      return new SyntheticInitializerConverter(appView, codeProvider, classInitializers);
    }
  }
}

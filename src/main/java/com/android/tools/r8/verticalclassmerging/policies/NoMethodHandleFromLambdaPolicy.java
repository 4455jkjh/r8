// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.TraversalContinuation;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

// TODO(b/557272504): Remove this policy once method handles are handled properly.
public class NoMethodHandleFromLambdaPolicy extends VerticalClassMergerPolicy {

  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;

  public NoMethodHandleFromLambdaPolicy(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.options = appView.options();
  }

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    return group
        .getSource()
        .traverseProgramMethods(
            method ->
                TraversalContinuation.continueIf(
                    appView.getKeepInfo(method).isClosedWorldReasoningAllowed(options)))
        .shouldContinue();
  }

  @Override
  public String getName() {
    return "NoMethodHandleFromLambdaPolicy";
  }

  @Override
  public boolean shouldSkipPolicy() {
    return options.isGeneratingDex();
  }
}

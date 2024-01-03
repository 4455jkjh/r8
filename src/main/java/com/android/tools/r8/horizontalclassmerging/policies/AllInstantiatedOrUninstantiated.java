// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.classmerging.ClassMergerMode;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.MultiClassSameReferencePolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class AllInstantiatedOrUninstantiated extends MultiClassSameReferencePolicy<Boolean> {

  private final AppView<AppInfoWithLiveness> appView;

  public AllInstantiatedOrUninstantiated(
      AppView<AppInfoWithLiveness> appView, ClassMergerMode mode) {
    // This policy is only used to prevent that horizontal class merging regresses the
    // uninstantiated type optimization. Since there won't be any IR processing after the final
    // round of horizontal class merging, there is no need to use the policy.
    assert mode.isInitial();
    this.appView = appView;
  }

  @Override
  public Boolean getMergeKey(DexProgramClass clazz) {
    return appView.appInfo().isInstantiatedDirectlyOrIndirectly(clazz);
  }

  @Override
  public String getName() {
    return "AllInstantiatedOrUninstantiated";
  }
}

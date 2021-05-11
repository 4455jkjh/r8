// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.SingleClassPolicy;

public class NotVerticallyMergedIntoSubtype extends SingleClassPolicy {
  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  public NotVerticallyMergedIntoSubtype(
      AppView<? extends AppInfoWithClassHierarchy> appView, Mode mode) {
    // This policy is only relevant for the initial round, since all vertically merged classes have
    // been removed from the application in the final round of horizontal class merging.
    assert mode.isInitial();
    this.appView = appView;
  }

  @Override
  public boolean canMerge(DexProgramClass program) {
    if (appView.verticallyMergedClasses() == null) {
      return true;
    }
    return !appView.verticallyMergedClasses().hasBeenMergedIntoSubtype(program.type);
  }

  @Override
  public String getName() {
    return "NotVerticallyMergedIntoSubtype";
  }
}

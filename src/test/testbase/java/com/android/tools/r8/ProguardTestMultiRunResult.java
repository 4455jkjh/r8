// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.AndroidApp;
import java.util.List;

public class ProguardTestMultiRunResult extends MultiTestRunResult<ProguardTestMultiRunResult> {

  public ProguardTestMultiRunResult(
      AndroidApp app,
      String proguardMap,
      TestState state,
      List<SingleTestRunResult> singleRunResults) {
    super(app, proguardMap, state, singleRunResults);
  }

  @Override
  protected ProguardTestMultiRunResult self() {
    return this;
  }
}

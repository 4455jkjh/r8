// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.AndroidApp;
import java.util.List;

public class D8TestMultiRunResult extends MultiTestRunResult<D8TestMultiRunResult> {

  public D8TestMultiRunResult(
      AndroidApp app,
      String proguardMap,
      TestState state,
      List<SingleTestRunResult> singleRunResults) {
    super(app, proguardMap, state, singleRunResults);
  }

  @Override
  protected D8TestMultiRunResult self() {
    return this;
  }
}

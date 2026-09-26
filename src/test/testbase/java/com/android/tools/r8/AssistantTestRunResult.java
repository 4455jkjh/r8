// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.AndroidApp;
import java.util.List;

public class AssistantTestRunResult extends MultiTestRunResult<AssistantTestRunResult> {

  public AssistantTestRunResult(
      AndroidApp app, TestState state, List<SingleTestRunResult> singleRunResults) {
    super(app, null, state, singleRunResults);
  }

  @Override
  protected AssistantTestRunResult self() {
    return this;
  }
}

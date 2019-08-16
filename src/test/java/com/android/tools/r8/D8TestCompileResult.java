// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.Box;

public class D8TestCompileResult extends TestCompileResult<D8TestCompileResult, D8TestRunResult> {

  D8TestCompileResult(
      TestState state, AndroidApp app, OutputMode outputMode, Box<String> keepRulesHolder) {
    super(state, app, outputMode, keepRulesHolder);
    assert ToolHelper.verifyValidOutputMode(Backend.DEX, outputMode);
  }

  @Override
  public D8TestCompileResult self() {
    return this;
  }

  @Override
  public TestDiagnosticMessages getDiagnosticMessages() {
    return state.getDiagnosticsMessages();
  }

  @Override
  public D8TestRunResult createRunResult(ProcessResult result) {
    return new D8TestRunResult(app, result);
  }
}

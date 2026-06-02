// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApp;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class Dex2OatTestRunResult extends SingleTestRunResult<Dex2OatTestRunResult> {

  private final Path oat;

  public Dex2OatTestRunResult(
      AndroidApp app, Path oat, TestRuntime runtime, ProcessResult result, TestState state) {
    super(app, runtime, result, state);
    this.oat = oat;
  }

  @Override
  protected Dex2OatTestRunResult self() {
    return this;
  }

  public Dex2OatTestRunResult assertNoLockVerificationErrors() {
    return assertInStderr("failed lock verification", false);
  }

  public Dex2OatTestRunResult assertNoVerificationErrors() {
    return assertInStderr("Verification error", false);
  }

  public Dex2OatTestRunResult assertSoftVerificationErrors() {
    return assertInStderr("Soft verification failures", true);
  }

  private Dex2OatTestRunResult assertInStderr(String substring, boolean expected) {
    assertSuccess();
    assertEquals(expected, getStdErr().contains(substring));
    return self();
  }

  public long getOatSizeOrDefault(long defaultValue) throws IOException {
    return Files.exists(oat) ? Files.size(oat) : defaultValue;
  }
}

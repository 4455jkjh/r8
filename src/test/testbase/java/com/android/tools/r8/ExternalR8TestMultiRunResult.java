// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.utils.AndroidApp;
import java.nio.file.Path;
import java.util.List;

public class ExternalR8TestMultiRunResult extends MultiTestRunResult<ExternalR8TestMultiRunResult> {

  private final Path outputJar;

  public ExternalR8TestMultiRunResult(
      AndroidApp app,
      Path outputJar,
      String proguardMap,
      TestState state,
      List<SingleTestRunResult> singleRunResults) {
    super(app, proguardMap, state, singleRunResults);
    this.outputJar = outputJar;
  }

  public Path outputJar() {
    return outputJar;
  }

  @Override
  protected ExternalR8TestMultiRunResult self() {
    return this;
  }
}

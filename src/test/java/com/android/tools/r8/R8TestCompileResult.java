// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class R8TestCompileResult extends TestCompileResult<R8TestCompileResult, R8TestRunResult> {

  private final Backend backend;
  private final ProguardConfiguration proguardConfiguration;
  private final List<ProguardConfigurationRule> syntheticProguardRules;
  private final String proguardMap;

  R8TestCompileResult(
      TestState state,
      Backend backend,
      AndroidApp app,
      ProguardConfiguration proguardConfiguration,
      List<ProguardConfigurationRule> syntheticProguardRules,
      String proguardMap) {
    super(state, app);
    this.backend = backend;
    this.proguardConfiguration = proguardConfiguration;
    this.syntheticProguardRules = syntheticProguardRules;
    this.proguardMap = proguardMap;
  }

  @Override
  public R8TestCompileResult self() {
    return this;
  }

  @Override
  public Backend getBackend() {
    return backend;
  }

  @Override
  public CodeInspector inspector() throws IOException, ExecutionException {
    return new CodeInspector(app, proguardMap);
  }

  public ProguardConfiguration getProguardConfiguration() {
    return proguardConfiguration;
  }

  public R8TestCompileResult inspectProguardConfiguration(
      Consumer<ProguardConfiguration> consumer) {
    consumer.accept(getProguardConfiguration());
    return self();
  }

  public List<ProguardConfigurationRule> getSyntheticProguardRules() {
    return syntheticProguardRules;
  }

  public R8TestCompileResult inspectSyntheticProguardRules(
      Consumer<List<ProguardConfigurationRule>> consumer) {
    consumer.accept(getSyntheticProguardRules());
    return self();
  }

  @Override
  public R8TestRunResult createRunResult(ProcessResult result) {
    return new R8TestRunResult(app, result, proguardMap);
  }
}

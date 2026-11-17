// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.benchmarks.appdumps;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.appdumps.AppDumpBenchmarkBuilder;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class ChromeInternalBenchmarks extends BenchmarkBase {

  protected ChromeInternalBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(ChromeInternalAppR8.config());
  }

  private static void configure(R8FullTestBuilder testBuilder) {
    testBuilder
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards();
  }

  @Test
  @Override
  public void testBenchmarks() throws Exception {
    assumeTrue(ToolHelper.isLocalDevelopment());
    super.testBenchmarks();
  }

  public static class ChromeInternalAppR8 extends ChromeInternalBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return AppDumpBenchmarkBuilder.builder()
          .setName("ChromeInternalApp")
          .setDumpDependencyPath(TestDeps::getChromeInternalDir)
          .buildR8(ChromeInternalBenchmarks::configure);
    }

    public ChromeInternalAppR8(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }
}

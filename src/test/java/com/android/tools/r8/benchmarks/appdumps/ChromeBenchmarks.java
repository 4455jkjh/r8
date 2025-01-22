// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8PartialTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ChromeBenchmarks extends BenchmarkBase {

  private static final Path dir = Paths.get(ToolHelper.THIRD_PARTY_DIR, "opensource-apps/chrome");

  public ChromeBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return parametersFromConfigs(configs());
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        AppDumpBenchmarkBuilder.builder()
            .setName("ChromeApp")
            .setDumpDependencyPath(dir)
            .setFromRevision(16457)
            .buildR8(ChromeBenchmarks::configure),
        AppDumpBenchmarkBuilder.builder()
            .setName("ChromeAppPartial")
            .setDumpDependencyPath(dir)
            .setFromRevision(16457)
            .buildR8WithPartialShrinking(ChromeBenchmarks::configurePartial));
  }

  private static void configure(R8FullTestBuilder testBuilder) {
    testBuilder
        .addDontWarn("android.adservices.common.AdServicesOutcomeReceiver")
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards();
  }

  private static void configurePartial(R8PartialTestBuilder testBuilder) {
    testBuilder
        .allowDiagnosticInfoMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards();
  }

  @Ignore
  @Test
  @Override
  public void testBenchmarks() throws Exception {
    super.testBenchmarks();
  }

  @Test
  public void testChromeApp() throws Exception {
    testBenchmarkWithName("ChromeApp");
  }

  // TODO(b/388421578): Add support for feature splits in R8 partial.
  @Ignore
  @Test
  public void testChromeAppPartial() throws Exception {
    testBenchmarkWithName("ChromeAppPartial");
  }
}

// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.benchmarks.appdumps;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.appdumps.AbortBenchmarkException;
import com.android.tools.r8.benchmarks.appdumps.AppDumpBenchmarkBuilder;
import com.android.tools.r8.errors.CheckEnumUnboxedDiagnostic;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class AGSABenchmarks extends BenchmarkBase {

  private static final Path dir =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "closedsource-apps/agsa/20250412-v16.14.47");

  protected AGSABenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(AGSA.config(), AGSATreeShaking.config());
  }

  private static void configure(R8FullTestBuilder testBuilder) {
    testBuilder
        .addOptionsModification(
            options -> {
              options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces();
              options.getTestingOptions().dontReportFailingCheckDiscarded = true;
              options.reporter.addDiagnosticsLevelMapping(
                  DiagnosticsLevel.ERROR,
                  CheckEnumUnboxedDiagnostic.class.getTypeName(),
                  DiagnosticsLevel.WARNING);
            })
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards();
  }

  private static void configureTreeShaking(R8FullTestBuilder testBuilder) {
    testBuilder
        .apply(AGSABenchmarks::configure)
        .addOptionsModification(
            options ->
                options.getTestingOptions().enqueuerInspector =
                    (appInfo, enqueuerMode, timing) -> {
                      if (appInfo.options().printTimes) {
                        timing.end(); // End "Create result"
                        timing.end(); // End "Trace application"
                        timing.end(); // End "Enqueuer"
                        timing.end(); // End "Strip unused code"
                        timing.report(); // Report "R8 main"
                      }
                      throw new AbortBenchmarkException();
                    });
  }

  @Test
  @Override
  public void testBenchmarks() throws Exception {
    assumeTrue(ToolHelper.isLocalDevelopment());
    super.testBenchmarks();
  }

  protected static AppDumpBenchmarkBuilder builder(String name) {
    return AppDumpBenchmarkBuilder.builder()
        .setName(name)
        .setDumpDependencyPath(dir)
        .setWarmupIterations(0);
  }

  public static class AGSA extends AGSABenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("AGSA").buildR8(AGSABenchmarks::configure);
    }

    public AGSA(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class AGSATreeShaking extends AGSABenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("AGSATreeShaking")
          .setRuntimeOnly()
          .buildR8(AGSABenchmarks::configureTreeShaking);
    }

    public AGSATreeShaking(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }
}

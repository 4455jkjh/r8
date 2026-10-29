// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.benchmarks.appdumps;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.DiagnosticsLevel;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.appdumps.AppDumpBenchmarkBuilder;
import com.android.tools.r8.errors.CheckEnumUnboxedDiagnostic;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class YouTubeBenchmarks extends BenchmarkBase {

  protected YouTubeBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        YouTubeAppD8Merge.config(), YouTubeAppD8MergeWithMapOutput.config(), YouTubeAppR8.config());
  }

  private static void configure(R8FullTestBuilder testBuilder) {
    testBuilder
        .addKeepAttributeInnerClassesAndEnclosingMethod()
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

  public static class YouTubeAppD8Merge extends YouTubeBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return AppDumpBenchmarkBuilder.builder()
          .setName("YouTubeAppD8Merge")
          .setCompilationMode(CompilationMode.RELEASE)
          .setDumpDependencyPath(TestDeps::getYouTube2140D8MergeDir)
          .buildBatchD8();
    }

    public YouTubeAppD8Merge(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class YouTubeAppD8MergeWithMapOutput extends YouTubeBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return AppDumpBenchmarkBuilder.builder()
          .setName("YouTubeAppD8MergeWithMapOutput")
          .setCompilationMode(CompilationMode.RELEASE)
          .setDumpDependencyPath(TestDeps::getYouTube2140D8MergeDir)
          .setPgMapInput("mapping.txt")
          .buildBatchD8();
    }

    public YouTubeAppD8MergeWithMapOutput(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }

    @Ignore("b/579315508")
    @Override
    @Test
    public void testBenchmarks() throws Exception {
      super.testBenchmarks();
    }
  }

  public static class YouTubeAppR8 extends YouTubeBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return AppDumpBenchmarkBuilder.builder()
          .setName("YouTubeApp")
          .setDumpDependencyPath(TestDeps::getYouTube2140Dir)
          .buildR8(YouTubeBenchmarks::configure);
    }

    public YouTubeAppR8(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }
}

// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8PartialTestBuilder;
import com.android.tools.r8.R8PartialTestCompileResult;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.Version;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.utils.LibraryProvidedProguardRulesTestUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class ChromeBenchmarks extends BenchmarkBase {

  private static final Path dir = Paths.get(ToolHelper.THIRD_PARTY_DIR, "opensource-apps/chrome");

  protected ChromeBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        ChromeApp.config(), ChromeAppPartial.config(), ChromeAppTreeShaking.config());
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
        .addDontWarn("android.adservices.common.AdServicesOutcomeReceiver")
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards();
  }

  private static void configureTreeShaking(R8FullTestBuilder testBuilder) {
    configure(testBuilder);
    testBuilder.addOptionsModification(
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

  private static void inspectPartial(R8PartialTestCompileResult compileResult) {
    compileResult.inspectDiagnosticMessages(
        diagnostics ->
            diagnostics
                .applyIf(
                    Version.isMainVersion(),
                    d ->
                        d.assertWarningsMatch(
                            LibraryProvidedProguardRulesTestUtils.getDiagnosticMatcher()),
                    TestDiagnosticMessages::assertNoWarnings)
                .assertNoErrors());
  }

  protected static AppDumpBenchmarkBuilder builder(String name) {
    return AppDumpBenchmarkBuilder.builder()
        .setName(name)
        .setDumpDependencyPath(dir)
        .setFromRevision(16457);
  }

  public static class ChromeApp extends ChromeBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("ChromeApp").buildR8(ChromeBenchmarks::configure);
    }

    public ChromeApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class ChromeAppPartial extends ChromeBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("ChromeAppPartial")
          .buildR8WithPartialShrinking(
              ChromeBenchmarks::configurePartial, ChromeBenchmarks::inspectPartial);
    }

    public ChromeAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class ChromeAppTreeShaking extends ChromeBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("ChromeAppTreeShaking")
          .setRuntimeOnly()
          .buildR8(ChromeBenchmarks::configureTreeShaking);
    }

    public ChromeAppTreeShaking(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }
}

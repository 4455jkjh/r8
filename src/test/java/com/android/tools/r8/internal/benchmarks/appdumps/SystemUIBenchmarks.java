// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.benchmarks.appdumps;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8PartialTestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.appdumps.AbortBenchmarkException;
import com.android.tools.r8.benchmarks.appdumps.AppDumpBenchmarkBuilder;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class SystemUIBenchmarks extends BenchmarkBase {

  private static final Path dir =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "closedsource-apps/systemui");

  protected SystemUIBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        SystemUIApp.config(),
        SystemUIAppGc.config(),
        SystemUIAppPartial.config(),
        SystemUIAppTreeShaking.config(),
        SystemUIAppContainerDex.config());
  }

  private static void configure(R8FullTestBuilder testBuilder) {
    testBuilder
        .addDontWarn("android.hardware.graphics.common.DisplayDecorationSupport")
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards()
        .setAndroidPlatformBuild();
  }

  private static void configurePartialShrinking(R8PartialTestBuilder testBuilder) {
    testBuilder
        .addR8PartialR8OptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .allowDiagnosticMessages()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards()
        .setAndroidPlatformBuild();
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

  protected static AppDumpBenchmarkBuilder builder(String name) {
    return AppDumpBenchmarkBuilder.builder()
        .setName(name)
        .setDumpDependencyPath(dir)
        .setEnableResourceShrinking(true)
        .setFromRevision(16457);
  }

  public static class SystemUIApp extends SystemUIBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("SystemUIApp")
          // TODO(b/373550435): Update dex2oat to enable checking absence of verification errors
          //  on SystemUI.
          .setEnableDex2OatVerification(false)
          .buildR8(SystemUIBenchmarks::configure);
    }

    public SystemUIApp(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  @Ignore
  public static class SystemUIAppGc extends SystemUIBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("SystemUIAppGc")
          .setEnableGcTracking(true)
          // TODO(b/373550435): Update dex2oat to enable checking absence of verification errors
          //  on SystemUI.
          .setEnableDex2OatVerification(false)
          .buildR8(SystemUIBenchmarks::configure);
    }

    public SystemUIAppGc(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class SystemUIAppPartial extends SystemUIBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("SystemUIAppPartial")
          // TODO(b/373550435): Update dex2oat to enable checking absence of verification errors
          //  on SystemUI.
          .setEnableDex2OatVerification(false)
          .buildR8WithPartialShrinking(SystemUIBenchmarks::configurePartialShrinking);
    }

    public SystemUIAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  @Ignore
  public static class SystemUIAppTreeShaking extends SystemUIBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("SystemUIAppTreeShaking")
          .setRuntimeOnly()
          .buildR8(SystemUIBenchmarks::configureTreeShaking);
    }

    public SystemUIAppTreeShaking(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  @Ignore
  public static class SystemUIAppContainerDex extends SystemUIBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("SystemUIAppContainerDex")
          .setEnableContainerDex(true)
          // TODO(b/373550435): Update dex2oat to enable checking container DEX on SystemUI.
          .setEnableDex2Oat(false)
          .buildR8(SystemUIBenchmarks::configure);
    }

    public SystemUIAppContainerDex(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }
}

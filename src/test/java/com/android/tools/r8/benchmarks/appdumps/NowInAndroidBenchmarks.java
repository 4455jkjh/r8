// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class NowInAndroidBenchmarks extends BenchmarkBase {

  private static final Path dump =
      Paths.get(ToolHelper.THIRD_PARTY_DIR, "opensource-apps", "android", "nowinandroid");

  protected NowInAndroidBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        NowInAndroidAppD8.config(),
        NowInAndroidAppRelease.config(),
        NowInAndroidAppNoJ$.config(),
        NowInAndroidAppNoJ$Release.config(),
        NowInAndroidAppIncremental.config(),
        NowInAndroidAppNoJ$Incremental.config(),
        NowInAndroidAppR8.config(),
        NowInAndroidAppPartial.config(),
        NowInAndroidAppWithResourceShrinking.config());
  }

  protected static AppDumpBenchmarkBuilder builder(String name) {
    return AppDumpBenchmarkBuilder.builder()
        .setName(name)
        .setDumpDependencyPath(dump)
        .setFromRevision(16017);
  }

  public static class NowInAndroidAppD8 extends NowInAndroidBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NowInAndroidApp").setCompilationMode(CompilationMode.DEBUG).buildBatchD8();
    }

    public NowInAndroidAppD8(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class NowInAndroidAppRelease extends NowInAndroidBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NowInAndroidAppRelease")
          .setCompilationMode(CompilationMode.RELEASE)
          .buildBatchD8();
    }

    public NowInAndroidAppRelease(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class NowInAndroidAppNoJ$ extends NowInAndroidBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NowInAndroidAppNoJ$")
          .setCompilationMode(CompilationMode.DEBUG)
          .setEnableLibraryDesugaring(false)
          .buildBatchD8();
    }

    public NowInAndroidAppNoJ$(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class NowInAndroidAppNoJ$Release extends NowInAndroidBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NowInAndroidAppNoJ$Release")
          .setCompilationMode(CompilationMode.RELEASE)
          .setEnableLibraryDesugaring(false)
          .buildBatchD8();
    }

    public NowInAndroidAppNoJ$Release(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class NowInAndroidAppIncremental extends NowInAndroidBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NowInAndroidAppIncremental")
          .setCompilationMode(CompilationMode.DEBUG)
          .addProgramPackages("com/google/samples/apps/nowinandroid")
          .buildIncrementalD8();
    }

    public NowInAndroidAppIncremental(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class NowInAndroidAppNoJ$Incremental extends NowInAndroidBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NowInAndroidAppNoJ$Incremental")
          .setCompilationMode(CompilationMode.DEBUG)
          .setEnableLibraryDesugaring(false)
          .addProgramPackages("com/google/samples/apps/nowinandroid")
          .buildIncrementalD8();
    }

    public NowInAndroidAppNoJ$Incremental(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class NowInAndroidAppR8 extends NowInAndroidBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NowInAndroidApp").buildR8();
    }

    public NowInAndroidAppR8(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class NowInAndroidAppPartial extends NowInAndroidBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NowInAndroidAppPartial").buildR8WithPartialShrinking();
    }

    public NowInAndroidAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class NowInAndroidAppWithResourceShrinking extends NowInAndroidBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("NowInAndroidAppWithResourceShrinking")
          .setEnableResourceShrinking(true)
          .buildR8();
    }

    public NowInAndroidAppWithResourceShrinking(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }
}

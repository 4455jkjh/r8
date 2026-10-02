// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8PartialTestBuilder;
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
public abstract class TiviBenchmarks extends BenchmarkBase {

  private static final Path dump = Paths.get(ToolHelper.THIRD_PARTY_DIR, "opensource-apps", "tivi");

  protected TiviBenchmarks(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        TiviAppR8.config(), TiviAppD8.config(), TiviIncremental.config(), TiviAppPartial.config());
  }

  private static void configureR8Partial(R8PartialTestBuilder testBuilder) {
    testBuilder
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        // TODO(b/222228826): Disallow unrecognized diagnostics and open interfaces.
        .allowDiagnosticMessages();
  }

  protected static AppDumpBenchmarkBuilder builder(String name, int fromRevision) {
    return AppDumpBenchmarkBuilder.builder()
        .setName(name)
        .setDumpDependencyPath(dump)
        .setFromRevision(fromRevision);
  }

  public static class TiviAppR8 extends TiviBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("TiviApp", 12215).buildR8();
    }

    public TiviAppR8(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class TiviAppD8 extends TiviBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("TiviApp", 12370).setCompilationMode(CompilationMode.DEBUG).buildBatchD8();
    }

    public TiviAppD8(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class TiviIncremental extends TiviBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("TiviIncremental", 12370)
          .setCompilationMode(CompilationMode.DEBUG)
          .addProgramPackages("app/tivi")
          .buildIncrementalD8();
    }

    public TiviIncremental(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class TiviAppPartial extends TiviBenchmarks {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return builder("TiviAppPartial", 12215)
          .buildR8WithPartialShrinking(TiviBenchmarks::configureR8Partial);
    }

    public TiviAppPartial(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }
}

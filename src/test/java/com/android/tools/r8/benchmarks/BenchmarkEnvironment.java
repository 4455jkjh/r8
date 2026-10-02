// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.rules.TemporaryFolder;

public class BenchmarkEnvironment {

  private final BenchmarkConfig config;
  private final TemporaryFolder temp;
  private final boolean isPerf;

  public BenchmarkEnvironment(BenchmarkConfig config, TemporaryFolder temp, boolean isPerf) {
    this.config = config;
    this.temp = temp;
    this.isPerf = isPerf;
  }

  public boolean failOnCodeSizeDifferences() {
    return System.getProperty("BENCHMARK_IGNORE_CODE_SIZE_DIFFERENCES") == null;
  }

  public BenchmarkConfig getConfig() {
    return config;
  }

  public TemporaryFolder getTemp() {
    return temp;
  }

  public boolean isPerf() {
    return isPerf;
  }

  public boolean isTest() {
    return !isPerf;
  }

  public boolean hasBenchmarkIterationsOverride() {
    return isTest() || System.getProperty("BENCHMARK_ITERATIONS") != null;
  }

  public int getBenchmarkIterationsOverride() {
    return System.getProperty("BENCHMARK_ITERATIONS") != null
        ? Integer.parseInt(System.getProperty("BENCHMARK_ITERATIONS"))
        : 1;
  }

  public boolean hasBenchmarkWarmupIterationsOverride() {
    return isTest() || System.getProperty("BENCHMARK_WARMUP_ITERATIONS") != null;
  }

  public int getBenchmarkWarmupIterationsOverride() {
    return System.getProperty("BENCHMARK_WARMUP_ITERATIONS") != null
        ? Integer.parseInt(System.getProperty("BENCHMARK_WARMUP_ITERATIONS"))
        : 0;
  }

  public boolean hasOutputPath() {
    return System.getProperty("BENCHMARK_OUTPUT") != null;
  }

  public Path getOutputPath() {
    return Paths.get(System.getProperty("BENCHMARK_OUTPUT"));
  }

  public boolean hasBuildOutputPath() {
    return System.getProperty("BENCHMARK_BUILD_OUTPUT") != null;
  }

  public Path getBuildOutputPath() {
    return Paths.get(System.getProperty("BENCHMARK_BUILD_OUTPUT"));
  }
}

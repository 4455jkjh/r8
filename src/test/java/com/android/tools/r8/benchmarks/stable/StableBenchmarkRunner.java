// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.stable;

import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.BenchmarkEnvironment;
import com.android.tools.r8.utils.internal.FileUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Orchestrates {@code --runner stable} runs for any single-benchmark (non-sub-benchmark) config by
 * spawning isolated child JVMs running {@link StableBenchmarkDriver}.
 */
public final class StableBenchmarkRunner {

  public static final String RUNNER_NAME = "stable";

  private static final String DRIVER_MAIN =
      "com.android.tools.r8.benchmarks.stable.StableBenchmarkDriver";

  private StableBenchmarkRunner() {}

  public static void run(BenchmarkEnvironment environment) throws Exception {
    BenchmarkConfig config = environment.getConfig();
    if (!config.isSingleBenchmark()) {
      throw new IllegalArgumentException(
          "The stable runner requires a single-benchmark config: " + config.getName());
    }
    int count = environment.getBenchmarkIterationsOverride();
    if (count < 2 || count % 2 != 0) {
      throw new IllegalArgumentException(
          "The stable runner requires an even --iterations >= 2, got " + count);
    }
    if (!environment.hasBenchmarkWarmupIterationsOverride()) {
      throw new IllegalArgumentException("The stable runner requires --warmup");
    }
    int warmup = environment.getBenchmarkWarmupIterationsOverride();
    if (warmup < 0) {
      throw new IllegalArgumentException("The stable runner requires --warmup >= 0, got " + warmup);
    }
    int quartets = Integer.parseInt(requireStringProperty("STABLE_QUARTETS"));
    if (quartets < 1) {
      throw new IllegalArgumentException(
          "The stable runner requires --quartets >= 1, got " + quartets);
    }

    String driverJar = requireStringProperty("STABLE_DRIVER_JAR");
    String headClasspath = requireStringProperty("STABLE_HEAD_CLASSPATH");
    String baselineClasspath = requireStringProperty("STABLE_BASELINE_CLASSPATH");
    String heapSize = requireStringProperty("STABLE_HEAP_SIZE");
    boolean verbose = Boolean.getBoolean("BENCHMARK_VERBOSE");
    boolean enableAssertions = StableBenchmarkRunner.class.desiredAssertionStatus();

    // Inherits the pinned third_party JDK configured by RunBenchmarkTask's JavaToolchainService.
    String javaBin = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
    List<String> forwardedProps = collectForwardedJvmProperties();

    JsonArray results = new JsonArray();
    double sumLogRatio = 0.0;
    long totalHeadNanos = 0;
    long totalBaselineNanos = 0;
    int totalSamples = 0;
    for (int i = 0; i < count; i++) {
      Path jvmDir = environment.getTemp().newFolder().toPath();
      Path rawPath = jvmDir.resolve("driver.txt");
      // Keep JVM behavior steady across compiles:
      // - Fixed ParallelGC heap + AlwaysPreTouch: avoids heap resizing and page faults during
      //   timed runs.
      // - Fixed 768m code cache + -UseCodeCacheFlushing: stops JDK 21 from unloading JIT-compiled
      //   R8 code during inter-run System.gc() calls.
      // - TypeProfileWidth=8: lets HotSpot track more receiver types in shared JDK methods,
      //   shrinking the warmup-order bias between the two loaders.
      List<String> cmd = new ArrayList<>();
      cmd.add(javaBin);
      cmd.add("-Xms" + heapSize);
      cmd.add("-Xmx" + heapSize);
      cmd.add("-XX:+UseParallelGC");
      cmd.add("-XX:-UseAdaptiveSizePolicy");
      cmd.add("-XX:+AlwaysPreTouch");
      cmd.add("-XX:InitialCodeCacheSize=768m");
      cmd.add("-XX:ReservedCodeCacheSize=768m");
      cmd.add("-XX:-UseCodeCacheFlushing");
      cmd.add("-XX:MetaspaceSize=256m");
      cmd.add("-XX:-UseDynamicNumberOfCompilerThreads");
      cmd.add("-XX:TypeProfileWidth=8");
      if (enableAssertions) {
        cmd.add("-ea");
      }
      cmd.addAll(forwardedProps);
      // Both classloaders share the JDK standard library, so whichever copy runs first trains
      // HotSpot's JIT type profiles in shared JDK collections and gets a ~1.5-3.5% speed advantage
      // for the rest of that JVM. Swapping the order on odd JVMs lets downstream analysis cancel
      // out that warmup-order bias across even/odd pairs.
      boolean baselineFirst = (i % 2 == 0);
      String firstClasspath = baselineFirst ? baselineClasspath : headClasspath;
      String secondClasspath = baselineFirst ? headClasspath : baselineClasspath;
      cmd.add("-cp");
      cmd.add(driverJar);
      cmd.add(DRIVER_MAIN);
      cmd.add(config.getName());
      cmd.add(config.getTarget().getIdentifierName());
      cmd.add(firstClasspath);
      cmd.add(secondClasspath);
      cmd.add(Integer.toString(warmup));
      cmd.add(Integer.toString(quartets));
      cmd.add(rawPath.toString());
      if (verbose) {
        System.out.println("Running: " + String.join(" ", cmd));
      }
      Process process = new ProcessBuilder(cmd).directory(jvmDir.toFile()).inheritIO().start();
      int exitCode;
      try {
        exitCode = process.waitFor();
      } finally {
        if (process.isAlive()) {
          process.destroyForcibly();
        }
      }
      if (exitCode != 0) {
        throw new RuntimeException("Stable driver JVM " + i + " failed with exit code " + exitCode);
      }
      List<String> lines = FileUtils.readAllLines(rawPath);
      long[] firstNanos = parseLongs(lines.get(0));
      long[] secondNanos = parseLongs(lines.get(1));
      long[] baselineNanos = baselineFirst ? firstNanos : secondNanos;
      long[] headNanos = baselineFirst ? secondNanos : firstNanos;

      for (int s = 0; s < headNanos.length; s++) {
        sumLogRatio += Math.log((double) headNanos[s] / baselineNanos[s]);
        totalHeadNanos += headNanos[s];
        totalBaselineNanos += baselineNanos[s];
        totalSamples++;
      }

      JsonObject entry = new JsonObject();
      entry.addProperty("order", baselineFirst ? "baseline_first" : "head_first");
      entry.add("head_runtime_ns", toJsonArray(headNanos));
      entry.add("baseline_runtime_ns", toJsonArray(baselineNanos));
      results.add(entry);
    }

    double diffPct = (Math.exp(sumLogRatio / totalSamples) - 1.0) * 100.0;
    double meanHeadSec = totalHeadNanos / (totalSamples * 1e9);
    double meanBaselineSec = totalBaselineNanos / (totalSamples * 1e9);
    System.out.printf(
        Locale.ROOT,
        "%s (%s): %+.2f%% (head=%.2fs, baseline=%.2fs, jvms=%d)%n",
        config.getName(),
        config.getTarget().getIdentifierName(),
        diffPct,
        meanHeadSec,
        meanBaselineSec,
        count);

    if (environment.hasOutputPath()) {
      JsonObject root = new JsonObject();
      root.addProperty("benchmark_name", config.getName());
      root.add("results", results);
      FileUtils.writeTextFile(environment.getOutputPath(), root.toString());
    }
  }

  private static List<String> collectForwardedJvmProperties() {
    List<String> props = new ArrayList<>();
    for (String key : System.getProperties().stringPropertyNames()) {
      if (key.startsWith("TEST_")
          || key.startsWith("TESTBASE_")
          || key.startsWith("BUILD_PROP_")
          || key.equals("REPO_ROOT")) {
        props.add("-D" + key + "=" + System.getProperty(key));
      }
    }
    return props;
  }

  private static String requireStringProperty(String name) {
    String value = System.getProperty(name);
    if (value == null || value.isEmpty()) {
      throw new IllegalStateException("Missing required system property: " + name);
    }
    return value;
  }

  private static long[] parseLongs(String csv) {
    List<String> parts = StringUtils.split(csv.trim(), ',');
    long[] values = new long[parts.size()];
    for (int i = 0; i < parts.size(); i++) {
      values[i] = Long.parseLong(parts.get(i).trim());
    }
    return values;
  }

  private static JsonArray toJsonArray(long[] values) {
    JsonArray array = new JsonArray();
    for (long value : values) {
      array.add(value);
    }
    return array;
  }
}

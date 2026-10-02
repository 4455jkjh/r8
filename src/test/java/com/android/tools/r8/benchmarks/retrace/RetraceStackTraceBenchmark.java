// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.retrace;

import static com.android.tools.r8.ToolHelper.shouldRunSlowTests;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.benchmarks.BenchmarkBase;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.BenchmarkDependency;
import com.android.tools.r8.benchmarks.BenchmarkEnvironment;
import com.android.tools.r8.benchmarks.BenchmarkMethod;
import com.android.tools.r8.benchmarks.BenchmarkRunner;
import com.android.tools.r8.benchmarks.BenchmarkTarget;
import com.android.tools.r8.retrace.MappingSupplier;
import com.android.tools.r8.retrace.PartitionMappingSupplier;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.android.tools.r8.utils.internal.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Example of setting up a benchmark based on the testing infrastructure. */
@RunWith(Parameterized.class)
public abstract class RetraceStackTraceBenchmark extends BenchmarkBase {

  private static final BenchmarkDependency benchmarkDependency =
      new BenchmarkDependency(
          "retraceBenchmark", "retrace_benchmark", Paths.get(ToolHelper.THIRD_PARTY_DIR));

  protected RetraceStackTraceBenchmark(BenchmarkConfig config, TestParameters parameters) {
    super(config, parameters);
  }

  /** Static method to add benchmarks to the benchmark collection. */
  public static List<BenchmarkConfig> configs() {
    return ImmutableList.of(
        R8.config(),
        R8250MBDisk.config(),
        R8250MBMem.config(),
        R8250MBPartition.config(),
        R8500MBDisk.config(),
        R8500MBMem.config(),
        R8500MBPartition.config(),
        R8750MBDisk.config(),
        R8750MBMem.config(),
        R8750MBPartition.config(),
        R81GBDisk.config(),
        R81GBMem.config(),
        R81GBPartition.config());
  }

  private static BenchmarkConfig makeConfig(String name, BenchmarkMethod method) {
    return BenchmarkConfig.builder()
        .setName(name)
        .setTarget(BenchmarkTarget.RETRACE)
        .measureRunTime()
        .setMethod(method)
        .setFromRevision(12266)
        .measureWarmup()
        .addDependency(benchmarkDependency)
        .build();
  }

  public static class R8 extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig("R8", benchmarkRetraceLegacy("r8lib.jar.map"));
    }

    public R8(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R8250MBDisk extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig("R8-250MB-DISK", benchmarkRetraceFromDisk("r8lib-250MB.jar.map"));
    }

    public R8250MBDisk(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R8250MBMem extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig("R8-250MB-MEM", benchmarkRetraceFromMemory("r8lib-250MB.jar.map"));
    }

    public R8250MBMem(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R8250MBPartition extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig(
          "R8-250MB-PARTITION", benchmarkRetraceFromPartition("r8lib-250MB.jar.map.partition"));
    }

    public R8250MBPartition(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R8500MBDisk extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig("R8-500MB-DISK", benchmarkRetraceFromDisk("r8lib-500MB.jar.map"));
    }

    public R8500MBDisk(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R8500MBMem extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig("R8-500MB-MEM", benchmarkRetraceFromMemory("r8lib-500MB.jar.map"));
    }

    public R8500MBMem(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R8500MBPartition extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig(
          "R8-500MB-PARTITION", benchmarkRetraceFromPartition("r8lib-500MB.jar.map.partition"));
    }

    public R8500MBPartition(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R8750MBDisk extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig("R8-750MB-DISK", benchmarkRetraceFromDisk("r8lib-750MB.jar.map"));
    }

    public R8750MBDisk(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R8750MBMem extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig("R8-750MB-MEM", benchmarkRetraceFromMemory("r8lib-750MB.jar.map"));
    }

    public R8750MBMem(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R8750MBPartition extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig(
          "R8-750MB-PARTITION", benchmarkRetraceFromPartition("r8lib-750MB.jar.map.partition"));
    }

    public R8750MBPartition(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R81GBDisk extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig("R8-1GB-DISK", benchmarkRetraceFromDisk("r8lib-1GB.jar.map"));
    }

    public R81GBDisk(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R81GBMem extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig("R8-1GB-MEM", benchmarkRetraceFromMemory("r8lib-1GB.jar.map"));
    }

    public R81GBMem(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static class R81GBPartition extends RetraceStackTraceBenchmark {

    @Parameters(name = "{0}")
    public static List<Object[]> data() {
      return parametersFromConfig(config());
    }

    public static BenchmarkConfig config() {
      return makeConfig(
          "R8-1GB-PARTITION", benchmarkRetraceFromPartition("r8lib-1GB.jar.map.partition"));
    }

    public R81GBPartition(BenchmarkConfig config, TestParameters parameters) {
      super(config, parameters);
    }
  }

  public static BenchmarkMethod benchmarkRetraceLegacy(String mappingFileName) {
    return internalBenchmarkRetrace(
        mappingFileName,
        false,
        false,
        environment ->
            BenchmarkRunner.runner(environment).reportResultSum().setBenchmarkIterations(4));
  }

  public static BenchmarkMethod benchmarkRetraceFromDisk(String mappingFileName) {
    return internalBenchmarkRetrace(
        mappingFileName,
        false,
        false,
        environment ->
            BenchmarkRunner.runner(environment).reportResultAverage().setBenchmarkIterations(5));
  }

  public static BenchmarkMethod benchmarkRetraceFromMemory(String mappingFileName) {
    return internalBenchmarkRetrace(
        mappingFileName,
        true,
        false,
        environment ->
            BenchmarkRunner.runner(environment).reportResultAverage().setBenchmarkIterations(5));
  }

  public static BenchmarkMethod benchmarkRetraceFromPartition(String mappingFileName) {
    return internalBenchmarkRetrace(
        mappingFileName,
        false,
        true,
        environment ->
            BenchmarkRunner.runner(environment).reportResultAverage().setBenchmarkIterations(5));
  }

  public static BenchmarkMethod internalBenchmarkRetrace(
      String mappingFileName,
      boolean inMemoryMappingFile,
      boolean partitionedMappingFile,
      Function<BenchmarkEnvironment, BenchmarkRunner> runnerFactory) {
    return environment ->
        runnerFactory
            .apply(environment)
            .setWarmupIterations(1)
            .run(
                results -> {
                  Path dependencyRoot = benchmarkDependency.getRoot(environment);
                  Path mappingFile = dependencyRoot.resolve(mappingFileName);
                  byte[] mappingFileContents =
                      inMemoryMappingFile
                          ? FileUtils.readTextFile(mappingFile).getBytes(UTF_8)
                          : null;
                  List<String> stackTrace =
                      Files.readAllLines(dependencyRoot.resolve("stacktrace.txt"));
                  List<String> retraced = new ArrayList<>();
                  long start = System.nanoTime();
                  Retrace.run(
                      RetraceCommand.builder()
                          .setMappingSupplier(
                              createMappingSupplier(
                                  mappingFile, mappingFileContents, partitionedMappingFile))
                          .setStackTrace(stackTrace)
                          .setRetracedStackTraceConsumer(retraced::addAll)
                          .build());
                  long end = System.nanoTime();
                  // Add a simple check to ensure that we do not, in case of invalid retracing,
                  // record an optimal benchmark result.
                  if (retraced.size() < stackTrace.size()) {
                    throw new RuntimeException("Unexpected missing lines in retraced result");
                  }
                  results.addRuntimeResult(end - start);
                  // Do not make subsequent iterations pay for the memory allocated by this
                  // iteration.
                  System.gc();
                  System.gc();
                });
  }

  private static MappingSupplier<?> createMappingSupplier(
      Path mappingFile, byte[] mappingFileContents, boolean partitionedMappingFile)
      throws IOException {
    if (partitionedMappingFile) {
      return PartitionMappingSupplier.fromPath(mappingFile);
    }
    return ProguardMappingSupplier.builder()
        .setProguardMapProducer(
            mappingFileContents != null
                ? ProguardMapProducer.fromBytes(mappingFileContents)
                : ProguardMapProducer.fromPath(mappingFile))
        .setLoadAllDefinitions(false)
        .build();
  }

  @Test
  @Override
  public void testBenchmarks() throws Exception {
    assumeTrue(shouldRunSlowTests());
    super.testBenchmarks();
  }
}

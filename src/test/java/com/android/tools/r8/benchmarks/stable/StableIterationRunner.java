// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.stable;

import com.android.tools.r8.benchmarks.BenchmarkCollection;
import com.android.tools.r8.benchmarks.BenchmarkConfig;
import com.android.tools.r8.benchmarks.BenchmarkEnvironment;
import com.android.tools.r8.benchmarks.BenchmarkIdentifier;
import com.android.tools.r8.benchmarks.BenchmarkResultsWarmup;
import it.unimi.dsi.fastutil.longs.LongList;
import java.util.concurrent.Callable;
import org.junit.rules.TemporaryFolder;

/**
 * Entry point loaded inside each commit's isolated {@link java.net.URLClassLoader} by {@link
 * StableBenchmarkDriver} to execute a single timed benchmark compilation directly through the
 * normal benchmark definition.
 */
public final class StableIterationRunner {

  private StableIterationRunner() {}

  public static Callable<Long> create(String benchmarkName, String targetName) {
    BenchmarkIdentifier identifier = BenchmarkIdentifier.parse(benchmarkName, targetName);
    BenchmarkConfig config = BenchmarkCollection.computeCollection().getBenchmark(identifier);
    if (config == null) {
      throw new IllegalArgumentException("Unknown benchmark: " + identifier);
    }
    return () -> {
      TemporaryFolder temp = new TemporaryFolder();
      temp.create();
      try {
        BenchmarkResultsWarmup results = new BenchmarkResultsWarmup(config.getName());
        config.run(new BenchmarkEnvironment(config, temp, results));
        LongList runtimeResults = results.getRuntimeResults();
        if (runtimeResults.size() != 1) {
          throw new IllegalStateException(
              "Expected 1 runtime result for "
                  + config.getName()
                  + ", got "
                  + runtimeResults.size());
        }
        return runtimeResults.getLong(0);
      } finally {
        temp.delete();
      }
    };
  }
}

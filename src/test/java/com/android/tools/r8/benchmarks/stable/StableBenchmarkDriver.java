// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.stable;

import java.io.File;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

/**
 * Compares two locally built {@code r8lib} + test classpaths inside the same JVM and writes raw
 * per-compile timings.
 *
 * <p>Each commit gets its own {@link URLClassLoader} (parented at the platform classloader) loading
 * its own {@code r8lib.jar} and matching rewritten test JARs so the two copies of R8 stay isolated
 * from each other and from this driver.
 *
 * <p>Because both loaders share the JDK standard library, whichever copy warms up first trains
 * HotSpot's JIT type profiles in shared JDK collections and runs ~1.5-3.5% faster for the rest of
 * that JVM. {@link StableBenchmarkRunner} swaps the order on every second JVM so downstream
 * analysis cancels that bias out.
 */
public final class StableBenchmarkDriver {

  private static final String RUNNER_CLASS =
      "com.android.tools.r8.benchmarks.stable.StableIterationRunner";

  public static void main(String[] args) throws Exception {
    String benchmarkName = args[0];
    String targetName = args[1];
    URL[] firstUrls = parseClasspath(args[2]);
    URL[] secondUrls = parseClasspath(args[3]);
    int warmup = Integer.parseInt(args[4]);
    int quartets = Integer.parseInt(args[5]);
    Path outputPath = Paths.get(args[6]);

    String result = runStable(benchmarkName, targetName, firstUrls, secondUrls, warmup, quartets);
    Files.write(outputPath, result.getBytes(StandardCharsets.UTF_8));
  }

  private static URL[] parseClasspath(String classpath) throws MalformedURLException {
    String[] entries = classpath.split(File.pathSeparator);
    URL[] urls = new URL[entries.length];
    for (int i = 0; i < entries.length; i++) {
      urls[i] = Paths.get(entries[i]).toUri().toURL();
    }
    return urls;
  }

  private static String runStable(
      String benchmarkName,
      String targetName,
      URL[] firstUrls,
      URL[] secondUrls,
      int warmup,
      int quartets)
      throws Exception {
    ClassLoader platform = ClassLoader.getPlatformClassLoader();
    try (URLClassLoader firstLoader = new URLClassLoader(firstUrls, platform);
        URLClassLoader secondLoader = new URLClassLoader(secondUrls, platform)) {
      Callable<Long> firstRunner = createRunner(firstLoader, benchmarkName, targetName);
      Callable<Long> secondRunner = createRunner(secondLoader, benchmarkName, targetName);
      for (int i = 0; i < warmup; i++) {
        callWithContextClassLoader(firstLoader, firstRunner);
        callWithContextClassLoader(secondLoader, secondRunner);
      }

      // Run each group in ABBA order (first, second, second, first) so gradual speed drift (e.g.
      // CPU heating up) affects both copies equally, and emit raw nanosecond timings for each.
      long[] firstNanos = new long[quartets * 2];
      long[] secondNanos = new long[quartets * 2];
      for (int p = 0; p < quartets; p++) {
        firstNanos[p * 2] = callWithContextClassLoader(firstLoader, firstRunner);
        secondNanos[p * 2] = callWithContextClassLoader(secondLoader, secondRunner);
        secondNanos[p * 2 + 1] = callWithContextClassLoader(secondLoader, secondRunner);
        firstNanos[p * 2 + 1] = callWithContextClassLoader(firstLoader, firstRunner);
      }
      return joinLongs(firstNanos) + "\n" + joinLongs(secondNanos) + "\n";
    }
  }

  @SuppressWarnings("unchecked")
  private static Callable<Long> createRunner(
      URLClassLoader loader, String benchmarkName, String targetName) throws Exception {
    return callWithContextClassLoader(
        loader,
        () -> {
          Class<?> cls = Class.forName(RUNNER_CLASS, true, loader);
          Method create = cls.getMethod("create", String.class, String.class);
          return (Callable<Long>) create.invoke(null, benchmarkName, targetName);
        });
  }

  private static <T> T callWithContextClassLoader(URLClassLoader loader, Callable<T> action)
      throws Exception {
    Thread thread = Thread.currentThread();
    ClassLoader previous = thread.getContextClassLoader();
    thread.setContextClassLoader(loader);
    try {
      return action.call();
    } finally {
      thread.setContextClassLoader(previous);
    }
  }

  private static String joinLongs(long[] values) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < values.length; i++) {
      if (i > 0) {
        sb.append(',');
      }
      sb.append(values[i]);
    }
    return sb.toString();
  }
}

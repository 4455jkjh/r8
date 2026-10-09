// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks;

import com.android.tools.r8.TestDeps;
import com.android.tools.r8.ToolHelper;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.function.Supplier;

public class BenchmarkDependency {

  public static BenchmarkDependency getRuntimeJarJava8() {
    return new BenchmarkDependency("java8rtjar", TestDeps::getJava8RuntimeDir);
  }

  public static BenchmarkDependency getAndroidJar30() {
    return new BenchmarkDependency(
        "android30jar", "lib-v30", Paths.get(ToolHelper.THIRD_PARTY_DIR, "android_jar"));
  }

  // Nice name of the dependency. Must be a valid dart identifier.
  private final String name;

  // Directory of the dependency in the R8 source tree.
  private final Supplier<Path> directorySupplier;

  public BenchmarkDependency(String name, String directoryName, Path location) {
    this(name, () -> location.resolve(directoryName));
  }

  public BenchmarkDependency(String name, Supplier<Path> directorySupplier) {
    this.name = name;
    this.directorySupplier = directorySupplier;
    String firstChar = name.substring(0, 1);
    if (!firstChar.equals(firstChar.toLowerCase(Locale.ROOT)) || name.contains("_")) {
      throw new BenchmarkConfigError("Benchmark name should use lowerCamelCase, found: " + name);
    }
  }

  public String getName() {
    return name;
  }

  public Path getTarball() {
    Path directory = directorySupplier.get();
    return directory.getParent().resolve(directory.getFileName() + ".tar.gz");
  }

  public Path getSha1() {
    Path directory = directorySupplier.get();
    return directory.getParent().resolve(directory.getFileName() + ".tar.gz.sha1");
  }

  public Path getRoot(BenchmarkEnvironment environment) {
    return directorySupplier.get();
  }
}

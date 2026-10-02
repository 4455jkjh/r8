// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;

import com.android.tools.r8.TestDeps;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

public class Jdk11TestLibraryDesugaringSpecification {

  public static final Path EXTENSION_PATH = TestDeps.getJavaBaseExtensionJar();

  public static LibraryDesugaringSpecification JDK8_JAVA_BASE_EXT;
  public static LibraryDesugaringSpecification JDK11_JAVA_BASE_EXT;
  public static LibraryDesugaringSpecification JDK11_PATH_JAVA_BASE_EXT;

  public static void setUp() {
    if (ToolHelper.isWindows()) {
      // The library configuration is not available on windows. Do not run anything.
      return;
    }
    JDK8_JAVA_BASE_EXT = createSpecification("JDK8_JAVA_BASE_EXT", JDK8);
    JDK11_JAVA_BASE_EXT = createSpecification("JDK11_JAVA_BASE_EXT", JDK11);
    JDK11_PATH_JAVA_BASE_EXT = createSpecification("JDK11_PATH_JAVA_BASE_EXT", JDK11_PATH);
  }

  private static LibraryDesugaringSpecification createSpecification(
      String name, LibraryDesugaringSpecification template) {
    Set<Path> desugaredJDKLibFiles = new HashSet<>(template.getDesugarJdkLibs());
    desugaredJDKLibFiles.add(EXTENSION_PATH);
    Set<Path> libFiles = new HashSet<>(template.getLibraryFiles());
    libFiles.add(EXTENSION_PATH);
    return new LibraryDesugaringSpecification(
        name,
        desugaredJDKLibFiles,
        template.getSpecification(),
        libFiles,
        template.getDescriptor(),
        getTestNGKeepRules());
  }

  private static String getTestNGKeepRules() {
    // Keep data providers and their annotations.
    return "-keepclasseswithmembers class * {\n"
        + "    @org.testng.annotations.DataProvider <methods>;\n"
        + "}\n"
        + "-keepattributes *Annotation*\n"
        // Do not even attempt to shrink testNG (unrelated to desugared lib shrinking goal).
        + "-keep class org.testng.** { *; }\n"
        // There are missing classes in testNG.
        + "-dontwarn";
  }
}

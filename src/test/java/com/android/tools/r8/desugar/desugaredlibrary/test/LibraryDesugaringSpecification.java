// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import static com.android.tools.r8.ToolHelper.DESUGARED_JDK_8_LIB_JAR;
import static com.android.tools.r8.ToolHelper.DESUGARED_LIB_RELEASES_DIR;

import com.android.tools.r8.L8TestBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.jdk11.DesugaredLibraryJDK11Undesugarer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;

public class LibraryDesugaringSpecification {

  private static final String RELEASES_DIR = "third_party/openjdk/desugar_jdk_libs_releases/";
  private static final Path UNDESUGARED_JDK_11_LIB_JAR =
      DesugaredLibraryJDK11Undesugarer.undesugaredJarJDK11(
          Paths.get("third_party/openjdk/desugar_jdk_libs_11/desugar_jdk_libs.jar"));

  // Main head specifications.
  public static LibraryDesugaringSpecification JDK8 =
      new LibraryDesugaringSpecification(
          "JDK8", false, DESUGARED_JDK_8_LIB_JAR, "desugar_jdk_libs.json", AndroidApiLevel.P);
  public static LibraryDesugaringSpecification JDK11 =
      new LibraryDesugaringSpecification(
          "JDK11", UNDESUGARED_JDK_11_LIB_JAR, "jdk11/desugar_jdk_libs.json", AndroidApiLevel.R);
  public static LibraryDesugaringSpecification JDK11_MINIMAL =
      new LibraryDesugaringSpecification(
          "JDK11_MINIMAL",
          UNDESUGARED_JDK_11_LIB_JAR,
          "jdk11/desugar_jdk_libs_minimal.json",
          AndroidApiLevel.R);
  public static LibraryDesugaringSpecification JDK11_PATH =
      new LibraryDesugaringSpecification(
          "JDK11_PATH",
          UNDESUGARED_JDK_11_LIB_JAR,
          "jdk11/desugar_jdk_libs_path.json",
          AndroidApiLevel.R);

  // Legacy specifications.
  public static LibraryDesugaringSpecification JDK11_PATH_ALTERNATIVE_3 =
      new LibraryDesugaringSpecification(
          "JDK11_PATH_ALTERNATIVE_3",
          UNDESUGARED_JDK_11_LIB_JAR,
          "jdk11/desugar_jdk_libs_path_alternative_3.json",
          AndroidApiLevel.R);
  public static LibraryDesugaringSpecification JDK11_CHM_ONLY =
      new LibraryDesugaringSpecification(
          "JDK11_CHM_ONLY",
          UNDESUGARED_JDK_11_LIB_JAR,
          "jdk11/chm_only_desugar_jdk_libs.json",
          AndroidApiLevel.R);
  public static LibraryDesugaringSpecification JDK11_LEGACY =
      new LibraryDesugaringSpecification(
          "JDK11_LEGACY",
          // The legacy specification is not using the undesugared JAR.
          Paths.get("third_party/openjdk/desugar_jdk_libs_11/desugar_jdk_libs.jar"),
          "jdk11/desugar_jdk_libs_legacy.json",
          AndroidApiLevel.R);
  private static final LibraryDesugaringSpecification RELEASED_1_0_9 =
      new LibraryDesugaringSpecification("1.0.9", true, AndroidApiLevel.P);
  private static final LibraryDesugaringSpecification RELEASED_1_0_10 =
      new LibraryDesugaringSpecification("1.0.10", true, AndroidApiLevel.P);
  private static final LibraryDesugaringSpecification RELEASED_1_1_0 =
      new LibraryDesugaringSpecification("1.1.0", true, AndroidApiLevel.P);
  private static final LibraryDesugaringSpecification RELEASED_1_1_1 =
      new LibraryDesugaringSpecification("1.1.1", true, AndroidApiLevel.P);
  private static final LibraryDesugaringSpecification RELEASED_1_1_5 =
      new LibraryDesugaringSpecification("1.1.5", true, AndroidApiLevel.P);

  private final String name;
  private final boolean isJdk11Based;
  private final Set<Path> desugarJdkLibs;
  private final Path specification;
  private final Set<Path> libraryFiles;
  private final String extraKeepRules;

  private LibraryDesugaringSpecification(
      String name,
      boolean isJdk11Based,
      Path desugarJdkLibs,
      String specificationPath,
      AndroidApiLevel androidJarLevel) {
    this(
        name,
        isJdk11Based,
        ImmutableSet.of(desugarJdkLibs, ToolHelper.DESUGAR_LIB_CONVERSIONS),
        Paths.get("src/library_desugar/" + specificationPath),
        ToolHelper.getAndroidJar(androidJarLevel));
  }

  private LibraryDesugaringSpecification(
      String name, Path desugarJdkLibs, String specificationPath, AndroidApiLevel androidJarLevel) {
    this(
        name,
        true,
        ImmutableSet.of(desugarJdkLibs, ToolHelper.DESUGAR_LIB_CONVERSIONS),
        Paths.get("src/library_desugar/" + specificationPath),
        ToolHelper.getAndroidJar(androidJarLevel));
  }

  // This can be used to build custom specifications for testing purposes.
  public LibraryDesugaringSpecification(
      String name,
      boolean isJdk11Based,
      Set<Path> desugarJdkLibs,
      Path specification,
      Path androidJar) {
    this(name, isJdk11Based, desugarJdkLibs, specification, ImmutableSet.of(androidJar), "");
  }

  // This can be used to build custom specifications for testing purposes.
  public LibraryDesugaringSpecification(
      String name, Set<Path> desugarJdkLibs, Path specification, Path androidJar) {
    this(name, desugarJdkLibs, specification, ImmutableSet.of(androidJar), "");
  }

  // This can be used to build custom specifications for testing purposes.
  public LibraryDesugaringSpecification(
      String name,
      Set<Path> desugarJdkLibs,
      Path specification,
      Set<Path> libraryFiles,
      String extraKeepRules) {
    this(name, false, desugarJdkLibs, specification, libraryFiles, extraKeepRules);
  }

  // This can be used to build custom specifications for testing purposes.
  public LibraryDesugaringSpecification(
      String name,
      boolean isJdk11Based,
      Set<Path> desugarJdkLibs,
      Path specification,
      Set<Path> libraryFiles,
      String extraKeepRules) {
    this.name = name;
    this.isJdk11Based = isJdk11Based;
    this.desugarJdkLibs = desugarJdkLibs;
    this.specification = specification;
    this.libraryFiles = libraryFiles;
    this.extraKeepRules = extraKeepRules;
  }

  private LibraryDesugaringSpecification(
      String version, boolean isJdk11Based, AndroidApiLevel androidJarLevel) {
    this(
        "Release_" + version,
        isJdk11Based,
        ImmutableSet.of(
            Paths.get(DESUGARED_LIB_RELEASES_DIR, version, "desugar_jdk_libs.jar"),
            Paths.get(DESUGARED_LIB_RELEASES_DIR, version, "desugar_jdk_libs_configuration.jar")),
        Paths.get(DESUGARED_LIB_RELEASES_DIR, version, "desugar.json"),
        ToolHelper.getAndroidJar(androidJarLevel));
  }

  @Override
  public String toString() {
    return name;
  }

  public boolean isJdk11Based() {
    return isJdk11Based;
  }

  public Set<Path> getDesugarJdkLibs() {
    return desugarJdkLibs;
  }

  public Path getSpecification() {
    return specification;
  }

  public Set<Path> getLibraryFiles() {
    return libraryFiles;
  }

  public String getExtraKeepRules() {
    return extraKeepRules;
  }

  public void configureL8TestBuilder(L8TestBuilder l8TestBuilder) {
    configureL8TestBuilder(l8TestBuilder, false, "");
  }

  public void configureL8TestBuilder(
      L8TestBuilder l8TestBuilder, boolean l8Shrink, String keepRule) {
    l8TestBuilder
        .addProgramFiles(getDesugarJdkLibs())
        .addLibraryFiles(getLibraryFiles())
        .setDesugaredLibraryConfiguration(getSpecification())
        .noDefaultDesugarJDKLibs()
        .applyIf(
            l8Shrink,
            builder -> {
              if (keepRule != null && !keepRule.trim().isEmpty()) {
                String totalKeepRules = keepRule + "\n" + getExtraKeepRules();
                builder.addGeneratedKeepRules(totalKeepRules);
              }
            },
            L8TestBuilder::setDebug);
  }

  public static List<LibraryDesugaringSpecification> getReleased() {
    return ImmutableList.of(
        RELEASED_1_0_9, RELEASED_1_0_10, RELEASED_1_1_0, RELEASED_1_1_1, RELEASED_1_1_5);
  }

  public static List<LibraryDesugaringSpecification> getJdk8Jdk11() {
    return ImmutableList.of(JDK8, JDK11);
  }
}

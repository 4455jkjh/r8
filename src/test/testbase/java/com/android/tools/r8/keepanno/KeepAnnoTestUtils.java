// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno;

import static com.android.tools.r8.R8TestBuilder.KeepAnnotationLibrary.ANDROIDX;
import static com.android.tools.r8.utils.internal.FileUtils.isClassFile;
import static com.android.tools.r8.utils.internal.FileUtils.isJarFile;
import static com.android.tools.r8.utils.internal.FileUtils.isZipFile;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.R8TestBuilder.KeepAnnotationLibrary;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.keepanno.asm.KeepEdgeReader;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractor;
import com.android.tools.r8.keepanno.keeprules.KeepRuleExtractorOptions;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.internal.ListUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class KeepAnnoTestUtils {

  public static ProguardVersion PG_VERSION = ProguardVersion.V7_7_0;

  public static Path getKeepAnnoLib(KeepAnnotationLibrary keepAnnotationLibrary) {
    return keepAnnotationLibrary == ANDROIDX
        ? TestDeps.getKeepAnnoAndroidXAnnotationsJar()
        : TestDeps.getKeepAnnoAnnotationsJar();
  }

  public static Path getKeepAnnoLib() {
    return getKeepAnnoLib(ANDROIDX);
  }

  public static List<String> extractRulesFromFiles(
      List<Path> inputFiles, KeepRuleExtractorOptions extractorOptions) {
    List<String> result = new ArrayList<>();
    for (Path inputFile : inputFiles) {
      try {
        if (isClassFile(inputFile)) {
          result.addAll(
              extractRulesFromBytes(
                  ImmutableList.of(Files.readAllBytes(inputFile)), extractorOptions));
        } else if (isJarFile(inputFile) || isZipFile(inputFile)) {
          List<byte[]> classFilesFromArchive = new ArrayList<>();
          ZipUtils.iter(
              inputFile,
              (entry, input) -> {
                if (ZipUtils.isClassFile(entry.getName())) {
                  classFilesFromArchive.add(ByteStreams.toByteArray(input));
                }
              });
          result.addAll(extractRulesFromBytes(classFilesFromArchive, extractorOptions));
        } else {
          assert false : "Unsupported file format";
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return result;
  }

  public static List<String> extractRules(
      List<Class<?>> inputClasses, KeepRuleExtractorOptions extractorOptions) {
    return extractRulesFromBytes(
        ListUtils.map(inputClasses, ToolHelper::getClassAsBytes), extractorOptions);
  }

  public static List<String> extractRulesFromBytes(
      List<byte[]> inputClasses, KeepRuleExtractorOptions extractorOptions) {
    List<String> rules = new ArrayList<>();
    for (byte[] bytes : inputClasses) {
      List<KeepDeclaration> declarations = KeepEdgeReader.readKeepEdges(bytes);
      KeepRuleExtractor extractor = new KeepRuleExtractor(rules::add, extractorOptions);
      declarations.forEach(extractor::extract);
    }
    return rules;
  }
}

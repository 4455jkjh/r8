// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tools.dex;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.dex.DexParser;
import com.android.tools.r8.dex.DexReader;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

public class StringConstantPoolPrinter {

  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      System.err.println("Usage: StringConstantPoolPrinter <apk-path>");
      System.exit(1);
    }
    String apkPathString = args[0];
    Path apkPath = Paths.get(apkPathString);
    if (!Files.exists(apkPath)) {
      throw new NoSuchFileException(apkPathString);
    }
    run(apkPath);
  }

  private static void run(Path apkPath) throws IOException, ResourceException {
    Set<DexString> uniqueStrings = new TreeSet<>();
    AndroidApp app = AndroidApp.builder().addProgramFiles(apkPath).build();
    InternalOptions options = new InternalOptions();
    for (ProgramResource resource : app.computeAllProgramResources()) {
      if (resource.getKind() == Kind.DEX) {
        readStrings(resource, options, uniqueStrings);
      }
    }
    uniqueStrings.forEach(System.out::println);
    System.out.println();
    System.out.println("Printed " + uniqueStrings.size() + " strings.");
  }

  private static void readStrings(
      ProgramResource resource, InternalOptions options, Set<DexString> uniqueStrings)
      throws IOException, ResourceException {
    DexReader reader = new DexReader(resource);
    DexParser<DexProgramClass> parser = new DexParser<>(reader, ClassKind.PROGRAM, options);
    parser.populateIndexTables();
    Collections.addAll(uniqueStrings, parser.getIndexedItems().getStringMap());
  }
}

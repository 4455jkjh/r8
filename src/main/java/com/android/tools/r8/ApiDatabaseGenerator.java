// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.androidapi.ApiDatabaseEntry;
import com.android.tools.r8.apimodel.AndroidApiHashingDatabaseBuilderGenerator;
import com.android.tools.r8.apimodel.AndroidApiHashingDatabaseBuilderGenerator.GenerationException;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsingException;
import com.android.tools.r8.apimodel.ParsedApiClass;
import com.android.tools.r8.apimodel.ParsedApiClassMerging;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ExceptionUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@KeepForApi
public class ApiDatabaseGenerator {

  public static void run(ApiDatabaseGeneratorCommand command) throws ApiDatabaseGeneratorException {
    if (command.isPrintHelp()) {
      System.out.println(ApiDatabaseGeneratorCommandParser.getUsageMessage());
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("ApiDatabaseGenerator " + Version.getVersionString());
      return;
    }
    ExceptionUtils.withDiagnosticsHandler(
        command.getReporter(),
        () -> runInternal(command),
        (message, cause, cancelled) -> new ApiDatabaseGeneratorException(message, cause));
  }

  private static void runInternal(ApiDatabaseGeneratorCommand command)
      throws ApiDatabaseGeneratorException {
    try {
      List<ParsedApiClass> classes = extractClasses(command);
      Collection<ParsedApiClass> mergedClasses =
          ParsedApiClassMerging.merge(classes, command.getDiagnosticsHandler());
      Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries =
          AndroidApiHashingDatabaseBuilderGenerator.generateEntries(mergedClasses);
      AndroidApiHashingDatabaseBuilderGenerator.writeEntries(
          databaseEntries, command.getOutputPath());
    } catch (ParsingException | GenerationException e) {
      throw new ApiDatabaseGeneratorException("Failed to generate API database", e);
    }
  }

  private static List<ParsedApiClass> extractClasses(ApiDatabaseGeneratorCommand command)
      throws ParsingException {
    List<ParsedApiClass> allParsed = new ArrayList<>();
    for (Path xmlPath : command.getXmlPaths()) {
      List<ParsedApiClass> parsed = AndroidApiVersionsXmlParser.parse(xmlPath, null);
      allParsed.addAll(parsed);
    }
    return allParsed;
  }

  public static void main(String[] args) {
    try {
      run(ApiDatabaseGeneratorCommand.parse(args, CommandLineOrigin.INSTANCE).build());
    } catch (ApiDatabaseGeneratorException e) {
      System.err.println("API Database Generation failed: " + e.getMessage());
      if (e.getCause() != null) {
        System.err.println("Cause: " + e.getCause().getMessage());
      }
      throw new RuntimeException(e);
    } catch (RuntimeException e) {
      System.err.println("API Database Generation failed with an internal error.");
      throw e;
    }
  }
}

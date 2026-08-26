// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.apimodel.AmendApiFromResources;
import com.android.tools.r8.apimodel.AndroidApiHashingDatabaseBuilderGenerator;
import com.android.tools.r8.apimodel.AndroidApiHashingDatabaseBuilderGenerator.GenerationException;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsingException;
import com.android.tools.r8.apimodel.ParsedApiClass;
import com.android.tools.r8.apimodel.ParsedApiClassFlattening;
import com.android.tools.r8.apimodel.ParsedApiClassMerging;
import com.android.tools.r8.apimodel.ParsedApiClassSorting;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.JarTrimmer;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.RemovedTrimmer;
import com.android.tools.r8.apimodel.ParsedApiClassTrimming.Trimmer;
import com.android.tools.r8.apimodel.ParsedApiClassVerifier;
import com.android.tools.r8.apimodel.jar.ApiJarInfo;
import com.android.tools.r8.apimodel.jar.ApiJarMerging;
import com.android.tools.r8.apimodel.jar.ApiJarReader;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.utils.ExceptionUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;

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
      var apiClasses = generateClasses(command, trimmer -> trimmer);
      var entries = AndroidApiHashingDatabaseBuilderGenerator.generateEntries(apiClasses);
      AndroidApiHashingDatabaseBuilderGenerator.writeEntries(entries, command.getOutputPath());
    } catch (GenerationException e) {
      throw new ApiDatabaseGeneratorException("Failed to generate API database", e);
    }
  }

  static <E extends Throwable> Collection<ParsedApiClass> generateClasses(
      ApiDatabaseGeneratorCommand command, Function<JarTrimmer, Trimmer<E>> jarTrimmerWrapper)
      throws ApiDatabaseGeneratorException, E {
    try {
      Collection<ParsedApiClass> classes = extractClasses(command);
      classes = ParsedApiClassMerging.merge(classes, command.getDiagnosticsHandler());

      List<ApiJarInfo> jarInfos = extractJars(command);
      ApiJarInfo jarInfo = ApiJarMerging.mergeJarInfos(jarInfos);

      if (command.shouldAmend()) {
        amendApiData(classes, jarInfo);
      }
      classes = flattenHierarchy(classes);
      classes = ParsedApiClassTrimming.trim(classes, new RemovedTrimmer());
      Trimmer<E> jarTrimmer = jarTrimmerWrapper.apply(new JarTrimmer(jarInfo));
      classes = ParsedApiClassTrimming.trim(classes, jarTrimmer);
      classes = ParsedApiClassSorting.sorted(classes);
      ParsedApiClassVerifier.verify(classes);
      return classes;
    } catch (ParsingException | IOException e) {
      throw new ApiDatabaseGeneratorException("Failed to generate API classes", e);
    }
  }

  private static Collection<ParsedApiClass> flattenHierarchy(Collection<ParsedApiClass> classes)
      throws ApiDatabaseGeneratorException {
    try {
      return ParsedApiClassFlattening.flatten(classes);
    } catch (ApiDatabaseGeneratorException e) {
      throw new ApiDatabaseGeneratorException("Could not flatten class hierarchy", e);
    }
  }

  private static void amendApiData(Collection<ParsedApiClass> classes, ApiJarInfo jarInfo)
      throws ApiDatabaseGeneratorException, IOException {
    try {
      AmendApiFromResources.applyAmendments(classes, jarInfo);
    } catch (ApiDatabaseGeneratorException e) {
      throw new ApiDatabaseGeneratorException("Database amendment failed", e);
    } catch (IOException e) {
      throw new IOException("Database amendment failed", e);
    }
  }

  private static List<ParsedApiClass> extractClasses(ApiDatabaseGeneratorCommand command)
      throws ParsingException {
    List<ParsedApiClass> allParsed = new ArrayList<>();
    for (Path xmlPath : command.getXmlPaths()) {
      List<ParsedApiClass> parsed = AndroidApiVersionsXmlParser.parse(xmlPath);
      allParsed.addAll(parsed);
    }
    return allParsed;
  }

  private static List<ApiJarInfo> extractJars(ApiDatabaseGeneratorCommand command)
      throws ApiDatabaseGeneratorException, IOException {
    List<ApiJarInfo> allRead = new ArrayList<>();
    for (Path jarPath : command.getJarPaths()) {
      allRead.add(ApiJarReader.read(jarPath));
    }
    return allRead;
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

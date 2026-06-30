// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.androidapi.ApiDatabaseEntry;
import com.android.tools.r8.androidapi.DuplicateApiDatabaseEntryDiagnostic;
import com.android.tools.r8.apimodel.AndroidApiHashingDatabaseBuilderGenerator;
import com.android.tools.r8.apimodel.AndroidApiHashingDatabaseBuilderGenerator.GenerationException;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsingException;
import com.android.tools.r8.apimodel.ParsedApiClass;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

@KeepForApi
public class ApiDatabaseGenerator {

  private static class ParsedApiClassWithSource {
    public final ParsedApiClass apiClass;
    public final Set<Path> sources;

    public ParsedApiClassWithSource(ParsedApiClass apiClass, Path source) {
      this.apiClass = apiClass;
      this.sources = new LinkedHashSet<>();
      this.sources.add(source);
    }

    public ParsedApiClassWithSource(ParsedApiClass apiClass, Set<Path> sources) {
      this.apiClass = apiClass;
      this.sources = sources;
    }

    public String getSourcesString() {
      return sources.stream().map(Path::toString).collect(Collectors.joining(", "));
    }
  }

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
      List<ParsedApiClassWithSource> sourcedClasses = extractSourcedClasses(command);
      List<ParsedApiClass> mergedClasses =
          mergeParsedApiClasses(sourcedClasses, command.getDiagnosticsHandler());
      Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries =
          AndroidApiHashingDatabaseBuilderGenerator.generateEntries(mergedClasses);
      AndroidApiHashingDatabaseBuilderGenerator.writeEntries(
          databaseEntries, command.getOutputPath());
    } catch (ParsingException | GenerationException e) {
      throw new ApiDatabaseGeneratorException("Failed to generate API database", e);
    }
  }

  private static List<ParsedApiClassWithSource> extractSourcedClasses(
      ApiDatabaseGeneratorCommand command) throws ParsingException {
    List<ParsedApiClassWithSource> allParsed = new ArrayList<>();
    for (Path inputPath : command.getInputPaths()) {
      List<ParsedApiClass> parsed = AndroidApiVersionsXmlParser.parse(inputPath, null);
      for (ParsedApiClass apiClass : parsed) {
        allParsed.add(new ParsedApiClassWithSource(apiClass, inputPath));
      }
    }
    return allParsed;
  }

  private static List<ParsedApiClass> mergeParsedApiClasses(
      List<ParsedApiClassWithSource> parsedClasses, DiagnosticsHandler diagnosticsHandler)
      throws ApiDatabaseGeneratorException {
    Map<ClassReference, ParsedApiClassWithSource> mergedClasses = new LinkedHashMap<>();
    for (ParsedApiClassWithSource apiClassWithSource : parsedClasses) {
      ClassReference ref = apiClassWithSource.apiClass.getClassReference();
      ParsedApiClassWithSource existing = mergedClasses.get(ref);
      if (existing == null) {
        mergedClasses.put(ref, apiClassWithSource);
      } else {
        mergedClasses.put(ref, merge(existing, apiClassWithSource, diagnosticsHandler));
      }
    }
    List<ParsedApiClass> result = new ArrayList<>();
    for (ParsedApiClassWithSource withSource : mergedClasses.values()) {
      result.add(withSource.apiClass);
    }
    return result;
  }

  private static DuplicateApiDatabaseEntryDiagnostic duplicateClassError(
      ParsedApiClass duplicateClass, ParsedApiClassWithSource src1, ParsedApiClassWithSource src2) {
    String key = duplicateClass.getClassReference().getTypeName();
    String message =
        "Duplicate class "
            + key
            + " found in "
            + src1.getSourcesString()
            + " and "
            + src2.getSourcesString();
    return new DuplicateApiDatabaseEntryDiagnostic(message);
  }

  private static <T> void mergeMembers(
      Consumer<BiConsumer<T, AndroidApiLevel>> forEachA,
      Consumer<BiConsumer<T, AndroidApiLevel>> forEachB,
      Function<T, AndroidApiLevel> lookupA,
      Function<T, AndroidApiLevel> lookupB,
      BiConsumer<T, AndroidApiLevel> register) {
    forEachA.accept(
        (member, lvlA) -> {
          AndroidApiLevel lvlB = lookupB.apply(member);
          register.accept(member, lvlB != null ? lvlA.min(lvlB) : lvlA);
        });
    forEachB.accept(
        (member, lvlB) -> {
          if (lookupA.apply(member) == null) {
            register.accept(member, lvlB);
          }
        });
  }

  private static ParsedApiClassWithSource merge(
      ParsedApiClassWithSource a, ParsedApiClassWithSource b, DiagnosticsHandler diagnosticsHandler)
      throws ApiDatabaseGeneratorException {
    ParsedApiClass classA = a.apiClass;
    ParsedApiClass classB = b.apiClass;
    assert classA.getClassReference().equals(classB.getClassReference());
    diagnosticsHandler.error(duplicateClassError(classA, a, b));

    AndroidApiLevel minIntro = classA.getApiLevel().min(classB.getApiLevel());
    ParsedApiClass mergedClass = new ParsedApiClass(classA.getClassReference(), minIntro);

    mergeMembers(
        classA::forEachSupertype,
        classB::forEachSupertype,
        classA::getSupertypeApiLevel,
        classB::getSupertypeApiLevel,
        mergedClass::registerSupertype);

    checkSupertypeConflict(mergedClass);

    mergeMembers(
        classA::forEachInterface,
        classB::forEachInterface,
        classA::getInterfaceApiLevel,
        classB::getInterfaceApiLevel,
        mergedClass::registerInterface);
    mergeMembers(
        classA::forEachMethod,
        classB::forEachMethod,
        classA::getMethodApiLevel,
        classB::getMethodApiLevel,
        mergedClass::registerMethod);
    mergeMembers(
        classA::forEachField,
        classB::forEachField,
        classA::getFieldApiLevel,
        classB::getFieldApiLevel,
        mergedClass::registerField);

    return new ParsedApiClassWithSource(mergedClass, b.sources);
  }

  private static void checkSupertypeConflict(ParsedApiClass mergedClass)
      throws ApiDatabaseGeneratorException {
    List<ClassReference> mergedSupertypes = new ArrayList<>();
    mergedClass.forEachSupertype((ref, lvl) -> mergedSupertypes.add(ref));
    if (mergedSupertypes.size() > 1) {
      String supertypesString =
          StringUtils.join(", ", mergedSupertypes, ClassReference::getTypeName);
      throw new ApiDatabaseGeneratorException(
          "Class "
              + mergedClass.getClassReference().getTypeName()
              + " has conflicting supertypes: "
              + supertypesString);
    }
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

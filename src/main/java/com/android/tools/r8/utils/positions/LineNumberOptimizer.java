// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.positions;

import static com.android.tools.r8.utils.positions.PositionUtils.mustHaveResidualDebugInfo;

import com.android.tools.r8.debuginfo.DebugRepresentation.DebugRepresentationPredicate;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.MappingComposer;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapSupplierResult;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OriginalSourceFiles;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.internal.ObjectUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.android.tools.r8.utils.positions.MappedPositionToClassNameMapperBuilder.MappedPositionToClassNamingBuilder;
import com.android.tools.r8.utils.timing.Timing;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class LineNumberOptimizer {

  public static ProguardMapSupplierResult runAndWriteMap(
      AndroidApp inputApp,
      AppView<?> appView,
      ExecutorService executorService,
      Timing timing,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation)
      throws ExecutionException {
    return new LineNumberOptimizer(
            inputApp, appView, executorService, originalSourceFiles, representation)
        .runAndWriteMap(timing);
  }

  private final AndroidApp inputApp;
  private final AppView<?> appView;
  private final ExecutorService executorService;
  private final OriginalSourceFiles originalSourceFiles;
  private final DebugRepresentationPredicate representation;

  private LineNumberOptimizer(
      AndroidApp inputApp,
      AppView<?> appView,
      ExecutorService executorService,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation) {
    this.inputApp = inputApp;
    this.appView = appView;
    this.executorService = executorService;
    this.originalSourceFiles = originalSourceFiles;
    this.representation = representation;
  }

  @SuppressWarnings("InconsistentOverloads")
  private ProguardMapSupplierResult runAndWriteMap(Timing timing) throws ExecutionException {
    assert appView.options().hasMappingFileSupport();

    if (shouldWriteOriginalMappingFile()) {
      return writeOriginalMappingFile(timing);
    }
    ClassNameMapper mapper = run(timing);
    notifyMappingConsumer(mapper);
    if (shouldComposeOriginalMappingFile()) {
      mapper = composeOriginalMappingFile(mapper, timing);
    }
    return writeMappingFile(mapper, timing);
  }

  private void notifyMappingConsumer(ClassNameMapper mapper) {
    var consumer = appView.options().mappingComposeOptions().generatedClassNameMapperConsumer;
    if (consumer != null) {
      consumer.accept(mapper);
    }
  }

  private ProguardMapSupplierResult writeMappingFile(ClassNameMapper mapper, Timing timing)
      throws ExecutionException {
    timing.begin("Spawn write proguard map");
    ProguardMapSupplierResult result =
        ProguardMapSupplier.create(mapper, appView.options())
            .writeProguardMap(appView, executorService, timing);
    timing.end();
    return result;
  }

  private ClassNameMapper composeOriginalMappingFile(ClassNameMapper mapper, Timing timing) {
    try (Timing ignored = timing.begin("Compose proguard map")) {
      String composed =
          MappingComposer.compose(
              appView.options(), appView.appInfo().app().getProguardMap(), mapper);
      mapper = ClassNameMapper.mapperFromStringWithPreamble(composed);
    } catch (IOException | MappingComposeException e) {
      throw new CompilationError(e.getMessage(), e);
    }
    return mapper;
  }

  private boolean shouldComposeOriginalMappingFile() {
    return appView.options().mappingComposeOptions().enableExperimentalMappingComposition
        && appView.appInfo().app().getProguardMap() != null;
  }

  private boolean shouldWriteOriginalMappingFile() {
    if (!appView.options().mappingComposeOptions().enableExperimentalMappingComposition
        || appView.appInfo().app().getProguardMap() == null) {
      return false;
    }
    MapVersionMappingInformation mapVersionInfo =
        appView.appInfo().app().getProguardMap().getFirstMapVersionInformation();
    if (mapVersionInfo == null) {
      return true;
    }
    MapVersion newMapVersion = mapVersionInfo.getMapVersion();
    return !ResidualSignatureMappingInformation.isSupported(newMapVersion)
        || newMapVersion.isUnknown();
  }

  private ProguardMapSupplierResult writeOriginalMappingFile(Timing timing)
      throws ExecutionException {
    appView.options().reporter.warning(new NotSupportedMapVersionForMappingComposeDiagnostic());
    return writeMappingFile(appView.appInfo().app().getProguardMap(), timing);
  }

  /** Optimizes line numbers and returns a corresponding mapping. */
  private ClassNameMapper run(Timing timing) throws ExecutionException {
    timing.begin("Line number remapping");
    PositionToMappedRangeMapper positionToMappedRangeMapper =
        PositionToMappedRangeMapper.create(appView);

    var optimizedClasses = optimizePositions(positionToMappedRangeMapper, timing);
    ClassNameMapper mapper = buildMapper(optimizedClasses, timing);
    positionToMappedRangeMapper.updateDebugInfoInCodeObjects(timing);

    timing.end();
    return mapper;
  }

  /** Optimize line numbers and return the resulting mapping. */
  private Iterable<ClassPositionMapping> optimizePositions(
      PositionToMappedRangeMapper positionToMappedRangeMapper, Timing timing)
      throws ExecutionException {
    timing.begin("Process classes");
    AppPositionRemapper positionRemapper = AppPositionRemapper.create(appView, inputApp, timing);
    Deque<ClassPositionMapping> worklist = new ConcurrentLinkedDeque<>();
    ThreadUtils.processItemsThatMatches(
        appView.appInfo().classes(),
        this::shouldRunForClass,
        (clazz, threadTiming) -> {
          ClassPositionMapping classResult =
              optimizePositionsForClass(
                  clazz, positionRemapper, positionToMappedRangeMapper, threadTiming);
          worklist.addLast(classResult);
        },
        appView.options(),
        executorService,
        timing,
        timing.beginMerger("Map positions concurrently", executorService));
    timing.end();
    return worklist;
  }

  private ClassNameMapper buildMapper(Iterable<ClassPositionMapping> results, Timing timing) {
    try (Timing ignored = timing.begin("Add class naming")) {
      // TODO(b/552916515): Do this concurrently.
      MappedPositionToClassNameMapperBuilder builder =
          MappedPositionToClassNameMapperBuilder.builder(appView, originalSourceFiles);
      for (ClassPositionMapping classResult : results) {
        MappedPositionToClassNamingBuilder classNamingBuilder =
            builder.addClassNaming(classResult.clazz);
        for (MethodPositionMapping methodResult : classResult.methodMappings) {
          classNamingBuilder.addMappedPositions(
              methodResult.method,
              methodResult.mappedPositions,
              methodResult.positionRemapper,
              methodResult.canUsePc);
        }
      }
      return builder.build();
    }
  }

  /** In R8 partial compilation, skip classes that are compiled by D8. */
  private boolean shouldRunForClass(DexProgramClass clazz) {
    InternalOptions options = appView.options();
    if (options.partialSubCompilationConfiguration == null) {
      return true;
    } else {
      return !options.partialSubCompilationConfiguration.asR8().hasD8DefinitionFor(clazz.getType());
    }
  }

  private ClassPositionMapping optimizePositionsForClass(
      DexProgramClass clazz,
      AppPositionRemapper positionRemapper,
      PositionToMappedRangeMapper positionToMappedRangeMapper,
      Timing timing) {
    timing.begin("Prelude");
    IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
        OverloadedMethodOrdering.groupMethodsByRenamedName(appView, clazz);

    // Process methods ordered by renamed name.
    List<DexString> renamedMethodNames = new ArrayList<>(methodsByRenamedName.keySet());
    renamedMethodNames.sort(DexString::compareTo);
    timing.end();

    ClassPositionRemapper classPositionRemapper =
        positionRemapper.createClassPositionRemapper(clazz);
    List<MethodPositionMapping> methodPositionMappings = new ArrayList<>();
    for (DexString newMethodName : renamedMethodNames) {
      List<ProgramMethod> methods = methodsByRenamedName.get(newMethodName);
      var results =
          optimizePositionsForOverloads(
              newMethodName, methods, classPositionRemapper, positionToMappedRangeMapper, timing);
      methodPositionMappings.addAll(results);
    }
    return new ClassPositionMapping(clazz, methodPositionMappings);
  }

  private List<MethodPositionMapping> optimizePositionsForOverloads(
      DexString newMethodName,
      List<ProgramMethod> methods,
      ClassPositionRemapper classPositionRemapper,
      PositionToMappedRangeMapper positionToMappedRangeMapper,
      Timing timing) {
    // Sort the methods for deterministic numbering.
    OverloadedMethodOrdering.sortOverloadedMethods(methods);
    assert methods.size() <= 1 || verifyMethodsAreKeptDirectlyOrIndirectly(methods)
        : "Overloads are only allowed with good reason";

    timing.begin("Process methods");
    MethodPositionRemapper methodPositionRemapper =
        classPositionRemapper.createMethodPositionRemapper();
    List<MethodPositionMapping> results = new ArrayList<>(methods.size());
    for (ProgramMethod method : methods) {
      if (shouldOptimizeMethod(newMethodName, method, methods)) {
        results.add(
            optimizePositionsForMethod(
                method, methods, methodPositionRemapper, positionToMappedRangeMapper, timing));
      }
    }
    timing.end();
    return results;
  }

  private boolean shouldOptimizeMethod(
      DexString newMethodName, ProgramMethod method, List<ProgramMethod> methods) {
    assert method.getDefinition() != null : "Method has no definition " + method;
    DexEncodedMethod definition = method.getDefinition();
    return !method.getName().isIdenticalTo(newMethodName)
        || mustHaveResidualDebugInfo(appView.options(), definition)
        || definition.isD8R8Synthesized()
        || methods.size() > 1;
  }

  private MethodPositionMapping optimizePositionsForMethod(
      ProgramMethod method,
      List<ProgramMethod> methods,
      MethodPositionRemapper positionRemapper,
      PositionToMappedRangeMapper positionToMappedRangeMapper,
      Timing timing) {
    assert method.getDefinition() != null : "Method has no definition " + method;
    Code code = method.getDefinition().getCode();
    if (code == null
        || !(code.isCfCode() || code.isDexCode())
        || appView.isCfByteCodePassThrough(method)) {
      return new MethodPositionMapping(
          method, Collections.emptyList(), positionRemapper, representation.canUseDexPc(methods));
    }
    try (Timing ignored = timing.begin("Get mapped positions")) {
      int pcEncodingCutoff =
          ObjectUtils.identical(method, methods.get(0))
              ? representation.getDexPcEncodingCutoff(method)
              : -1;
      boolean canUseDexPc = pcEncodingCutoff > 0;
      List<MappedPosition> mappedPositions =
          positionToMappedRangeMapper.getMappedPositions(
              method, positionRemapper, methods.size() > 1, canUseDexPc, pcEncodingCutoff, timing);
      return new MethodPositionMapping(method, mappedPositions, positionRemapper, canUseDexPc);
    }
  }

  @SuppressWarnings("SameReturnValue")
  private boolean verifyMethodsAreKeptDirectlyOrIndirectly(List<ProgramMethod> methods) {
    assert !methods.isEmpty();
    if (appView.options().isGeneratingClassFiles() || !appView.appInfo().hasClassHierarchy()) {
      return true;
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfo().withClassHierarchy();
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    boolean allSeenAreInstanceInitializers = true;
    List<DexString> originalNames = new ArrayList<>(methods.size());
    for (ProgramMethod method : methods) {
      // We cannot rename instance initializers.
      assert method.getDefinition() != null;
      if (method.getDefinition().isInstanceInitializer()) {
        assert allSeenAreInstanceInitializers;
        continue;
      }
      allSeenAreInstanceInitializers = false;
      // If the method is pinned, we cannot minify it.
      if (!keepInfo.isMinificationAllowed(method, appView.options())) {
        continue;
      }
      // With desugared library, call-back names are reserved here.
      if (method.getDefinition().isLibraryMethodOverride().isTrue()) {
        continue;
      }
      // We use the same name for interface names even if it has different types.
      DexClassAndMethod lookupResult =
          appInfo.lookupMaximallySpecificMethod(method.getHolder(), method.getReference());
      if (lookupResult == null) {
        // We cannot rename methods we cannot look up.
        continue;
      }
      String fullMethodName = method.getReference().qualifiedName();
      assert lookupResult.getHolder().isInterface()
          : "Expected " + fullMethodName + " to be kept or an interface method";
      originalNames.add(method.getReference().name);
    }
    assert originalNames.stream().allMatch(name -> originalNames.get(0).isIdenticalTo(name))
        : "Non-overloaded methods should not become overloaded "
            + StringUtils.join(", ", originalNames);
    return true;
  }

  private static class ClassPositionMapping {

    private final DexProgramClass clazz;
    private final List<MethodPositionMapping> methodMappings;

    private ClassPositionMapping(
        DexProgramClass clazz, List<MethodPositionMapping> methodMappings) {
      this.clazz = clazz;
      this.methodMappings = methodMappings;
    }
  }

  private static class MethodPositionMapping {

    private final ProgramMethod method;
    private final List<MappedPosition> mappedPositions;
    private final MethodPositionRemapper positionRemapper;
    private final boolean canUsePc;

    private MethodPositionMapping(
        ProgramMethod method,
        List<MappedPosition> mappedPositions,
        MethodPositionRemapper positionRemapper,
        boolean canUsePc) {
      this.method = method;
      this.mappedPositions = mappedPositions;
      this.positionRemapper = positionRemapper;
      this.canUsePc = canUsePc;
    }
  }
}

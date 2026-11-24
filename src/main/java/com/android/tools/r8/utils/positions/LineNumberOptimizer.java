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
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNamingForNameMapper;
import com.android.tools.r8.naming.MapVersion;
import com.android.tools.r8.naming.MappingComposeException;
import com.android.tools.r8.naming.MappingComposer;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapSupplierResult;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.naming.mappinginformation.ResidualSignatureMappingInformation;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OriginalSourceFiles;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.internal.IntBox;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.ObjectUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.android.tools.r8.utils.positions.MappedPositionToClassNameMapperBuilder.MappedPositionToClassNamingBuilder;
import com.android.tools.r8.utils.timing.Timing;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class LineNumberOptimizer {

  public static ProguardMapSupplierResult runAndWriteMap(
      AppView<?> appView,
      ExecutorService executorService,
      Timing timing,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation)
      throws ExecutionException {
    return new LineNumberOptimizer(appView, executorService, originalSourceFiles, representation)
        .runAndWriteMap(timing);
  }

  private final AppView<?> appView;
  private final ExecutorService executorService;
  private final OriginalSourceFiles originalSourceFiles;
  private final DebugRepresentationPredicate representation;

  private LineNumberOptimizer(
      AppView<?> appView,
      ExecutorService executorService,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation) {
    this.appView = appView;
    this.executorService = executorService;
    this.originalSourceFiles = originalSourceFiles;
    this.representation = representation;
  }

  @SuppressWarnings("InconsistentOverloads")
  private ProguardMapSupplierResult runAndWriteMap(Timing timing) throws ExecutionException {
    assert appView.options().shouldOutputMappingFile();
    assert appView.getKotlinInlineMethodMap() != null;

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

  private ClassNameMapper composeOriginalMappingFile(ClassNameMapper mapper, Timing timing)
      throws ExecutionException {
    try (Timing ignored = timing.begin("Compose proguard map")) {
      mapper =
          MappingComposer.composeToClassNameMapper(
              appView.options(), executorService, appView.appInfo().app().getProguardMap(), mapper);
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
    try (Timing t0 = timing.begin("Line number remapping")) {
      PositionToMappedRangeMapper positionToMappedRangeMapper =
          PositionToMappedRangeMapper.create(appView);
      MappedPositionToClassNameMapperBuilder builder =
          MappedPositionToClassNameMapperBuilder.builder(appView, originalSourceFiles);
      Deque<ClassNamingForNameMapper> classNamings = new ConcurrentLinkedDeque<>();

      try (Timing t1 = timing.begin("Process classes")) {
        AppPositionRemapper positionRemapper = AppPositionRemapper.create(appView, timing);
        ThreadUtils.processItemsThatMatches(
            appView.appInfo().classes(),
            this::shouldRunForClass,
            (clazz, threadTiming) -> {
              ClassNamingForNameMapper classNaming =
                  optimizePositionsForClass(
                      clazz, positionRemapper, positionToMappedRangeMapper, builder, threadTiming);
              if (classNaming != null) {
                classNamings.addLast(classNaming);
              }
            },
            appView.options(),
            executorService,
            timing);
      }

      try (Timing t1 = timing.begin("Build mapper")) {
        return builder.build(classNamings);
      }
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

  private ClassNamingForNameMapper optimizePositionsForClass(
      DexProgramClass clazz,
      AppPositionRemapper positionRemapper,
      PositionToMappedRangeMapper positionToMappedRangeMapper,
      MappedPositionToClassNameMapperBuilder builder,
      Timing timing) {
    timing.begin("Prelude");
    IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
        OverloadedMethodOrdering.groupMethodsByRenamedName(appView, clazz);

    // Process methods ordered by renamed name.
    List<DexString> renamedMethodNames = new ArrayList<>(methodsByRenamedName.keySet());
    renamedMethodNames.sort(DexString::compareTo);
    MappedPositionToClassNamingBuilder classNamingBuilder = builder.createClassNamingBuilder(clazz);
    timing.end();

    var classPositionRemapper = positionRemapper.createClassPositionRemapper(clazz);
    for (DexString newMethodName : renamedMethodNames) {
      List<ProgramMethod> methods = methodsByRenamedName.get(newMethodName);
      optimizePositionsForOverloads(
          newMethodName,
          methods,
          classPositionRemapper,
          positionToMappedRangeMapper,
          classNamingBuilder,
          timing);
    }
    return classNamingBuilder.build();
  }

  private void optimizePositionsForOverloads(
      DexString newMethodName,
      List<ProgramMethod> methods,
      ClassPositionRemapper classPositionRemapper,
      PositionToMappedRangeMapper positionToMappedRangeMapper,
      MappedPositionToClassNamingBuilder classNamingBuilder,
      Timing timing) {
    // Sort the methods for deterministic numbering.
    OverloadedMethodOrdering.sortOverloadedMethods(methods);
    assert methods.size() <= 1 || verifyMethodsAreKeptDirectlyOrIndirectly(methods)
        : "Overloads are only allowed with good reason";

    timing.begin("Process methods");
    MethodPositionRemapper methodPositionRemapper =
        classPositionRemapper.createMethodPositionRemapper();
    for (ProgramMethod method : methods) {
      if (shouldOptimizeMethod(newMethodName, method, methods)) {
        optimizePositionsForMethod(
            method,
            methods,
            methodPositionRemapper,
            positionToMappedRangeMapper,
            classNamingBuilder,
            timing);
      }
    }
    timing.end();
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

  private void optimizePositionsForMethod(
      ProgramMethod method,
      List<ProgramMethod> methods,
      MethodPositionRemapper positionRemapper,
      PositionToMappedRangeMapper positionToMappedRangeMapper,
      MappedPositionToClassNamingBuilder classNamingBuilder,
      Timing timing) {
    assert method.getDefinition() != null : "Method has no definition " + method;
    Code code = method.getDefinition().getCode();
    if (code == null
        || !(code.isCfCode() || code.isDexCode())
        || appView.isCfByteCodePassThrough(method)) {
      classNamingBuilder.addMappedPositions(method, Collections.emptyList(), null);
      return;
    }
    List<MappedPosition> mappedPositions;
    Map<OutlineCallerPosition, Int2IntMap> outlineCalleePositions = null;
    try (Timing ignored = timing.begin("Get mapped positions")) {
      int pcEncodingCutoff =
          ObjectUtils.identical(method, methods.get(0))
              ? representation.getDexPcEncodingCutoff(method)
              : -1;
      boolean canUseDexPc = pcEncodingCutoff > 0;
      mappedPositions =
          positionToMappedRangeMapper.getMappedPositions(
              method, positionRemapper, methods.size() > 1, canUseDexPc, pcEncodingCutoff, timing);
      if (!mappedPositions.isEmpty()) {
        mappedPositions.sort(Comparator.comparingInt(MappedPosition::getObfuscatedLine));
        outlineCalleePositions =
            computeOutlineCalleePositions(mappedPositions, positionRemapper, canUseDexPc);
      }
    }
    try (Timing ignored = timing.begin("Add mapped positions")) {
      classNamingBuilder.addMappedPositions(method, mappedPositions, outlineCalleePositions);
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

  private static Map<OutlineCallerPosition, Int2IntMap> computeOutlineCalleePositions(
      List<MappedPosition> mappedPositions,
      MethodPositionRemapper positionRemapper,
      boolean canUsePc) {
    assert !mappedPositions.isEmpty();
    // Allocate synthetic placeholder line numbers for outline callee positions immediately after
    // remapping this method's instruction positions, before moving on to the next overload in
    // optimizePositionsForOverloads (which shares the same MethodPositionRemapper). This ensures
    // that a method's outline callee placeholder positions are contiguous with its instruction
    // positions rather than being allocated after subsequent overloads (see b/579315508).
    Map<OutlineCallerPosition, Int2IntMap> outlineCalleePositions = null;
    IntBox maxPc = new IntBox(ListUtils.last(mappedPositions).getObfuscatedLine());
    for (MappedPosition mappedPosition : mappedPositions) {
      Position position = mappedPosition.getPosition();
      if (position.isOutlineCaller()) {
        OutlineCallerPosition outlineCallerPosition = position.asOutlineCaller();
        if (outlineCalleePositions == null) {
          outlineCalleePositions = new LinkedHashMap<>();
        }
        outlineCalleePositions.computeIfAbsent(
            outlineCallerPosition,
            key -> {
              Int2IntMap positionMap = new Int2IntArrayMap();
              key.getOutlinePositions()
                  .forEach(
                      (line, outlinePosition) -> {
                        int placeHolderLineToBeFixed;
                        if (canUsePc) {
                          placeHolderLineToBeFixed = maxPc.get() + line + 1;
                        } else {
                          placeHolderLineToBeFixed =
                              positionRemapper
                                  .createRemappedPosition(outlinePosition)
                                  .getSecond()
                                  .getLine();
                        }
                        positionMap.put((int) line, placeHolderLineToBeFixed);
                        maxPc.set(placeHolderLineToBeFixed);
                      });
              return positionMap;
            });
      }
    }
    if (canUsePc && outlineCalleePositions != null) {
      // When PC-based encoding is used (for the first overload), DexPositionToPcMappedRangeMapper
      // only advances positionRemapper up to the method's last instruction PC. Advance
      // positionRemapper past the outline callee placeholder positions allocated above so that
      // subsequent overloads do not reuse those line numbers.
      positionRemapper.setNextOptimizedLineNumber(maxPc.get() + 1);
    }
    return outlineCalleePositions;
  }
}

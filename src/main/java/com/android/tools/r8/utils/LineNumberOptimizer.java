// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.ResourceException;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.debuginfo.DebugRepresentation.DebugRepresentationPredicate;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugEvent.AdvancePC;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEvent.EndLocal;
import com.android.tools.r8.graph.DexDebugEvent.RestartLocal;
import com.android.tools.r8.graph.DexDebugEvent.SetEpilogueBegin;
import com.android.tools.r8.graph.DexDebugEvent.SetFile;
import com.android.tools.r8.graph.DexDebugEvent.SetPrologueEnd;
import com.android.tools.r8.graph.DexDebugEvent.StartLocal;
import com.android.tools.r8.graph.DexDebugEventBuilder;
import com.android.tools.r8.graph.DexDebugEventVisitor;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexDebugInfoForSingleLineMethod;
import com.android.tools.r8.graph.DexDebugPositionState;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition;
import com.android.tools.r8.ir.code.Position.OutlineCallerPosition.OutlineCallerPositionBuilder;
import com.android.tools.r8.ir.code.Position.OutlinePosition;
import com.android.tools.r8.ir.code.Position.PositionBuilder;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.Result;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.ClassNaming;
import com.android.tools.r8.naming.ClassNaming.Builder;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.naming.ProguardMapSupplier.ProguardMapId;
import com.android.tools.r8.naming.Range;
import com.android.tools.r8.naming.mappinginformation.CompilerSynthesizedMappingInformation;
import com.android.tools.r8.naming.mappinginformation.FileNameInformation;
import com.android.tools.r8.naming.mappinginformation.MappingInformation;
import com.android.tools.r8.naming.mappinginformation.OutlineCallsiteMappingInformation;
import com.android.tools.r8.naming.mappinginformation.OutlineMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.RemoveInnerFramesAction;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.ThrowsCondition;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.internal.RetraceUtils;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.google.common.base.Suppliers;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2IntSortedMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;

public class LineNumberOptimizer {

  private static final int MAX_LINE_NUMBER = 65535;

  // PositionRemapper is a stateful function which takes a position (represented by a
  // DexDebugPositionState) and returns a remapped Position.
  private interface PositionRemapper {
    Pair<Position, Position> createRemappedPosition(Position position);
  }

  private static class IdentityPositionRemapper implements PositionRemapper {

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      // If we create outline calls we have to map them.
      assert position.getOutlineCallee() == null;
      return new Pair<>(position, position);
    }
  }

  private static class OptimizingPositionRemapper implements PositionRemapper {
    private final int maxLineDelta;
    private DexMethod previousMethod = null;
    private int previousSourceLine = -1;
    private int nextOptimizedLineNumber = 1;

    OptimizingPositionRemapper(InternalOptions options) {
      // TODO(113198295): For dex using "Constants.DBG_LINE_RANGE + Constants.DBG_LINE_BASE"
      // instead of 1 creates a ~30% smaller map file but the dex files gets larger due to reduced
      // debug info canonicalization.
      maxLineDelta = options.isGeneratingClassFiles() ? Integer.MAX_VALUE : 1;
    }

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      assert position.getMethod() != null;
      if (previousMethod == position.getMethod()) {
        assert previousSourceLine >= 0;
        if (position.getLine() > previousSourceLine
            && position.getLine() - previousSourceLine <= maxLineDelta) {
          nextOptimizedLineNumber += (position.getLine() - previousSourceLine) - 1;
        }
      }

      Position newPosition =
          position
              .builderWithCopy()
              .setLine(nextOptimizedLineNumber++)
              .setCallerPosition(null)
              .build();
      previousSourceLine = position.getLine();
      previousMethod = position.getMethod();
      return new Pair<>(position, newPosition);
    }
  }

  private static class KotlinInlineFunctionPositionRemapper implements PositionRemapper {

    private final AppView<?> appView;
    private final DexItemFactory factory;
    private final Map<DexType, Result> parsedKotlinSourceDebugExtensions = new IdentityHashMap<>();
    private final CfLineToMethodMapper lineToMethodMapper;
    private final PositionRemapper baseRemapper;

    // Fields for the current context.
    private DexEncodedMethod currentMethod;
    private Result parsedData = null;

    private KotlinInlineFunctionPositionRemapper(
        AppView<?> appView,
        PositionRemapper baseRemapper,
        CfLineToMethodMapper lineToMethodMapper) {
      this.appView = appView;
      this.factory = appView.dexItemFactory();
      this.baseRemapper = baseRemapper;
      this.lineToMethodMapper = lineToMethodMapper;
    }

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      assert currentMethod != null;
      int line = position.getLine();
      Result parsedData = getAndParseSourceDebugExtension(position.getMethod().holder);
      if (parsedData == null) {
        return baseRemapper.createRemappedPosition(position);
      }
      Map.Entry<Integer, KotlinSourceDebugExtensionParser.Position> inlinedPosition =
          parsedData.lookupInlinedPosition(line);
      if (inlinedPosition == null) {
        return baseRemapper.createRemappedPosition(position);
      }
      int inlineeLineDelta = line - inlinedPosition.getKey();
      int originalInlineeLine = inlinedPosition.getValue().getRange().from + inlineeLineDelta;
      try {
        String binaryName = inlinedPosition.getValue().getSource().getPath();
        String nameAndDescriptor =
            lineToMethodMapper.lookupNameAndDescriptor(binaryName, originalInlineeLine);
        if (nameAndDescriptor == null) {
          return baseRemapper.createRemappedPosition(position);
        }
        String clazzDescriptor = DescriptorUtils.getDescriptorFromClassBinaryName(binaryName);
        String methodName = CfLineToMethodMapper.getName(nameAndDescriptor);
        String methodDescriptor = CfLineToMethodMapper.getDescriptor(nameAndDescriptor);
        String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(methodDescriptor);
        String[] argumentDescriptors = DescriptorUtils.getArgumentTypeDescriptors(methodDescriptor);
        DexString[] argumentDexStringDescriptors = new DexString[argumentDescriptors.length];
        for (int i = 0; i < argumentDescriptors.length; i++) {
          argumentDexStringDescriptors[i] = factory.createString(argumentDescriptors[i]);
        }
        DexMethod inlinee =
            factory.createMethod(
                factory.createString(clazzDescriptor),
                factory.createString(methodName),
                factory.createString(returnTypeDescriptor),
                argumentDexStringDescriptors);
        if (!inlinee.equals(position.getMethod())) {
          // We have an inline from a different method than the current position.
          Entry<Integer, KotlinSourceDebugExtensionParser.Position> calleePosition =
              parsedData.lookupCalleePosition(line);
          if (calleePosition != null) {
            // Take the first line as the callee position
            position =
                position
                    .builderWithCopy()
                    .setLine(calleePosition.getValue().getRange().from)
                    .build();
          }
          return baseRemapper.createRemappedPosition(
              SourcePosition.builder()
                  .setLine(originalInlineeLine)
                  .setMethod(inlinee)
                  .setCallerPosition(position)
                  .build());
        }
        // This is the same position, so we should really not mark this as an inline position. Fall
        // through to the default case.
      } catch (ResourceException ignored) {
        // Intentionally left empty. Remapping of kotlin functions utility is a best effort mapping.
      }
      return baseRemapper.createRemappedPosition(position);
    }

    private Result getAndParseSourceDebugExtension(DexType holder) {
      if (parsedData == null) {
        parsedData = parsedKotlinSourceDebugExtensions.get(holder);
      }
      if (parsedData != null || parsedKotlinSourceDebugExtensions.containsKey(holder)) {
        return parsedData;
      }
      DexClass clazz = appView.definitionFor(currentMethod.getHolderType());
      DexValueString dexValueString = appView.getSourceDebugExtensionForType(clazz);
      if (dexValueString != null) {
        parsedData = KotlinSourceDebugExtensionParser.parse(dexValueString.value.toString());
      }
      parsedKotlinSourceDebugExtensions.put(holder, parsedData);
      return parsedData;
    }

    public void setMethod(DexEncodedMethod method) {
      this.currentMethod = method;
      this.parsedData = null;
    }
  }

  // PositionEventEmitter is a stateful function which converts a Position into series of
  // position-related DexDebugEvents and puts them into a processedEvents list.
  private static class PositionEventEmitter {
    private final DexItemFactory dexItemFactory;
    private int startLine = -1;
    private final DexMethod method;
    private int previousPc = 0;
    private Position previousPosition = null;
    private final List<DexDebugEvent> processedEvents;

    private PositionEventEmitter(
        DexItemFactory dexItemFactory, DexMethod method, List<DexDebugEvent> processedEvents) {
      this.dexItemFactory = dexItemFactory;
      this.method = method;
      this.processedEvents = processedEvents;
    }

    private void emitAdvancePc(int pc) {
      processedEvents.add(new AdvancePC(pc - previousPc));
      previousPc = pc;
    }

    private void emitPositionEvents(int currentPc, Position currentPosition) {
      if (previousPosition == null) {
        startLine = currentPosition.getLine();
        previousPosition = SourcePosition.builder().setLine(startLine).setMethod(method).build();
      }
      DexDebugEventBuilder.emitAdvancementEvents(
          previousPc,
          previousPosition,
          currentPc,
          currentPosition,
          processedEvents,
          dexItemFactory,
          true);
      previousPc = currentPc;
      previousPosition = currentPosition;
    }

    private int getStartLine() {
      assert (startLine >= 0);
      return startLine;
    }
  }

  // We will be remapping positional debug events and collect them as MappedPositions.
  private static class MappedPosition {

    private final DexMethod method;
    private final int originalLine;
    private final Position caller;
    private final int obfuscatedLine;
    private final boolean isOutline;
    private final DexMethod outlineCallee;
    private final Int2StructuralItemArrayMap<Position> outlinePositions;

    private MappedPosition(
        DexMethod method,
        int originalLine,
        Position caller,
        int obfuscatedLine,
        boolean isOutline,
        DexMethod outlineCallee,
        Int2StructuralItemArrayMap<Position> outlinePositions) {
      this.method = method;
      this.originalLine = originalLine;
      this.caller = caller;
      this.obfuscatedLine = obfuscatedLine;
      this.isOutline = isOutline;
      this.outlineCallee = outlineCallee;
      this.outlinePositions = outlinePositions;
    }

    public boolean isOutlineCaller() {
      return outlineCallee != null;
    }
  }

  public static ProguardMapId runAndWriteMap(
      AndroidApp inputApp,
      AppView<?> appView,
      NamingLens namingLens,
      Timing timing,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation) {
    assert appView.options().proguardMapConsumer != null;
    // When line number optimization is turned off the identity mapping for line numbers is
    // used. We still run the line number optimizer to collect line numbers and inline frame
    // information for the mapping file.
    timing.begin("Line number remapping");
    ClassNameMapper mapper =
        run(
            appView,
            appView.appInfo().app(),
            inputApp,
            namingLens,
            originalSourceFiles,
            representation);
    timing.end();
    timing.begin("Write proguard map");
    ProguardMapId mapId = ProguardMapSupplier.create(mapper, appView.options()).writeProguardMap();
    timing.end();
    return mapId;
  }

  private interface PcBasedDebugInfoRecorder {
    /** Callback to record a code object with a given max instruction PC and parameter count. */
    void recordPcMappingFor(DexCode code, int lastInstructionPc, int parameterCount);

    /** Callback to record a code object with only a single "line". */
    void recordSingleLineFor(DexCode code, int parameterCount);

    void recordSingleLineFor(DexCode code, int parameterCount, int lastInstructionPc);

    /**
     * Install the correct debug info objects.
     *
     * <p>Must be called after all recordings have been given to allow computing the debug info
     * items to be installed.
     */
    void updateDebugInfoInCodeObjects();
  }

  private static class Pc2PcMappingSupport implements PcBasedDebugInfoRecorder {

    // Some DEX VMs require matching parameter count in methods and debug info.
    // Record the max pc for each parameter count so we can share the param count objects.
    private Int2IntMap paramToMaxPc = new Int2IntOpenHashMap();

    private final List<Pair<Integer, DexCode>> codesToUpdate = new ArrayList<>();

    // We can only drop single-line debug info if it is OK to lose the source-file info.
    // This list is null if we must retain single-line entries.
    private final List<DexCode> singleLineCodesToClear;

    public Pc2PcMappingSupport(boolean allowDiscardingSourceFile) {
      singleLineCodesToClear = allowDiscardingSourceFile ? new ArrayList<>() : null;
    }

    @Override
    public void recordPcMappingFor(DexCode code, int lastInstructionPc, int parameterCount) {
      codesToUpdate.add(new Pair<>(parameterCount, code));
      int existing = paramToMaxPc.getOrDefault(parameterCount, -1);
      if (existing < lastInstructionPc) {
        paramToMaxPc.put(parameterCount, lastInstructionPc);
      }
    }

    @Override
    public void recordSingleLineFor(DexCode code, int parameterCount) {
      if (singleLineCodesToClear != null) {
        singleLineCodesToClear.add(code);
        return;
      }
      int lastInstructionPc = ArrayUtils.last(code.instructions).getOffset();
      recordPcMappingFor(code, lastInstructionPc, parameterCount);
    }

    @Override
    public void recordSingleLineFor(DexCode code, int parameterCount, int lastInstructionPc) {
      if (singleLineCodesToClear != null) {
        singleLineCodesToClear.add(code);
        return;
      }
      recordPcMappingFor(code, lastInstructionPc, parameterCount);
    }

    @Override
    public void updateDebugInfoInCodeObjects() {
      Int2ReferenceMap<DexDebugInfo> debugInfos =
          new Int2ReferenceOpenHashMap<>(paramToMaxPc.size());
      codesToUpdate.forEach(
          entry -> {
            int parameterCount = entry.getFirst();
            DexCode code = entry.getSecond();
            DexDebugInfo debugInfo =
                debugInfos.computeIfAbsent(
                    parameterCount,
                    key -> buildPc2PcDebugInfo(paramToMaxPc.get(key), parameterCount));
            code.setDebugInfo(debugInfo);
          });
      if (singleLineCodesToClear != null) {
        singleLineCodesToClear.forEach(c -> c.setDebugInfo(null));
      }
    }

    private static DexDebugInfo buildPc2PcDebugInfo(int lastInstructionPc, int parameterCount) {
      return new DexDebugInfo.PcBasedDebugInfo(parameterCount, lastInstructionPc);
    }
  }

  private static class NativePcSupport implements PcBasedDebugInfoRecorder {

    @Override
    public void recordPcMappingFor(DexCode code, int lastInstructionPc, int length) {
      // Strip the info in full as the runtime will emit the PC directly.
      code.setDebugInfo(null);
    }

    @Override
    public void recordSingleLineFor(DexCode code, int parameterCount) {
      // Strip the info at once as it does not conflict with any PC mapping update.
      code.setDebugInfo(null);
    }

    @Override
    public void recordSingleLineFor(DexCode code, int parameterCount, int lastInstructionPc) {
      recordSingleLineFor(code, parameterCount);
    }

    @Override
    public void updateDebugInfoInCodeObjects() {
      // Already null out the info so nothing to do.
    }
  }

  public static ClassNameMapper run(
      AppView<?> appView,
      DexApplication application,
      AndroidApp inputApp,
      NamingLens namingLens,
      OriginalSourceFiles originalSourceFiles,
      DebugRepresentationPredicate representation) {
    // For finding methods in kotlin files based on SourceDebugExtensions, we use a line method map.
    // We create it here to ensure it is only reading class files once.
    // TODO(b/220999985): Make this threaded per virtual file. Possibly pull the kotlin line mapping
    //  onto main thread before threading.
    CfLineToMethodMapper cfLineToMethodMapper = new CfLineToMethodMapper(inputApp);
    ClassNameMapper.Builder classNameMapperBuilder = ClassNameMapper.builder();

    Map<DexMethod, OutlineFixupBuilder> outlinesToFix = new IdentityHashMap<>();

    PcBasedDebugInfoRecorder pcBasedDebugInfo =
        appView.options().canUseNativeDexPcInsteadOfDebugInfo()
            ? new NativePcSupport()
            : new Pc2PcMappingSupport(appView.options().allowDiscardingResidualDebugInfo());

    // Collect which files contain which classes that need to have their line numbers optimized.
    for (DexProgramClass clazz : application.classes()) {
      boolean isSyntheticClass = appView.getSyntheticItems().isSyntheticClass(clazz);

      IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
          groupMethodsByRenamedName(appView.graphLens(), namingLens, clazz);

      // At this point we don't know if we really need to add this class to the builder.
      // It depends on whether any methods/fields are renamed or some methods contain positions.
      // Create a supplier which creates a new, cached ClassNaming.Builder on-demand.
      DexType originalType = appView.graphLens().getOriginalType(clazz.type);
      DexString renamedDescriptor = namingLens.lookupDescriptor(clazz.getType());
      Supplier<ClassNaming.Builder> onDemandClassNamingBuilder =
          Suppliers.memoize(
              () ->
                  classNameMapperBuilder.classNamingBuilder(
                      DescriptorUtils.descriptorToJavaType(renamedDescriptor.toString()),
                      originalType.toSourceString(),
                      com.android.tools.r8.position.Position.UNKNOWN));

      // Check if source file should be added to the map
      DexString originalSourceFile = originalSourceFiles.getOriginalSourceFile(clazz);
      if (originalSourceFile != null) {
        String sourceFile = originalSourceFile.toString();
        if (!RetraceUtils.hasPredictableSourceFileName(clazz.toSourceString(), sourceFile)) {
          onDemandClassNamingBuilder
              .get()
              .addMappingInformation(FileNameInformation.build(sourceFile), Unreachable::raise);
        }
      }

      if (isSyntheticClass) {
        onDemandClassNamingBuilder
            .get()
            .addMappingInformation(
                CompilerSynthesizedMappingInformation.builder().build(), Unreachable::raise);
      }

      // If the class is renamed add it to the classNamingBuilder.
      addClassToClassNaming(originalType, renamedDescriptor, onDemandClassNamingBuilder);

      // First transfer renamed fields to classNamingBuilder.
      addFieldsToClassNaming(
          appView.graphLens(), namingLens, clazz, originalType, onDemandClassNamingBuilder);

      // Then process the methods, ordered by renamed name.
      List<DexString> renamedMethodNames = new ArrayList<>(methodsByRenamedName.keySet());
      renamedMethodNames.sort(DexString::compareTo);
      for (DexString methodName : renamedMethodNames) {
        List<ProgramMethod> methods = methodsByRenamedName.get(methodName);
        if (methods.size() > 1) {
          // If there are multiple methods with the same name (overloaded) then sort them for
          // deterministic behaviour: the algorithm will assign new line numbers in this order.
          // Methods with different names can share the same line numbers, that's why they don't
          // need to be sorted.
          // If we are compiling to DEX we will try to not generate overloaded names. This saves
          // space by allowing more debug-information to be canonicalized. If we have overloaded
          // methods, we either did not rename them, we renamed them according to a supplied map or
          // they may be bridges for interface methods with covariant return types.
          sortMethods(methods);
          assert verifyMethodsAreKeptDirectlyOrIndirectly(appView, methods);
        }

        boolean identityMapping =
            appView.options().lineNumberOptimization == LineNumberOptimization.OFF;
        PositionRemapper positionRemapper =
            identityMapping
                ? new IdentityPositionRemapper()
                : new OptimizingPositionRemapper(appView.options());

        // Kotlin inline functions and arguments have their inlining information stored in the
        // source debug extension annotation. Instantiate the kotlin remapper on top of the original
        // remapper to allow for remapping original positions to kotlin inline positions.
        KotlinInlineFunctionPositionRemapper kotlinRemapper =
            new KotlinInlineFunctionPositionRemapper(
                appView, positionRemapper, cfLineToMethodMapper);

        for (ProgramMethod method : methods) {
          DexEncodedMethod definition = method.getDefinition();
          kotlinRemapper.currentMethod = definition;
          List<MappedPosition> mappedPositions;
          Code code = definition.getCode();
          boolean canUseDexPc =
              methods.size() == 1 && representation.useDexPcEncoding(clazz, definition);
          if (code != null) {
            if (code.isDexCode() && doesContainPositions(code.asDexCode())) {
              if (canUseDexPc) {
                mappedPositions =
                    optimizeDexCodePositionsForPc(
                        definition, appView, kotlinRemapper, pcBasedDebugInfo);
              } else {
                mappedPositions =
                    optimizeDexCodePositions(
                        definition, appView, kotlinRemapper, identityMapping, methods.size() != 1);
              }
            } else if (code.isCfCode()
                && doesContainPositions(code.asCfCode())
                && !appView.isCfByteCodePassThrough(definition)) {
              mappedPositions = optimizeCfCodePositions(method, kotlinRemapper, appView);
            } else {
              mappedPositions = new ArrayList<>();
            }
          } else {
            mappedPositions = new ArrayList<>();
          }

          DexMethod originalMethod =
              appView.graphLens().getOriginalMethodSignature(method.getReference());
          MethodSignature originalSignature =
              MethodSignature.fromDexMethod(originalMethod, originalMethod.holder != originalType);

          DexString obfuscatedNameDexString = namingLens.lookupName(method.getReference());
          String obfuscatedName = obfuscatedNameDexString.toString();

          List<MappingInformation> methodMappingInfo = new ArrayList<>();
          if (definition.isD8R8Synthesized()) {
            methodMappingInfo.add(CompilerSynthesizedMappingInformation.builder().build());
          }

          // Don't emit pure identity mappings.
          if (mappedPositions.isEmpty()
              && methodMappingInfo.isEmpty()
              && obfuscatedNameDexString == originalMethod.name
              && originalMethod.holder == originalType) {
            assert appView.options().lineNumberOptimization == LineNumberOptimization.OFF
                || !doesContainPositions(definition)
                || appView.isCfByteCodePassThrough(definition);
            continue;
          }

          MemberNaming memberNaming = new MemberNaming(originalSignature, obfuscatedName);
          onDemandClassNamingBuilder.get().addMemberEntry(memberNaming);

          // Add simple "a() -> b" mapping if we won't have any other with concrete line numbers
          if (mappedPositions.isEmpty()) {
            MappedRange range =
                onDemandClassNamingBuilder
                    .get()
                    .addMappedRange(null, originalSignature, null, obfuscatedName);
            methodMappingInfo.forEach(
                info -> range.addMappingInformation(info, Unreachable::raise));
            continue;
          }

          Map<DexMethod, MethodSignature> signatures = new IdentityHashMap<>();
          signatures.put(originalMethod, originalSignature);
          Function<DexMethod, MethodSignature> getOriginalMethodSignature =
              m ->
                  signatures.computeIfAbsent(
                      m, key -> MethodSignature.fromDexMethod(m, m.holder != clazz.getType()));

          // Check if mapped position is an outline
          DexMethod outlineMethod = getOutlineMethod(mappedPositions.get(0));
          if (outlineMethod != null) {
            outlinesToFix
                .computeIfAbsent(outlineMethod, ignored -> new OutlineFixupBuilder())
                .setMappedPositionsOutline(mappedPositions);
            methodMappingInfo.add(OutlineMappingInformation.builder().build());
          }

          // Update memberNaming with the collected positions, merging multiple positions into a
          // single region whenever possible.
          for (int i = 0; i < mappedPositions.size(); /* updated in body */ ) {
            MappedPosition firstPosition = mappedPositions.get(i);
            int j = i + 1;
            MappedPosition lastPosition = firstPosition;
            for (; j < mappedPositions.size(); j++) {
              // Break if this position cannot be merged with lastPosition.
              MappedPosition currentPosition = mappedPositions.get(j);
              // We allow for ranges being mapped to the same line but not to other ranges:
              //   1:10:void foo():42:42 -> a
              // is OK since retrace(a(:7)) = 42, however, the following is not OK:
              //   1:10:void foo():42:43 -> a
              // since retrace(a(:7)) = 49, which is not correct.
              boolean isSingleLine = currentPosition.originalLine == firstPosition.originalLine;
              boolean differentDelta =
                  currentPosition.originalLine - lastPosition.originalLine
                      != currentPosition.obfuscatedLine - lastPosition.obfuscatedLine;
              boolean isMappingRangeToSingleLine =
                  firstPosition.obfuscatedLine != lastPosition.obfuscatedLine
                      && firstPosition.originalLine == lastPosition.originalLine;
              // Note that currentPosition.caller and lastPosition.class must be deep-compared since
              // multiple inlining passes lose the canonical property of the positions.
              if (currentPosition.method != lastPosition.method
                  || (!isSingleLine && differentDelta)
                  || (!isSingleLine && isMappingRangeToSingleLine)
                  || !Objects.equals(currentPosition.caller, lastPosition.caller)
                  // Break when we see a mapped outline
                  || currentPosition.outlineCallee != null
                  // Ensure that we break when we start iterating with an outline caller again.
                  || firstPosition.outlineCallee != null) {
                break;
              }
              // The mapped positions are not guaranteed to be in order, so maintain first and last
              // position.
              if (firstPosition.obfuscatedLine > currentPosition.obfuscatedLine) {
                firstPosition = currentPosition;
              }
              if (lastPosition.obfuscatedLine < currentPosition.obfuscatedLine) {
                lastPosition = currentPosition;
              }
            }
            Range obfuscatedRange;
            if (definition.getCode().isDexCode()
                && definition.getCode().asDexCode().getDebugInfo()
                    == DexDebugInfoForSingleLineMethod.getInstance()) {
              assert firstPosition.originalLine == lastPosition.originalLine;
              obfuscatedRange = new Range(0, MAX_LINE_NUMBER);
            } else {
              obfuscatedRange =
                  new Range(firstPosition.obfuscatedLine, lastPosition.obfuscatedLine);
            }
            ClassNaming.Builder classNamingBuilder = onDemandClassNamingBuilder.get();
            MappedRange lastMappedRange =
                getMappedRangesForPosition(
                    appView.options().dexItemFactory(),
                    getOriginalMethodSignature,
                    classNamingBuilder,
                    firstPosition.method,
                    obfuscatedName,
                    obfuscatedRange,
                    new Range(firstPosition.originalLine, lastPosition.originalLine),
                    firstPosition.caller);
            for (MappingInformation info : methodMappingInfo) {
              lastMappedRange.addMappingInformation(info, Unreachable::raise);
            }
            // firstPosition will contain a potential outline caller.
            if (firstPosition.outlineCallee != null) {
              Int2IntMap positionMap = new Int2IntArrayMap();
              int maxPc = ListUtils.last(mappedPositions).obfuscatedLine;
              firstPosition.outlinePositions.forEach(
                  (line, position) -> {
                    int placeHolderLineToBeFixed;
                    if (canUseDexPc) {
                      placeHolderLineToBeFixed = maxPc + line + 1;
                    } else {
                      placeHolderLineToBeFixed =
                          positionRemapper.createRemappedPosition(position).getSecond().getLine();
                    }
                    positionMap.put((int) line, placeHolderLineToBeFixed);
                    getMappedRangesForPosition(
                        appView.options().dexItemFactory(),
                        getOriginalMethodSignature,
                        classNamingBuilder,
                        position.getMethod(),
                        obfuscatedName,
                        new Range(placeHolderLineToBeFixed, placeHolderLineToBeFixed),
                        new Range(position.getLine(), position.getLine()),
                        position.getCallerPosition());
                  });
              outlinesToFix
                  .computeIfAbsent(
                      firstPosition.outlineCallee, ignored -> new OutlineFixupBuilder())
                  .addMappedRangeForOutlineCallee(lastMappedRange, positionMap);
            }
            i = j;
          }
          if (definition.getCode().isDexCode()
              && definition.getCode().asDexCode().getDebugInfo()
                  == DexDebugInfoForSingleLineMethod.getInstance()) {
            pcBasedDebugInfo.recordSingleLineFor(
                definition.getCode().asDexCode(), method.getParameters().size());
          }
        } // for each method of the group
      } // for each method group, grouped by name
    } // for each class

    // Fixup all outline positions
    outlinesToFix.values().forEach(OutlineFixupBuilder::fixup);

    // Update all the debug-info objects.
    pcBasedDebugInfo.updateDebugInfoInCodeObjects();

    return classNameMapperBuilder.build();
  }

  private static DexMethod getOutlineMethod(MappedPosition mappedPosition) {
    if (mappedPosition.isOutline) {
      return mappedPosition.method;
    }
    if (mappedPosition.caller == null) {
      return null;
    }
    Position outermostCaller = mappedPosition.caller.getOutermostCaller();
    return outermostCaller.isOutline() ? outermostCaller.getMethod() : null;
  }

  private static MappedRange getMappedRangesForPosition(
      DexItemFactory factory,
      Function<DexMethod, MethodSignature> getOriginalMethodSignature,
      Builder classNamingBuilder,
      DexMethod method,
      String obfuscatedName,
      Range obfuscatedRange,
      Range originalLine,
      Position caller) {
    MappedRange lastMappedRange =
        classNamingBuilder.addMappedRange(
            obfuscatedRange,
            getOriginalMethodSignature.apply(method),
            originalLine,
            obfuscatedName);
    int inlineFramesCount = 0;
    while (caller != null) {
      inlineFramesCount += 1;
      lastMappedRange =
          classNamingBuilder.addMappedRange(
              obfuscatedRange,
              getOriginalMethodSignature.apply(caller.getMethod()),
              new Range(Math.max(caller.getLine(), 0)), // Prevent against "no-position".
              obfuscatedName);
      if (caller.isRemoveInnerFramesIfThrowingNpe()) {
        lastMappedRange.addMappingInformation(
            RewriteFrameMappingInformation.builder()
                .addCondition(
                    ThrowsCondition.create(
                        Reference.classFromDescriptor(factory.npeDescriptor.toString())))
                .addRewriteAction(RemoveInnerFramesAction.create(inlineFramesCount))
                .build(),
            Unreachable::raise);
      }
      caller = caller.getCallerPosition();
    }
    return lastMappedRange;
  }

  private static boolean verifyMethodsAreKeptDirectlyOrIndirectly(
      AppView<?> appView, List<ProgramMethod> methods) {
    if (appView.options().isGeneratingClassFiles() || !appView.appInfo().hasClassHierarchy()) {
      return true;
    }
    AppInfoWithClassHierarchy appInfo = appView.appInfo().withClassHierarchy();
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    boolean allSeenAreInstanceInitializers = true;
    DexString originalName = null;
    for (ProgramMethod method : methods) {
      // We cannot rename instance initializers.
      if (method.getDefinition().isInstanceInitializer()) {
        assert allSeenAreInstanceInitializers;
        continue;
      }
      allSeenAreInstanceInitializers = false;
      // If the method is pinned, we cannot minify it.
      if (!keepInfo.isMinificationAllowed(method.getReference(), appView, appView.options())) {
        continue;
      }
      // With desugared library, call-backs names are reserved here.
      if (method.getDefinition().isLibraryMethodOverride().isTrue()) {
        continue;
      }
      // We use the same name for interface names even if it has different types.
      DexProgramClass clazz = appView.definitionForProgramType(method.getHolderType());
      DexClassAndMethod lookupResult =
          appInfo.lookupMaximallySpecificMethod(clazz, method.getReference());
      if (lookupResult == null) {
        // We cannot rename methods we cannot look up.
        continue;
      }
      String errorString = method.getReference().qualifiedName() + " is not kept but is overloaded";
      assert lookupResult.getHolder().isInterface() : errorString;
      // TODO(b/159113601): Reenable assert.
      assert true || originalName == null || originalName.equals(method.getReference().name)
          : errorString;
      originalName = method.getReference().name;
    }
    return true;
  }

  private static int getMethodStartLine(ProgramMethod method) {
    Code code = method.getDefinition().getCode();
    if (code == null) {
      return 0;
    }
    if (code.isDexCode()) {
      DexDebugInfo dexDebugInfo = code.asDexCode().getDebugInfo();
      return dexDebugInfo == null ? 0 : dexDebugInfo.getStartLine();
    } else if (code.isCfCode()) {
      List<CfInstruction> instructions = code.asCfCode().getInstructions();
      for (CfInstruction instruction : instructions) {
        if (!(instruction instanceof CfPosition)) {
          continue;
        }
        return ((CfPosition) instruction).getPosition().getLine();
      }
    }
    return 0;
  }

  // Sort by startline, then DexEncodedMethod.slowCompare.
  // Use startLine = 0 if no debuginfo.
  private static void sortMethods(List<ProgramMethod> methods) {
    methods.sort(
        (lhs, rhs) -> {
          int lhsStartLine = getMethodStartLine(lhs);
          int rhsStartLine = getMethodStartLine(rhs);
          int startLineDiff = lhsStartLine - rhsStartLine;
          if (startLineDiff != 0) return startLineDiff;
          return DexEncodedMethod.slowCompare(lhs.getDefinition(), rhs.getDefinition());
        });
  }

  @SuppressWarnings("ReturnValueIgnored")
  private static void addClassToClassNaming(
      DexType originalType,
      DexString renamedClassName,
      Supplier<Builder> onDemandClassNamingBuilder) {
    // We do know we need to create a ClassNaming.Builder if the class itself had been renamed.
    if (originalType.descriptor != renamedClassName) {
      // Not using return value, it's registered in classNameMapperBuilder
      onDemandClassNamingBuilder.get();
    }
  }

  private static void addFieldsToClassNaming(
      GraphLens graphLens,
      NamingLens namingLens,
      DexProgramClass clazz,
      DexType originalType,
      Supplier<Builder> onDemandClassNamingBuilder) {
    clazz.forEachField(
        dexEncodedField -> {
          DexField dexField = dexEncodedField.getReference();
          DexField originalField = graphLens.getOriginalFieldSignature(dexField);
          DexString renamedName = namingLens.lookupName(dexField);
          if (renamedName != originalField.name || originalField.holder != originalType) {
            FieldSignature originalSignature =
                FieldSignature.fromDexField(originalField, originalField.holder != originalType);
            MemberNaming memberNaming = new MemberNaming(originalSignature, renamedName.toString());
            onDemandClassNamingBuilder.get().addMemberEntry(memberNaming);
          }
        });
  }

  public static IdentityHashMap<DexString, List<ProgramMethod>> groupMethodsByRenamedName(
      GraphLens graphLens, NamingLens namingLens, DexProgramClass clazz) {
    IdentityHashMap<DexString, List<ProgramMethod>> methodsByRenamedName =
        new IdentityHashMap<>(clazz.getMethodCollection().size());
    for (ProgramMethod programMethod : clazz.programMethods()) {
      // Add method only if renamed, moved, or contains positions.
      DexEncodedMethod definition = programMethod.getDefinition();
      DexMethod method = programMethod.getReference();
      DexString renamedName = namingLens.lookupName(method);
      if (renamedName != method.name
          || graphLens.getOriginalMethodSignature(method) != method
          || doesContainPositions(definition)
          || definition.isD8R8Synthesized()) {
        methodsByRenamedName
            .computeIfAbsent(renamedName, key -> new ArrayList<>())
            .add(programMethod);
      }
    }
    return methodsByRenamedName;
  }

  private static boolean doesContainPositions(DexEncodedMethod method) {
    Code code = method.getCode();
    if (code == null) {
      return false;
    }
    if (code.isDexCode()) {
      return doesContainPositions(code.asDexCode());
    } else if (code.isCfCode()) {
      return doesContainPositions(code.asCfCode());
    }
    return false;
  }

  public static boolean doesContainPositions(DexCode dexCode) {
    DexDebugInfo debugInfo = dexCode.getDebugInfo();
    if (debugInfo == null) {
      return false;
    }
    if (debugInfo.isPcBasedInfo()) {
      return true;
    }
    for (DexDebugEvent event : debugInfo.asEventBasedInfo().events) {
      if (event instanceof DexDebugEvent.Default) {
        return true;
      }
    }
    return false;
  }

  private static boolean doesContainPositions(CfCode cfCode) {
    List<CfInstruction> instructions = cfCode.getInstructions();
    for (CfInstruction instruction : instructions) {
      if (instruction instanceof CfPosition) {
        return true;
      }
    }
    return false;
  }

  private static List<MappedPosition> optimizeDexCodePositions(
      DexEncodedMethod method,
      AppView<?> appView,
      PositionRemapper positionRemapper,
      boolean identityMapping,
      boolean hasOverloads) {
    List<MappedPosition> mappedPositions = new ArrayList<>();
    // Do the actual processing for each method.
    DexApplication application = appView.appInfo().app();
    DexCode dexCode = method.getCode().asDexCode();
    // TODO(b/213411850): Do we need to reconsider conversion here to support pc-based D8 merging?
    EventBasedDebugInfo debugInfo =
        DexDebugInfo.convertToEventBased(dexCode, appView.dexItemFactory());
    assert debugInfo != null;
    List<DexDebugEvent> processedEvents = new ArrayList<>();

    PositionEventEmitter positionEventEmitter =
        new PositionEventEmitter(
            application.dexItemFactory,
            appView.graphLens().getOriginalMethodSignature(method.getReference()),
            processedEvents);

    Box<Boolean> inlinedOriginalPosition = new Box<>(false);

    // Debug event visitor to map line numbers.
    DexDebugPositionState visitor =
        new DexDebugPositionState(
            debugInfo.startLine,
            appView.graphLens().getOriginalMethodSignature(method.getReference())) {

          // Keep track of what PC has been emitted.
          private int emittedPc = 0;

          // Force the current PC to emitted.
          private void flushPc() {
            if (emittedPc != getCurrentPc()) {
              positionEventEmitter.emitAdvancePc(getCurrentPc());
              emittedPc = getCurrentPc();
            }
          }

          // A default event denotes a line table entry and must always be emitted. Remap its line.
          @Override
          public void visit(Default defaultEvent) {
            super.visit(defaultEvent);
            assert getCurrentLine() >= 0;
            Position position = getPositionFromPositionState(this);
            Position currentPosition = remapAndAdd(position, positionRemapper, mappedPositions);
            positionEventEmitter.emitPositionEvents(getCurrentPc(), currentPosition);
            if (currentPosition != position) {
              inlinedOriginalPosition.set(true);
            }
            emittedPc = getCurrentPc();
            resetOutlineInformation();
          }

          // Non-materializing events use super, ie, AdvancePC, AdvanceLine and SetInlineFrame.

          // Materializing events are just amended to the stream.

          @Override
          public void visit(SetFile setFile) {
            processedEvents.add(setFile);
          }

          @Override
          public void visit(SetPrologueEnd setPrologueEnd) {
            processedEvents.add(setPrologueEnd);
          }

          @Override
          public void visit(SetEpilogueBegin setEpilogueBegin) {
            processedEvents.add(setEpilogueBegin);
          }

          // Local changes must force flush the PC ensuing they pertain to the correct point.

          @Override
          public void visit(StartLocal startLocal) {
            flushPc();
            processedEvents.add(startLocal);
          }

          @Override
          public void visit(EndLocal endLocal) {
            flushPc();
            processedEvents.add(endLocal);
          }

          @Override
          public void visit(RestartLocal restartLocal) {
            flushPc();
            processedEvents.add(restartLocal);
          }
        };

    for (DexDebugEvent event : debugInfo.events) {
      event.accept(visitor);
    }

    // If we only have one line event we can always retrace back uniquely.
    if (mappedPositions.size() <= 1
        && !hasOverloads
        && !appView.options().debug
        && appView.options().lineNumberOptimization != LineNumberOptimization.OFF
        && appView.options().allowDiscardingResidualDebugInfo()
        && (mappedPositions.isEmpty() || !mappedPositions.get(0).isOutlineCaller())) {
      dexCode.setDebugInfo(DexDebugInfoForSingleLineMethod.getInstance());
      return mappedPositions;
    }

    EventBasedDebugInfo optimizedDebugInfo =
        new EventBasedDebugInfo(
            positionEventEmitter.getStartLine(),
            debugInfo.parameters,
            processedEvents.toArray(DexDebugEvent.EMPTY_ARRAY));

    assert !identityMapping
        || inlinedOriginalPosition.get()
        || verifyIdentityMapping(debugInfo, optimizedDebugInfo);

    dexCode.setDebugInfo(optimizedDebugInfo);
    return mappedPositions;
  }

  private static Position getPositionFromPositionState(DexDebugPositionState state) {
    PositionBuilder<?, ?> positionBuilder;
    if (state.getOutlineCallee() != null) {
      OutlineCallerPositionBuilder outlineCallerPositionBuilder =
          OutlineCallerPosition.builder()
              .setOutlineCallee(state.getOutlineCallee())
              .setIsOutline(state.isOutline());
      state.getOutlineCallerPositions().forEach(outlineCallerPositionBuilder::addOutlinePosition);
      positionBuilder = outlineCallerPositionBuilder;
    } else if (state.isOutline()) {
      positionBuilder = OutlinePosition.builder();
    } else {
      positionBuilder = SourcePosition.builder().setFile(state.getCurrentFile());
    }
    return positionBuilder
        .setLine(state.getCurrentLine())
        .setMethod(state.getCurrentMethod())
        .setCallerPosition(state.getCurrentCallerPosition())
        .build();
  }

  private static List<MappedPosition> optimizeDexCodePositionsForPc(
      DexEncodedMethod method,
      AppView<?> appView,
      PositionRemapper positionRemapper,
      PcBasedDebugInfoRecorder debugInfoProvider) {
    List<MappedPosition> mappedPositions = new ArrayList<>();
    // Do the actual processing for each method.
    DexCode dexCode = method.getCode().asDexCode();
    // TODO(b/213411850): Do we need to reconsider conversion here to support pc-based D8 merging?
    EventBasedDebugInfo debugInfo =
        DexDebugInfo.convertToEventBased(dexCode, appView.dexItemFactory());
    assert debugInfo != null;
    BooleanBox singleOriginalLine = new BooleanBox(true);
    Pair<Integer, Position> lastPosition = new Pair<>();
    DexDebugEventVisitor visitor =
        new DexDebugPositionState(
            debugInfo.startLine,
            appView.graphLens().getOriginalMethodSignature(method.getReference())) {
          @Override
          public void visit(Default defaultEvent) {
            super.visit(defaultEvent);
            assert getCurrentLine() >= 0;
            Position currentPosition = getPositionFromPositionState(this);
            if (lastPosition.getSecond() != null) {
              if (singleOriginalLine.isTrue()
                  && !currentPosition.equals(lastPosition.getSecond())) {
                singleOriginalLine.set(false);
              }
              remapAndAddForPc(
                  lastPosition.getFirst(),
                  getCurrentPc(),
                  lastPosition.getSecond(),
                  positionRemapper,
                  mappedPositions);
            }
            lastPosition.setFirst(getCurrentPc());
            lastPosition.setSecond(currentPosition);
            resetOutlineInformation();
          }
        };

    for (DexDebugEvent event : debugInfo.events) {
      event.accept(visitor);
    }

    int lastInstructionPc = ArrayUtils.last(dexCode.instructions).getOffset();
    if (lastPosition.getSecond() != null) {
      remapAndAddForPc(
          lastPosition.getFirst(),
          lastInstructionPc + 1,
          lastPosition.getSecond(),
          positionRemapper,
          mappedPositions);
    }

    // If we only have one original line we can always retrace back uniquely.
    assert !mappedPositions.isEmpty();
    if (singleOriginalLine.isTrue()
        && lastPosition.getSecond() != null
        && !mappedPositions.get(0).isOutlineCaller()) {
      dexCode.setDebugInfo(DexDebugInfoForSingleLineMethod.getInstance());
      debugInfoProvider.recordSingleLineFor(
          dexCode, method.getParameters().size(), lastInstructionPc);
    } else {
      debugInfoProvider.recordPcMappingFor(dexCode, lastInstructionPc, debugInfo.parameters.length);
    }
    return mappedPositions;
  }

  private static boolean verifyIdentityMapping(
      EventBasedDebugInfo originalDebugInfo, EventBasedDebugInfo optimizedDebugInfo) {
    assert optimizedDebugInfo.startLine == originalDebugInfo.startLine;
    assert optimizedDebugInfo.events.length == originalDebugInfo.events.length;
    for (int i = 0; i < originalDebugInfo.events.length; ++i) {
      assert optimizedDebugInfo.events[i].equals(originalDebugInfo.events[i]);
    }
    return true;
  }

  private static List<MappedPosition> optimizeCfCodePositions(
      ProgramMethod method, PositionRemapper positionRemapper, AppView<?> appView) {
    List<MappedPosition> mappedPositions = new ArrayList<>();
    // Do the actual processing for each method.
    CfCode oldCode = method.getDefinition().getCode().asCfCode();
    List<CfInstruction> oldInstructions = oldCode.getInstructions();
    List<CfInstruction> newInstructions = new ArrayList<>(oldInstructions.size());
    for (CfInstruction oldInstruction : oldInstructions) {
      CfInstruction newInstruction;
      if (oldInstruction instanceof CfPosition) {
        CfPosition cfPosition = (CfPosition) oldInstruction;
        newInstruction =
            new CfPosition(
                cfPosition.getLabel(),
                remapAndAdd(cfPosition.getPosition(), positionRemapper, mappedPositions));
      } else {
        newInstruction = oldInstruction;
      }
      newInstructions.add(newInstruction);
    }
    method.setCode(
        new CfCode(
            method.getHolderType(),
            oldCode.getMaxStack(),
            oldCode.getMaxLocals(),
            newInstructions,
            oldCode.getTryCatchRanges(),
            oldCode.getLocalVariables()),
        appView);
    return mappedPositions;
  }

  private static Position remapAndAdd(
      Position position, PositionRemapper remapper, List<MappedPosition> mappedPositions) {
    Pair<Position, Position> remappedPosition = remapper.createRemappedPosition(position);
    Position oldPosition = remappedPosition.getFirst();
    Position newPosition = remappedPosition.getSecond();
    mappedPositions.add(
        new MappedPosition(
            oldPosition.getMethod(),
            oldPosition.getLine(),
            oldPosition.getCallerPosition(),
            newPosition.getLine(),
            oldPosition.isOutline(),
            oldPosition.getOutlineCallee(),
            oldPosition.getOutlinePositions()));
    return newPosition;
  }

  private static void remapAndAddForPc(
      int startPc,
      int endPc,
      Position position,
      PositionRemapper remapper,
      List<MappedPosition> mappedPositions) {
    Pair<Position, Position> remappedPosition = remapper.createRemappedPosition(position);
    Position oldPosition = remappedPosition.getFirst();
    for (int currentPc = startPc; currentPc < endPc; currentPc++) {
      boolean firstEntry = currentPc == startPc;
      mappedPositions.add(
          new MappedPosition(
              oldPosition.getMethod(),
              oldPosition.getLine(),
              oldPosition.getCallerPosition(),
              currentPc,
              // Outline info is placed exactly on the positions that relate to it so we should
              // only emit it for the first entry.
              firstEntry && oldPosition.isOutline(),
              firstEntry ? oldPosition.getOutlineCallee() : null,
              firstEntry ? oldPosition.getOutlinePositions() : null));
    }
  }

  private static class OutlineFixupBuilder {

    private static int MINIFIED_POSITION_REMOVED = -1;

    private List<MappedPosition> mappedOutlinePositions = null;
    private final List<Pair<MappedRange, Int2IntMap>> mappedOutlineCalleePositions =
        new ArrayList<>();

    public void setMappedPositionsOutline(List<MappedPosition> mappedPositionsOutline) {
      this.mappedOutlinePositions = mappedPositionsOutline;
    }

    public void addMappedRangeForOutlineCallee(
        MappedRange mappedRangeForOutline, Int2IntMap calleePositions) {
      mappedOutlineCalleePositions.add(Pair.create(mappedRangeForOutline, calleePositions));
    }

    public void fixup() {
      if (mappedOutlinePositions == null || mappedOutlineCalleePositions.isEmpty()) {
        assert mappedOutlinePositions != null : "Mapped outline positions is null";
        assert false : "Mapped outline positions is empty";
        return;
      }
      for (Pair<MappedRange, Int2IntMap> mappingInfo : mappedOutlineCalleePositions) {
        MappedRange mappedRange = mappingInfo.getFirst();
        Int2IntMap positions = mappingInfo.getSecond();
        Int2IntSortedMap map = new Int2IntLinkedOpenHashMap();
        positions.forEach(
            (outlinePosition, calleePosition) -> {
              int minifiedLinePosition =
                  getMinifiedLinePosition(outlinePosition, mappedOutlinePositions);
              if (minifiedLinePosition != MINIFIED_POSITION_REMOVED) {
                map.put(minifiedLinePosition, (int) calleePosition);
              }
            });
        mappedRange.addMappingInformation(
            OutlineCallsiteMappingInformation.create(map), Unreachable::raise);
      }
    }

    private int getMinifiedLinePosition(
        int originalPosition, List<MappedPosition> mappedPositions) {
      for (MappedPosition mappedPosition : mappedPositions) {
        if (mappedPosition.originalLine == originalPosition) {
          return mappedPosition.obfuscatedLine;
        }
      }
      return MINIFIED_POSITION_REMOVED;
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.errors.FinalRClassEntriesWithOptimizedShrinkingDiagnostic;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.FinishedEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.FixpointEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.MarkFieldAsKeptEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveClassEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveFieldEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.StartEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.TraceFieldAccessEnqueuerAnalysis;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.partial.R8PartialResourceUseCollector;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState.R8ResourceShrinkerModel;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState.ResourceShrinkerCallback;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.nio.file.Paths;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ResourceShrinkerEnqueuerExtension
    implements TraceFieldAccessEnqueuerAnalysis,
        MarkFieldAsKeptEnqueuerAnalysis,
        NewlyLiveClassEnqueuerAnalysis,
        NewlyLiveFieldEnqueuerAnalysis,
        StartEnqueuerAnalysis,
        FixpointEnqueuerAnalysis,
        FinishedEnqueuerAnalysis,
        ResourceShrinkerCallback {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Enqueuer enqueuer;
  private final ResourceShrinkerState<FeatureSplit> resourceShrinkerState;
  private final Map<DexType, RClassFieldToValueStore> fieldToValueMapping = new IdentityHashMap<>();

  // Deferred state
  private final Map<DexString, Origin> onClickMethodReferences = new IdentityHashMap<>();
  private final ProgramMethodSet processedOnClickMethods = ProgramMethodSet.create();

  // Pending incremental state
  private final Set<DexProgramClass> pendingLiveClasses = Sets.newIdentityHashSet();
  private final Map<DexString, Origin> pendingOnClickMethodReferences = new IdentityHashMap<>();

  private ResourceShrinkerEnqueuerExtension(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    this.appView = appView;
    this.enqueuer = enqueuer;
    this.resourceShrinkerState =
        enqueuer.getMode().isInitialTreeShaking()
            ? appView.initResourceShrinkerState()
            : appView.getResourceShrinkerState();
  }

  public static ResourceShrinkerEnqueuerExtension register(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      EnqueuerAnalysisCollection.Builder builder) {
    if (appView.options().isOptimizedResourceShrinking()) {
      ResourceShrinkerEnqueuerExtension extension =
          new ResourceShrinkerEnqueuerExtension(appView, enqueuer);

      // Always register for started, fixpoint, finished, and newly live class events
      builder.addStartAnalysis(extension);
      builder.addFixpointAnalysis(extension);
      builder.addFinishedAnalysis(extension);
      builder.addNewlyLiveClassAnalysis(extension);

      if (fieldAccessAnalysisEnabled(enqueuer)) {
        builder.addTraceFieldAccessAnalysis(extension);
        builder.addMarkFieldAsKeptAnalysis(extension);
      }
      if (liveFieldAnalysisEnabled(appView, enqueuer)) {
        builder.addNewlyLiveFieldAnalysis(extension);
      }
      return extension;
    }
    return null;
  }

  private static boolean liveFieldAnalysisEnabled(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    return appView.options().androidResourceProvider != null
        && enqueuer.getMode().isFinalTreeShaking();
  }

  private static boolean fieldAccessAnalysisEnabled(Enqueuer enqueuer) {
    return enqueuer.getMode().isInitialTreeShaking();
  }

  // ClassReferenceCallback (called from ResourceShrinkerState during trace)
  @Override
  public boolean tryClass(String possibleClass, String xmlFilePath, boolean markAsLive) {
    Origin xmlFileOrigin = new PathOrigin(Paths.get(xmlFilePath));
    return enqueuer.recordReferenceFromResources(possibleClass, xmlFileOrigin, markAsLive);
  }

  @Override
  public void tryMethod(String methodName, String xmlFilePath) {
    Origin xmlFileOrigin = new PathOrigin(Paths.get(xmlFilePath));
    pendingOnClickMethodReferences.put(
        appView.dexItemFactory().createString(methodName), xmlFileOrigin);
  }

  public void traceResourceValue(int value) {
    resourceShrinkerState.trace(value, "from dex", this);
  }

  @Override
  public void processNewlyKeptField(
      ProgramField field, KeepReason keepReason, EnqueuerWorklist worklist) {
    processField(field);
  }

  @Override
  public void traceStaticFieldRead(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    processField(resolutionResult.getProgramField());
  }

  private void processField(ProgramField resolvedField) {
    if (resolvedField == null) {
      return;
    }
    if (enqueuer.isRClass(resolvedField.getHolder())) {
      DexType holderType = resolvedField.getHolderType();
      if (!fieldToValueMapping.containsKey(holderType)) {
        populateRClassValues(resolvedField.getHolder());
      }
      RClassFieldToValueStore rClassFieldToValueStore = fieldToValueMapping.get(holderType);
      IntList integers = rClassFieldToValueStore.valueMapping.get(resolvedField.getReference());
      // The R class can have fields injected, e.g., by jacoco, we don't have resource values for
      // these.
      if (integers != null) {
        for (int id : integers) {
          resourceShrinkerState.trace(id, resolvedField.getReference().toString(), this);
        }
      }
    }
  }

  @Override
  public void processNewlyLiveClass(DexProgramClass clazz, EnqueuerWorklist worklist) {
    pendingLiveClasses.add(clazz);
    // Warn on final ID fields if needed
    if (enqueuer.isRClass(clazz)) {
      for (DexEncodedField field : clazz.staticFields()) {
        if (field.isFinal() && field.hasExplicitStaticValue() && field.getType().isIntType()) {
          appView
              .reporter()
              .warning(
                  new FinalRClassEntriesWithOptimizedShrinkingDiagnostic(
                      clazz.origin, field.getReference()));
        }
      }
    }
  }

  @Override
  public void processNewlyLiveField(
      ProgramField field, ProgramDefinition context, EnqueuerWorklist worklist) {
    DexEncodedField definition = field.getDefinition();
    if (field.getAccessFlags().isStatic()
        && definition.hasExplicitStaticValue()
        && definition.getStaticValue().isDexValueResourceNumber()) {
      resourceShrinkerState.trace(
          definition.getStaticValue().asDexValueResourceNumber().getValue(),
          field.getReference().toString(),
          this);
    }
  }

  @Override
  public void onStarted(Enqueuer enqueuer) {
    if (!enqueuer.getMode().isTreeShaking()) {
      return;
    }
    resourceShrinkerState.traceKeepXmlAndManifest(this);

    // Trace resources.
    IntSet resourceRootIds = appView.rootSet().resourceIds;
    if (enqueuer.getMode().isInitialTreeShaking()
        && appView.options().partialSubCompilationConfiguration != null) {
      R8PartialResourceUseCollector resourceUseCollector =
          new R8PartialResourceUseCollector(appView) {

            private final R8ResourceShrinkerModel model =
                appView.getResourceShrinkerState().getR8ResourceShrinkerModel();

            @Override
            protected void keep(int resourceId) {
              if (model.hasResourceId(resourceId)) {
                resourceRootIds.add(resourceId);
              }
            }
          };
      resourceUseCollector.run();
    }
    for (int rootResourceId : resourceRootIds) {
      resourceShrinkerState.trace(rootResourceId, "Non shrunken dex code", this);
    }
  }

  @Override
  public void notifyFixpoint(
      Enqueuer enqueuer, EnqueuerWorklist worklist, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    if (pendingLiveClasses.isEmpty() && pendingOnClickMethodReferences.isEmpty()) {
      return;
    }

    // 1. Match pendingLiveClasses against committed onClickMethodReferences
    if (!onClickMethodReferences.isEmpty()) {
      for (DexProgramClass clazz : pendingLiveClasses) {
        matchOnClickMethods(clazz, onClickMethodReferences);
      }
    }

    // 2. Match pendingOnClickMethodReferences against ALL liveClasses
    if (!pendingOnClickMethodReferences.isEmpty()) {
      enqueuer.forAllLiveClasses(
          clazz -> matchOnClickMethods(clazz, pendingOnClickMethodReferences));
    }

    // 3. Commit pending states
    pendingLiveClasses.clear();
    onClickMethodReferences.putAll(pendingOnClickMethodReferences);
    pendingOnClickMethodReferences.clear();
  }

  private void matchOnClickMethods(
      DexProgramClass clazz, Map<DexString, Origin> onClickReferences) {
    for (ProgramMethod method :
        clazz.virtualProgramMethods(
            p ->
                p.getParameters().size() == 1
                    && p.getParameter(0).isIdenticalTo(appView.dexItemFactory().androidViewViewType)
                    && onClickReferences.containsKey(p.getName()))) {
      if (processedOnClickMethods.add(method)) {
        KeepMethodInfo methodInfo = enqueuer.getKeepInfo().getMethodInfo(method);
        if (!methodInfo.isOptimizationAllowed(appView.options())
            && !methodInfo.isShrinkingAllowed(appView.options())
            && !methodInfo.isMinificationAllowed(appView.options())) {
          continue;
        }
        KeepReason reason =
            KeepReason.reflectiveUseFromXml(onClickReferences.get(method.getName()));
        KeepMethodInfo.Joiner minimumKeepInfo =
            KeepMethodInfo.newEmptyJoiner()
                .disallowOptimization()
                .disallowShrinking()
                .disallowMinification()
                .addReason(reason);
        enqueuer.applyMinimumKeepInfo(method, minimumKeepInfo);
      }
    }
  }

  @Override
  public void done(Enqueuer enqueuer, ExecutorService executorService) {
    resourceShrinkerState.enqueuerDone(enqueuer.getMode().isFinalTreeShaking());
  }

  private void populateRClassValues(DexProgramClass programClass) {
    RClassFieldToValueStore.Builder rClassValueBuilder = new RClassFieldToValueStore.Builder();
    analyzeStaticFields(programClass, rClassValueBuilder);
    ProgramMethod programClassInitializer = programClass.getProgramClassInitializer();
    if (programClassInitializer != null) {
      analyzeClassInitializer(rClassValueBuilder, programClassInitializer);
    }
    fieldToValueMapping.put(programClass.getType(), rClassValueBuilder.build());
  }

  private void analyzeClassInitializer(
      RClassFieldToValueStore.Builder rClassValueBuilder, ProgramMethod programClassInitializer) {
    IRCode code = programClassInitializer.buildIR(appView, MethodConversionOptions.nonConverting());

    for (StaticPut staticPut : code.<StaticPut>instructions(Instruction::isStaticPut)) {
      Value value = staticPut.value();
      if (value.isPhi()) {
        continue;
      }
      IntList values;
      Instruction definition = staticPut.value().definition;
      if (definition.isConstNumber()) {
        values = new IntArrayList(1);
        values.add(definition.asConstNumber().getIntValue());
      } else if (definition.isResourceConstNumber()) {
        values = new IntArrayList(1);
        values.add(definition.asResourceConstNumber().getValue());
      } else if (definition.isNewArrayEmpty()) {
        NewArrayEmpty newArrayEmpty = definition.asNewArrayEmpty();
        values = new IntArrayList();
        for (Instruction uniqueUser : newArrayEmpty.outValue().uniqueUsers()) {
          if (uniqueUser.isArrayPut()) {
            Value constValue = uniqueUser.asArrayPut().value();
            if (constValue.isConstNumber()) {
              values.add(constValue.getDefinition().asConstNumber().getIntValue());
            } else if (constValue.isConstResourceNumber()) {
              values.add(constValue.getDefinition().asResourceConstNumber().getValue());
            }
          } else {
            assert uniqueUser == staticPut;
          }
        }
      } else if (definition.isNewArrayFilled()) {
        values = new IntArrayList();
        for (Value inValue : definition.asNewArrayFilled().inValues()) {
          if (inValue.isPhi()) {
            continue;
          }
          Instruction valueDefinition = inValue.definition;
          if (valueDefinition.isConstNumber()) {
            values.add(valueDefinition.asConstNumber().getIntValue());
          } else if (valueDefinition.isResourceConstNumber()) {
            throw new Unreachable(
                "Only running ResourceShrinkerEnqueuerExtension in initial tree shaking");
          }
        }
      } else {
        continue;
      }
      rClassValueBuilder.addMapping(staticPut.getField(), values);
    }
  }

  private void analyzeStaticFields(
      DexProgramClass programClass, RClassFieldToValueStore.Builder rClassValueBuilder) {
    for (DexEncodedField staticField :
        programClass.staticFields(DexEncodedField::hasExplicitStaticValue)) {
      DexValue staticValue = staticField.getStaticValue();
      if (staticValue.isDexValueInt()) {
        IntList values = new IntArrayList(1);
        values.add(staticValue.asDexValueInt().getValue());
        staticField.setStaticValue(
            DexValue.DexValueResourceNumber.create(staticValue.asDexValueInt().value));
        rClassValueBuilder.addMapping(staticField.getReference(), values);
      }
    }
  }

  private static class RClassFieldToValueStore {
    private final Map<DexField, IntList> valueMapping;

    private RClassFieldToValueStore(Map<DexField, IntList> valueMapping) {
      this.valueMapping = valueMapping;
    }

    public static class Builder {
      private final Map<DexField, IntList> valueMapping = new IdentityHashMap<>();

      public void addMapping(DexField field, IntList values) {
        assert !valueMapping.containsKey(field);
        valueMapping.put(field, values);
      }

      public RClassFieldToValueStore build() {
        return new RClassFieldToValueStore(valueMapping);
      }
    }
  }
}

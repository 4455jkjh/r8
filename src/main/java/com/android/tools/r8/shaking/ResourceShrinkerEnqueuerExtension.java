// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.errors.FinalRClassEntriesWithOptimizedShrinkingDiagnostic;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
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
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState.R8ResourceShrinkerModel;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState.ResourceShrinkerCallback;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
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
  private final Map<DexType, Map<DexField, Object>> fieldToValueMapping = new IdentityHashMap<>();

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

      if (fieldAccessAnalysisEnabled(appView, enqueuer)) {
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

  private static boolean fieldAccessAnalysisEnabled(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    if (!appView.options().removeUnreadKeptRClassResources) {
      // When R class filtering is not enabled, we only trace field accesses during the initial tree
      // shaking pass. Otherwise, we need to trace field accesses in all tree shaking passes.
      return enqueuer.getMode().isInitialTreeShaking();
    }
    return enqueuer.getMode().isTreeShaking();
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
    if (appView.options().removeUnreadKeptRClassResources) {
      // When R class filtering is enabled, we only care about static field accesses to R class
      // fields, which is handled by traceStaticFieldRead. Simply keeping an R class field does not
      // imply the resource is used.
      return;
    }
    processField(field);
  }

  @Override
  public void traceStaticFieldRead(
      DexField field,
      SingleFieldResolutionResult<?> resolutionResult,
      ProgramMethod context,
      EnqueuerWorklist worklist) {
    DexClassAndField resolvedField = resolutionResult.getResolutionPair();
    if (resolvedField != null) {
      processField(resolvedField);
    }
  }

  private DexProgramClass resolveRClass(DexClassAndField field) {
    DexClass holder = field.getHolder();
    if (holder.isProgramClass()) {
      DexProgramClass programClass = holder.asProgramClass();
      if (enqueuer.isRClass(programClass)) {
        return programClass;
      }
    } else if (holder.isClasspathClass()
        && appView.options().partialSubCompilationConfiguration != null) {
      R8PartialR8SubCompilationConfiguration partialConfig =
          appView.options().partialSubCompilationConfiguration.asR8();
      DexProgramClass dexingClass = partialConfig.getDexingOutputClass(holder.getType());
      if (dexingClass != null && enqueuer.isRClass(dexingClass)) {
        return dexingClass;
      }
    }
    return null;
  }

  private void processField(DexClassAndField field) {
    DexProgramClass rClass = resolveRClass(field);
    if (rClass != null) {
      processRClassField(field, rClass);
    }
  }

  private void processRClassField(DexClassAndField field, DexProgramClass rClass) {
    DexType holderType = rClass.getType();
    if (!fieldToValueMapping.containsKey(holderType)) {
      populateRClassValues(rClass);
    }
    Map<DexField, Object> store = fieldToValueMapping.get(holderType);
    Object value = store.get(field.getReference());
    if (value != null) {
      traceMappedValue(value, field.getReference().toSourceString());
    }
  }

  private void traceMappedValue(Object value, String context) {
    if (value instanceof Integer) {
      resourceShrinkerState.trace((Integer) value, context, this);
    } else if (value instanceof IntList) {
      for (int id : (IntList) value) {
        resourceShrinkerState.trace(id, context, this);
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
    if (appView.options().removeUnreadKeptRClassResources && enqueuer.isRClass(field.getHolder())) {
      // We only trace resource values when they are read in the code (via traceStaticFieldRead).
      // Writes to R class fields (such as their initialization inside <clinit> of the R class)
      // do not constitute a read of the resource and should be ignored.
      return;
    }
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

            @Override
            protected void keepField(DexClassAndField field) {
              // When partial compilation is enabled, the excluded (D8) part of the app is compiled
              // separately and can contain references to R class fields. Since we cannot analyze
              // the D8 code in this compilation run, we must keep all resources referenced by kept
              // fields of the D8 part, unlike in full R8 where we can skip them.
              DexProgramClass rClass = resolveRClass(field);
              if (rClass != null) {
                DexType holderType = rClass.getType();
                if (!fieldToValueMapping.containsKey(holderType)) {
                  populateRClassValues(rClass);
                }
                Map<DexField, Object> store = fieldToValueMapping.get(holderType);
                Object value = store.get(field.getReference());
                if (value != null) {
                  if (value instanceof Integer) {
                    int id = (Integer) value;
                    if (model.hasResourceId(id)) {
                      resourceRootIds.add(id);
                    }
                  } else if (value instanceof IntList) {
                    for (int id : (IntList) value) {
                      if (model.hasResourceId(id)) {
                        resourceRootIds.add(id);
                      }
                    }
                  }
                }
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
    Map<DexField, Object> valueMapping = new IdentityHashMap<>();
    analyzeStaticFields(programClass, valueMapping);
    ProgramMethod programClassInitializer = programClass.getProgramClassInitializer();
    if (programClassInitializer != null) {
      analyzeClassInitializer(valueMapping, programClassInitializer);
    }
    fieldToValueMapping.put(programClass.getType(), valueMapping);
  }

  private void analyzeClassInitializer(
      Map<DexField, Object> valueMapping, ProgramMethod programClassInitializer) {
    IRCode code = programClassInitializer.buildIR(appView, MethodConversionOptions.nonConverting());

    for (StaticPut staticPut : code.<StaticPut>instructions(Instruction::isStaticPut)) {
      Value value = staticPut.value();
      if (value.isPhi()) {
        continue;
      }
      Object mappedValue;
      Instruction definition = staticPut.value().definition;
      if (definition.isConstNumber()) {
        mappedValue = definition.asConstNumber().getIntValue();
      } else if (definition.isResourceConstNumber()) {
        mappedValue = definition.asResourceConstNumber().getValue();
      } else if (definition.isNewArrayEmpty()) {
        NewArrayEmpty newArrayEmpty = definition.asNewArrayEmpty();
        IntList values = new IntArrayList();
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
        mappedValue = values;
      } else if (definition.isNewArrayFilled()) {
        IntList values = new IntArrayList();
        for (Value inValue : definition.asNewArrayFilled().inValues()) {
          if (inValue.isPhi()) {
            continue;
          }
          Instruction valueDefinition = inValue.definition;
          if (valueDefinition.isConstNumber()) {
            values.add(valueDefinition.asConstNumber().getIntValue());
          } else if (valueDefinition.isResourceConstNumber()) {
            values.add(valueDefinition.asResourceConstNumber().getValue());
          }
        }
        mappedValue = values;
      } else {
        continue;
      }
      assert !valueMapping.containsKey(staticPut.getField());
      valueMapping.put(staticPut.getField(), mappedValue);
    }
  }

  private void analyzeStaticFields(
      DexProgramClass programClass, Map<DexField, Object> valueMapping) {
    for (DexEncodedField staticField :
        programClass.staticFields(DexEncodedField::hasExplicitStaticValue)) {
      DexValue staticValue = staticField.getStaticValue();
      if (staticValue.isDexValueInt()) {
        Integer value = staticValue.asDexValueInt().getValue();
        staticField.setStaticValue(
            DexValue.DexValueResourceNumber.create(staticValue.asDexValueInt().value));
        assert !valueMapping.containsKey(staticField.getReference());
        valueMapping.put(staticField.getReference(), value);
      } else if (staticValue.isDexValueResourceNumber()) {
        Integer value = staticValue.asDexValueResourceNumber().getValue();
        assert !valueMapping.containsKey(staticField.getReference());
        valueMapping.put(staticField.getReference(), value);
      }
    }
  }

}

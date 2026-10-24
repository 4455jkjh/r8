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
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.analysis.EnqueuerAnalysisCollection;
import com.android.tools.r8.graph.analysis.FinishedEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.FixpointEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.IrBasedEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.MarkFieldAsKeptEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveClassEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.NewlyLiveFieldEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.StartEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.TraceFieldAccessEnqueuerAnalysis;
import com.android.tools.r8.graph.analysis.TraceInvokeEnqueuerAnalysis;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.partial.R8PartialResourceUseCollector;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState.R8ResourceShrinkerModel;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerState.ResourceShrinkerCallback;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ResourceShrinkerEnqueuerExtension
    implements TraceFieldAccessEnqueuerAnalysis,
        TraceInvokeEnqueuerAnalysis,
        IrBasedEnqueuerAnalysis,
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
      if (getIdentifierAnalysisEnabled(appView, enqueuer)) {
        builder.addTraceInvokeAnalysis(extension);
        builder.addIrBasedEnqueuerAnalysis(extension);
      }
      return extension;
    }
    return null;
  }

  private static boolean getIdentifierAnalysisEnabled(
      AppView<? extends AppInfoWithClassHierarchy> appView, Enqueuer enqueuer) {
    return appView.options().enableResourceGetIdentifierTracing
        && enqueuer.getMode().isTreeShaking();
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

  @Override
  public void traceInvokeSuper(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    enqueueIfGetIdentifier(invokedMethod, context);
  }

  @Override
  public void traceInvokeVirtual(
      DexMethod invokedMethod, MethodResolutionResult resolutionResult, ProgramMethod context) {
    enqueueIfGetIdentifier(invokedMethod, context);
  }

  private void enqueueIfGetIdentifier(DexMethod invokedMethod, ProgramMethod context) {
    if (isGetIdentifier(invokedMethod)) {
      // The arguments are only available in the IR, so defer to handleReflectiveInvoke.
      enqueuer.getReflectiveIdentification().enqueue(context);
    }
  }

  @Override
  public boolean handleReflectiveInvoke(ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeMethodWithReceiver() || !isGetIdentifier(invoke.getInvokedMethod())) {
      return false;
    }
    GetIdentifierCall call = parseGetIdentifierInvoke(invoke.asInvokeMethodWithReceiver());
    if (call != null) {
      resourceShrinkerState.traceGetIdentifier(
          call.name, call.defType, call.defPackage, method.toSourceString(), this);
    }
    return true;
  }

  private static GetIdentifierCall parseGetIdentifierInvoke(InvokeMethodWithReceiver invoke) {
    // Resources.getIdentifier(String name, String defType, String defPackage).
    String name = getConstString(invoke.getFirstNonReceiverArgument());
    if (name == null) {
      return null;
    }
    String defType = getDefArgument(invoke.getSecondNonReceiverArgument());
    String defPackage = getDefArgument(invoke.getThirdNonReceiverArgument());
    return new GetIdentifierCall(name, defType, defPackage);
  }

  private boolean isGetIdentifier(DexMethod invokedMethod) {
    // Also match calls through subclasses of Resources, such as androidx ResourcesWrapper.
    DexItemFactory factory = appView.dexItemFactory();
    return invokedMethod.match(factory.androidResourcesGetIdentifierMethod)
        && invokedMethod.getHolderType().isClassType()
        && appView.appInfo().isSubtype(invokedMethod.getHolderType(), factory.androidResourcesType);
  }

  private static String getConstString(Value value) {
    Value root = value.getAliasedValue();
    if (root.isDefinedByInstructionSatisfying(Instruction::isConstString)) {
      return root.getDefinition().asConstString().getValue().toString();
    }
    return null;
  }

  private static String getDefArgument(Value value) {
    String constant = getConstString(value);
    if (constant != null) {
      return constant;
    }
    // Map definitely null values to "" so missing defaults remain distinct from non-constant
    // wildcards (null).
    return value.getType().isDefinitelyNull() ? "" : null;
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

  private static class GetIdentifierCall {
    final String name;
    final String defType;
    final String defPackage;

    GetIdentifierCall(String name, String defType, String defPackage) {
      this.name = name;
      this.defType = defType;
      this.defPackage = defPackage;
    }
  }

  private List<GetIdentifierCall> collectGetIdentifierCalls(ProgramMethod method) {
    IRCode ir = method.buildIR(appView, MethodConversionOptions.nonConverting());
    List<GetIdentifierCall> calls = new ArrayList<>();
    for (InvokeMethodWithReceiver invoke :
        ir.<InvokeMethodWithReceiver>instructions(Instruction::isInvokeMethodWithReceiver)) {
      if (isGetIdentifier(invoke.getInvokedMethod())) {
        GetIdentifierCall call = parseGetIdentifierInvoke(invoke);
        if (call != null) {
          calls.add(call);
        }
      }
    }
    return calls;
  }

  @Override
  public void onStarted(Enqueuer enqueuer, ExecutorService executorService)
      throws ExecutionException {
    if (!enqueuer.getMode().isTreeShaking()) {
      return;
    }
    resourceShrinkerState.traceKeepXmlAndManifest(this);

    // Trace resources.
    IntSet resourceRootIds = appView.rootSet().resourceIds;
    if (enqueuer.getMode().isInitialTreeShaking()
        && appView.options().partialSubCompilationConfiguration != null) {
      boolean traceGetIdentifier = getIdentifierAnalysisEnabled(appView, enqueuer);
      List<ProgramMethod> getIdentifierMethods = new ArrayList<>();
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

            @Override
            protected void analyzeMethod(ProgramMethod method, LirCode<?> code) {
              if (traceGetIdentifier
                  && code.hasConstantItemThatMatches(
                      constant ->
                          constant instanceof DexMethod && isGetIdentifier((DexMethod) constant))) {
                getIdentifierMethods.add(method);
              }
            }
          };
      resourceUseCollector.run();
      // Build IR only once, and only for D8 methods whose LIR constant pool references
      // Resources.getIdentifier, pinning resolved resources across Enqueuer passes.
      if (!getIdentifierMethods.isEmpty()) {
        Collection<List<GetIdentifierCall>> callsPerMethod =
            ThreadUtils.processItemsWithResultsThatMatches(
                getIdentifierMethods,
                this::collectGetIdentifierCalls,
                calls -> !calls.isEmpty(),
                appView.options().getThreadingModule(),
                executorService);
        for (List<GetIdentifierCall> calls : callsPerMethod) {
          for (GetIdentifierCall call : calls) {
            resourceShrinkerState.pinGetIdentifier(call.name, call.defType, call.defPackage);
          }
        }
      }
    }
    for (int rootResourceId : resourceRootIds) {
      resourceShrinkerState.trace(rootResourceId, "Non shrunken dex code", this);
    }
    resourceShrinkerState.tracePinnedResources(this);
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
    resourceShrinkerState.enqueuerDone(
        enqueuer.getMode().isFinalTreeShaking() || !appView.options().shouldRerunEnqueuer());
  }

  private void populateRClassValues(DexProgramClass programClass) {
    Map<DexField, Object> valueMapping = new IdentityHashMap<>();
    analyzeStaticFields(programClass, valueMapping);
    // Follow <clinit>() -> staticInit0() -> staticInit1() -> ... for R classes exceeding 64 KB.
    ProgramMethod method = programClass.getProgramClassInitializer();
    while (method != null) {
      method = analyzeStaticInitMethod(method, valueMapping);
    }
    fieldToValueMapping.put(programClass.getType(), valueMapping);
  }

  @SuppressWarnings("ReferenceEquality")
  private ProgramMethod analyzeStaticInitMethod(
      ProgramMethod method, Map<DexField, Object> valueMapping) {
    IRCode code = method.buildIR(appView, MethodConversionOptions.nonConverting());
    InvokeStatic overflowInvoke = getStaticInitOverflowInvoke(method, code);
    if (!method.getDefinition().isClassInitializer()
        && !hasExpectedStaticInitStructure(method, code, overflowInvoke)) {
      // Not an overflow method generated by RClassGenerator, so it is not treated as part of the
      // class initializer.
      return null;
    }

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
        for (Instruction user : newArrayEmpty.outValue().uniqueUsers()) {
          if (user.isArrayPut()) {
            Value constValue = user.asArrayPut().value();
            if (constValue.isConstNumber()) {
              values.add(constValue.getDefinition().asConstNumber().getIntValue());
            } else if (constValue.isConstResourceNumber()) {
              values.add(constValue.getDefinition().asResourceConstNumber().getValue());
            }
          } else if (user.isNewArrayFilledData()) {
            user.asNewArrayFilledData().forEachInt(values::add);
          } else {
            assert user == staticPut;
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

    if (overflowInvoke == null) {
      return null;
    }
    DexEncodedMethod target =
        method.getHolder().lookupDirectMethod(overflowInvoke.getInvokedMethod());
    assert target != null && target.getReference().isNotIdenticalTo(method.getReference());
    return target.asProgramMethod(method.getHolder());
  }

  private InvokeStatic getStaticInitOverflowInvoke(ProgramMethod method, IRCode code) {
    for (InvokeStatic invoke : code.<InvokeStatic>instructions(Instruction::isInvokeStatic)) {
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (invokedMethod.getHolderType().isIdenticalTo(method.getHolderType())) {
        DexEncodedMethod target = method.getHolder().lookupDirectMethod(invokedMethod);
        if (target != null
            && DescriptorUtils.isRClassStaticInitOverflowMethodWithoutHolderCheck(target)) {
          return invoke;
        }
      }
    }
    return null;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean hasExpectedStaticInitStructure(
      ProgramMethod method, IRCode code, InvokeStatic overflowInvoke) {
    // All field writes must be visible here as static puts to the R class, and the only call is
    // the call to the next staticInit<N>() method.
    for (Instruction instruction : code.instructions()) {
      if (instruction.isInvokeMethod() && instruction != overflowInvoke) {
        return false;
      }
      if (instruction.isFieldPut()
          && (!instruction.isStaticPut()
              || instruction
                  .asStaticPut()
                  .getField()
                  .getHolderType()
                  .isNotIdenticalTo(method.getHolderType()))) {
        return false;
      }
    }
    return true;
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

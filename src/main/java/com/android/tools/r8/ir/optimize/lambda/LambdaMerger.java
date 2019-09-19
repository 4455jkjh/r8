// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.Outliner;
import com.android.tools.r8.ir.optimize.info.FieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback.OptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.lambda.CodeProcessor.Strategy;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroup.LambdaStructureError;
import com.android.tools.r8.ir.optimize.lambda.kotlin.KotlinLambdaGroupIdFactory;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

// Merging lambda classes into single lambda group classes. There are three flavors
// of lambdas we are dealing with:
//   (a) lambda classes synthesized in desugaring, handles java lambdas
//   (b) k-style lambda classes synthesized by kotlin compiler
//   (c) j-style lambda classes synthesized by kotlin compiler
//
// Lambda merging is potentially applicable to all three of them, but
// current implementation deals with both k- and j-style lambdas.
//
// In general we merge lambdas in 5 phases:
//   1. collect all lambdas and compute group candidates. we do it synchronously
//      and ensure that the order of lambda groups and lambdas inside each group
//      is stable.
//   2. analyze usages of lambdas and exclude lambdas with unexpected usage
//      NOTE: currently we consider *all* usages outside the code invalid
//      so we only need to patch method code when replacing the lambda class.
//   3. exclude (invalidate) all lambda classes with usages we don't understand
//      or support, compact the remaining lambda groups, remove trivial groups
//      with less that 2 lambdas.
//   4. replace lambda valid/supported class constructions with references to
//      lambda group classes.
//   5. synthesize group lambda classes.
//
public final class LambdaMerger {
  // Maps lambda into a group, only contains lambdas we decided to merge.
  // NOTE: needs synchronization.
  private final Map<DexType, LambdaGroup> lambdas = new IdentityHashMap<>();
  // We use linked map to ensure stable ordering of the groups
  // when they are processed sequentially.
  // NOTE: needs synchronization.
  private final Map<LambdaGroupId, LambdaGroup> groups = new LinkedHashMap<>();

  // Since invalidating lambdas may happen concurrently we don't remove invalidated lambdas
  // from groups (and `lambdas`) right away since the ordering may be important. Instead we
  // collect invalidated lambdas and remove them from groups after analysis is done.
  private final Set<DexType> invalidatedLambdas = Sets.newConcurrentHashSet();

  // Methods which need to be patched to reference lambda group classes instead of the
  // original lambda classes. The number of methods is expected to be small since there
  // is a 1:1 relation between lambda and method it is defined in (unless such a method
  // was inlined by either kotlinc or r8).
  //
  // Note that we don't track precisely lambda -> method mapping, so it may happen that
  // we mark a method for further processing, and then invalidate the only lambda referenced
  // from it. In this case we will reprocess method that does not need patching, but it
  // should not be happening very frequently and we ignore possible overhead.
  private final Set<DexEncodedMethod> methodsToReprocess = Sets.newIdentityHashSet();

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;
  private final Kotlin kotlin;
  private final DiagnosticsHandler reporter;

  private BiFunction<DexEncodedMethod, IRCode, CodeProcessor> strategyFactory = null;

  // Lambda visitor invalidating lambdas it sees.
  private final LambdaTypeVisitor lambdaInvalidator;
  // Lambda visitor throwing Unreachable on each lambdas it sees.
  private final LambdaTypeVisitor lambdaChecker;

  public LambdaMerger(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.kotlin = factory.kotlin;
    this.reporter = appView.options().reporter;

    this.lambdaInvalidator = new LambdaTypeVisitor(factory, this::isMergeableLambda,
        this::invalidateLambda);
    this.lambdaChecker = new LambdaTypeVisitor(factory, this::isMergeableLambda,
        type -> {
          throw new Unreachable("Unexpected lambda " + type.toSourceString());
        });
  }

  private void invalidateLambda(DexType lambda) {
    invalidatedLambdas.add(lambda);
  }

  private synchronized boolean isMergeableLambda(DexType lambda) {
    return lambdas.containsKey(lambda);
  }

  private synchronized LambdaGroup getLambdaGroup(DexType lambda) {
    return lambdas.get(lambda);
  }

  private synchronized void queueForProcessing(DexEncodedMethod method) {
    methodsToReprocess.add(method);
  }

  // Collect all group candidates and assign unique lambda ids inside each group.
  // We do this before methods are being processed to guarantee stable order of
  // lambdas inside each group.
  public final void collectGroupCandidates(
      DexApplication app, AppView<AppInfoWithLiveness> appView) {
    // Collect lambda groups.
    app.classes().stream()
        .filter(cls -> !appView.appInfo().isPinned(cls.type))
        .filter(
            cls ->
                cls.hasKotlinInfo()
                    && cls.getKotlinInfo().isSyntheticClass()
                    && cls.getKotlinInfo().asSyntheticClass().isLambda()
                    && KotlinLambdaGroupIdFactory.hasValidAnnotations(kotlin, cls))
        .sorted((a, b) -> a.type.slowCompareTo(b.type)) // Ensure stable ordering.
        .forEachOrdered(
            lambda -> {
              try {
                LambdaGroupId id =
                    KotlinLambdaGroupIdFactory.create(kotlin, lambda, appView.options());
                LambdaGroup group = groups.computeIfAbsent(id, LambdaGroupId::createGroup);
                group.add(lambda);
                lambdas.put(lambda.type, group);
              } catch (LambdaStructureError error) {
                if (error.reportable) {
                  reporter.info(
                      new StringDiagnostic(
                          "Unrecognized Kotlin lambda ["
                              + lambda.type.toSourceString()
                              + "]: "
                              + error.getMessage()));
                }
              }
            });

    // Remove trivial groups.
    removeTrivialLambdaGroups();

    assert strategyFactory == null;
    strategyFactory = AnalysisStrategy::new;
  }

  // Is called by IRConverter::rewriteCode, performs different actions
  // depending on phase:
  //   - in ANALYZE phase just analyzes invalid usages of lambda classes
  //     inside the method code, invalidated such lambda classes,
  //     collects methods that need to be patched.
  //   - in APPLY phase patches the code to use lambda group classes, also
  //     asserts that there are no more invalid lambda class references.
  public final void processMethodCode(DexEncodedMethod method, IRCode code) {
    if (strategyFactory != null) {
      strategyFactory.apply(method, code).processCode();
    }
  }

  public final void applyLambdaClassMapping(
      DexApplication app,
      IRConverter converter,
      OptimizationFeedback feedback,
      Builder<?> builder,
      ExecutorService executorService)
      throws ExecutionException {
    if (lambdas.isEmpty()) {
      return;
    }

    // Analyse references from program classes. We assume that this optimization
    // is only used for full program analysis and there are no classpath classes.
    analyzeReferencesInProgramClasses(app, executorService);

    // Analyse more complex aspects of lambda classes including method code.
    analyzeLambdaClassesStructure(executorService);

    // Remove invalidated lambdas, compact groups to ensure
    // sequential lambda ids, create group lambda classes.
    Map<LambdaGroup, DexProgramClass> lambdaGroupsClasses = finalizeLambdaGroups();

    // Fixup optimization info to ensure that the optimization info does not refer to any merged
    // lambdas.
    LambdaMergerOptimizationInfoFixer optimizationInfoFixer =
        new LambdaMergerOptimizationInfoFixer(lambdaGroupsClasses);
    feedback.fixupOptimizationInfos(appView, executorService, optimizationInfoFixer);

    // Switch to APPLY strategy.
    this.strategyFactory = ApplyStrategy::new;

    // Add synthesized lambda group classes to the builder.

    for (Entry<LambdaGroup, DexProgramClass> entry : lambdaGroupsClasses.entrySet()) {
      DexProgramClass synthesizedClass = entry.getValue();
      appView.appInfo().addSynthesizedClass(synthesizedClass);
      builder.addSynthesizedClass(synthesizedClass, entry.getKey().shouldAddToMainDex(appView));
      // Eventually, we need to process synthesized methods in the lambda group.
      // Otherwise, abstract SynthesizedCode will be flown to Enqueuer.
      // But that process should not see the holder. Otherwise, lambda calls in the main dispatch
      // method became recursive calls via the lense rewriter. They should remain, then inliner
      // will inline methods from mergee lambdas to the main dispatch method.
      // Then, there is a dilemma: other sub optimizations trigger subtype lookup that will throw
      // NPE if it cannot find the holder for this synthesized lambda group.
      // One hack here is to mark those methods `processed` so that the lense rewriter is skipped.
      synthesizedClass.forEachMethod(
          encodedMethod -> encodedMethod.markProcessed(ConstraintWithTarget.NEVER));
    }
    converter.optimizeSynthesizedClasses(lambdaGroupsClasses.values(), executorService);

    // Rewrite lambda class references into lambda group class
    // references inside methods from the processing queue.
    rewriteLambdaReferences(converter, executorService, feedback);
    this.strategyFactory = null;
  }

  private void analyzeReferencesInProgramClasses(
      DexApplication app, ExecutorService service) throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (DexProgramClass clazz : app.classes()) {
      futures.add(service.submit(() -> analyzeClass(clazz)));
    }
    ThreadUtils.awaitFutures(futures);
  }

  private void analyzeLambdaClassesStructure(ExecutorService service) throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (LambdaGroup group : groups.values()) {
      ThrowingConsumer<DexClass, LambdaStructureError> validator =
          group.lambdaClassValidator(kotlin, appView.appInfo());
      group.forEachLambda(info ->
          futures.add(service.submit(() -> {
            try {
              validator.accept(info.clazz);
            } catch (LambdaStructureError error) {
              if (error.reportable) {
                reporter.info(
                    new StringDiagnostic("Unexpected Kotlin lambda structure [" +
                        info.clazz.type.toSourceString() + "]: " + error.getMessage())
                );
              }
              invalidateLambda(info.clazz.type);
            }
          })));
    }
    ThreadUtils.awaitFutures(futures);
  }

  private Map<LambdaGroup, DexProgramClass> finalizeLambdaGroups() {
    for (DexType lambda : invalidatedLambdas) {
      LambdaGroup group = lambdas.get(lambda);
      assert group != null;
      lambdas.remove(lambda);
      group.remove(lambda);
    }
    invalidatedLambdas.clear();

    // Remove new trivial lambdas.
    removeTrivialLambdaGroups();

    // Compact lambda groups, synthesize lambda group classes.
    Map<LambdaGroup, DexProgramClass> result = new LinkedHashMap<>();
    for (LambdaGroup group : groups.values()) {
      assert !group.isTrivial() : "No trivial group is expected here.";
      group.compact();
      DexProgramClass lambdaGroupClass = group.synthesizeClass(factory);
      result.put(group, lambdaGroupClass);

      // We have to register this new class as a subtype of object.
      appView.appInfo().registerNewType(lambdaGroupClass.type, lambdaGroupClass.superType);
    }
    return result;
  }

  private void removeTrivialLambdaGroups() {
    Iterator<Entry<LambdaGroupId, LambdaGroup>> iterator = groups.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<LambdaGroupId, LambdaGroup> group = iterator.next();
      if (group.getValue().isTrivial()) {
        iterator.remove();
        assert group.getValue().size() < 2;
        group.getValue().forEachLambda(info -> this.lambdas.remove(info.clazz.type));
      }
    }
  }

  private void rewriteLambdaReferences(
      IRConverter converter, ExecutorService executorService, OptimizationFeedback feedback)
      throws ExecutionException {
    if (methodsToReprocess.isEmpty()) {
      return;
    }
    Set<DexEncodedMethod> methods =
        methodsToReprocess.stream()
            .map(method -> appView.graphLense().mapDexEncodedMethod(method, appView))
            .collect(Collectors.toSet());
    List<Future<?>> futures = new ArrayList<>();
    for (DexEncodedMethod method : methods) {
      futures.add(
          executorService.submit(
              () -> {
                converter.processMethod(
                    method,
                    feedback,
                    methods::contains,
                    CallSiteInformation.empty(),
                    Outliner::noProcessing);
                assert method.isProcessed();
                return null;
              }));
    }
    ThreadUtils.awaitFutures(futures);
  }

  private void analyzeClass(DexProgramClass clazz) {
    lambdaInvalidator.accept(clazz.superType);
    lambdaInvalidator.accept(clazz.interfaces);
    lambdaInvalidator.accept(clazz.annotations);

    for (DexEncodedField field : clazz.staticFields()) {
      lambdaInvalidator.accept(field.annotations);
      if (field.field.type != clazz.type) {
        // Ignore static fields of the same type.
        lambdaInvalidator.accept(field.field, clazz.type);
      }
    }
    for (DexEncodedField field : clazz.instanceFields()) {
      lambdaInvalidator.accept(field.annotations);
      lambdaInvalidator.accept(field.field, clazz.type);
    }

    for (DexEncodedMethod method : clazz.directMethods()) {
      lambdaInvalidator.accept(method.annotations);
      lambdaInvalidator.accept(method.parameterAnnotationsList);
      lambdaInvalidator.accept(method.method, clazz.type);
    }
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      lambdaInvalidator.accept(method.annotations);
      lambdaInvalidator.accept(method.parameterAnnotationsList);
      lambdaInvalidator.accept(method.method, clazz.type);
    }
  }

  private Strategy strategyProvider(DexType type) {
    LambdaGroup group = this.getLambdaGroup(type);
    return group != null ? group.getCodeStrategy() : CodeProcessor.NoOp;
  }

  private final class AnalysisStrategy extends CodeProcessor {
    private AnalysisStrategy(DexEncodedMethod method, IRCode code) {
      super(
          LambdaMerger.this.appView,
          LambdaMerger.this::strategyProvider,
          lambdaInvalidator,
          method,
          code);
    }

    @Override
    void process(Strategy strategy, InvokeMethod invokeMethod) {
      queueForProcessing(method);
    }

    @Override
    void process(Strategy strategy, NewInstance newInstance) {
      queueForProcessing(method);
    }

    @Override
    void process(Strategy strategy, InstancePut instancePut) {
      queueForProcessing(method);
    }

    @Override
    void process(Strategy strategy, InstanceGet instanceGet) {
      queueForProcessing(method);
    }

    @Override
    void process(Strategy strategy, StaticPut staticPut) {
      queueForProcessing(method);
    }

    @Override
    void process(Strategy strategy, StaticGet staticGet) {
      queueForProcessing(method);
    }
  }

  public final class ApplyStrategy extends CodeProcessor {
    private ApplyStrategy(DexEncodedMethod method, IRCode code) {
      super(
          LambdaMerger.this.appView,
          LambdaMerger.this::strategyProvider,
          lambdaChecker,
          method,
          code);
    }

    @Override
    void process(Strategy strategy, InvokeMethod invokeMethod) {
      strategy.patch(this, invokeMethod);
    }

    @Override
    void process(Strategy strategy, NewInstance newInstance) {
      strategy.patch(this, newInstance);
    }

    @Override
    void process(Strategy strategy, InstancePut instancePut) {
      // Instance put should only appear in lambda class instance constructor,
      // we should never get here since we never rewrite them.
      throw new Unreachable();
    }

    @Override
    void process(Strategy strategy, InstanceGet instanceGet) {
      strategy.patch(this, instanceGet);
    }

    @Override
    void process(Strategy strategy, StaticPut staticPut) {
      // Static put should only appear in lambda class static initializer,
      // we should never get here since we never rewrite them.
      throw new Unreachable();
    }

    @Override
    void process(Strategy strategy, StaticGet staticGet) {
      strategy.patch(this, staticGet);
    }
  }

  private final class LambdaMergerOptimizationInfoFixer
      implements Function<DexType, DexType>, OptimizationInfoFixer {

    private final Map<LambdaGroup, DexProgramClass> lambdaGroupsClasses;

    LambdaMergerOptimizationInfoFixer(Map<LambdaGroup, DexProgramClass> lambdaGroupsClasses) {
      this.lambdaGroupsClasses = lambdaGroupsClasses;
    }

    @Override
    public DexType apply(DexType type) {
      LambdaGroup group = lambdas.get(type);
      if (group != null) {
        DexProgramClass clazz = lambdaGroupsClasses.get(group);
        if (clazz != null) {
          return clazz.type;
        }
      }
      return type;
    }

    @Override
    public void fixup(DexEncodedField field) {
      FieldOptimizationInfo optimizationInfo = field.getOptimizationInfo();
      if (optimizationInfo.isMutableFieldOptimizationInfo()) {
        optimizationInfo.asMutableFieldOptimizationInfo().fixupClassTypeReferences(this, appView);
      } else {
        assert optimizationInfo.isDefaultFieldOptimizationInfo();
      }
    }

    @Override
    public void fixup(DexEncodedMethod method) {
      MethodOptimizationInfo optimizationInfo = method.getOptimizationInfo();
      if (optimizationInfo.isUpdatableMethodOptimizationInfo()) {
        optimizationInfo
            .asUpdatableMethodOptimizationInfo()
            .fixupClassTypeReferences(this, appView);
      } else {
        assert optimizationInfo.isDefaultMethodOptimizationInfo();
      }
    }
  }
}

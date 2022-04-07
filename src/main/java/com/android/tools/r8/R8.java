// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static com.android.tools.r8.R8Command.USAGE_MESSAGE;
import static com.android.tools.r8.utils.AssertionUtils.forTesting;
import static com.android.tools.r8.utils.ExceptionUtils.unwrapExecutionException;

import com.android.tools.r8.androidapi.ApiReferenceStubber;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryKeepRuleGenerator;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.ApplicationWriter;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.errors.CheckDiscardDiagnostic;
import com.android.tools.r8.experimental.graphinfo.GraphConsumer;
import com.android.tools.r8.experimental.startup.StartupInstrumentation;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppServices;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.AppliedGraphLens;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.GenericSignatureContextBuilder;
import com.android.tools.r8.graph.GenericSignatureCorrectnessHelper;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.analysis.ClassInitializerAssertionEnablingAnalysis;
import com.android.tools.r8.graph.analysis.InitializedClassesInInstanceMethodsAnalysis;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger;
import com.android.tools.r8.inspector.internal.InspectorImpl;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringCollection;
import com.android.tools.r8.ir.desugar.CfClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAmender;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;
import com.android.tools.r8.ir.desugar.records.RecordDesugaring;
import com.android.tools.r8.ir.desugar.records.RecordFieldValuesRewriter;
import com.android.tools.r8.ir.optimize.AssertionsRewriter;
import com.android.tools.r8.ir.optimize.Inliner;
import com.android.tools.r8.ir.optimize.NestReducer;
import com.android.tools.r8.ir.optimize.SwitchMapCollector;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxingCfMethods;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.templates.CfUtilityMethodsForCodeOptimizations;
import com.android.tools.r8.jar.CfApplicationWriter;
import com.android.tools.r8.kotlin.KotlinMetadataRewriter;
import com.android.tools.r8.kotlin.KotlinMetadataUtils;
import com.android.tools.r8.naming.Minifier;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.naming.PrefixRewritingNamingLens;
import com.android.tools.r8.naming.ProguardMapMinifier;
import com.android.tools.r8.naming.RecordRewritingNamingLens;
import com.android.tools.r8.naming.signature.GenericSignatureRewriter;
import com.android.tools.r8.optimize.ClassAndMemberPublicizer;
import com.android.tools.r8.optimize.MemberRebindingAnalysis;
import com.android.tools.r8.optimize.MemberRebindingIdentityLensFactory;
import com.android.tools.r8.optimize.VisibilityBridgeRemover;
import com.android.tools.r8.optimize.bridgehoisting.BridgeHoisting;
import com.android.tools.r8.optimize.proto.ProtoNormalizer;
import com.android.tools.r8.origin.CommandLineOrigin;
import com.android.tools.r8.repackaging.Repackaging;
import com.android.tools.r8.repackaging.RepackagingLens;
import com.android.tools.r8.shaking.AbstractMethodRemover;
import com.android.tools.r8.shaking.AnnotationRemover;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ClassInitFieldSynthesizer;
import com.android.tools.r8.shaking.DefaultTreePrunerConfiguration;
import com.android.tools.r8.shaking.DiscardedChecker;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.Mode;
import com.android.tools.r8.shaking.EnqueuerFactory;
import com.android.tools.r8.shaking.EnqueuerResult;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.shaking.MainDexListBuilder;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationUtils;
import com.android.tools.r8.shaking.RootSetUtils.MainDexRootSet;
import com.android.tools.r8.shaking.RootSetUtils.RootSet;
import com.android.tools.r8.shaking.RootSetUtils.RootSetBuilder;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.android.tools.r8.shaking.TreePruner;
import com.android.tools.r8.shaking.TreePrunerConfiguration;
import com.android.tools.r8.shaking.VerticalClassMerger;
import com.android.tools.r8.shaking.VerticalClassMergerGraphLens;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.synthesis.SyntheticFinalization;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.CfgPrinter;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SelfRetraceTest;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteStreams;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;

/**
 * The R8 compiler.
 *
 * <p>R8 performs whole-program optimizing compilation of Java bytecode. It supports compilation of
 * Java bytecode to Java bytecode or DEX bytecode. R8 supports tree-shaking the program to remove
 * unneeded code and it supports minification of the program names to reduce the size of the
 * resulting program.
 *
 * <p>The R8 API is intentionally limited and should "do the right thing" given a command. If this
 * API does not suffice please contact the D8/R8 team.
 *
 * <p>R8 supports some configuration using configuration files mostly compatible with the format of
 * the <a href="https://www.guardsquare.com/en/proguard">ProGuard</a> optimizer.
 *
 * <p>The compiler is invoked by calling {@link #run(R8Command) R8.run} with an appropriate {link
 * R8Command}. For example:
 *
 * <pre>
 *   R8.run(R8Command.builder()
 *       .addProgramFiles(inputPathA, inputPathB)
 *       .addLibraryFiles(androidJar)
 *       .setOutput(outputPath, OutputMode.DexIndexed)
 *       .build());
 * </pre>
 *
 * The above reads the input files denoted by {@code inputPathA} and {@code inputPathB}, compiles
 * them to DEX bytecode, using {@code androidJar} as the reference of the system runtime library,
 * and then writes the result to the directory or zip archive specified by {@code outputPath}.
 */
@Keep
public class R8 {

  private final Timing timing;
  private final InternalOptions options;

  private R8(InternalOptions options) {
    this.options = options;
    if (options.printMemory) {
      System.gc();
    }
    timing = Timing.create("R8", options);
  }

  /**
   * Main API entry for the R8 compiler.
   *
   * <p>The R8 API is intentionally limited and should "do the right thing" given a command. If this
   * API does not suffice please contact the R8 team.
   *
   * @param command R8 command.
   */
  public static void run(R8Command command) throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    runForTesting(app, options);
  }

  /**
   * Main API entry for the R8 compiler.
   *
   * <p>The R8 API is intentionally limited and should "do the right thing" given a command. If this
   * API does not suffice please contact the R8 team.
   *
   * @param command R8 command.
   * @param executor executor service from which to get threads for multi-threaded processing.
   */
  public static void run(R8Command command, ExecutorService executor)
      throws CompilationFailedException {
    AndroidApp app = command.getInputApp();
    InternalOptions options = command.getInternalOptions();
    ExceptionUtils.withR8CompilationHandler(
        command.getReporter(),
        () -> {
          run(app, options, executor);
        });
  }

  static void writeApplication(
      ExecutorService executorService,
      AppView<?> appView,
      NamingLens namingLens,
      InternalOptions options,
      AndroidApp inputApp)
      throws ExecutionException {
    InspectorImpl.runInspections(options.outputInspections, appView.appInfo().classes());
    try {
      Marker marker = options.getMarker(Tool.R8);
      assert marker != null;
      // Get the markers from the input which are different from the one created for this
      // compilation
      Set<Marker> markers = new HashSet<>(options.itemFactory.extractMarkers());
      markers.remove(marker);
      if (options.isGeneratingClassFiles()) {
        new CfApplicationWriter(appView, marker, namingLens)
            .write(options.getClassFileConsumer(), inputApp);
      } else {
        new ApplicationWriter(
                appView,
                // Ensure that the marker for this compilation is the first in the list.
                ImmutableList.<Marker>builder().add(marker).addAll(markers).build(),
                namingLens)
            .write(executorService, inputApp);
      }
    } catch (IOException e) {
      throw new RuntimeException("Cannot write application", e);
    }
  }

  static void runForTesting(AndroidApp app, InternalOptions options)
      throws CompilationFailedException {
    ExecutorService executor = ThreadUtils.getExecutorService(options);
    ExceptionUtils.withR8CompilationHandler(
        options.reporter,
        () -> {
          try {
            run(app, options, executor);
          } finally {
            executor.shutdown();
          }
        });
  }

  private static void run(AndroidApp app, InternalOptions options, ExecutorService executor)
      throws IOException {
    new R8(options).run(app, executor);
  }

  private static DirectMappedDexApplication getDirectApp(AppView<?> appView) {
    return appView.appInfo().app().asDirect();
  }

  private void run(AndroidApp inputApp, ExecutorService executorService) throws IOException {
    assert options.programConsumer != null;
    if (options.quiet) {
      System.setOut(new PrintStream(ByteStreams.nullOutputStream()));
    }
    if (this.getClass().desiredAssertionStatus()) {
      options.reporter.info(
          new StringDiagnostic(
              "Running R8 version " + Version.LABEL + " with assertions enabled."));
    }
    // Synthetic assertion to check that testing assertions works and can be enabled.
    assert forTesting(options, () -> !options.testing.testEnableTestAssertions);
    if (options.printMemory) {
      // Run GC twice to remove objects with finalizers.
      System.gc();
      System.gc();
      Runtime runtime = Runtime.getRuntime();
      System.out.println("R8 is running with total memory:" + runtime.totalMemory());
      System.out.println("R8 is running with free memory:" + runtime.freeMemory());
      System.out.println("R8 is running with max memory:" + runtime.maxMemory());
    }
    try {
      AppView<AppInfoWithClassHierarchy> appView;
      {
        ApplicationReader applicationReader = new ApplicationReader(inputApp, options, timing);
        DirectMappedDexApplication application = applicationReader.read(executorService).toDirect();
        options.loadMachineDesugaredLibrarySpecification(timing, application);
        MainDexInfo mainDexInfo = applicationReader.readMainDexClassesForR8(application);
        // Now that the dex-application is fully loaded, close any internal archive providers.
        inputApp.closeInternalArchiveProviders();

        appView = AppView.createForR8(application, mainDexInfo);
        appView.setAppServices(AppServices.builder(appView).build());
        SyntheticItems.collectSyntheticInputs(appView);
      }

      // Check for potentially having pass-through of Cf-code for kotlin libraries.
      options.enableCfByteCodePassThrough =
          options.isGeneratingClassFiles() && KotlinMetadataUtils.mayProcessKotlinMetadata(appView);

      // Up-front check for valid library setup.
      if (!options.mainDexKeepRules.isEmpty()) {
        MainDexListBuilder.checkForAssumedLibraryTypes(appView.appInfo());
      }
      DesugaredLibraryAmender.run(appView);
      InterfaceMethodRewriter.checkForAssumedLibraryTypes(appView.appInfo(), options);
      BackportedMethodRewriter.registerAssumedLibraryTypes(options);
      if (options.enableEnumUnboxing) {
        EnumUnboxingCfMethods.registerSynthesizedCodeReferences(appView.dexItemFactory());
      }
      if (options.shouldDesugarRecords()) {
        RecordDesugaring.registerSynthesizedCodeReferences(appView.dexItemFactory());
      }
      CfUtilityMethodsForCodeOptimizations.registerSynthesizedCodeReferences(
          appView.dexItemFactory());

      // Upfront desugaring generation: Generates new program classes to be added in the app.
      CfClassSynthesizerDesugaringEventConsumer classSynthesizerEventConsumer =
          new CfClassSynthesizerDesugaringEventConsumer();
      CfClassSynthesizerDesugaringCollection.create(appView)
          .synthesizeClasses(executorService, classSynthesizerEventConsumer);
      if (appView.getSyntheticItems().hasPendingSyntheticClasses()) {
        appView.setAppInfo(
            appView
                .appInfo()
                .rebuildWithClassHierarchy(
                    appView.getSyntheticItems().commit(appView.appInfo().app())));
      }

      List<ProguardConfigurationRule> synthesizedProguardRules = new ArrayList<>();
      timing.begin("Strip unused code");
      RuntimeTypeCheckInfo.Builder classMergingEnqueuerExtensionBuilder =
          new RuntimeTypeCheckInfo.Builder(appView);
      try {
        // Add synthesized -assumenosideeffects from min api if relevant.
        if (options.isGeneratingDex()) {
          if (!ProguardConfigurationUtils.hasExplicitAssumeValuesOrAssumeNoSideEffectsRuleForMinSdk(
              options.itemFactory, options.getProguardConfiguration().getRules())) {
            synthesizedProguardRules.add(
                ProguardConfigurationUtils.buildAssumeNoSideEffectsRuleForApiLevel(
                    options.itemFactory, options.getMinApiLevel()));
          }
        }
        SubtypingInfo subtypingInfo = SubtypingInfo.create(appView);
        appView.setRootSet(
            RootSet.builder(
                    appView,
                    subtypingInfo,
                    Iterables.concat(
                        options.getProguardConfiguration().getRules(), synthesizedProguardRules))
                .build(executorService));

        // Compute the main dex rootset that will be the base of first and final main dex tracing
        // before building a new appview with only live classes (and invalidating subtypingInfo).
        if (!options.mainDexKeepRules.isEmpty()) {
          assert appView.graphLens().isIdentityLens();
          // Find classes which may have code executed before secondary dex files installation.
          MainDexRootSet mainDexRootSet =
              MainDexRootSet.builder(appView, subtypingInfo, options.mainDexKeepRules)
                  .build(executorService);
          appView.setMainDexRootSet(mainDexRootSet);
          appView.appInfo().unsetObsolete();
        }

        AnnotationRemover.Builder annotationRemoverBuilder =
            options.isShrinking() ? AnnotationRemover.builder(Mode.INITIAL_TREE_SHAKING) : null;
        AppView<AppInfoWithLiveness> appViewWithLiveness =
            runEnqueuer(
                annotationRemoverBuilder,
                executorService,
                appView,
                subtypingInfo,
                classMergingEnqueuerExtensionBuilder);

        assert appView.rootSet().verifyKeptFieldsAreAccessedAndLive(appViewWithLiveness);
        assert appView.rootSet().verifyKeptMethodsAreTargetedAndLive(appViewWithLiveness);
        assert appView.rootSet().verifyKeptTypesAreLive(appViewWithLiveness);
        assert appView.rootSet().verifyKeptItemsAreKept(appView);
        appView.rootSet().checkAllRulesAreUsed(options);

        if (options.proguardSeedsConsumer != null) {
          ByteArrayOutputStream bytes = new ByteArrayOutputStream();
          PrintStream out = new PrintStream(bytes);
          RootSetBuilder.writeSeeds(appView.appInfo().withLiveness(), out, type -> true);
          out.flush();
          ExceptionUtils.withConsumeResourceHandler(
              options.reporter, options.proguardSeedsConsumer, bytes.toString());
          ExceptionUtils.withFinishedResourceHandler(
              options.reporter, options.proguardSeedsConsumer);
        }
        if (options.isShrinking()) {
          // Mark dead proto extensions fields as neither being read nor written. This step must
          // run prior to the tree pruner.
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker -> shrinker.run(Mode.INITIAL_TREE_SHAKING));

          // Build enclosing information and type-parameter information before pruning.
          // TODO(b/187922482): Only consider referenced classes.
          GenericSignatureContextBuilder genericContextBuilder =
              GenericSignatureContextBuilder.create(appView);

          // Compute if all signatures are valid before modifying them.
          GenericSignatureCorrectnessHelper.createForInitialCheck(appView, genericContextBuilder)
              .run(appView.appInfo().classes());

          // TODO(b/226539525): Implement enum lite proto shrinking as deferred tracing.
          if (appView.options().protoShrinking().isEnumLiteProtoShrinkingEnabled()) {
            appView.protoShrinker().enumLiteProtoShrinker.clearDeadEnumLiteMaps();
          }

          TreePruner pruner = new TreePruner(appViewWithLiveness);
          DirectMappedDexApplication prunedApp = pruner.run(executorService);

          // Recompute the subtyping information.
          Set<DexType> removedClasses = pruner.getRemovedClasses();
          appView.pruneItems(
              PrunedItems.builder()
                  .setPrunedApp(prunedApp)
                  .addRemovedClasses(removedClasses)
                  .addAdditionalPinnedItems(pruner.getMethodsToKeepForConfigurationDebugging())
                  .build(),
              executorService);
          new AbstractMethodRemover(
                  appViewWithLiveness, appViewWithLiveness.appInfo().computeSubtypingInfo())
              .run();

          AnnotationRemover annotationRemover =
              annotationRemoverBuilder
                  .build(appViewWithLiveness, removedClasses);
          annotationRemover.ensureValid().run(executorService);
          new GenericSignatureRewriter(appView, NamingLens.getIdentityLens(), genericContextBuilder)
              .run(appView.appInfo().classes(), executorService);

          assert appView.checkForTesting(() -> allReferencesAssignedApiLevel(appViewWithLiveness));
        }
      } finally {
        timing.end();
      }

      assert appView.appInfo().hasLiveness();
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();

      new StartupInstrumentation(appView).instrumentClasses(executorService);

      assert verifyNoJarApplicationReaders(appView.appInfo().classes());
      assert appView.checkForTesting(() -> allReferencesAssignedApiLevel(appViewWithLiveness));
      // Build conservative main dex content after first round of tree shaking. This is used
      // by certain optimizations to avoid introducing additional class references into main dex
      // classes, as that can cause the final number of main dex methods to grow.
      // TODO(b/190941270): See if we can move this up before treepruning.
      performInitialMainDexTracing(appView, executorService);

      // The class type lattice elements include information about the interfaces that a class
      // implements. This information can change as a result of vertical class merging, so we need
      // to clear the cache, so that we will recompute the type lattice elements.
      appView.dexItemFactory().clearTypeElementsCache();

      if (options.getProguardConfiguration().isAccessModificationAllowed()) {
        SubtypingInfo subtypingInfo = appViewWithLiveness.appInfo().computeSubtypingInfo();
        GraphLens publicizedLens =
            ClassAndMemberPublicizer.run(
                executorService,
                timing,
                appViewWithLiveness.appInfo().app(),
                appViewWithLiveness,
                subtypingInfo);
        boolean changed = appView.setGraphLens(publicizedLens);
        if (changed) {
          // We can now remove visibility bridges. Note that we do not need to update the
          // invoke-targets here, as the existing invokes will simply dispatch to the now
          // visible super-method. MemberRebinding, if run, will then dispatch it correctly.
          new VisibilityBridgeRemover(appView.withLiveness()).run(executorService);
        }
      }

      // This pass attempts to reduce the number of nests and nest size to allow further passes, and
      // should therefore be run after the publicizer.
      new NestReducer(appViewWithLiveness).run(executorService, timing);

      appView.setGraphLens(new MemberRebindingAnalysis(appViewWithLiveness).run(executorService));
      appView.appInfo().withLiveness().getFieldAccessInfoCollection().restrictToProgram(appView);

      boolean isKotlinLibraryCompilationWithInlinePassThrough =
          options.enableCfByteCodePassThrough && appView.hasCfByteCodePassThroughMethods();

      RuntimeTypeCheckInfo runtimeTypeCheckInfo =
          classMergingEnqueuerExtensionBuilder.build(appView.graphLens());
      classMergingEnqueuerExtensionBuilder = null;

      if (!isKotlinLibraryCompilationWithInlinePassThrough
          && options.getProguardConfiguration().isOptimizing()) {
        if (options.enableVerticalClassMerging) {
          timing.begin("VerticalClassMerger");
          VerticalClassMergerGraphLens lens =
              new VerticalClassMerger(
                      getDirectApp(appViewWithLiveness),
                      appViewWithLiveness,
                      executorService,
                      timing)
                  .run();
          if (lens != null) {
            runtimeTypeCheckInfo = runtimeTypeCheckInfo.rewriteWithLens(lens);
          }
          timing.end();
        } else {
          appView.setVerticallyMergedClasses(VerticallyMergedClasses.empty());
        }
        assert appView.verticallyMergedClasses() != null;

        HorizontalClassMerger.createForInitialClassMerging(appViewWithLiveness)
            .runIfNecessary(executorService, timing, runtimeTypeCheckInfo);
      }

      new ProtoNormalizer(appViewWithLiveness).run(executorService, timing);

      // Clear traced methods roots to not hold on to the main dex live method set.
      appView.appInfo().getMainDexInfo().clearTracedMethodRoots();

      // None of the optimizations above should lead to the creation of type lattice elements.
      assert appView.dexItemFactory().verifyNoCachedTypeElements();
      assert appView.checkForTesting(() -> allReferencesAssignedApiLevel(appViewWithLiveness));

      // Collect switch maps and ordinals maps.
      if (options.enableEnumSwitchMapRemoval) {
        appViewWithLiveness.setAppInfo(new SwitchMapCollector(appViewWithLiveness).run());
      }

      // Collect the already pruned types before creating a new app info without liveness.
      // TODO: we should avoid removing liveness.
      Set<DexType> prunedTypes = appView.withLiveness().appInfo().getPrunedTypes();

      timing.begin("Create IR");
      CfgPrinter printer = options.printCfg ? new CfgPrinter() : null;
      try {
        IRConverter converter = new IRConverter(appView, timing, printer);
        DexApplication application =
            converter.optimize(appViewWithLiveness, executorService).asDirect();
        appView.setAppInfo(appView.appInfo().rebuildWithClassHierarchy(previous -> application));
      } finally {
        timing.end();
      }

      // Clear the reference type lattice element cache to reduce memory pressure.
      appView.dexItemFactory().clearTypeElementsCache();

      // At this point all code has been mapped according to the graph lens. We cannot remove the
      // graph lens entirely, though, since it is needed for mapping all field and method signatures
      // back to the original program.
      timing.begin("AppliedGraphLens construction");
      appView.setGraphLens(new AppliedGraphLens(appView));
      timing.end();

      if (options.printCfg) {
        if (options.printCfgFile == null || options.printCfgFile.isEmpty()) {
          System.out.print(printer.toString());
        } else {
          try (OutputStreamWriter writer = new OutputStreamWriter(
              new FileOutputStream(options.printCfgFile),
              StandardCharsets.UTF_8)) {
            writer.write(printer.toString());
          }
        }
      }

      if (options.shouldRerunEnqueuer()) {
        timing.begin("Post optimization code stripping");
        try {
          GraphConsumer keptGraphConsumer = null;
          WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = null;
          if (options.isShrinking()) {
            keptGraphConsumer = options.keptGraphConsumer;
            if (!appView.rootSet().reasonAsked.isEmpty()) {
              whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(keptGraphConsumer);
              keptGraphConsumer = whyAreYouKeepingConsumer;
            }
          }

          Enqueuer enqueuer =
              EnqueuerFactory.createForFinalTreeShaking(
                  appView,
                  executorService,
                  SubtypingInfo.create(appView),
                  keptGraphConsumer,
                  prunedTypes);
          if (options.isClassMergingExtensionRequired(enqueuer.getMode())) {
            classMergingEnqueuerExtensionBuilder = new RuntimeTypeCheckInfo.Builder(appView);
            classMergingEnqueuerExtensionBuilder.attach(enqueuer);
          }
          EnqueuerResult enqueuerResult =
              enqueuer.traceApplication(appView.rootSet(), executorService, timing);
          appView.setAppInfo(enqueuerResult.getAppInfo());
          // Rerunning the enqueuer should not give rise to any method rewritings.
          appView.withGeneratedMessageLiteBuilderShrinker(
              shrinker ->
                  shrinker.rewriteDeadBuilderReferencesFromDynamicMethods(
                      appViewWithLiveness, executorService, timing));

          if (options.isShrinking()) {
            // Mark dead proto extensions fields as neither being read nor written. This step must
            // run prior to the tree pruner.
            TreePrunerConfiguration treePrunerConfiguration =
                appView.withGeneratedExtensionRegistryShrinker(
                    shrinker -> shrinker.run(enqueuer.getMode()),
                    DefaultTreePrunerConfiguration.getInstance());

            GenericSignatureContextBuilder genericContextBuilder =
                GenericSignatureContextBuilder.create(appView);

            TreePruner pruner = new TreePruner(appViewWithLiveness, treePrunerConfiguration);
            DirectMappedDexApplication application = pruner.run(executorService);
            Set<DexType> removedClasses = pruner.getRemovedClasses();

            if (options.usageInformationConsumer != null) {
              ExceptionUtils.withFinishedResourceHandler(
                  options.reporter, options.usageInformationConsumer);
            }

            appView.pruneItems(
                PrunedItems.builder()
                    .setPrunedApp(application)
                    .addRemovedClasses(CollectionUtils.mergeSets(prunedTypes, removedClasses))
                    .addAdditionalPinnedItems(pruner.getMethodsToKeepForConfigurationDebugging())
                    .build(),
                executorService);

            new BridgeHoisting(appViewWithLiveness).run();

            assert Inliner.verifyAllSingleCallerMethodsHaveBeenPruned(appViewWithLiveness);
            assert Inliner.verifyAllMultiCallerInlinedMethodsHaveBeenPruned(appView);

            assert appView.allMergedClasses().verifyAllSourcesPruned(appViewWithLiveness);
            assert appView.validateUnboxedEnumsHaveBeenPruned();
            assert appView.withLiveness().appInfo().verifyNoIteratingOverPrunedClasses();

            processWhyAreYouKeepingAndCheckDiscarded(
                appView.rootSet(),
                () -> appView.appInfo().app().classesWithDeterministicOrder(),
                whyAreYouKeepingConsumer,
                appView,
                enqueuer,
                false,
                options,
                timing,
                executorService);

            // Remove annotations that refer to types that no longer exist.
            AnnotationRemover.builder(Mode.FINAL_TREE_SHAKING)
                .build(appView.withLiveness(), removedClasses)
                .run(executorService);
            new GenericSignatureRewriter(
                    appView, NamingLens.getIdentityLens(), genericContextBuilder)
                .run(appView.appInfo().classes(), executorService);
            assert appView.checkForTesting(
                    () ->
                        GenericSignatureCorrectnessHelper.createForVerification(
                                appView, GenericSignatureContextBuilder.create(appView))
                            .run(appView.appInfo().classes())
                            .isValid())
                : "Could not validate generic signatures";

            // Synthesize fields for triggering class initializers.
            new ClassInitFieldSynthesizer(appViewWithLiveness).run(executorService);
          }
        } finally {
          timing.end();
        }

        if (appView.options().protoShrinking().isProtoShrinkingEnabled()) {
          if (appView.options().protoShrinking().isEnumLiteProtoShrinkingEnabled()) {
            appView.protoShrinker().enumLiteProtoShrinker.verifyDeadEnumLiteMapsAreDead();
          }

          IRConverter converter = new IRConverter(appView, timing, null);

          // If proto shrinking is enabled, we need to reprocess every dynamicMethod(). This ensures
          // that proto fields that have been removed by the second round of tree shaking are also
          // removed from the proto schemas in the bytecode.
          appView.withGeneratedMessageLiteShrinker(
              shrinker -> shrinker.postOptimizeDynamicMethods(converter, executorService, timing));

          // If proto shrinking is enabled, we need to post-process every
          // findLiteExtensionByNumber() method. This ensures that there are no references to dead
          // extensions that have been removed by the second round of tree shaking.
          appView.withGeneratedExtensionRegistryShrinker(
              shrinker ->
                  shrinker.postOptimizeGeneratedExtensionRegistry(
                      converter, executorService, timing));
        }
      }

      performFinalMainDexTracing(appView, executorService);

      if (appView.appInfo().hasLiveness()) {
        RecordFieldValuesRewriter recordFieldArrayRemover =
            RecordFieldValuesRewriter.create(appView.withLiveness());
        if (recordFieldArrayRemover != null) {
          recordFieldArrayRemover.rewriteRecordFieldValues();
        }
      }

      // Remove unneeded visibility bridges that have been inserted for member rebinding.
      // This can only be done if we have AppInfoWithLiveness.
      if (appView.appInfo().hasLiveness()) {
        new VisibilityBridgeRemover(appView.withLiveness()).run(executorService);
      } else {
        // If we don't have AppInfoWithLiveness here, it must be because we are not shrinking. When
        // we are not shrinking, we can't move visibility bridges. In principle, though, it would be
        // possible to remove visibility bridges that have been synthesized by R8, but we currently
        // do not have this information.
        assert !options.isShrinking();
      }

      // Insert a member rebinding oracle in the graph to ensure that all subsequent rewritings of
      // the application has an applied oracle for looking up non-rebound references.
      appView.setGraphLens(MemberRebindingIdentityLensFactory.create(appView, executorService));

      if (appView.appInfo().hasLiveness()) {
        SyntheticFinalization.finalizeWithLiveness(appView.withLiveness(), executorService);
      } else {
        SyntheticFinalization.finalizeWithClassHierarchy(appView, executorService);
      }

      // Read any -applymapping input to allow for repackaging to not relocate the classes.
      timing.begin("read -applymapping file");
      appView.loadApplyMappingSeedMapper();
      timing.end();

      // Perform repackaging.
      if (options.isRepackagingEnabled()) {
        DirectMappedDexApplication.Builder appBuilder =
            appView.appInfo().app().asDirect().builder();
        RepackagingLens lens =
            new Repackaging(appView.withLiveness()).run(appBuilder, executorService, timing);
        if (lens != null) {
          appView.rewriteWithLensAndApplication(lens, appBuilder.build());
        }
      }
      if (appView.appInfo().hasLiveness()) {
        assert Repackaging.verifyIdentityRepackaging(appView.withLiveness());
      }


      // Clear the reference type lattice element cache. This is required since class merging may
      // need to build IR.
      appView.dexItemFactory().clearTypeElementsCache();

      GenericSignatureContextBuilder genericContextBuilderBeforeFinalMerging =
          GenericSignatureContextBuilder.create(appView);

      // Run horizontal class merging. This runs even if shrinking is disabled to ensure synthetics
      // are always merged.
      HorizontalClassMerger.createForFinalClassMerging(appView)
          .runIfNecessary(
              executorService,
              timing,
              classMergingEnqueuerExtensionBuilder != null
                  ? classMergingEnqueuerExtensionBuilder.build(appView.graphLens())
                  : null);

      // Perform minification.
      NamingLens namingLens;
      if (options.getProguardConfiguration().hasApplyMappingFile()) {
        timing.begin("apply-mapping");
        namingLens = new ProguardMapMinifier(appView.withLiveness()).run(executorService, timing);
        timing.end();
        // Clear the applymapping data
        appView.clearApplyMappingSeedMapper();
      } else if (options.isMinifying()) {
        timing.begin("Minification");
        namingLens = new Minifier(appView.withLiveness()).run(executorService, timing);
        timing.end();
      } else {
        namingLens = NamingLens.getIdentityLens();
      }

      assert verifyMovedMethodsHaveOriginalMethodPosition(appView, getDirectApp(appView));

      // If a method filter is present don't produce output since the application is likely partial.
      if (options.hasMethodsFilter()) {
        System.out.println("Finished compilation with method filter: ");
        options.methodsFilter.forEach(m -> System.out.println("  - " + m));
        return;
      }

      // Validity checks.
      assert getDirectApp(appView).verifyCodeObjectsOwners();
      assert appView.appInfo().classes().stream().allMatch(clazz -> clazz.isValid(options));
      if (options.isShrinking()
          || options.isMinifying()
          || options.getProguardConfiguration().hasApplyMappingFile()) {
        assert appView.rootSet().verifyKeptItemsAreKept(appView);
      }

      assert options.testing.disableMappingToOriginalProgramVerification
          || appView
              .graphLens()
              .verifyMappingToOriginalProgram(
                  appView,
                  new ApplicationReader(inputApp.withoutMainDexList(), options, timing)
                      .readWithoutDumping(executorService));

      // Report synthetic rules (only for testing).
      // TODO(b/120959039): Move this to being reported through the graph consumer.
      if (options.syntheticProguardRulesConsumer != null) {
        options.syntheticProguardRulesConsumer.accept(synthesizedProguardRules);
      }

      namingLens = PrefixRewritingNamingLens.createPrefixRewritingNamingLens(appView, namingLens);
      namingLens = RecordRewritingNamingLens.createRecordRewritingNamingLens(appView, namingLens);

      new ApiReferenceStubber(appView).run(executorService);

      timing.begin("MinifyKotlinMetadata");
      new KotlinMetadataRewriter(appView, namingLens).runForR8(executorService);
      timing.end();

      new GenericSignatureRewriter(appView, namingLens, genericContextBuilderBeforeFinalMerging)
          .run(appView.appInfo().classes(), executorService);

      assert appView.checkForTesting(
              () ->
                  !options.isShrinking()
                      || GenericSignatureCorrectnessHelper.createForVerification(
                              appView, GenericSignatureContextBuilder.create(appView))
                          .run(appView.appInfo().classes())
                          .isValid())
          : "Could not validate generic signatures";

      new DesugaredLibraryKeepRuleGenerator(appView, namingLens).runIfNecessary(timing);

      // Generate the resulting application resources.
      writeApplication(
          executorService,
          appView,
          namingLens,
          options,
          inputApp);

      assert appView.getDontWarnConfiguration().validate(options);

      options.printWarnings();
    } catch (ExecutionException e) {
      throw unwrapExecutionException(e);
    } finally {
      options.signalFinishedToConsumers();
      // Dump timings.
      if (options.printTimes) {
        timing.report();
      }
    }
  }

  private static boolean allReferencesAssignedApiLevel(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    if (!appView.options().apiModelingOptions().checkAllApiReferencesAreSet
        || appView.options().configurationDebugging) {
      return true;
    }
    // This will return false if we find anything in the library which is not modeled.
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      clazz.forEachProgramMember(
          member -> {
            assert !member.getDefinition().getApiLevel().isNotSetApiLevel()
                : "Every member should have been analyzed";
            assert appView.options().apiModelingOptions().enableApiCallerIdentification
                    || member.getDefinition().getApiLevel().isUnknownApiLevel()
                : "Every member should have level UNKNOWN";
          });
    }
    return true;
  }

  private void performInitialMainDexTracing(
      AppView<AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    if (options.mainDexKeepRules.isEmpty()) {
      return;
    }
    assert appView.graphLens().isIdentityLens();

    // Find classes which may have code executed before secondary dex files installation by
    // computing from the initially computed main dex root set.
    MainDexInfo mainDexInfo =
        EnqueuerFactory.createForInitialMainDexTracing(
                appView, executorService, SubtypingInfo.create(appView))
            .traceMainDex(executorService, timing);
    appView.setAppInfo(appView.appInfo().rebuildWithMainDexInfo(mainDexInfo));
  }

  private void performFinalMainDexTracing(
      AppView<AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    if (options.mainDexKeepRules.isEmpty()) {
      return;
    }
    // No need to build a new main dex root set
    assert appView.getMainDexRootSet() != null;
    GraphConsumer mainDexKeptGraphConsumer = options.mainDexKeptGraphConsumer;
    WhyAreYouKeepingConsumer whyAreYouKeepingConsumer = null;
    if (!appView.getMainDexRootSet().reasonAsked.isEmpty()) {
      whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(mainDexKeptGraphConsumer);
      mainDexKeptGraphConsumer = whyAreYouKeepingConsumer;
    }

    Enqueuer enqueuer =
        EnqueuerFactory.createForFinalMainDexTracing(
            appView, executorService, SubtypingInfo.create(appView), mainDexKeptGraphConsumer);
    // Find classes which may have code executed before secondary dex files installation.
    MainDexInfo mainDexInfo = enqueuer.traceMainDex(executorService, timing);
    appView.setAppInfo(appView.appInfo().rebuildWithMainDexInfo(mainDexInfo));

    processWhyAreYouKeepingAndCheckDiscarded(
        appView.getMainDexRootSet(),
        () -> {
          ArrayList<DexProgramClass> classes = new ArrayList<>();
          // TODO(b/131668850): This is not a deterministic order!
          mainDexInfo.forEach(
              type -> {
                DexClass clazz = appView.definitionFor(type);
                assert clazz.isProgramClass();
                classes.add(clazz.asProgramClass());
              });
          return classes;
        },
        whyAreYouKeepingConsumer,
        appView,
        enqueuer,
        true,
        options,
        timing,
        executorService);
  }

  private static boolean verifyMovedMethodsHaveOriginalMethodPosition(
      AppView<?> appView, DirectMappedDexApplication application) {
    application
        .classesWithDeterministicOrder()
        .forEach(
            clazz ->
                clazz.forEachProgramMethod(
                    method -> {
                      DexMethod originalMethod =
                          appView.graphLens().getOriginalMethodSignature(method.getReference());
                      if (originalMethod != method.getReference()) {
                        DexEncodedMethod definition = method.getDefinition();
                        Code code = definition.getCode();
                        if (code == null) {
                          return;
                        }
                        if (code.isCfCode()) {
                          assert verifyOriginalMethodInPosition(code.asCfCode(), originalMethod);
                        } else if (code.isDexCode()) {
                          assert verifyOriginalMethodInDebugInfo(code.asDexCode(), originalMethod);
                        } else {
                          assert code.isDefaultInstanceInitializerCode() || code.isThrowNullCode();
                        }
                      }
                    }));
    return true;
  }

  private static boolean verifyOriginalMethodInPosition(CfCode code, DexMethod originalMethod) {
    for (CfInstruction instruction : code.getInstructions()) {
      if (!instruction.isPosition()) {
        continue;
      }
      CfPosition position = instruction.asPosition();
      assert position.getPosition().getOutermostCaller().getMethod() == originalMethod;
    }
    return true;
  }

  private static boolean verifyOriginalMethodInDebugInfo(DexCode code, DexMethod originalMethod) {
    if (code.getDebugInfo() == null || code.getDebugInfo().isPcBasedInfo()) {
      return true;
    }
    for (DexDebugEvent event : code.getDebugInfo().asEventBasedInfo().events) {
      assert !event.isPositionFrame()
          || event.asSetPositionFrame().getPosition().getOutermostCaller().getMethod()
              == originalMethod;
    }
    return true;
  }

  private AppView<AppInfoWithLiveness> runEnqueuer(
      AnnotationRemover.Builder annotationRemoverBuilder,
      ExecutorService executorService,
      AppView<AppInfoWithClassHierarchy> appView,
      SubtypingInfo subtypingInfo,
      RuntimeTypeCheckInfo.Builder classMergingEnqueuerExtensionBuilder)
      throws ExecutionException {
    Enqueuer enqueuer =
        EnqueuerFactory.createForInitialTreeShaking(appView, executorService, subtypingInfo);
    enqueuer.setAnnotationRemoverBuilder(annotationRemoverBuilder);
    if (appView.options().enableInitializedClassesInInstanceMethodsAnalysis) {
      enqueuer.registerAnalysis(new InitializedClassesInInstanceMethodsAnalysis(appView));
    }
    if (AssertionsRewriter.isEnabled(appView.options())) {
      ClassInitializerAssertionEnablingAnalysis analysis =
          new ClassInitializerAssertionEnablingAnalysis(
              appView, OptimizationFeedbackSimple.getInstance());
      enqueuer.registerAnalysis(analysis);
      enqueuer.registerFieldAccessAnalysis(analysis);
    }

    if (options.isClassMergingExtensionRequired(enqueuer.getMode())) {
      classMergingEnqueuerExtensionBuilder.attach(enqueuer);
    }

    EnqueuerResult enqueuerResult =
        enqueuer.traceApplication(appView.rootSet(), executorService, timing);
    AppView<AppInfoWithLiveness> appViewWithLiveness =
        appView.setAppInfo(enqueuerResult.getAppInfo());
    if (InternalOptions.assertionsEnabled()) {
      // Register the dead proto types. These are needed to verify that no new missing types are
      // reported and that no dead proto types are referenced in the generated application.
      appViewWithLiveness.withProtoShrinker(
          shrinker ->
              shrinker.setDeadProtoTypes(appViewWithLiveness.appInfo().getDeadProtoTypes()));
    }
    appView.withGeneratedMessageLiteBuilderShrinker(
        shrinker ->
            shrinker.rewriteDeadBuilderReferencesFromDynamicMethods(
                appViewWithLiveness, executorService, timing));
    return appViewWithLiveness;
  }

  static void processWhyAreYouKeepingAndCheckDiscarded(
      RootSet rootSet,
      Supplier<Iterable<DexProgramClass>> classes,
      WhyAreYouKeepingConsumer whyAreYouKeepingConsumer,
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      boolean forMainDex,
      InternalOptions options,
      Timing timing,
      ExecutorService executorService)
      throws ExecutionException {
    if (whyAreYouKeepingConsumer != null) {
      for (DexReference reference : rootSet.reasonAsked) {
        whyAreYouKeepingConsumer.printWhyAreYouKeeping(
            enqueuer.getGraphReporter().getGraphNode(reference), System.out);
      }
    }
    if (appView.options().testing.dontReportFailingCheckDiscarded) {
      return;
    }
    DiscardedChecker discardedChecker =
        forMainDex ? DiscardedChecker.createForMainDex(appView) : DiscardedChecker.create(appView);
    List<ProgramDefinition> failed = discardedChecker.run(classes.get(), executorService);
    if (failed.isEmpty()) {
      return;
    }
    // If there is no kept-graph info, re-run the enqueueing to compute it.
    if (whyAreYouKeepingConsumer == null) {
      whyAreYouKeepingConsumer = new WhyAreYouKeepingConsumer(null);
      SubtypingInfo subtypingInfo = SubtypingInfo.create(appView);
      if (forMainDex) {
        enqueuer =
            EnqueuerFactory.createForFinalMainDexTracing(
                appView, executorService, subtypingInfo, whyAreYouKeepingConsumer);
        enqueuer.traceMainDex(executorService, timing);
      } else {
        enqueuer =
            EnqueuerFactory.createForWhyAreYouKeeping(
                appView, executorService, subtypingInfo, whyAreYouKeepingConsumer);
        enqueuer.traceApplication(
            rootSet,
            executorService,
            timing);
      }
    }
    options.reporter.error(
        new CheckDiscardDiagnostic.Builder()
            .addFailedItems(failed, enqueuer.getGraphReporter(), whyAreYouKeepingConsumer)
            .build());
    options.reporter.failIfPendingErrors();
  }

  private static boolean verifyNoJarApplicationReaders(Collection<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.getCode() != null) {
          assert method.getCode().verifyNoInputReaders();
        }
      }
    }
    return true;
  }

  private static void run(String[] args) throws CompilationFailedException {
    R8Command command = R8Command.parse(args, CommandLineOrigin.INSTANCE).build();
    if (command.isPrintHelp()) {
      SelfRetraceTest.test();
      System.out.println(USAGE_MESSAGE);
      return;
    }
    if (command.isPrintVersion()) {
      System.out.println("R8 " + Version.getVersionString());
      return;
    }
    InternalOptions options = command.getInternalOptions();
    ExecutorService executorService = ThreadUtils.getExecutorService(options);
    try {
      ExceptionUtils.withR8CompilationHandler(options.reporter, () ->
          run(command.getInputApp(), options, executorService));
    } finally {
      executorService.shutdown();
    }
  }

  /**
   * Command-line entry to R8.
   *
   * See {@link R8Command#USAGE_MESSAGE} or run {@code r8 --help} for usage information.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      throw new RuntimeException(StringUtils.joinLines("Invalid invocation.", USAGE_MESSAGE));
    }
    ExceptionUtils.withMainProgramHandler(() -> run(args));
  }
}

// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static com.google.common.base.Predicates.alwaysTrue;

import com.android.tools.r8.features.ClassToFeatureSplitMap;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import com.android.tools.r8.graph.NestedGraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.TreeFixerBase;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.shaking.MainDexInfo;
import com.android.tools.r8.synthesis.SyntheticItems.State;
import com.android.tools.r8.synthesis.SyntheticNaming.Phase;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.structural.RepresentativeMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.hash.HashCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class SyntheticFinalization {

  public static class Result {
    public final CommittedItems commit;
    public final NonIdentityGraphLens lens;
    public final PrunedItems prunedItems;
    public final MainDexInfo mainDexInfo;

    public Result(
        CommittedItems commit,
        SyntheticFinalizationGraphLens lens,
        PrunedItems prunedItems,
        MainDexInfo mainDexInfo) {
      this.commit = commit;
      this.lens = lens;
      this.prunedItems = prunedItems;
      this.mainDexInfo = mainDexInfo;
    }
  }

  public static class SyntheticFinalizationGraphLens extends NestedGraphLens {

    private SyntheticFinalizationGraphLens(
        AppView<?> appView,
        BidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap,
        BidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> methodMap,
        Map<DexType, DexType> typeMap) {
      super(appView, fieldMap, methodMap, typeMap);
    }

    @Override
    public boolean isSyntheticFinalizationGraphLens() {
      return true;
    }
  }

  private static class Builder {

    private final BidirectionalManyToOneRepresentativeHashMap<DexField, DexField> fieldMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final MutableBidirectionalManyToOneRepresentativeMap<DexMethod, DexMethod> methodMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    private final Map<DexType, DexType> typeMap = new IdentityHashMap<>();

    boolean isEmpty() {
      if (typeMap.isEmpty()) {
        assert fieldMap.isEmpty();
        assert methodMap.isEmpty();
        return true;
      }
      return false;
    }

    void move(DexType from, DexType to) {
      DexType old = typeMap.put(from, to);
      assert old == null || old == to;
    }

    void move(DexField from, DexField to) {
      DexField old = fieldMap.put(from, to);
      assert old == null || old == to;
    }

    void move(DexMethod from, DexMethod to) {
      methodMap.put(from, to);
    }

    void setRepresentative(DexField field, DexField representative) {
      fieldMap.setRepresentative(field, representative);
    }

    void setRepresentative(DexMethod method, DexMethod representative) {
      methodMap.setRepresentative(method, representative);
    }

    SyntheticFinalizationGraphLens build(AppView<?> appView) {
      if (typeMap.isEmpty() && fieldMap.isEmpty() && methodMap.isEmpty()) {
        return null;
      }
      return new SyntheticFinalizationGraphLens(appView, fieldMap, methodMap, typeMap);
    }
  }

  private final InternalOptions options;
  private final SyntheticItems synthetics;
  private final CommittedSyntheticsCollection committed;

  SyntheticFinalization(
      InternalOptions options, SyntheticItems synthetics, CommittedSyntheticsCollection committed) {
    this.options = options;
    this.synthetics = synthetics;
    this.committed = committed;
  }

  public static void finalize(AppView<AppInfo> appView, ExecutorService executorService)
      throws ExecutionException {
    assert !appView.appInfo().hasClassHierarchy();
    assert !appView.appInfo().hasLiveness();
    Result result = appView.getSyntheticItems().computeFinalSynthetics(appView);
    appView.setAppInfo(new AppInfo(result.commit, result.mainDexInfo));
    if (result.lens != null) {
      appView.setAppInfo(
          appView
              .appInfo()
              .rebuildWithMainDexInfo(
                  appView
                      .appInfo()
                      .getMainDexInfo()
                      .rewrittenWithLens(appView.getSyntheticItems(), result.lens)));
      appView.setGraphLens(result.lens);
    }
    appView.pruneItems(result.prunedItems, executorService);
  }

  public static void finalizeWithClassHierarchy(
      AppView<AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    assert !appView.appInfo().hasLiveness();
    Result result = appView.getSyntheticItems().computeFinalSynthetics(appView);
    appView.setAppInfo(appView.appInfo().rebuildWithClassHierarchy(result.commit));
    appView.setAppInfo(appView.appInfo().rebuildWithMainDexInfo(result.mainDexInfo));
    if (result.lens != null) {
      appView.setGraphLens(result.lens);
      appView.setAppInfo(
          appView
              .appInfo()
              .rebuildWithMainDexInfo(
                  appView
                      .appInfo()
                      .getMainDexInfo()
                      .rewrittenWithLens(appView.getSyntheticItems(), result.lens)));
    }
    appView.pruneItems(result.prunedItems, executorService);
  }

  public static void finalizeWithLiveness(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService)
      throws ExecutionException {
    appView.options().testing.checkDeterminism(appView);
    Result result = appView.getSyntheticItems().computeFinalSynthetics(appView);
    appView.setAppInfo(appView.appInfo().rebuildWithMainDexInfo(result.mainDexInfo));
    if (result.lens != null) {
      appView.rewriteWithLensAndApplication(result.lens, result.commit.getApplication().asDirect());
    } else {
      assert result.commit.getApplication() == appView.appInfo().app();
    }
    appView.setAppInfo(appView.appInfo().rebuildWithLiveness(result.commit));
    appView.pruneItems(result.prunedItems, executorService);
  }

  Result computeFinalSynthetics(AppView<?> appView) {
    assert verifyNoNestedSynthetics(appView.dexItemFactory());
    assert verifyOneSyntheticPerSyntheticClass();
    DexApplication application;
    Builder lensBuilder = new Builder();
    ImmutableMap.Builder<DexType, List<SyntheticMethodReference>> finalMethodsBuilder =
        ImmutableMap.builder();
    ImmutableMap.Builder<DexType, List<SyntheticProgramClassReference>> finalClassesBuilder =
        ImmutableMap.builder();
    Set<DexType> derivedMainDexTypes = Sets.newIdentityHashSet();
    {
      Map<String, NumberGenerator> generators = new HashMap<>();
      application =
          buildLensAndProgram(
              appView,
              computeEquivalences(appView, committed.getMethods(), generators, lensBuilder),
              computeEquivalences(appView, committed.getClasses(), generators, lensBuilder),
              lensBuilder,
              (clazz, reference) ->
                  finalClassesBuilder.put(clazz.getType(), ImmutableList.of(reference)),
              (clazz, reference) ->
                  finalMethodsBuilder.put(clazz.getType(), ImmutableList.of(reference)),
              derivedMainDexTypes);
    }
    ImmutableMap<DexType, List<SyntheticMethodReference>> finalMethods =
        finalMethodsBuilder.build();
    ImmutableMap<DexType, List<SyntheticProgramClassReference>> finalClasses =
        finalClassesBuilder.build();

    Set<DexType> prunedSynthetics = Sets.newIdentityHashSet();
    committed.forEachItem(
        reference -> {
          DexType type = reference.getHolder();
          if (!finalMethods.containsKey(type) && !finalClasses.containsKey(type)) {
            prunedSynthetics.add(type);
          }
        });

    SyntheticFinalizationGraphLens syntheticFinalizationGraphLens = lensBuilder.build(appView);

    ImmutableSet<DexType> finalInputSynthetics =
        syntheticFinalizationGraphLens != null
            ? SetUtils.newImmutableSet(
                builder ->
                    committed.forEachSyntheticInput(
                        syntheticInputType ->
                            builder.accept(
                                syntheticFinalizationGraphLens.lookupType(syntheticInputType))))
            : committed.syntheticInputs;

    // TODO(b/181858113): Remove once deprecated main-dex-list is removed.
    MainDexInfo.Builder mainDexInfoBuilder = appView.appInfo().getMainDexInfo().builderFromCopy();
    derivedMainDexTypes.forEach(mainDexInfoBuilder::addList);

    return new Result(
        new CommittedItems(
            State.FINALIZED,
            application,
            new CommittedSyntheticsCollection(
                synthetics.getNaming(), finalMethods, finalClasses, finalInputSynthetics),
            ImmutableList.of()),
        syntheticFinalizationGraphLens,
        PrunedItems.builder().setPrunedApp(application).addRemovedClasses(prunedSynthetics).build(),
        mainDexInfoBuilder.build());
  }

  private <R extends SyntheticReference<R, D, ?>, D extends SyntheticDefinition<R, D, ?>>
      Map<DexType, EquivalenceGroup<D>> computeEquivalences(
          AppView<?> appView,
          ImmutableMap<DexType, List<R>> references,
          Map<String, NumberGenerator> generators,
          Builder lensBuilder) {
    boolean intermediate = appView.options().intermediate;
    Map<DexType, D> definitions = lookupDefinitions(appView, references);
    ClassToFeatureSplitMap classToFeatureSplitMap =
        appView.appInfo().hasClassHierarchy()
            ? appView.appInfo().withClassHierarchy().getClassToFeatureSplitMap()
            : ClassToFeatureSplitMap.createEmptyClassToFeatureSplitMap();
    Collection<List<D>> potentialEquivalences =
        computePotentialEquivalences(
            definitions,
            intermediate,
            appView.dexItemFactory(),
            appView.graphLens(),
            classToFeatureSplitMap,
            synthetics);
    return computeActualEquivalences(
        potentialEquivalences,
        generators,
        appView,
        intermediate,
        classToFeatureSplitMap,
        lensBuilder);
  }

  private boolean isNotSyntheticType(DexType type) {
    return !committed.containsType(type);
  }

  private boolean verifyNoNestedSynthetics(DexItemFactory dexItemFactory) {
    // Check that the prefix of each synthetic is never itself synthetic.
    committed.forEachItem(
        item -> {
          if (item.getKind().isGlobal()) {
            return;
          }
          String prefix =
              SyntheticNaming.getPrefixForExternalSyntheticType(item.getKind(), item.getHolder());
          assert !prefix.contains(SyntheticNaming.getPhaseSeparator(Phase.INTERNAL));
          DexType context =
              dexItemFactory.createType(DescriptorUtils.getDescriptorFromClassBinaryName(prefix));
          assert isNotSyntheticType(context);
        });
    return true;
  }

  private boolean verifyOneSyntheticPerSyntheticClass() {
    Set<DexType> seen = Sets.newIdentityHashSet();
    committed
        .getClasses()
        .forEach(
            (type, references) -> {
              assert seen.add(type);
              assert references.size() == 1;
            });
    committed
        .getMethods()
        .forEach(
            (type, references) -> {
              assert seen.add(type);
              assert references.size() == 1;
            });
    return true;
  }

  private static void ensureSourceFile(
      DexProgramClass externalSyntheticClass, DexString syntheticSourceFileName) {
    if (externalSyntheticClass.getSourceFile() == null) {
      externalSyntheticClass.setSourceFile(syntheticSourceFileName);
    }
  }

  private static DexApplication buildLensAndProgram(
      AppView<?> appView,
      Map<DexType, EquivalenceGroup<SyntheticMethodDefinition>> syntheticMethodGroups,
      Map<DexType, EquivalenceGroup<SyntheticProgramClassDefinition>> syntheticClassGroups,
      Builder lensBuilder,
      BiConsumer<DexProgramClass, SyntheticProgramClassReference> addFinalSyntheticClass,
      BiConsumer<DexProgramClass, SyntheticMethodReference> addFinalSyntheticMethod,
      Set<DexType> derivedMainDexSynthetics) {
    DexApplication application = appView.appInfo().app();
    MainDexInfo mainDexInfo = appView.appInfo().getMainDexInfo();
    Set<DexProgramClass> pruned = Sets.newIdentityHashSet();

    TreeFixerBase treeFixer =
        new TreeFixerBase(appView) {
          @Override
          public DexType mapClassType(DexType type) {
            return lensBuilder.typeMap.getOrDefault(type, type);
          }

          @Override
          public void recordFieldChange(DexField from, DexField to) {
            lensBuilder.move(from, to);
          }

          @Override
          public void recordMethodChange(DexMethod from, DexMethod to) {
            lensBuilder.move(from, to);
          }

          @Override
          public void recordClassChange(DexType from, DexType to) {
            lensBuilder.move(from, to);
          }
        };

    List<DexProgramClass> deduplicatedClasses = new ArrayList<>();
    syntheticMethodGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          SyntheticMethodDefinition representative = syntheticGroup.getRepresentative();
          SynthesizingContext context = representative.getContext();
          context.registerPrefixRewriting(syntheticType, appView);
          addSyntheticMarker(representative.getKind(), representative.getHolder(), appView);
          if (syntheticGroup.isDerivedFromMainDexList(mainDexInfo)) {
            derivedMainDexSynthetics.add(syntheticType);
          }
          syntheticGroup.forEachNonRepresentativeMember(
              member -> {
                pruned.add(member.getHolder());
                deduplicatedClasses.add(member.getHolder());
              });
        });

    syntheticClassGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          SyntheticProgramClassDefinition representative = syntheticGroup.getRepresentative();
          SynthesizingContext context = representative.getContext();
          context.registerPrefixRewriting(syntheticType, appView);
          addSyntheticMarker(representative.getKind(), representative.getHolder(), appView);
          if (syntheticGroup.isDerivedFromMainDexList(mainDexInfo)) {
            derivedMainDexSynthetics.add(syntheticType);
          }
          syntheticGroup.forEachNonRepresentativeMember(
              member -> {
                pruned.add(member.getHolder());
                deduplicatedClasses.add(member.getHolder());
              });
        });

    // Only create a new application if anything changed.
    if (lensBuilder.isEmpty()) {
      assert deduplicatedClasses.isEmpty();
      assert pruned.isEmpty();
    } else {
      if (!pruned.isEmpty()) {
        List<DexProgramClass> newProgramClasses = new ArrayList<>();
        for (DexProgramClass clazz : application.classes()) {
          if (!pruned.contains(clazz)) {
            newProgramClasses.add(clazz);
          }
        }
        assert newProgramClasses.size() < application.classes().size();
        application = application.builder().replaceProgramClasses(newProgramClasses).build();
      }

      // Assert that the non-representatives have been removed from the app.
      assert verifyNonRepresentativesRemovedFromApplication(application, syntheticClassGroups);
      assert verifyNonRepresentativesRemovedFromApplication(application, syntheticMethodGroups);

      DexApplication.Builder<?> builder = application.builder();
      treeFixer.fixupClasses(deduplicatedClasses);
      builder.replaceProgramClasses(treeFixer.fixupClasses(application.classes()));
      application = builder.build();
    }

    DexString syntheticSourceFileName =
        appView.enableWholeProgramOptimizations()
            ? appView.dexItemFactory().createString("R8$$SyntheticClass")
            : appView.dexItemFactory().createString("D8$$SyntheticClass");

    // Add the synthesized from after repackaging which changed class definitions.
    final DexApplication appForLookup = application;
    syntheticClassGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          DexProgramClass externalSyntheticClass = appForLookup.programDefinitionFor(syntheticType);
          assert externalSyntheticClass != null
              : "Expected definition for " + syntheticType.getTypeName();
          ensureSourceFile(externalSyntheticClass, syntheticSourceFileName);
          SyntheticProgramClassDefinition representative = syntheticGroup.getRepresentative();
          addFinalSyntheticClass.accept(
              externalSyntheticClass,
              new SyntheticProgramClassReference(
                  representative.getKind(),
                  representative.getContext(),
                  externalSyntheticClass.type));
        });
    syntheticMethodGroups.forEach(
        (syntheticType, syntheticGroup) -> {
          DexProgramClass externalSyntheticClass = appForLookup.programDefinitionFor(syntheticType);
          ensureSourceFile(externalSyntheticClass, syntheticSourceFileName);
          SyntheticMethodDefinition representative = syntheticGroup.getRepresentative();
          assert externalSyntheticClass.getMethodCollection().size() == 1;
          assert externalSyntheticClass.getMethodCollection().hasDirectMethods();
          DexEncodedMethod syntheticMethodDefinition =
              externalSyntheticClass.getMethodCollection().getDirectMethod(alwaysTrue());
          addFinalSyntheticMethod.accept(
              externalSyntheticClass,
              new SyntheticMethodReference(
                  representative.getKind(),
                  representative.getContext(),
                  syntheticMethodDefinition.getReference()));
        });

    Iterables.<EquivalenceGroup<? extends SyntheticDefinition<?, ?, DexProgramClass>>>concat(
            syntheticClassGroups.values(), syntheticMethodGroups.values())
        .forEach(
            syntheticGroup ->
                syntheticGroup
                    .getRepresentative()
                    .getHolder()
                    .forEachProgramMember(
                        member -> {
                          if (member.isProgramField()) {
                            DexField field = member.asProgramField().getReference();
                            DexField rewrittenField = treeFixer.fixupFieldReference(field);
                            lensBuilder.setRepresentative(rewrittenField, field);
                          } else {
                            DexMethod method = member.asProgramMethod().getReference();
                            DexMethod rewrittenMethod = treeFixer.fixupMethodReference(method);
                            lensBuilder.setRepresentative(rewrittenMethod, method);
                          }
                        }));

    for (DexType key : syntheticMethodGroups.keySet()) {
      assert application.definitionFor(key) != null;
    }

    for (DexType key : syntheticClassGroups.keySet()) {
      assert application.definitionFor(key) != null;
    }

    return application;
  }

  private static <T extends SyntheticDefinition<?, T, ?>>
      boolean verifyNonRepresentativesRemovedFromApplication(
          DexApplication application, Map<DexType, EquivalenceGroup<T>> syntheticGroups) {
    for (EquivalenceGroup<?> syntheticGroup : syntheticGroups.values()) {
      syntheticGroup.forEachNonRepresentativeMember(
          member -> {
            assert application.definitionFor(member.getHolder().getType()) == null;
          });
    }
    return true;
  }

  private static void addSyntheticMarker(
      SyntheticKind kind,
      DexProgramClass externalSyntheticClass,
      AppView<?> appView) {
    if (shouldAnnotateSynthetics(appView.options())) {
      SyntheticMarker.addMarkerToClass(externalSyntheticClass, kind, appView.options());
    }
  }

  private static boolean shouldAnnotateSynthetics(InternalOptions options) {
    // Only intermediate builds have annotated synthetics to allow later sharing.
    // Also, CF builds are marked in the writer using an attribute.
    return options.intermediate && options.isGeneratingDex();
  }

  private <T extends SyntheticDefinition<?, T, ?>>
      Map<DexType, EquivalenceGroup<T>> computeActualEquivalences(
          Collection<List<T>> potentialEquivalences,
          Map<String, NumberGenerator> generators,
          AppView<?> appView,
          boolean intermediate,
          ClassToFeatureSplitMap classToFeatureSplitMap,
          Builder lensBuilder) {
    Map<String, List<EquivalenceGroup<T>>> groupsPerPrefix = new HashMap<>();
    Map<DexType, EquivalenceGroup<T>> equivalences = new IdentityHashMap<>();
    potentialEquivalences.forEach(
        members -> {
          List<EquivalenceGroup<T>> groups =
              groupEquivalent(appView, members, intermediate, classToFeatureSplitMap);
          for (EquivalenceGroup<T> group : groups) {
            // If the group already has a representative, then this representative is pinned.
            // Otherwise, we select a deterministic representative.
            if (group.hasRepresentative()) {
              EquivalenceGroup<T> previous =
                  equivalences.put(group.getRepresentative().getHolder().getType(), group);
              assert previous == null;
            } else {
              group.selectDeterministicRepresentative();
              groupsPerPrefix
                  .computeIfAbsent(
                      group.getRepresentative().getPrefixForExternalSyntheticType(),
                      k -> new ArrayList<>())
                  .add(group);
            }
          }
        });
    groupsPerPrefix.forEach(
        (externalSyntheticTypePrefix, groups) -> {
          Comparator<EquivalenceGroup<T>> comparator = this::compareForFinalGroupSorting;
          ListUtils.destructiveSort(groups, comparator);
          for (int i = 0; i < groups.size(); i++) {
            EquivalenceGroup<T> group = groups.get(i);
            assert group
                .getRepresentative()
                .getPrefixForExternalSyntheticType()
                .equals(externalSyntheticTypePrefix);
            // Two equivalence groups in same context type must be distinct otherwise the assignment
            // of the synthetic name will be non-deterministic between the two.
            assert i == 0 || checkGroupsAreDistinct(groups.get(i - 1), group, comparator);
            SyntheticKind kind = group.getRepresentative().getKind();
            DexType representativeType =
                intermediate
                        && synthetics.isSyntheticInput(
                            group.getRepresentative().getHolder().asProgramClass())
                    ? group.getRepresentative().getHolder().getType()
                    : createExternalType(
                        kind,
                        externalSyntheticTypePrefix,
                        generators,
                        appView,
                        candidateType ->
                            equivalences.containsKey(candidateType)
                                || appView
                                    .horizontallyMergedClasses()
                                    .hasBeenMergedIntoDifferentType(candidateType));
            equivalences.put(representativeType, group);
          }
        });
    equivalences.forEach(
        (representativeType, group) ->
            group.forEach(
                member -> lensBuilder.move(member.getHolder().getType(), representativeType)));
    return equivalences;
  }

  private <T extends SyntheticDefinition<?, T, ?>> int compareForFinalGroupSorting(
      EquivalenceGroup<T> a, EquivalenceGroup<T> b) {
    // Sort the equivalence groups based on the representative types. The representatives are
    // deterministically chosen and the internal synthetics deterministically named so using
    // the internal type as the order is deterministic.
    return a.getRepresentative()
        .getHolder()
        .getType()
        .compareTo(b.getRepresentative().getHolder().getType());
  }

  private static <T extends SyntheticDefinition<?, T, ?>> List<EquivalenceGroup<T>> groupEquivalent(
      AppView<?> appView,
      List<T> potentialEquivalence,
      boolean intermediate,
      ClassToFeatureSplitMap classToFeatureSplitMap) {
    List<EquivalenceGroup<T>> groups = new ArrayList<>();
    // Each other member is in a shared group if it is actually equivalent to the first member.
    for (T synthetic : potentialEquivalence) {
      boolean mustBeRepresentative = isPinned(appView, synthetic);
      EquivalenceGroup<T> equivalenceGroup = null;
      for (EquivalenceGroup<T> group : groups) {
        if (synthetic.isEquivalentTo(
            group.hasRepresentative()
                ? group.getRepresentative()
                : group.getFirstNonRepresentativeMember(),
            intermediate,
            appView.graphLens(),
            classToFeatureSplitMap)) {
          if (mustBeRepresentative && group.hasRepresentative()) {
            // Check if the current synthetic is smaller than the group's representative. If so,
            // then replace the representative, to ensure deterministic groups, and create a new
            // singleton group containing the old representative. Otherwise, just add a singleton
            // group containing the new synthetic.
            T representative = group.getRepresentative();
            if (representative
                    .toReference()
                    .getReference()
                    .compareTo(synthetic.toReference().getReference())
                > 0) {
              group.replaceAndRemoveRepresentative(synthetic);
              synthetic = representative;
            }
          } else {
            equivalenceGroup = group;
          }
          break;
        }
      }
      if (equivalenceGroup != null) {
        equivalenceGroup.add(synthetic, mustBeRepresentative);
      } else {
        groups.add(new EquivalenceGroup<>(synthetic, mustBeRepresentative));
      }
    }
    return groups;
  }

  /**
   * In R8, keep rules may apply to synthetics from the input, if the input has been compiled using
   * intermediate mode.
   */
  private static <D extends SyntheticDefinition<?, D, ?>> boolean isPinned(
      AppView<?> appView, D definition) {
    if (!appView.enableWholeProgramOptimizations()) {
      return false;
    }
    if (!definition.getHolder().isProgramClass()) {
      return true;
    }
    DexProgramClass holder = definition.getHolder().asProgramClass();
    // TODO(b/192924387): Change such that the keep info for internal synthetics is always bottom.
    if (!appView.getSyntheticItems().isSubjectToKeepRules(holder)) {
      return false;
    }
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    InternalOptions options = appView.options();
    if (keepInfo.getClassInfo(holder).isPinned(options)) {
      return true;
    }
    for (DexEncodedMember<?, ?> member : holder.members()) {
      if (keepInfo.getMemberInfo(member, holder).isPinned(options)) {
        return true;
      }
    }
    return false;
  }

  private static <T extends SyntheticDefinition<?, T, ?>> boolean checkGroupsAreDistinct(
      EquivalenceGroup<T> g1, EquivalenceGroup<T> g2, Comparator<EquivalenceGroup<T>> comparator) {
    int smaller = comparator.compare(g1, g2);
    assert smaller < 0;
    int bigger = comparator.compare(g2, g1);
    assert bigger > 0;
    return true;
  }

  private DexType createExternalType(
      SyntheticKind kind,
      String externalSyntheticTypePrefix,
      Map<String, NumberGenerator> generators,
      AppView<?> appView,
      Predicate<DexType> reserved) {
    DexItemFactory factory = appView.dexItemFactory();
    if (kind.isFixedSuffixSynthetic()) {
      return SyntheticNaming.createExternalType(kind, externalSyntheticTypePrefix, "", factory);
    }
    NumberGenerator generator =
        generators.computeIfAbsent(externalSyntheticTypePrefix, k -> new NumberGenerator());
    DexType externalType;
    do {
      externalType =
          SyntheticNaming.createExternalType(
              kind, externalSyntheticTypePrefix, Integer.toString(generator.next()), factory);
      // If the generated external type matches an external synthetic from the input, which is kept,
      // then continue.
      if (reserved.test(externalType)) {
        externalType = null;
        continue;
      }
      DexClass clazz = appView.appInfo().definitionForWithoutExistenceAssert(externalType);
      if (clazz != null && isNotSyntheticType(clazz.type)) {
        assert options.testing.allowConflictingSyntheticTypes
            : "Unexpected creation of an existing external synthetic type: " + clazz;
        externalType = null;
      }
    } while (externalType == null);
    return externalType;
  }

  private static <T extends SyntheticDefinition<?, T, ?>>
      Collection<List<T>> computePotentialEquivalences(
          Map<DexType, T> definitions,
          boolean intermediate,
          DexItemFactory factory,
          GraphLens graphLens,
          ClassToFeatureSplitMap classToFeatureSplitMap,
          SyntheticItems syntheticItems) {
    if (definitions.isEmpty()) {
      return Collections.emptyList();
    }
    // Map all synthetic types to the java 'void' type. This is not an actual valid type, so it
    // cannot collide with any valid java type providing a good hashing key for the synthetics.
    Set<DexType> syntheticTypes;
    if (graphLens.isIdentityLens()) {
      syntheticTypes = definitions.keySet();
    } else {
      // If the synthetics are renamed include their original names in the equivalence too.
      syntheticTypes = SetUtils.newIdentityHashSet(definitions.size() * 2);
      definitions
          .keySet()
          .forEach(
              t -> {
                syntheticTypes.add(t);
                syntheticTypes.add(graphLens.getOriginalType(t));
              });
    }
    RepresentativeMap map = t -> syntheticTypes.contains(t) ? factory.voidType : t;
    Map<HashCode, List<T>> equivalences = new HashMap<>(definitions.size());
    for (T definition : definitions.values()) {
      HashCode hash =
          definition.computeHash(map, intermediate, classToFeatureSplitMap, syntheticItems);
      equivalences.computeIfAbsent(hash, k -> new ArrayList<>()).add(definition);
    }
    return equivalences.values();
  }

  private <R extends SyntheticReference<R, D, ?>, D extends SyntheticDefinition<R, D, ?>>
      Map<DexType, D> lookupDefinitions(
          AppView<?> appView, ImmutableMap<DexType, List<R>> references) {
    Map<DexType, D> definitions = new IdentityHashMap<>(references.size());
    for (R reference : IterableUtils.flatten(references.values())) {
      D definition = reference.lookupDefinition(appView::definitionFor);
      if (definition == null) {
        // We expect pruned definitions to have been removed.
        assert false;
        continue;
      }
      if (definition.isValid()) {
        definitions.put(reference.getHolder(), definition);
      } else {
        // Failing this check indicates that an optimization has modified the synthetic in a
        // disruptive way.
        assert false;
      }
    }
    return definitions;
  }

  private static class EquivalenceGroup<T extends SyntheticDefinition<?, T, ?>> {

    // The members of the equivalence group, *excluding* the representative.
    private List<T> members = new ArrayList<>();
    private T representative;

    EquivalenceGroup(T member, boolean isRepresentative) {
      add(member, isRepresentative);
    }

    void add(T member, boolean isRepresentative) {
      if (isRepresentative) {
        assert !hasRepresentative();
        representative = member;
      } else {
        members.add(member);
      }
    }

    int compareToIncludingContext(
        EquivalenceGroup<T> other,
        GraphLens graphLens,
        ClassToFeatureSplitMap classToFeatureSplitMap) {
      return getRepresentative()
          .compareTo(other.getRepresentative(), true, graphLens, classToFeatureSplitMap);
    }

    public void forEach(Consumer<T> consumer) {
      consumer.accept(getRepresentative());
      members.forEach(consumer);
    }

    public void forEachNonRepresentativeMember(Consumer<T> consumer) {
      members.forEach(consumer);
    }

    T getFirstNonRepresentativeMember() {
      assert !members.isEmpty();
      return members.get(0);
    }

    T getRepresentative() {
      assert hasRepresentative();
      return representative;
    }

    boolean hasRepresentative() {
      return representative != null;
    }

    boolean isDerivedFromMainDexList(MainDexInfo mainDexInfo) {
      return getRepresentative().getContext().isDerivedFromMainDexList(mainDexInfo)
          || Iterables.any(
              members, member -> member.getContext().isDerivedFromMainDexList(mainDexInfo));
    }

    void replaceAndRemoveRepresentative(T representative) {
      assert hasRepresentative();
      this.representative = representative;
    }

    void selectDeterministicRepresentative() {
      // Pick a deterministic member as representative.
      assert !hasRepresentative();
      int representativeIndex = 0;
      for (int i = 1; i < members.size(); i++) {
        T next = members.get(i);
        T representative = members.get(representativeIndex);
        if (next.toReference().getReference().compareTo(representative.toReference().getReference())
            < 0) {
          representativeIndex = i;
        }
      }
      T representative = members.get(representativeIndex);
      members.set(representativeIndex, ListUtils.last(members));
      ListUtils.removeLast(members);
      setRepresentative(representative);
    }

    void setRepresentative(T representative) {
      assert !hasRepresentative();
      this.representative = representative;
    }

    @Override
    public String toString() {
      if (hasRepresentative()) {
        return "EquivalenceGroup{ size = "
            + (members.size() + 1)
            + ", repr = "
            + getRepresentative()
            + " }";
      }
      return "EquivalenceGroup{ size = " + members.size() + " }";
    }
  }
}

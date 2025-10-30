// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.features;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.partial.R8PartialSubCompilationConfiguration.R8PartialR8SubCompilationConfiguration;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class ClassToFeatureSplitMap {

  private final Map<DexType, FeatureSplit> classToFeatureSplitMap;
  private final Map<FeatureSplit, DexType> representativeStringsForFeatureSplit;

  private ClassToFeatureSplitMap(
      Map<DexType, FeatureSplit> classToFeatureSplitMap,
      Map<FeatureSplit, DexType> representativeStringsForFeatureSplit) {
    this.classToFeatureSplitMap = classToFeatureSplitMap;
    this.representativeStringsForFeatureSplit = representativeStringsForFeatureSplit;
  }

  public static ClassToFeatureSplitMap createEmptyClassToFeatureSplitMap() {
    return new ClassToFeatureSplitMap(new IdentityHashMap<>(), null);
  }

  public static ClassToFeatureSplitMap createInitialD8ClassToFeatureSplitMap(
      InternalOptions options) {
    return createInitialClassToFeatureSplitMap(options.getFeatureSplitConfiguration());
  }

  public static ClassToFeatureSplitMap createInitialR8ClassToFeatureSplitMap(
      InternalOptions options) {
    if (options.partialSubCompilationConfiguration != null) {
      R8PartialR8SubCompilationConfiguration subCompilationConfiguration =
          options.partialSubCompilationConfiguration.asR8();
      return subCompilationConfiguration.getClassToFeatureSplitMap();
    }
    return createInitialClassToFeatureSplitMap(options.getFeatureSplitConfiguration());
  }

  public static ClassToFeatureSplitMap createInitialClassToFeatureSplitMap(
      FeatureSplitConfiguration featureSplitConfiguration) {
    if (featureSplitConfiguration == null) {
      return createEmptyClassToFeatureSplitMap();
    }

    Map<DexType, FeatureSplit> classToFeatureSplitMap = new IdentityHashMap<>();
    Map<FeatureSplit, DexType> representativeStringsForFeatureSplit = new IdentityHashMap<>();
    for (FeatureSplit featureSplit : featureSplitConfiguration.getFeatureSplits()) {
      DexType representativeType = null;
      for (FeatureSplitProgramResourceProvider programResourceProvider :
          featureSplitConfiguration.getFeatureSplitProgramResourceProviders(featureSplit)) {
        for (DexType type : programResourceProvider.unsetTypes()) {
          classToFeatureSplitMap.put(type, featureSplit);
          if (representativeType == null || type.compareTo(representativeType) > 0) {
            representativeType = type;
          }
        }
      }
      if (representativeType != null) {
        representativeStringsForFeatureSplit.put(featureSplit, representativeType);
      }
    }
    return new ClassToFeatureSplitMap(classToFeatureSplitMap, representativeStringsForFeatureSplit);
  }

  public ClassToFeatureSplitMap commitSyntheticsForR8Partial(AppView<AppInfo> appView) {
    Map<DexType, FeatureSplit> newClassToFeatureSplitMap =
        new IdentityHashMap<>(classToFeatureSplitMap);
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    assert !syntheticItems.hasPendingSyntheticClasses();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (syntheticItems.isSynthetic(clazz)) {
        FeatureSplit featureSplit = getFeatureSplit(clazz, syntheticItems);
        if (!featureSplit.isBase()) {
          newClassToFeatureSplitMap.put(clazz.getType(), featureSplit);
        }
      }
    }
    return new ClassToFeatureSplitMap(
        newClassToFeatureSplitMap, representativeStringsForFeatureSplit);
  }

  public int compareFeatureSplits(FeatureSplit featureSplitA, FeatureSplit featureSplitB) {
    assert featureSplitA != null;
    assert featureSplitB != null;
    if (featureSplitA == featureSplitB) {
      return 0;
    }
    // Base bigger than any other feature
    if (featureSplitA.isBase()) {
      return 1;
    }
    if (featureSplitB.isBase()) {
      return -1;
    }
    return representativeStringsForFeatureSplit
        .get(featureSplitA)
        .compareTo(representativeStringsForFeatureSplit.get(featureSplitB));
  }

  public Map<FeatureSplit, Set<DexProgramClass>> getFeatureSplitClasses(
      Set<DexProgramClass> classes, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return getFeatureSplitClasses(classes, appView.getSyntheticItems());
  }

  public Map<FeatureSplit, Set<DexProgramClass>> getFeatureSplitClasses(
      Set<DexProgramClass> classes,
      SyntheticItems syntheticItems) {
    Map<FeatureSplit, Set<DexProgramClass>> result = new IdentityHashMap<>();
    for (DexProgramClass clazz : classes) {
      FeatureSplit featureSplit = getFeatureSplit(clazz, syntheticItems);
      if (featureSplit != null && !featureSplit.isBase()) {
        result.computeIfAbsent(featureSplit, ignore -> Sets.newIdentityHashSet()).add(clazz);
      }
    }
    return result;
  }

  public FeatureSplit getFeatureSplit(ProgramDefinition definition, AppView<?> appView) {
    return getFeatureSplit(definition, appView.getSyntheticItems());
  }

  public FeatureSplit getFeatureSplit(
      ProgramDefinition definition,
      SyntheticItems syntheticItems) {
    return getFeatureSplit(definition.getReference(), syntheticItems);
  }

  public FeatureSplit getFeatureSplit(
      DexReference reference, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return getFeatureSplit(reference, appView.getSyntheticItems());
  }

  public FeatureSplit getFeatureSplit(DexReference reference, SyntheticItems syntheticItems) {
    DexType type = reference.getContextType();
    if (syntheticItems == null) {
      // Called from AndroidApp.dumpProgramResources().
      return classToFeatureSplitMap.getOrDefault(type, FeatureSplit.BASE);
    }
    FeatureSplit feature;
    boolean isSynthetic = syntheticItems.isSyntheticClass(type);
    if (isSynthetic) {
      if (syntheticItems.isSyntheticOfKind(type, k -> k.ENUM_UNBOXING_SHARED_UTILITY_CLASS)) {
        return FeatureSplit.BASE;
      }
      feature = syntheticItems.getContextualFeatureSplitOrDefault(type, FeatureSplit.BASE);
      // Verify the synthetic is not in the class to feature split map or the synthetic has the same
      // feature split as its context.
      assert classToFeatureSplitMap.getOrDefault(type, feature) == feature;
    } else {
      feature = classToFeatureSplitMap.getOrDefault(type, FeatureSplit.BASE);
    }
    if (feature.isBase()) {
      return FeatureSplit.BASE;
    }
    return feature;
  }

  // Note, this predicate may be misleading as the map does not include synthetics.
  // In practice it should not be an issue as there should not be a way to have a feature shrink
  // to only contain synthetic content. At a minimum the entry points of the feature must remain.
  public boolean isEmpty() {
    return classToFeatureSplitMap.isEmpty();
  }

  public boolean isInBase(ProgramDefinition definition, AppView<?> appView) {
    return isInBase(definition, appView.getSyntheticItems());
  }

  public boolean isInBase(ProgramDefinition definition, SyntheticItems syntheticItems) {
    return getFeatureSplit(definition, syntheticItems).isBase();
  }

  public boolean isInBaseOrSameFeatureAs(
      ProgramDefinition clazz,
      ProgramDefinition context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isInBaseOrSameFeatureAs(
        clazz,
        context,
        appView.getSyntheticItems());
  }

  public boolean isInBaseOrSameFeatureAs(
      ProgramDefinition clazz,
      ProgramDefinition context,
      SyntheticItems syntheticItems) {
    return isInBaseOrSameFeatureAs(clazz.getContextType(), context, syntheticItems);
  }

  public boolean isInBaseOrSameFeatureAs(
      DexType clazz,
      ProgramDefinition context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isInBaseOrSameFeatureAs(
        clazz,
        context,
        appView.getSyntheticItems());
  }

  public boolean isInBaseOrSameFeatureAs(
      DexType clazz,
      ProgramDefinition context,
      SyntheticItems syntheticItems) {
    FeatureSplit split = getFeatureSplit(clazz, syntheticItems);
    return split.isBase() || split == getFeatureSplit(context, syntheticItems);
  }

  public boolean isInFeature(
      DexProgramClass clazz,
      SyntheticItems syntheticItems) {
    return !isInBase(clazz, syntheticItems);
  }

  public boolean isInSameFeature(
      ProgramDefinition definition, ProgramDefinition other, AppView<?> appView) {
    return isInSameFeature(definition, other, appView.getSyntheticItems());
  }

  public boolean isInSameFeature(
      ProgramDefinition definition, ProgramDefinition other, SyntheticItems syntheticItems) {
    return getFeatureSplit(definition, syntheticItems) == getFeatureSplit(other, syntheticItems);
  }

  public ClassToFeatureSplitMap rewrittenWithLens(GraphLens lens, Timing timing) {
    return timing.time("Rewrite ClassToFeatureSplitMap", () -> rewrittenWithLens(lens));
  }

  private ClassToFeatureSplitMap rewrittenWithLens(GraphLens lens) {
    Map<DexType, FeatureSplit> rewrittenClassToFeatureSplitMap = new IdentityHashMap<>();
    classToFeatureSplitMap.forEach(
        (type, featureSplit) -> {
          DexType rewrittenType = lens.lookupType(type, GraphLens.getIdentityLens());
          if (rewrittenType.isIntType()) {
            // The type was removed by enum unboxing.
            return;
          }
          FeatureSplit existing = rewrittenClassToFeatureSplitMap.put(rewrittenType, featureSplit);
          // If we map two classes to the same class then they must be from the same feature split.
          assert existing == null || existing == featureSplit;
        });
    return new ClassToFeatureSplitMap(
        rewrittenClassToFeatureSplitMap, representativeStringsForFeatureSplit);
  }

  public ClassToFeatureSplitMap withoutPrunedItems(PrunedItems prunedItems) {
    Map<DexType, FeatureSplit> rewrittenClassToFeatureSplitMap = new IdentityHashMap<>();
    classToFeatureSplitMap.forEach(
        (type, featureSplit) -> {
          if (!prunedItems.getRemovedClasses().contains(type)) {
            rewrittenClassToFeatureSplitMap.put(type, featureSplit);
          }
        });
    return new ClassToFeatureSplitMap(
        rewrittenClassToFeatureSplitMap, representativeStringsForFeatureSplit);
  }

  // Static helpers to avoid verbose predicates.

  private static ClassToFeatureSplitMap getMap(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return appView.appInfo().getClassToFeatureSplitMap();
  }

  public static boolean isInFeature(
      DexProgramClass clazz, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return getMap(appView).isInFeature(clazz, appView.getSyntheticItems());
  }
}

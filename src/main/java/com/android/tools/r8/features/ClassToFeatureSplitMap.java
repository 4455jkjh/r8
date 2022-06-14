// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.features;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
import com.android.tools.r8.experimental.startup.StartupOrder;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class ClassToFeatureSplitMap {

  private final Map<DexType, FeatureSplit> classToFeatureSplitMap;
  private final Map<FeatureSplit, String> representativeStringsForFeatureSplit;

  private ClassToFeatureSplitMap(
      Map<DexType, FeatureSplit> classToFeatureSplitMap,
      Map<FeatureSplit, String> representativeStringsForFeatureSplit) {
    this.classToFeatureSplitMap = classToFeatureSplitMap;
    this.representativeStringsForFeatureSplit = representativeStringsForFeatureSplit;
  }

  public static ClassToFeatureSplitMap createEmptyClassToFeatureSplitMap() {
    return new ClassToFeatureSplitMap(new IdentityHashMap<>(), null);
  }

  public static ClassToFeatureSplitMap createInitialClassToFeatureSplitMap(
      InternalOptions options) {
    return createInitialClassToFeatureSplitMap(
        options.dexItemFactory(),
        options.featureSplitConfiguration,
        options.reporter);
  }

  public static ClassToFeatureSplitMap createInitialClassToFeatureSplitMap(
      DexItemFactory dexItemFactory,
      FeatureSplitConfiguration featureSplitConfiguration,
      Reporter reporter) {
    if (featureSplitConfiguration == null) {
      return createEmptyClassToFeatureSplitMap();
    }

    Map<DexType, FeatureSplit> classToFeatureSplitMap = new IdentityHashMap<>();
    Map<FeatureSplit, String> representativeStringsForFeatureSplit = new IdentityHashMap<>();
    for (FeatureSplit featureSplit : featureSplitConfiguration.getFeatureSplits()) {
      String representativeType = null;
      for (ProgramResourceProvider programResourceProvider :
          featureSplit.getProgramResourceProviders()) {
        try {
          for (ProgramResource programResource : programResourceProvider.getProgramResources()) {
            for (String classDescriptor : programResource.getClassDescriptors()) {
              DexType type = dexItemFactory.createType(classDescriptor);
              classToFeatureSplitMap.put(type, featureSplit);
              if (representativeType == null || classDescriptor.compareTo(representativeType) > 0) {
                representativeType = classDescriptor;
              }
            }
          }
        } catch (ResourceException e) {
          throw reporter.fatalError(e.getMessage());
        }
      }
      if (representativeType != null) {
        representativeStringsForFeatureSplit.put(featureSplit, representativeType);
      }
    }
    return new ClassToFeatureSplitMap(classToFeatureSplitMap, representativeStringsForFeatureSplit);
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
    return getFeatureSplitClasses(
        classes, appView.appInfo().getStartupOrder(), appView.getSyntheticItems());
  }

  public Map<FeatureSplit, Set<DexProgramClass>> getFeatureSplitClasses(
      Set<DexProgramClass> classes, StartupOrder startupOrder, SyntheticItems syntheticItems) {
    Map<FeatureSplit, Set<DexProgramClass>> result = new IdentityHashMap<>();
    for (DexProgramClass clazz : classes) {
      FeatureSplit featureSplit = getFeatureSplit(clazz, startupOrder, syntheticItems);
      if (featureSplit != null && !featureSplit.isBase()) {
        result.computeIfAbsent(featureSplit, ignore -> Sets.newIdentityHashSet()).add(clazz);
      }
    }
    return result;
  }

  public FeatureSplit getFeatureSplit(
      ProgramDefinition definition, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return getFeatureSplit(
        definition, appView.appInfo().getStartupOrder(), appView.getSyntheticItems());
  }

  public FeatureSplit getFeatureSplit(
      ProgramDefinition definition, StartupOrder startupOrder, SyntheticItems syntheticItems) {
    return getFeatureSplit(definition.getContextType(), startupOrder, syntheticItems);
  }

  public FeatureSplit getFeatureSplit(
      DexType type, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return getFeatureSplit(type, appView.appInfo().getStartupOrder(), appView.getSyntheticItems());
  }

  public FeatureSplit getFeatureSplit(
      DexType type, StartupOrder startupOrder, SyntheticItems syntheticItems) {
    if (syntheticItems == null) {
      // Called from AndroidApp.dumpProgramResources().
      assert startupOrder.isEmpty();
      return classToFeatureSplitMap.getOrDefault(type, FeatureSplit.BASE);
    }
    FeatureSplit feature;
    boolean isSynthetic = syntheticItems.isSyntheticClass(type);
    if (isSynthetic) {
      assert !classToFeatureSplitMap.containsKey(type);
      if (syntheticItems.isSyntheticOfKind(type, k -> k.ENUM_UNBOXING_SHARED_UTILITY_CLASS)) {
        // Use the startup base if there is one, such that we don't merge non-startup classes with
        // the shared utility class in case it is used during startup. The use of base startup
        // allows for merging startup classes with the shared utility class, however, which could be
        // bad for startup if the shared utility class is not used during startup.
        return startupOrder.isEmpty() ? FeatureSplit.BASE : FeatureSplit.BASE_STARTUP;
      }
      feature = syntheticItems.getContextualFeatureSplitOrDefault(type, FeatureSplit.BASE);
    } else {
      feature = classToFeatureSplitMap.getOrDefault(type, FeatureSplit.BASE);
    }
    if (feature.isBase()) {
      return startupOrder.contains(type, syntheticItems)
          ? FeatureSplit.BASE_STARTUP
          : FeatureSplit.BASE;
    }
    return feature;
  }

  // Note, this predicate may be misleading as the map does not include synthetics.
  // In practice it should not be an issue as there should not be a way to have a feature shrink
  // to only contain synthetic content. At a minimum the entry points of the feature must remain.
  public boolean isEmpty() {
    return classToFeatureSplitMap.isEmpty();
  }

  public boolean isInBase(
      DexProgramClass clazz, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isInBase(clazz, appView.appInfo().getStartupOrder(), appView.getSyntheticItems());
  }

  public boolean isInBase(
      DexProgramClass clazz, StartupOrder startupOrder, SyntheticItems syntheticItems) {
    return getFeatureSplit(clazz, startupOrder, syntheticItems).isBase();
  }

  public boolean isInBaseOrSameFeatureAs(
      DexProgramClass clazz,
      ProgramDefinition context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isInBaseOrSameFeatureAs(
        clazz, context, appView.appInfo().getStartupOrder(), appView.getSyntheticItems());
  }

  public boolean isInBaseOrSameFeatureAs(
      DexProgramClass clazz,
      ProgramDefinition context,
      StartupOrder startupOrder,
      SyntheticItems syntheticItems) {
    return isInBaseOrSameFeatureAs(clazz.getContextType(), context, startupOrder, syntheticItems);
  }

  public boolean isInBaseOrSameFeatureAs(
      DexType clazz,
      ProgramDefinition context,
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isInBaseOrSameFeatureAs(
        clazz, context, appView.appInfo().getStartupOrder(), appView.getSyntheticItems());
  }

  public boolean isInBaseOrSameFeatureAs(
      DexType clazz,
      ProgramDefinition context,
      StartupOrder startupOrder,
      SyntheticItems syntheticItems) {
    FeatureSplit split = getFeatureSplit(clazz, startupOrder, syntheticItems);
    return split.isBase() || split == getFeatureSplit(context, startupOrder, syntheticItems);
  }

  public boolean isInFeature(
      DexProgramClass clazz, StartupOrder startupOrder, SyntheticItems syntheticItems) {
    return !isInBase(clazz, startupOrder, syntheticItems);
  }

  public boolean isInSameFeatureOrBothInSameBase(
      ProgramMethod a, ProgramMethod b, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isInSameFeatureOrBothInSameBase(
        a, b, appView.appInfo().getStartupOrder(), appView.getSyntheticItems());
  }

  public boolean isInSameFeatureOrBothInSameBase(
      ProgramMethod a, ProgramMethod b, StartupOrder startupOrder, SyntheticItems syntheticItems) {
    return isInSameFeatureOrBothInSameBase(
        a.getHolder(), b.getHolder(), startupOrder, syntheticItems);
  }

  public boolean isInSameFeatureOrBothInSameBase(
      DexProgramClass a, DexProgramClass b, AppView<? extends AppInfoWithClassHierarchy> appView) {
    return isInSameFeatureOrBothInSameBase(
        a, b, appView.appInfo().getStartupOrder(), appView.getSyntheticItems());
  }

  public boolean isInSameFeatureOrBothInSameBase(
      DexProgramClass a,
      DexProgramClass b,
      StartupOrder startupOrder,
      SyntheticItems syntheticItems) {
    return getFeatureSplit(a, startupOrder, syntheticItems)
        == getFeatureSplit(b, startupOrder, syntheticItems);
  }

  public ClassToFeatureSplitMap rewrittenWithLens(GraphLens lens) {
    Map<DexType, FeatureSplit> rewrittenClassToFeatureSplitMap = new IdentityHashMap<>();
    classToFeatureSplitMap.forEach(
        (type, featureSplit) -> {
          DexType rewrittenType = lens.lookupType(type);
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

  private static ClassToFeatureSplitMap getMap(AppView<AppInfoWithLiveness> appView) {
    return appView.appInfo().getClassToFeatureSplitMap();
  }

  public static boolean isInFeature(DexProgramClass clazz, AppView<AppInfoWithLiveness> appView) {
    return getMap(appView)
        .isInFeature(clazz, appView.appInfo().getStartupOrder(), appView.getSyntheticItems());
  }
}

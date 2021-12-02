// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.features;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResourceProvider;
import com.android.tools.r8.ResourceException;
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
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class ClassToFeatureSplitMap {

  private final Map<DexType, FeatureSplit> classToFeatureSplitMap = new IdentityHashMap<>();
  private final Map<FeatureSplit, String> representativeStringsForFeatureSplit;

  private ClassToFeatureSplitMap() {
    this(new HashMap<>());
  }

  private ClassToFeatureSplitMap(Map<FeatureSplit, String> representativeStringsForFeatureSplit) {
    this.representativeStringsForFeatureSplit = representativeStringsForFeatureSplit;
  }

  public static ClassToFeatureSplitMap createEmptyClassToFeatureSplitMap() {
    return new ClassToFeatureSplitMap(null);
  }

  public static ClassToFeatureSplitMap createInitialClassToFeatureSplitMap(
      InternalOptions options) {
    return createInitialClassToFeatureSplitMap(
        options.dexItemFactory(), options.featureSplitConfiguration, options.reporter);
  }

  public static ClassToFeatureSplitMap createInitialClassToFeatureSplitMap(
      DexItemFactory dexItemFactory,
      FeatureSplitConfiguration featureSplitConfiguration,
      Reporter reporter) {
    ClassToFeatureSplitMap result = new ClassToFeatureSplitMap();
    if (featureSplitConfiguration == null) {
      return result;
    }

    for (FeatureSplit featureSplit : featureSplitConfiguration.getFeatureSplits()) {
      String representativeType = null;
      for (ProgramResourceProvider programResourceProvider :
          featureSplit.getProgramResourceProviders()) {
        try {
          for (ProgramResource programResource : programResourceProvider.getProgramResources()) {
            for (String classDescriptor : programResource.getClassDescriptors()) {
              DexType type = dexItemFactory.createType(classDescriptor);
              result.classToFeatureSplitMap.put(type, featureSplit);
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
        result.representativeStringsForFeatureSplit.put(featureSplit, representativeType);
      }
    }
    return result;
  }

  public int compareFeatureSplitsForDexTypes(DexType a, DexType b, SyntheticItems syntheticItems) {
    FeatureSplit featureSplitA = getFeatureSplit(a, syntheticItems);
    FeatureSplit featureSplitB = getFeatureSplit(b, syntheticItems);
    return compareFeatureSplits(featureSplitA, featureSplitB);
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
      Set<DexProgramClass> classes, SyntheticItems syntheticItems) {
    Map<FeatureSplit, Set<DexProgramClass>> result = new IdentityHashMap<>();
    for (DexProgramClass clazz : classes) {
      FeatureSplit featureSplit = getFeatureSplit(clazz, syntheticItems);
      if (featureSplit != null && !featureSplit.isBase()) {
        result.computeIfAbsent(featureSplit, ignore -> Sets.newIdentityHashSet()).add(clazz);
      }
    }
    return result;
  }

  public FeatureSplit getFeatureSplit(ProgramDefinition clazz, SyntheticItems syntheticItems) {
    return getFeatureSplit(clazz.getContextType(), syntheticItems);
  }

  public FeatureSplit getFeatureSplit(DexType type, SyntheticItems syntheticItems) {
    FeatureSplit feature = classToFeatureSplitMap.get(type);
    if (feature != null) {
      return feature;
    }
    feature = syntheticItems.getContextualFeatureSplit(type);
    if (feature != null) {
      return feature;
    }
    return FeatureSplit.BASE;
  }

  // Note, this predicate may be misleading as the map does not include synthetics.
  // In practice it should not be an issue as there should not be a way to have a feature shrink
  // to only contain synthetic content. At a minimum the entry points of the feature must remain.
  public boolean isEmpty() {
    return classToFeatureSplitMap.isEmpty();
  }

  public boolean isInBase(DexProgramClass clazz, SyntheticItems syntheticItems) {
    return getFeatureSplit(clazz, syntheticItems).isBase();
  }

  public boolean isInBaseOrSameFeatureAs(
      DexProgramClass clazz, ProgramDefinition context, SyntheticItems syntheticItems) {
    return isInBaseOrSameFeatureAs(clazz.getContextType(), context, syntheticItems);
  }

  public boolean isInBaseOrSameFeatureAs(
      DexType clazz, ProgramDefinition context, SyntheticItems syntheticItems) {
    FeatureSplit split = getFeatureSplit(clazz, syntheticItems);
    return split.isBase() || split == getFeatureSplit(context, syntheticItems);
  }

  public boolean isInFeature(DexProgramClass clazz, SyntheticItems syntheticItems) {
    return !isInBase(clazz, syntheticItems);
  }

  public boolean isInSameFeatureOrBothInBase(
      ProgramMethod a, ProgramMethod b, SyntheticItems syntheticItems) {
    return isInSameFeatureOrBothInBase(a.getHolder(), b.getHolder(), syntheticItems);
  }

  public boolean isInSameFeatureOrBothInBase(
      DexProgramClass a, DexProgramClass b, SyntheticItems syntheticItems) {
    return getFeatureSplit(a, syntheticItems) == getFeatureSplit(b, syntheticItems);
  }

  public boolean isInSameFeatureOrBothInBase(DexType a, DexType b, SyntheticItems syntheticItems) {
    return getFeatureSplit(a, syntheticItems) == getFeatureSplit(b, syntheticItems);
  }

  public ClassToFeatureSplitMap rewrittenWithLens(GraphLens lens) {
    ClassToFeatureSplitMap rewrittenClassToFeatureSplitMap =
        new ClassToFeatureSplitMap(representativeStringsForFeatureSplit);
    classToFeatureSplitMap.forEach(
        (type, featureSplit) -> {
          DexType rewrittenType = lens.lookupType(type);
          if (rewrittenType.isIntType()) {
            // The type was removed by enum unboxing.
            return;
          }
          FeatureSplit existing =
              rewrittenClassToFeatureSplitMap.classToFeatureSplitMap.put(
                  rewrittenType, featureSplit);
          // If we map two classes to the same class then they must be from the same feature split.
          assert existing == null || existing == featureSplit;
        });
    return rewrittenClassToFeatureSplitMap;
  }

  public ClassToFeatureSplitMap withoutPrunedItems(PrunedItems prunedItems) {
    ClassToFeatureSplitMap classToFeatureSplitMapAfterPruning =
        new ClassToFeatureSplitMap(representativeStringsForFeatureSplit);
    classToFeatureSplitMap.forEach(
        (type, featureSplit) -> {
          if (!prunedItems.getRemovedClasses().contains(type)) {
            classToFeatureSplitMapAfterPruning.classToFeatureSplitMap.put(type, featureSplit);
          }
        });
    return classToFeatureSplitMapAfterPruning;
  }

  // Static helpers to avoid verbose predicates.

  private static ClassToFeatureSplitMap getMap(AppView<AppInfoWithLiveness> appView) {
    return appView.appInfo().getClassToFeatureSplitMap();
  }

  public static boolean isInFeature(DexProgramClass clazz, AppView<AppInfoWithLiveness> appView) {
    return getMap(appView).isInFeature(clazz, appView.getSyntheticItems());
  }
}

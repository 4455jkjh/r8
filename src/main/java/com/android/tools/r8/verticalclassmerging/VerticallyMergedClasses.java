// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClasses;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.collections.EmptyBidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneRepresentativeMap;
import java.util.Collection;
import java.util.Set;
import java.util.function.BiConsumer;

public class VerticallyMergedClasses implements MergedClasses {

  private final BidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses;
  private final BidirectionalManyToOneMap<DexType, DexType> mergedInterfaces;

  public VerticallyMergedClasses(
      BidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses,
      BidirectionalManyToOneMap<DexType, DexType> mergedInterfaces) {
    this.mergedClasses = mergedClasses;
    this.mergedInterfaces = mergedInterfaces;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static VerticallyMergedClasses empty() {
    EmptyBidirectionalOneToOneMap<DexType, DexType> emptyMap =
        new EmptyBidirectionalOneToOneMap<>();
    return new VerticallyMergedClasses(emptyMap, emptyMap);
  }

  @Override
  public void forEachMergeGroup(BiConsumer<Set<DexType>, DexType> consumer) {
    mergedClasses.forEachManyToOneMapping(consumer);
  }

  public BidirectionalManyToOneRepresentativeMap<DexType, DexType> getBidirectionalMap() {
    return mergedClasses;
  }

  public Set<DexType> getSources() {
    return mergedClasses.keySet();
  }

  public Collection<DexType> getSourcesFor(DexType type) {
    return mergedClasses.getKeys(type);
  }

  public DexType getTargetFor(DexType type) {
    assert mergedClasses.containsKey(type);
    return mergedClasses.get(type);
  }

  public DexType getTargetForOrDefault(DexType type, DexType defaultValue) {
    return mergedClasses.getOrDefault(type, defaultValue);
  }

  public boolean hasBeenMergedIntoSubtype(DexType type) {
    return mergedClasses.containsKey(type);
  }

  public boolean hasInterfaceBeenMergedIntoSubtype(DexType type) {
    return mergedInterfaces.containsKey(type);
  }

  public boolean isEmpty() {
    return mergedClasses.isEmpty();
  }

  @Override
  public boolean isMergeTarget(DexType type) {
    return !getSourcesFor(type).isEmpty();
  }

  @Override
  public boolean hasBeenMergedIntoDifferentType(DexType type) {
    return hasBeenMergedIntoSubtype(type);
  }

  @Override
  public boolean verifyAllSourcesPruned(AppView<AppInfoWithLiveness> appView) {
    for (DexType source : mergedClasses.keySet()) {
      assert appView.appInfo().wasPruned(source)
          : "Expected vertically merged class `" + source.toSourceString() + "` to be absent";
    }
    return true;
  }

  public static class Builder {

    private final MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> mergedClasses =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();

    private final BidirectionalManyToOneHashMap<DexType, DexType> mergedInterfaces =
        BidirectionalManyToOneHashMap.newIdentityHashMap();

    void add(DexProgramClass source, DexProgramClass target) {
      mergedClasses.put(source.getType(), target.getType());
      if (source.isInterface()) {
        mergedInterfaces.put(source.getType(), target.getType());
      }
    }

    Set<DexType> getSourcesFor(DexProgramClass target) {
      return mergedClasses.getKeys(target.getType());
    }

    boolean isMergeSource(DexProgramClass clazz) {
      return mergedClasses.containsKey(clazz.getType());
    }

    boolean isMergeTarget(DexProgramClass clazz) {
      return mergedClasses.containsValue(clazz.getType());
    }

    void merge(VerticallyMergedClasses.Builder other) {
      mergedClasses.putAll(other.mergedClasses);
      mergedInterfaces.putAll(other.mergedInterfaces);
    }

    VerticallyMergedClasses build() {
      return new VerticallyMergedClasses(mergedClasses, mergedInterfaces);
    }
  }
}

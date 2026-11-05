// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.utils.internal.IteratorUtils.createCircularIterator;

import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class NoClassAnnotationCollisions extends MultiClassPolicy {

  @Override
  public Collection<HorizontalMergeGroup> apply(HorizontalMergeGroup group) {
    // Create a new merge group for each distinct non-empty set of class annotations.
    Map<DexAnnotationSet, HorizontalMergeGroup> newGroups = new LinkedHashMap<>();
    for (DexProgramClass clazz : group) {
      if (clazz.hasAnnotations()) {
        newGroups
            .computeIfAbsent(clazz.annotations(), ignore -> new HorizontalMergeGroup())
            .add(clazz);
      }
    }

    // If there was at most one distinct set of annotations, keep the original merge group.
    if (newGroups.size() <= 1) {
      return ImmutableList.of(group);
    }

    // Otherwise, distribute the non-annotated classes across the new merge groups.
    Iterator<HorizontalMergeGroup> newGroupsIterator = createCircularIterator(newGroups.values());
    for (DexProgramClass clazz : group) {
      if (!clazz.hasAnnotations()) {
        newGroupsIterator.next().add(clazz);
      }
    }
    return removeTrivialGroups(newGroups.values());
  }

  @Override
  public String getName() {
    return "NoClassAnnotationCollisions";
  }
}

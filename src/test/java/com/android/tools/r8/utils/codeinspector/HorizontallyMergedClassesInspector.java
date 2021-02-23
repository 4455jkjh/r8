// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.TestBase.toDexType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontallyMergedClasses;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HorizontallyMergedClassesInspector {

  private final DexItemFactory dexItemFactory;
  private final HorizontallyMergedClasses horizontallyMergedClasses;

  public HorizontallyMergedClassesInspector(
      DexItemFactory dexItemFactory, HorizontallyMergedClasses horizontallyMergedClasses) {
    this.dexItemFactory = dexItemFactory;
    this.horizontallyMergedClasses = horizontallyMergedClasses;
  }

  public void forEachMergeGroup(BiConsumer<Set<DexType>, DexType> consumer) {
    horizontallyMergedClasses.forEachMergeGroup(consumer);
  }

  public Set<Set<DexType>> getMergeGroups() {
    Set<Set<DexType>> mergeGroups = Sets.newLinkedHashSet();
    forEachMergeGroup(
        (sources, target) -> {
          Set<DexType> mergeGroup = SetUtils.newIdentityHashSet(sources);
          mergeGroup.add(target);
          mergeGroups.add(mergeGroup);
        });
    return mergeGroups;
  }

  public Set<DexType> getSources() {
    return horizontallyMergedClasses.getSources();
  }

  public DexType getTarget(DexType clazz) {
    return horizontallyMergedClasses.getMergeTargetOrDefault(clazz);
  }

  public Set<DexType> getTargets() {
    return horizontallyMergedClasses.getTargets();
  }

  public HorizontallyMergedClassesInspector assertMergedInto(Class<?> from, Class<?> target) {
    assertEquals(
        horizontallyMergedClasses.getMergeTargetOrDefault(toDexType(from)), toDexType(target));
    return this;
  }

  public HorizontallyMergedClassesInspector assertClassesMerged(Collection<Class<?>> classes) {
    return assertTypesMerged(classes.stream().map(this::toDexType).collect(Collectors.toList()));
  }

  public HorizontallyMergedClassesInspector assertClassReferencesMerged(
      Collection<ClassReference> classReferences) {
    return assertTypesMerged(
        classReferences.stream().map(this::toDexType).collect(Collectors.toList()));
  }

  public HorizontallyMergedClassesInspector assertTypesMerged(Collection<DexType> types) {
    List<DexType> unmerged = new ArrayList<>();
    for (DexType type : types) {
      if (!horizontallyMergedClasses.hasBeenMergedOrIsMergeTarget(type)) {
        unmerged.add(type);
      }
    }
    assertEquals(
        "Expected the following classes to be merged: "
            + StringUtils.join(", ", unmerged, DexType::getTypeName),
        0,
        unmerged.size());
    return this;
  }

  public HorizontallyMergedClassesInspector assertNoClassesMerged() {
    assertTrue(horizontallyMergedClasses.getSources().isEmpty());
    return this;
  }

  public HorizontallyMergedClassesInspector assertClassesNotMerged(Class<?>... classes) {
    return assertClassesNotMerged(Arrays.asList(classes));
  }

  public HorizontallyMergedClassesInspector assertClassesNotMerged(Collection<Class<?>> classes) {
    return assertTypesNotMerged(classes.stream().map(this::toDexType).collect(Collectors.toList()));
  }

  public HorizontallyMergedClassesInspector assertClassReferencesNotMerged(
      ClassReference... classReferences) {
    return assertClassReferencesNotMerged(Arrays.asList(classReferences));
  }

  public HorizontallyMergedClassesInspector assertClassReferencesNotMerged(
      Collection<ClassReference> classReferences) {
    return assertTypesNotMerged(
        classReferences.stream().map(this::toDexType).collect(Collectors.toList()));
  }

  public HorizontallyMergedClassesInspector assertTypesNotMerged(DexType... types) {
    return assertTypesNotMerged(Arrays.asList(types));
  }

  public HorizontallyMergedClassesInspector assertTypesNotMerged(Collection<DexType> types) {
    for (DexType type : types) {
      assertTrue(type.isClassType());
      assertFalse(horizontallyMergedClasses.hasBeenMergedOrIsMergeTarget(type));
    }
    return this;
  }

  public HorizontallyMergedClassesInspector assertIsCompleteMergeGroup(Class<?>... classes) {
    return assertIsCompleteMergeGroup(
        Stream.of(classes).map(Reference::classFromClass).collect(Collectors.toList()));
  }

  public HorizontallyMergedClassesInspector assertIsCompleteMergeGroup(String... typeNames) {
    return assertIsCompleteMergeGroup(
        Stream.of(typeNames).map(Reference::classFromTypeName).collect(Collectors.toList()));
  }

  public HorizontallyMergedClassesInspector assertIsCompleteMergeGroup(
      Collection<ClassReference> classReferences) {
    assertFalse(classReferences.isEmpty());
    List<DexType> types =
        classReferences.stream().map(this::toDexType).collect(Collectors.toList());
    DexType uniqueTarget = null;
    for (DexType type : types) {
      if (horizontallyMergedClasses.isMergeTarget(type)) {
        if (uniqueTarget == null) {
          uniqueTarget = type;
        } else {
          fail(
              "Expected a single merge target, but found "
                  + type.getTypeName()
                  + " and "
                  + uniqueTarget.getTypeName());
        }
      }
    }
    if (uniqueTarget == null) {
      for (DexType type : types) {
        if (horizontallyMergedClasses.hasBeenMergedIntoDifferentType(type)) {
          fail(
              "Expected merge target "
                  + horizontallyMergedClasses.getMergeTargetOrDefault(type).getTypeName()
                  + " to be in merge group");
        }
      }
      fail("Expected to find a merge target, but none found");
    }
    Set<DexType> sources = horizontallyMergedClasses.getSourcesFor(uniqueTarget);
    assertEquals(
        "Expected to find "
            + (classReferences.size() - 1)
            + " source(s) for merge target "
            + uniqueTarget.getTypeName()
            + ", but only found: "
            + StringUtils.join(", ", sources, DexType::getTypeName),
        classReferences.size() - 1,
        sources.size());
    assertTrue(types.containsAll(sources));
    return this;
  }

  private DexType toDexType(Class<?> clazz) {
    return TestBase.toDexType(clazz, dexItemFactory);
  }

  private DexType toDexType(ClassReference classReference) {
    return TestBase.toDexType(classReference, dexItemFactory);
  }
}

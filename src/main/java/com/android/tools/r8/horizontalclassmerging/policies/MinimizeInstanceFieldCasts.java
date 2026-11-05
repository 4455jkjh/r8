// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.horizontalclassmerging.ClassMerger;
import com.android.tools.r8.horizontalclassmerging.CoroutineStateMachineMergeCandidates;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.horizontalclassmerging.policies.CompatibleInstanceFields.InstanceFieldInfo;
import com.android.tools.r8.utils.internal.ListUtils;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.collect.Multisets;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MinimizeInstanceFieldCasts extends MultiClassPolicy {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final CoroutineStateMachineMergeCandidates coroutineStateMachineMergeCandidates;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;

  public MinimizeInstanceFieldCasts(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      CoroutineStateMachineMergeCandidates coroutineStateMachineMergeCandidates,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    this.appView = appView;
    this.coroutineStateMachineMergeCandidates = coroutineStateMachineMergeCandidates;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
  }

  @Override
  public final Collection<HorizontalMergeGroup> apply(HorizontalMergeGroup group) {
    // Coroutine state machine merge candidates may have different instance field types. Keep them
    // together so that ClassInstanceFieldsMerger can overlap their fields.
    if (coroutineStateMachineMergeCandidates.isCandidate(group.getClasses().getFirst())) {
      return ListUtils.newArrayList(group);
    }

    if (!appView.options().testing.enableHorizontalClassMergingWithDifferentFields) {
      return splitByExactFieldTypes(group);
    }
    Map<Multiset<InstanceFieldInfo>, HorizontalMergeGroup> classesByRelaxedFields =
        new LinkedHashMap<>();
    for (DexProgramClass clazz : group) {
      classesByRelaxedFields
          .computeIfAbsent(
              CompatibleInstanceFields.getRelaxedFields(appView, clazz),
              ignore -> new HorizontalMergeGroup())
          .add(clazz);
    }
    if (classesByRelaxedFields.size() == 1) {
      return splitByExactFieldTypes(group);
    }

    int superFieldsEndOffset =
        ArtInstanceFieldLayout.getInstanceFieldsEndOffset(appView, group.getSuperType());
    assert superFieldsEndOffset != ArtInstanceFieldLayout.UNKNOWN_OFFSET;
    List<HorizontalMergeGroup> newGroups = new ArrayList<>();
    List<PackedGroup> candidates =
        splitClassesByExactFieldTypes(classesByRelaxedFields, superFieldsEndOffset, newGroups);
    for (PackedGroup packedGroup : packByResultingInstanceSize(candidates, superFieldsEndOffset)) {
      newGroups.add(packedGroup.group);
    }
    newGroups.removeIf(HorizontalMergeGroup::isTrivial);
    return newGroups;
  }

  /**
   * Splits the classes with the same relaxed fields by exact field types. The resulting groups that
   * contain a class with subclasses are added to {@param newGroups}, since adding fields to them
   * could increase the instance size of the subclasses. The other groups are returned as candidates
   * for packing.
   */
  private List<PackedGroup> splitClassesByExactFieldTypes(
      Map<Multiset<InstanceFieldInfo>, HorizontalMergeGroup> classesByRelaxedFields,
      int superFieldsEndOffset,
      List<HorizontalMergeGroup> newGroups) {
    List<PackedGroup> candidates = new ArrayList<>();
    int relaxedFieldsId = 0;
    for (Map.Entry<Multiset<InstanceFieldInfo>, HorizontalMergeGroup> entry :
        classesByRelaxedFields.entrySet()) {
      Multiset<InstanceFieldInfo> relaxedFields = entry.getKey();
      HorizontalMergeGroup group = entry.getValue();
      Collection<HorizontalMergeGroup> exactGroups = splitByExactFieldTypes(group);
      int fieldsEndOffset = getFieldsEndOffset(superFieldsEndOffset, relaxedFields);
      for (HorizontalMergeGroup exactGroup : exactGroups) {
        if (Iterables.any(exactGroup, immediateSubtypingInfo::hasSubclasses)) {
          // Emit the group as-is, since any changes to the field layout of this class could
          // increase the instance size of its subclasses.
          newGroups.add(exactGroup);
        } else {
          candidates.add(
              new PackedGroup(exactGroup, relaxedFieldsId, relaxedFields, fieldsEndOffset));
        }
      }
      relaxedFieldsId++;
    }
    return candidates;
  }

  /**
   * Packs the candidates together as long as this does not increase the instance size, using
   * best-fit decreasing bin packing. Packing may merge reference fields of different types, which
   * then require field casts.
   */
  private List<PackedGroup> packByResultingInstanceSize(
      List<PackedGroup> candidates, int superFieldsEndOffset) {
    if (candidates.isEmpty()) {
      return candidates;
    }
    candidates.sort(
        Comparator.<PackedGroup>comparingInt(candidate -> candidate.fieldsEndOffset).reversed());
    // All classes have the same instance size (see CompatibleInstanceFields).
    int instanceSize = ArtInstanceFieldLayout.getInstanceSize(candidates.get(0).fieldsEndOffset);
    List<PackedGroup> packedGroups = new ArrayList<>();
    for (PackedGroup candidate : candidates) {
      PackedGroup bestPackedGroup = null;
      Multiset<InstanceFieldInfo> bestMergedFields = null;
      int bestMergedFieldsEndOffset = -1;
      for (PackedGroup packedGroup : packedGroups) {
        if (!Collections.disjoint(packedGroup.relaxedFieldsIds, candidate.relaxedFieldsIds)) {
          // These classes were split by exact field types to avoid field casts.
          continue;
        }
        Multiset<InstanceFieldInfo> mergedFields =
            ImmutableMultiset.copyOf(Multisets.union(packedGroup.fields, candidate.fields));
        DexType classIdType =
            ClassMerger.classIdType(packedGroup.group.size() + candidate.group.size(), appView);
        int mergedFieldsEndOffset =
            getMergedFieldsEndOffset(superFieldsEndOffset, mergedFields, classIdType);
        if (ArtInstanceFieldLayout.getInstanceSize(mergedFieldsEndOffset) <= instanceSize
            && mergedFieldsEndOffset > bestMergedFieldsEndOffset) {
          bestPackedGroup = packedGroup;
          bestMergedFields = mergedFields;
          bestMergedFieldsEndOffset = mergedFieldsEndOffset;
        }
      }
      if (bestPackedGroup != null) {
        bestPackedGroup.add(candidate, bestMergedFields);
      } else {
        packedGroups.add(candidate);
      }
    }
    return packedGroups;
  }

  private static int getFieldsEndOffset(
      int superFieldsEndOffset, Collection<InstanceFieldInfo> fields) {
    return ArtInstanceFieldLayout.getInstanceFieldsEndOffset(superFieldsEndOffset, fields);
  }

  // Conservatively assumes that the merged class needs a $r8$classId field.
  private static int getMergedFieldsEndOffset(
      int superFieldsEndOffset, Multiset<InstanceFieldInfo> fields, DexType classIdType) {
    return ArtInstanceFieldLayout.getInstanceFieldsEndOffset(
        superFieldsEndOffset,
        Iterables.concat(
            Iterables.transform(fields, InstanceFieldInfo::getType),
            Collections.singletonList(classIdType)));
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  private Collection<HorizontalMergeGroup> splitByExactFieldTypes(HorizontalMergeGroup group) {
    if (group.isTrivial()) {
      return Collections.singletonList(group);
    }
    // First find all classes that can be merged without changing field types.
    Map<Multiset<DexType>, HorizontalMergeGroup> newGroups = new LinkedHashMap<>();
    group.forEach(clazz -> addExact(clazz, newGroups));

    // Create a single group from all trivial groups.
    HorizontalMergeGroup pendingGroup = new HorizontalMergeGroup();
    newGroups
        .values()
        .removeIf(
            newGroup -> {
              if (newGroup.isTrivial()) {
                pendingGroup.add(newGroup);
                return true;
              }
              return false;
            });

    if (pendingGroup.isEmpty()) {
      return newGroups.values();
    }

    if (!pendingGroup.isTrivial()) {
      List<HorizontalMergeGroup> newGroupsIncludingRelaxedGroup =
          new ArrayList<>(newGroups.values());
      newGroupsIncludingRelaxedGroup.add(pendingGroup);
      return newGroupsIncludingRelaxedGroup;
    }

    HorizontalMergeGroup smallestNewGroup = null;
    for (HorizontalMergeGroup newGroup : newGroups.values()) {
      if (smallestNewGroup == null || newGroup.size() < smallestNewGroup.size()) {
        smallestNewGroup = newGroup;
      }
    }
    assert smallestNewGroup != null;
    smallestNewGroup.add(pendingGroup);
    return newGroups.values();
  }

  private void addExact(
      DexProgramClass clazz, Map<Multiset<DexType>, HorizontalMergeGroup> groups) {
    groups
        .computeIfAbsent(getExactMergeKey(clazz), ignore -> new HorizontalMergeGroup())
        .add(clazz);
  }

  private Multiset<DexType> getExactMergeKey(DexProgramClass clazz) {
    Multiset<DexType> fieldTypes = HashMultiset.create();
    for (DexEncodedField field : clazz.instanceFields()) {
      fieldTypes.add(field.getType());
    }
    return fieldTypes;
  }

  @Override
  public String getName() {
    return "MinimizeFieldCasts";
  }

  /** A merge group together with the instance fields of the class resulting from the merge. */
  private static class PackedGroup {

    private final HorizontalMergeGroup group;

    // Identifies the relaxed fields of the classes in the group.
    private final IntSet relaxedFieldsIds = new IntOpenHashSet();

    private Multiset<InstanceFieldInfo> fields;

    // The end offset of the fields before packing, used to pack the largest groups first.
    private final int fieldsEndOffset;

    PackedGroup(
        HorizontalMergeGroup group,
        int relaxedFieldsId,
        Multiset<InstanceFieldInfo> fields,
        int fieldsEndOffset) {
      this.group = group;
      this.relaxedFieldsIds.add(relaxedFieldsId);
      this.fields = fields;
      this.fieldsEndOffset = fieldsEndOffset;
    }

    void add(PackedGroup other, Multiset<InstanceFieldInfo> mergedFields) {
      group.add(other.group);
      relaxedFieldsIds.addAll(other.relaxedFieldsIds);
      fields = mergedFields;
    }
  }
}

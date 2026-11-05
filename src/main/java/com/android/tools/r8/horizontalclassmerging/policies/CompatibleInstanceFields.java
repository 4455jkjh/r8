// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.horizontalclassmerging.CoroutineStateMachineMergeCandidates;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.AndroidApiLevelUtils;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.ObjectUtils;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CompatibleInstanceFields extends MultiClassPolicy {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final CoroutineStateMachineMergeCandidates coroutineStateMachineMergeCandidates;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;

  public CompatibleInstanceFields(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      CoroutineStateMachineMergeCandidates coroutineStateMachineMergeCandidates,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    this.appView = appView;
    this.coroutineStateMachineMergeCandidates = coroutineStateMachineMergeCandidates;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
  }

  @Override
  public Collection<HorizontalMergeGroup> apply(HorizontalMergeGroup group) {
    // Coroutine state machine merge candidates are instead grouped by CoroutineStateMachinePolicy.
    // TODO(b/579251034): Consider if this exemption can be avoided using store-store fences and
    // generalized instance field merging.
    if (coroutineStateMachineMergeCandidates.isCandidate(group.getClasses().getFirst())) {
      assert Iterables.all(group, coroutineStateMachineMergeCandidates::isCandidate);
      return ListUtils.newArrayList(group);
    }
    assert Iterables.all(group, clazz -> !coroutineStateMachineMergeCandidates.isCandidate(clazz));
    Map<Multiset<InstanceFieldInfo>, HorizontalMergeGroup> sameFieldsGroups = new LinkedHashMap<>();
    for (DexProgramClass clazz : group) {
      sameFieldsGroups
          .computeIfAbsent(getRelaxedFields(appView, clazz), ignore -> new HorizontalMergeGroup())
          .add(clazz);
    }
    if (!appView.options().testing.enableHorizontalClassMergingWithDifferentFields) {
      removeTrivialGroups(sameFieldsGroups.values());
      return sameFieldsGroups.values();
    }
    // Classes with the same instance fields are kept in their own group. Otherwise, policies that
    // distribute classes across groups, such as NoClassAnnotationCollisions, could split them
    // apart, after which MinimizeInstanceFieldCasts may not be able to merge them with the classes
    // with different instance fields in their new group. The remaining leaf classes are grouped by
    // their instance size in ART, so that MinimizeInstanceFieldCasts can merge them without
    // increasing the instance size. Leaf classes with different instance fields are therefore
    // never merged into a group of classes with the same instance fields.
    List<HorizontalMergeGroup> newGroups = new ArrayList<>();
    Map<Integer, HorizontalMergeGroup> differentFieldsGroups = new LinkedHashMap<>();
    // All classes have the same superclass (see SameParentClass).
    int superFieldsEndOffset =
        ArtInstanceFieldLayout.getInstanceFieldsEndOffset(appView, group.getSuperType());
    sameFieldsGroups.forEach(
        (relaxedFields, sameFieldsGroup) -> {
          if (!sameFieldsGroup.isTrivial()) {
            newGroups.add(sameFieldsGroup);
          } else if (superFieldsEndOffset != ArtInstanceFieldLayout.UNKNOWN_OFFSET
              && !immediateSubtypingInfo.hasSubclasses(sameFieldsGroup.getClasses().getFirst())) {
            int instanceSize =
                ArtInstanceFieldLayout.getInstanceSize(
                    ArtInstanceFieldLayout.getInstanceFieldsEndOffset(
                        superFieldsEndOffset, relaxedFields));
            differentFieldsGroups
                .computeIfAbsent(instanceSize, ignore -> new HorizontalMergeGroup())
                .add(sameFieldsGroup);
          }
        });
    removeTrivialGroups(differentFieldsGroups.values());
    newGroups.addAll(differentFieldsGroups.values());
    return newGroups;
  }

  static Multiset<InstanceFieldInfo> getRelaxedFields(
      AppView<? extends AppInfoWithClassHierarchy> appView, DexProgramClass clazz) {
    Multiset<InstanceFieldInfo> fields = HashMultiset.create();
    for (DexEncodedField field : clazz.instanceFields()) {
      fields.add(InstanceFieldInfo.createRelaxed(appView, field));
    }
    return fields;
  }

  @Override
  public String getName() {
    return "CompatibleInstanceFields";
  }

  public static class InstanceFieldInfo {

    private final FieldAccessFlags accessFlags;
    private final DexType type;

    private InstanceFieldInfo(FieldAccessFlags accessFlags, DexType type) {
      this.accessFlags =
          FieldAccessFlags.fromSharedAccessFlags(accessFlags.materialize()).unsetSynthetic();
      this.type = type;
    }

    public static InstanceFieldInfo createExact(DexEncodedField field) {
      return new InstanceFieldInfo(field.getAccessFlags(), field.getType());
    }

    public static InstanceFieldInfo createRelaxed(
        AppView<? extends AppInfoWithClassHierarchy> appView, DexEncodedField field) {
      return new InstanceFieldInfo(
          field.getAccessFlags(), getRelaxedType(appView, field.getType()));
    }

    public FieldAccessFlags getAccessFlags() {
      return accessFlags;
    }

    public DexType getType() {
      return type;
    }

    public InstanceFieldInfo toInfoWithRelaxedType(
        AppView<? extends AppInfoWithClassHierarchy> appView) {
      return new InstanceFieldInfo(accessFlags, getRelaxedType(appView, type));
    }

    private static DexType getRelaxedType(
        AppView<? extends AppInfoWithClassHierarchy> appView, DexType type) {
      return type.isReferenceType() && AndroidApiLevelUtils.isApiSafeForReference(type, appView)
          ? appView.dexItemFactory().objectType
          : type;
    }

    @Override
    @SuppressWarnings({"EqualsGetClass", "ReferenceEquality"})
    public boolean equals(Object obj) {
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      InstanceFieldInfo info = (InstanceFieldInfo) obj;
      return accessFlags.materialize() == info.accessFlags.materialize() && type == info.type;
    }

    @Override
    public int hashCode() {
      return ObjectUtils.hashLL(accessFlags, type);
    }
  }
}

// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.classmerging.ClassMergerMode;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.EmptyBidirectionalOneToOneMap;
import java.util.Collection;
import java.util.Set;

/**
 * Identifies when instance initializer merging is required and bails out. This is needed to ensure
 * that we don't need to append extra null arguments at constructor call sites, such that the result
 * of the final round of class merging can be described as a renaming only.
 *
 * <p>This policy requires that all instance initializers with the same signature (relaxed, by
 * converting references types to java.lang.Object) have the same behavior.
 */
public class FinalizeMergeGroup extends MultiClassPolicy {

  private final AppView<?> appView;
  private final ClassMergerMode mode;

  public FinalizeMergeGroup(AppView<?> appView, ClassMergerMode mode) {
    this.appView = appView;
    this.mode = mode;
  }

  @Override
  public Collection<HorizontalMergeGroup> apply(HorizontalMergeGroup group) {
    if (appView.enableWholeProgramOptimizations()) {
      if (group.isInterfaceGroup() || !mode.isRestrictedToAlphaRenamingInR8()) {
        group.selectTarget(appView);
        group.selectInstanceFieldMap(appView.withClassHierarchy());
      } else {
        // In the final round of merging each group should be finalized by the
        // NoInstanceInitializerMerging policy.
        assert verifyAlreadyFinalized(group);
      }
    } else {
      assert !group.hasTarget();
      assert !group.hasInstanceFieldMap();
      group.selectTarget(appView);
      group.setInstanceFieldMap(new EmptyBidirectionalOneToOneMap<>());
    }
    return ListUtils.newLinkedList(group);
  }

  @Override
  public String getName() {
    return "FinalizeMergeGroup";
  }

  @Override
  public boolean isIdentityForInterfaceGroups() {
    return true;
  }

  private boolean verifyAlreadyFinalized(HorizontalMergeGroup group) {
    assert group.hasTarget();
    assert group.getClasses().contains(group.getTarget());
    assert group.hasInstanceFieldMap();
    Set<DexType> types =
        SetUtils.newIdentityHashSet(
            builder -> group.forEach(clazz -> builder.accept(clazz.getType())));
    group
        .getInstanceFieldMap()
        .forEach(
            (sourceField, targetField) -> {
              assert types.contains(sourceField.getHolderType());
              assert types.contains(targetField.getHolderType());
            });
    return true;
  }
}

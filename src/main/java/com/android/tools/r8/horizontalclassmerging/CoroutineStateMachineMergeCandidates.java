// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.horizontalclassmerging.policies.CoroutineStateMachinePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.SameInstanceFields.InstanceFieldInfo;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.Multiset;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Kotlin coroutine state machines (subclasses of ContinuationImpl and SuspendLambda) that the
 * horizontal class merger merges according to {@link CoroutineStateMachinePolicy} instead of the
 * generic instance field policy.
 *
 * <p>A candidate is merged with candidates that have the same final fields.
 */
public class CoroutineStateMachineMergeCandidates {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final Map<DexProgramClass, Multiset<InstanceFieldInfo>> mergeKeys =
      new IdentityHashMap<>();

  public CoroutineStateMachineMergeCandidates(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
  }

  public void registerCandidates(Collection<HorizontalMergeGroup> groups) {
    assert mergeKeys.isEmpty();
    for (HorizontalMergeGroup group : groups) {
      if (group.isInterfaceGroup()) {
        continue;
      }
      for (DexProgramClass clazz : group) {
        Multiset<InstanceFieldInfo> mergeKey = computeMergeKey(clazz);
        if (mergeKey != null) {
          mergeKeys.put(clazz, mergeKey);
        }
      }
    }
  }

  private Multiset<InstanceFieldInfo> computeMergeKey(DexProgramClass clazz) {
    if (!clazz.hasSuperType()
        || !appView
            .dexItemFactory()
            .kotlin()
            .continuations()
            .isCoroutineStateMachineSuperType(appView, clazz.getSuperType())
        || clazz.isAbstract()
        || clazz.hasStaticFields()
        || immediateSubtypingInfo.hasSubclasses(clazz)) {
      return null;
    }
    return computeFieldMergeKey(clazz);
  }

  private Multiset<InstanceFieldInfo> computeFieldMergeKey(DexProgramClass clazz) {
    Multiset<InstanceFieldInfo> finalFields = null;
    for (DexEncodedField field : clazz.instanceFields()) {
      FieldAccessFlags flags = field.getAccessFlags();
      if (!hasAcceptedFlags(flags)) {
        return null;
      }
      // A class may have both final and non-final fields. The final fields of all classes in a
      // group must be the same, so that no final field is merged with a missing or non-final
      // field.
      // TODO(b/579251034): Consider allowing final fields to be merged with non-final and missing
      // fields when a store-store fence can be inserted in the instance initializers that assign
      // them, and whether that allows avoiding the SameInstanceFields exemption.
      if (flags.isFinal()) {
        if (finalFields == null) {
          finalFields = HashMultiset.create();
        }
        finalFields.add(InstanceFieldInfo.createRelaxed(appView, field));
      }
    }
    return finalFields != null ? finalFields : ImmutableMultiset.of();
  }

  private boolean hasAcceptedFlags(FieldAccessFlags flags) {
    assert !flags.isStatic();
    return !flags.isProtected() && !flags.isVolatile() && !flags.isTransient() && !flags.isEnum();
  }

  public boolean isEmpty() {
    return mergeKeys.isEmpty();
  }

  public boolean isCandidate(DexProgramClass clazz) {
    return mergeKeys.containsKey(clazz);
  }

  public Multiset<InstanceFieldInfo> getMergeKey(DexProgramClass clazz) {
    return mergeKeys.get(clazz);
  }
}

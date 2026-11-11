// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.CoroutineStateMachineMergeCandidates;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicyWithPreprocessing;
import com.android.tools.r8.horizontalclassmerging.policies.CompatibleInstanceFields.InstanceFieldInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multiset;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Separates Kotlin coroutine state machine merge candidates from all other classes, and groups the
 * candidates by their final fields.
 *
 * <p>Candidates are exempt from {@link CompatibleInstanceFields} and {@link
 * MinimizeInstanceFieldCasts} (see {@link CoroutineStateMachineMergeCandidates}).
 */
public class CoroutineStateMachinePolicy extends MultiClassPolicyWithPreprocessing<Void> {

  private static final Object NOT_A_COROUTINE_STATE = new Object();

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final CoroutineStateMachineMergeCandidates candidates;

  public CoroutineStateMachinePolicy(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      CoroutineStateMachineMergeCandidates candidates) {
    this.appView = appView;
    this.candidates = candidates;
  }

  @Override
  public Void preprocess(Collection<HorizontalMergeGroup> groups, ExecutorService executorService) {
    candidates.registerCandidates(groups);
    return null;
  }

  @Override
  public Collection<HorizontalMergeGroup> apply(HorizontalMergeGroup group, Void data) {
    if (group.isInterfaceGroup() || candidates.isEmpty()) {
      return ImmutableList.of(group);
    }
    Map<Object, HorizontalMergeGroup> newGroups = new LinkedHashMap<>();
    for (DexProgramClass clazz : group) {
      Multiset<InstanceFieldInfo> mergeKey = candidates.getMergeKey(clazz);
      Object groupKey = mergeKey != null ? mergeKey : NOT_A_COROUTINE_STATE;
      newGroups.computeIfAbsent(groupKey, ignore -> new HorizontalMergeGroup()).add(clazz);
    }
    return removeTrivialGroups(newGroups.values());
  }

  @Override
  public boolean shouldSkipPolicy() {
    return appView.options().horizontalClassMergerOptions().isRestrictedToSynthetics(appView);
  }

  @Override
  public String getName() {
    return "CoroutineStateMachinePolicy";
  }
}

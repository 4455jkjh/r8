// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepradius;

import static com.android.tools.r8.utils.internal.MapUtils.ignoreKey;

import com.android.tools.r8.shaking.ProguardKeepRuleModifiers;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

public class KeepRuleSubsumptionAnalysis {

  private final RootSetKeepRadius keepRadius;
  private final Map<RootSetKeepRadiusForRule, Collection<RootSetKeepRadiusForRule>> subsumedBy =
      new IdentityHashMap<>();

  KeepRuleSubsumptionAnalysis(RootSetKeepRadius keepRadius) {
    this.keepRadius = keepRadius;
  }

  Map<RootSetKeepRadiusForRule, Collection<RootSetKeepRadiusForRule>> run(
      KeepRadiusOptions options) {
    if (!options.enableSubsumptionAnalysis) {
      return subsumedBy;
    }
    // Visit all keep rule pairs.
    // TODO(b/441055269): Parallelize.
    // TODO(b/441055269): Consider narrowing the candidate pairs by syntactic analysis of the keep
    //  rules. For example, consider `-keep class com.**` and `-keep class com.example.**` but not
    //  `-keep class com.example.**` and `-keep class com.google.**`.
    List<RootSetKeepRadiusForRule> keepRadiusForRules = new ArrayList<>(keepRadius.getKeepRadius());
    for (int i = 0; i < keepRadiusForRules.size(); i++) {
      RootSetKeepRadiusForRule keepRadiusForRule = keepRadiusForRules.get(i);
      if (keepRadiusForRule.isEmpty()) {
        continue;
      }
      for (int j = i + 1; j < keepRadiusForRules.size(); j++) {
        RootSetKeepRadiusForRule keepRadiusForOtherRule = keepRadiusForRules.get(j);
        if (keepRadiusForOtherRule.isEmpty()) {
          continue;
        }
        if (hasSameModifiers(keepRadiusForRule, keepRadiusForOtherRule)) {
          if (isSubsumedBy(keepRadiusForRule, keepRadiusForOtherRule)) {
            addSubsumedBy(keepRadiusForRule, keepRadiusForOtherRule);
          }
          if (isSubsumedBy(keepRadiusForOtherRule, keepRadiusForRule)) {
            addSubsumedBy(keepRadiusForOtherRule, keepRadiusForRule);
          }
        }
      }
    }
    return subsumedBy;
  }

  private void addSubsumedBy(
      RootSetKeepRadiusForRule keepRadiusForRule, RootSetKeepRadiusForRule keepRadiusForOtherRule) {
    subsumedBy
        .computeIfAbsent(keepRadiusForRule, ignoreKey(ArrayList::new))
        .add(keepRadiusForOtherRule);
  }

  private static boolean hasSameModifiers(
      RootSetKeepRadiusForRule keepRadiusForRule, RootSetKeepRadiusForRule keepRadiusForOtherRule) {
    ProguardKeepRuleModifiers modifiers = keepRadiusForRule.getRule().getModifiers();
    ProguardKeepRuleModifiers otherModifiers = keepRadiusForOtherRule.getRule().getModifiers();
    return modifiers.equals(otherModifiers);
  }

  private static boolean isSubsumedBy(
      RootSetKeepRadiusForRule keepRadiusForRule, RootSetKeepRadiusForRule keepRadiusForOtherRule) {
    // Fast path based on size of keep radius.
    if (keepRadiusForRule.getMatchedClasses().size()
            > keepRadiusForOtherRule.getMatchedClasses().size()
        || keepRadiusForRule.getMatchedFields().size()
            > keepRadiusForOtherRule.getMatchedFields().size()
        || keepRadiusForRule.getMatchedMethods().size()
            > keepRadiusForOtherRule.getMatchedMethods().size()) {
      return false;
    }
    return isMatchedItemsSubsumedBy(
            keepRadiusForRule, keepRadiusForOtherRule, RootSetKeepRadiusForRule::getMatchedClasses)
        && isMatchedItemsSubsumedBy(
            keepRadiusForRule, keepRadiusForOtherRule, RootSetKeepRadiusForRule::getMatchedFields)
        && isMatchedItemsSubsumedBy(
            keepRadiusForRule, keepRadiusForOtherRule, RootSetKeepRadiusForRule::getMatchedMethods);
  }

  private static <T> boolean isMatchedItemsSubsumedBy(
      RootSetKeepRadiusForRule keepRadiusForRule,
      RootSetKeepRadiusForRule keepRadiusForOtherRule,
      Function<RootSetKeepRadiusForRule, Set<T>> fn) {
    Set<T> matchedItems = fn.apply(keepRadiusForRule);
    Set<T> otherMatchedItems = fn.apply(keepRadiusForOtherRule);
    assert matchedItems.size() <= otherMatchedItems.size();
    return otherMatchedItems.containsAll(matchedItems);
  }
}

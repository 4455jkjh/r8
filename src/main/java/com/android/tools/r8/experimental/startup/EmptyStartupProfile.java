// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.experimental.startup.profile.StartupProfileClassRule;
import com.android.tools.r8.experimental.startup.profile.StartupProfileMethodRule;
import com.android.tools.r8.experimental.startup.profile.StartupProfileRule;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.util.Collection;
import java.util.Collections;

public class EmptyStartupProfile extends StartupProfile {

  EmptyStartupProfile() {}

  @Override
  public boolean containsClassRule(DexType type) {
    return false;
  }

  @Override
  public boolean containsMethodRule(DexMethod method) {
    return false;
  }

  @Override
  public <E1 extends Exception, E2 extends Exception> void forEachRule(
      ThrowingConsumer<StartupProfileClassRule, E1> classRuleConsumer,
      ThrowingConsumer<StartupProfileMethodRule, E2> methodRuleConsumer) {
    // Intentionally empty.
  }

  @Override
  public StartupProfileClassRule getClassRule(DexType type) {
    return null;
  }

  @Override
  public StartupProfileMethodRule getMethodRule(DexMethod method) {
    return null;
  }

  @Override
  public Collection<StartupProfileRule> getRules() {
    return Collections.emptyList();
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public EmptyStartupProfile rewrittenWithLens(GraphLens graphLens) {
    return this;
  }

  @Override
  public EmptyStartupProfile toStartupOrderForWriting(AppView<?> appView) {
    return this;
  }

  @Override
  public EmptyStartupProfile withoutPrunedItems(
      PrunedItems prunedItems, SyntheticItems syntheticItems) {
    return this;
  }
}

// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup.profile;

import com.android.tools.r8.experimental.startup.StartupProfile;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.ThrowingConsumer;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.function.BiConsumer;

public class NonEmptyStartupProfile extends StartupProfile {

  private final LinkedHashMap<DexReference, StartupProfileRule> startupItems;

  public NonEmptyStartupProfile(LinkedHashMap<DexReference, StartupProfileRule> startupItems) {
    assert !startupItems.isEmpty();
    this.startupItems = startupItems;
  }

  @Override
  public boolean containsMethodRule(DexMethod method) {
    return startupItems.containsKey(method);
  }

  @Override
  public boolean containsClassRule(DexType type) {
    return startupItems.containsKey(type);
  }

  @Override
  public <E1 extends Exception, E2 extends Exception> void forEachRule(
      ThrowingConsumer<StartupProfileClassRule, E1> classRuleConsumer,
      ThrowingConsumer<StartupProfileMethodRule, E2> methodRuleConsumer)
      throws E1, E2 {
    for (StartupProfileRule rule : getRules()) {
      rule.accept(classRuleConsumer, methodRuleConsumer);
    }
  }

  @Override
  public StartupProfileClassRule getClassRule(DexType type) {
    return (StartupProfileClassRule) startupItems.get(type);
  }

  @Override
  public StartupProfileMethodRule getMethodRule(DexMethod method) {
    return (StartupProfileMethodRule) startupItems.get(method);
  }

  @Override
  public Collection<StartupProfileRule> getRules() {
    return startupItems.values();
  }

  @Override
  public boolean isEmpty() {
    return false;
  }

  @Override
  public StartupProfile rewrittenWithLens(GraphLens graphLens) {
    return transform(
        (classRule, builder) ->
            builder.addClassRule(
                StartupProfileClassRule.builder()
                    .setClassReference(graphLens.lookupType(classRule.getReference()))
                    .build()),
        (methodRule, builder) ->
            builder.addMethodRule(
                StartupProfileMethodRule.builder()
                    .setMethod(graphLens.getRenamedMethodSignature(methodRule.getReference()))
                    .build()));
  }

  public int size() {
    return startupItems.size();
  }

  /**
   * This is called to process the startup profile before computing the startup layouts.
   *
   * <p>This processing makes the following key change to the startup profile: A {@link
   * StartupProfileClassRule} is inserted for all supertypes of a given class next to the class in
   * the startup profile. This ensures that the classes from the super hierarchy will be laid out
   * close to their subclasses, at the point where the subclasses are used during startup.
   *
   * <p>This normally follows from the trace already, except that the class initializers of
   * interfaces are not executed when a subclass is used.
   */
  @Override
  public StartupProfile toStartupProfileForWriting(AppView<?> appView) {
    return transform(
        (classRule, builder) -> addStartupItem(classRule, builder, appView),
        (methodRule, builder) -> addStartupItem(methodRule, builder, appView));
  }

  private static void addStartupItem(
      StartupProfileRule startupItem, Builder builder, AppView<?> appView) {
    startupItem.accept(
        classRule -> addClassAndParentClasses(classRule.getReference(), builder, appView),
        builder::addMethodRule);
  }

  private static boolean addClass(DexProgramClass clazz, Builder builder) {
    int oldSize = builder.size();
    builder.addClassRule(
        StartupProfileClassRule.builder().setClassReference(clazz.getType()).build());
    return builder.size() > oldSize;
  }

  private static void addClassAndParentClasses(DexType type, Builder builder, AppView<?> appView) {
    DexProgramClass definition = appView.app().programDefinitionFor(type);
    if (definition != null) {
      addClassAndParentClasses(definition, builder, appView);
    }
  }

  private static void addClassAndParentClasses(
      DexProgramClass clazz, Builder builder, AppView<?> appView) {
    if (addClass(clazz, builder)) {
      addParentClasses(clazz, builder, appView);
    }
  }

  private static void addParentClasses(DexProgramClass clazz, Builder builder, AppView<?> appView) {
    clazz.forEachImmediateSupertype(
        supertype -> addClassAndParentClasses(supertype, builder, appView));
  }

  @Override
  public StartupProfile withoutMissingItems(AppView<?> appView) {
    AppInfo appInfo = appView.appInfo();
    return transform(
        (classRule, builder) -> {
          if (appInfo.hasDefinitionForWithoutExistenceAssert(classRule.getReference())) {
            builder.addClassRule(classRule);
          }
        },
        (methodRule, builder) -> {
          DexClass clazz =
              appInfo.definitionForWithoutExistenceAssert(
                  methodRule.getReference().getHolderType());
          if (methodRule.getReference().isDefinedOnClass(clazz)) {
            builder.addMethodRule(methodRule);
          }
        });
  }

  @Override
  public StartupProfile withoutPrunedItems(PrunedItems prunedItems, SyntheticItems syntheticItems) {
    return transform(
        (classRule, builder) -> {
          if (!prunedItems.isRemoved(classRule.getReference())) {
            builder.addClassRule(classRule);
          }
        },
        (methodRule, builder) -> {
          if (!prunedItems.isRemoved(methodRule.getReference())) {
            builder.addMethodRule(methodRule);
          }
        });
  }

  private StartupProfile transform(
      BiConsumer<StartupProfileClassRule, Builder> classRuleTransformer,
      BiConsumer<StartupProfileMethodRule, Builder> methodRuleTransformer) {
    Builder builder = builderWithCapacity(startupItems.size());
    forEachRule(
        classRule -> classRuleTransformer.accept(classRule, builder),
        methodRule -> methodRuleTransformer.accept(methodRule, builder));
    return builder.build();
  }
}

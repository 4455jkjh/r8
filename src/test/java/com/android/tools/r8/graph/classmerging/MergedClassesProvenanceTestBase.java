// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.UnorderedCollectionMatcher;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.Participant;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ProvenanceGroup;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class MergedClassesProvenanceTestBase extends TestBase {

  protected static final class MergedClassAndGroup {
    final DexProgramClass clazz;
    final ProvenanceGroup group;

    MergedClassAndGroup(DexProgramClass clazz, ProvenanceGroup group) {
      this.clazz = clazz;
      this.group = group;
    }
  }

  protected static Consumer<InternalOptions> withProvenanceConsumer(
      BooleanBox checked, BiConsumer<AppView<?>, MergedClassesProvenance> consumer) {
    return options -> {
      options.getTestingOptions().enableExperimentalMapFileVersion = true;
      options.getTestingOptions().mergedClassesProvenanceConsumer =
          (appView, provenance) -> {
            checked.set();
            consumer.accept(appView, provenance);
          };
    };
  }

  protected static DexProgramClass pinTarget(
      AppView<?> appView,
      Iterable<DexProgramClass> candidates,
      DexProgramClass defaultTarget,
      Class<?> preferred) {
    DexType preferredType = toDexType(preferred, appView.dexItemFactory());
    for (DexProgramClass candidate : candidates) {
      DexType originalType =
          appView.graphLens().getOriginalType(candidate.getType(), GraphLens.getIdentityLens());
      if (originalType.isIdenticalTo(preferredType)) {
        return candidate;
      }
    }
    return defaultTarget;
  }

  protected static List<ProvenanceGroup> allProvenanceGroups(
      AppView<?> appView, MergedClassesProvenance provenance) {
    List<ProvenanceGroup> groups = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      ProvenanceGroup group = provenance.getGroup(appView, clazz.getType());
      if (group != null) {
        groups.add(group);
      }
    }
    return groups;
  }

  protected static ProvenanceGroup findIdentifiedGroup(
      AppView<?> appView, MergedClassesProvenance provenance) {
    ProvenanceGroup found = findNestedGroup(appView, provenance, ProvenanceGroup::isIdentified);
    assertNotNull("Expected an identified provenance group", found);
    return found;
  }

  protected static MergedClassAndGroup findGroupWithMergedFields(
      AppView<?> appView, MergedClassesProvenance provenance) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      ProvenanceGroup group = provenance.getGroup(appView, clazz.getType());
      if (group != null) {
        ProvenanceGroup match = findNestedGroup(group, ProvenanceGroup::hasMergedFields);
        if (match != null) {
          return new MergedClassAndGroup(clazz, match);
        }
      }
    }
    assertNotNull("Expected a provenance group with merged fields", null);
    return null;
  }

  protected static DexField singleOriginalInstanceField(
      AppView<?> appView, MergedClassAndGroup merged) {
    List<DexField> fields = new ArrayList<>();
    for (DexEncodedField field : merged.clazz.instanceFields()) {
      if (merged.group.isIdentified()
          && field.getReference().isIdenticalTo(merged.group.asIdentified().getClassIdField())) {
        continue;
      }
      fields.add(
          appView
              .graphLens()
              .getOriginalFieldSignature(field.getReference(), GraphLens.getIdentityLens()));
    }
    assertThat(
        fields,
        UnorderedCollectionMatcher.matchesOneToOne(ImmutableList.of(notNullValue(DexField.class))));
    return fields.get(0);
  }

  protected static List<DexField> mergedFieldContributors(
      ProvenanceGroup group, DexField originalTarget) {
    List<DexField> contributors = new ArrayList<>();
    for (Participant participant : group.getParticipants()) {
      contributors.add(participant.getMergedField(originalTarget));
    }
    return contributors;
  }

  protected static ProvenanceGroup findNestedGroup(
      AppView<?> appView,
      MergedClassesProvenance provenance,
      Predicate<ProvenanceGroup> predicate) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      ProvenanceGroup group = provenance.getGroup(appView, clazz.getType());
      if (group != null) {
        ProvenanceGroup match = findNestedGroup(group, predicate);
        if (match != null) {
          return match;
        }
      }
    }
    return null;
  }

  private static ProvenanceGroup findNestedGroup(
      ProvenanceGroup group, Predicate<ProvenanceGroup> predicate) {
    if (predicate.test(group)) {
      return group;
    }
    for (Participant participant : group.getParticipants()) {
      if (participant.getNode().isGroup()) {
        ProvenanceGroup match = findNestedGroup(participant.getNode().asGroup(), predicate);
        if (match != null) {
          return match;
        }
      }
    }
    return null;
  }
}

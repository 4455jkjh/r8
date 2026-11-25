// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.Participant;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ProvenanceGroup;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.BooleanBox;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;

public abstract class MergedClassesProvenanceTestBase extends TestBase {

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

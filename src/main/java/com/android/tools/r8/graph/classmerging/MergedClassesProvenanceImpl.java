// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.collections.Pair;
import com.google.common.collect.Iterables;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

public class MergedClassesProvenanceImpl extends MergedClassesProvenance {

  private static class ProvenanceGroupTracker {
    private final Map<DexType, ProvenanceGroup> indexedGroups = new IdentityHashMap<>();

    ProvenanceGroup getGroup(DexType type) {
      return indexedGroups.get(type);
    }

    ProvenanceGroup getAndRemoveGroup(DexType type) {
      return indexedGroups.remove(type);
    }

    void addGroup(ProvenanceGroup group) {
      DexType target = group.getTarget();
      assert !indexedGroups.containsKey(target) : target + " already bound to a group";
      indexedGroups.put(target, group);
    }
  }

  private final ProvenanceGroupTracker groups = new ProvenanceGroupTracker();

  @Override
  public ProvenanceGroup getGroupForOriginalType(DexType originalType) {
    return groups.getGroup(originalType);
  }

  private ProvenanceNode removeOrCreateNode(DexType originalType) {
    ProvenanceGroup existingGroup = groups.getAndRemoveGroup(originalType);
    return existingGroup != null ? existingGroup : new ClassProvenance(originalType);
  }

  private DexType getOriginalType(AppView<?> appView, DexType type) {
    return appView.graphLens().getOriginalType(type, GraphLens.getIdentityLens());
  }

  private List<UnidentifiedParticipant> getSortedUnidentifiedParticipants(
      AppView<?> appView, Iterable<DexType> sources) {
    Iterable<DexType> originalSources =
        Iterables.transform(sources, source -> getOriginalType(appView, source));
    Iterable<UnidentifiedParticipant> participants =
        Iterables.transform(
            originalSources,
            originalType -> new UnidentifiedParticipant(removeOrCreateNode(originalType)));
    List<UnidentifiedParticipant> sortedParticipants =
        ListUtils.sort(participants, Comparator.comparing(Participant::getType));
    assert assertNoDuplicateTypes(sortedParticipants);
    return sortedParticipants;
  }

  @Override
  public void recordVerticalMerge(AppView<?> appView, DexType target, Iterable<DexType> sources) {
    DexType originalTarget = getOriginalType(appView, target);
    List<UnidentifiedParticipant> sortedParticipants =
        getSortedUnidentifiedParticipants(appView, sources);
    groups.addGroup(new VerticalProvenanceGroup(originalTarget, sortedParticipants));
  }

  @Override
  public void recordHorizontalMerge(
      AppView<?> appView,
      DexType target,
      Iterable<DexType> sources,
      DexField classIdField,
      ToIntFunction<DexType> classIdGetter) {
    DexType originalTarget = getOriginalType(appView, target);
    if (classIdField == null) {
      List<UnidentifiedParticipant> sortedParticipants =
          getSortedUnidentifiedParticipants(appView, sources);
      groups.addGroup(
          new UnidentifiedHorizontalProvenanceGroup(originalTarget, sortedParticipants));
      return;
    }
    Iterable<Pair<DexType, Integer>> originalSources =
        Iterables.transform(
            sources,
            source ->
                Pair.create(getOriginalType(appView, source), classIdGetter.applyAsInt(source)));
    Iterable<Participant> participants =
        Iterables.transform(
            originalSources,
            pair -> {
              DexType originalType = pair.getFirst();
              int classId = pair.getSecond();
              return new IdentifiedParticipant(removeOrCreateNode(originalType), classId);
            });
    List<Participant> sortedParticipants =
        ListUtils.sort(participants, Comparator.comparing(Participant::getType));
    assert assertNoDuplicateTypes(sortedParticipants);
    assert assertNoDuplicateClassIds(sortedParticipants);
    groups.addGroup(
        new IdentifiedHorizontalProvenanceGroup(originalTarget, classIdField, sortedParticipants));
  }

  private static boolean assertNoDuplicateTypes(List<? extends Participant> sortedParticipants) {
    DexType previous = null;
    for (Participant participant : sortedParticipants) {
      DexType current = participant.getType();
      assert previous == null || current.isNotIdenticalTo(previous);
      previous = current;
    }
    return true;
  }

  private static boolean assertNoDuplicateClassIds(List<Participant> participants) {
    IntSet seen = new IntOpenHashSet(participants.size());
    for (Participant participant : participants) {
      if (participant.isIdentified()) {
        boolean firstOccurrence = seen.add(participant.asIdentified().getClassId());
        assert firstOccurrence;
      }
    }
    return true;
  }
}

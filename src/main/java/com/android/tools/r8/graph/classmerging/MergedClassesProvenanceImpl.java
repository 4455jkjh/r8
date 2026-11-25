// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.SetUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
      assert !indexedGroups.containsKey(target)
          : target.getTypeName() + " already bound to a group";
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

  private static DexType getOriginalType(AppView<?> appView, DexType type) {
    return appView
        .graphLens()
        .getOriginalTypeThroughSyntheticFinalization(type, GraphLens.getIdentityLens());
  }

  @FunctionalInterface
  private interface ParticipantFactory<P extends Participant> {
    P create(DexType source, ProvenanceNode node, boolean instantiated);
  }

  @Override
  public void recordMerge(
      AppView<?> appView,
      DexType target,
      Iterable<DexType> maybeInstantiatedParticipants,
      Iterable<DexType> definitelyUninstantiatedParticipants,
      ClassIdInfo classIdInfo) {
    assert target != null;
    DexType originalTarget = getOriginalType(appView, target);
    ProvenanceGroup group;
    if (classIdInfo == null) {
      group =
          createUnidentifiedGroup(
              appView,
              originalTarget,
              maybeInstantiatedParticipants,
              definitelyUninstantiatedParticipants);
    } else {
      group =
          createIdentifiedGroup(
              appView,
              originalTarget,
              maybeInstantiatedParticipants,
              definitelyUninstantiatedParticipants,
              classIdInfo);
    }
    groups.addGroup(group);
  }

  private UnidentifiedProvenanceGroup createUnidentifiedGroup(
      AppView<?> appView,
      DexType originalTarget,
      Iterable<DexType> maybeInstantiatedParticipants,
      Iterable<DexType> definitelyUninstantiatedParticipants) {
    List<UnidentifiedParticipant> participants =
        collectParticipants(
            appView,
            maybeInstantiatedParticipants,
            definitelyUninstantiatedParticipants,
            (source, node, instantiated) -> new UnidentifiedParticipant(node, instantiated));
    return new UnidentifiedProvenanceGroup(
        originalTarget, ListUtils.unmodifiableForTesting(participants));
  }

  private IdentifiedProvenanceGroup createIdentifiedGroup(
      AppView<?> appView,
      DexType originalTarget,
      Iterable<DexType> maybeInstantiatedParticipants,
      Iterable<DexType> definitelyUninstantiatedParticipants,
      ClassIdInfo classIdInfo) {
    List<IdentifiedParticipant> participants =
        collectParticipants(
            appView,
            maybeInstantiatedParticipants,
            definitelyUninstantiatedParticipants,
            (source, node, instantiated) ->
                new IdentifiedParticipant(node, classIdInfo.getClassId(source), instantiated));
    assert assertNoDuplicateClassIds(participants);
    return new IdentifiedProvenanceGroup(
        originalTarget, classIdInfo.getField(), ListUtils.unmodifiableForTesting(participants));
  }

  private <P extends Participant> List<P> collectParticipants(
      AppView<?> appView,
      Iterable<DexType> maybeInstantiatedParticipants,
      Iterable<DexType> definitelyUninstantiatedParticipants,
      ParticipantFactory<P> factory) {
    List<P> participants = new ArrayList<>();
    addParticipants(appView, maybeInstantiatedParticipants, true, factory, participants);
    addParticipants(appView, definitelyUninstantiatedParticipants, false, factory, participants);
    assert !participants.isEmpty();
    assert assertNoDuplicateTypes(participants);
    return participants;
  }

  private <P extends Participant> void addParticipants(
      AppView<?> appView,
      Iterable<DexType> participantTypes,
      boolean instantiated,
      ParticipantFactory<P> factory,
      List<P> result) {
    if (participantTypes == null) {
      return;
    }
    for (DexType source : participantTypes) {
      DexType originalType = getOriginalType(appView, source);
      result.add(factory.create(source, removeOrCreateNode(originalType), instantiated));
    }
  }

  private static boolean assertNoDuplicateTypes(Collection<? extends Participant> participants) {
    Set<DexType> seen = SetUtils.newIdentityHashSet(participants.size());
    for (Participant participant : participants) {
      boolean firstOccurrence = seen.add(participant.getType());
      assert firstOccurrence : "Duplicate participant type " + participant.getType().getTypeName();
    }
    return true;
  }

  private static boolean assertNoDuplicateClassIds(Collection<IdentifiedParticipant> participants) {
    IntSet seen = new IntOpenHashSet(participants.size());
    for (IdentifiedParticipant participant : participants) {
      boolean firstOccurrence = seen.add(participant.getClassId());
      assert firstOccurrence : "Duplicate class id " + participant.getClassId();
    }
    return true;
  }
}

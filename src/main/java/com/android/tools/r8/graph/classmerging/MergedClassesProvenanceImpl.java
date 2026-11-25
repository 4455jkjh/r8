// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.internal.AssertionUtils;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.SetUtils;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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

  private static DexField getOriginalFieldSignature(AppView<?> appView, DexField field) {
    // Unlike getPreviousClassType, SyntheticFinalizationGraphLens inherits NestedGraphLens's
    // getPreviousFieldSignature, so this already resolves through synthetic finalization.
    return appView.graphLens().getOriginalFieldSignature(field, GraphLens.getIdentityLens());
  }

  @FunctionalInterface
  private interface ParticipantFactory<P extends Participant> {
    P create(
        DexType source,
        ProvenanceNode node,
        boolean instantiated,
        Map<DexField, DexField> mergedFields);
  }

  @Override
  public void recordMerge(
      AppView<?> appView,
      DexType target,
      Iterable<DexType> maybeInstantiatedParticipants,
      Iterable<DexType> definitelyUninstantiatedParticipants,
      ClassIdInfo classIdInfo,
      Map<DexField, ? extends Iterable<DexField>> mergedFields) {
    assert target != null;
    DexType originalTarget = getOriginalType(appView, target);
    ProvenanceGroup group;
    if (classIdInfo == null) {
      group =
          createUnidentifiedGroup(
              appView,
              originalTarget,
              maybeInstantiatedParticipants,
              definitelyUninstantiatedParticipants,
              mergedFields);
    } else {
      group =
          createIdentifiedGroup(
              appView,
              originalTarget,
              maybeInstantiatedParticipants,
              definitelyUninstantiatedParticipants,
              classIdInfo,
              mergedFields);
    }
    groups.addGroup(group);
  }

  private UnidentifiedProvenanceGroup createUnidentifiedGroup(
      AppView<?> appView,
      DexType originalTarget,
      Iterable<DexType> maybeInstantiatedParticipants,
      Iterable<DexType> definitelyUninstantiatedParticipants,
      Map<DexField, ? extends Iterable<DexField>> mergedFields) {
    List<UnidentifiedParticipant> participants =
        collectParticipants(
            appView,
            maybeInstantiatedParticipants,
            definitelyUninstantiatedParticipants,
            mergedFields,
            (source, node, instantiated, participantMergedFields) ->
                new UnidentifiedParticipant(node, instantiated, participantMergedFields));
    return new UnidentifiedProvenanceGroup(
        originalTarget, ListUtils.unmodifiableForTesting(participants));
  }

  private IdentifiedProvenanceGroup createIdentifiedGroup(
      AppView<?> appView,
      DexType originalTarget,
      Iterable<DexType> maybeInstantiatedParticipants,
      Iterable<DexType> definitelyUninstantiatedParticipants,
      ClassIdInfo classIdInfo,
      Map<DexField, ? extends Iterable<DexField>> mergedFields) {
    List<IdentifiedParticipant> participants =
        collectParticipants(
            appView,
            maybeInstantiatedParticipants,
            definitelyUninstantiatedParticipants,
            mergedFields,
            (source, node, instantiated, participantMergedFields) ->
                new IdentifiedParticipant(
                    node, classIdInfo.getClassId(source), instantiated, participantMergedFields));
    assert assertNoDuplicateClassIds(participants);
    return new IdentifiedProvenanceGroup(
        originalTarget, classIdInfo.getField(), ListUtils.unmodifiableForTesting(participants));
  }

  private <P extends Participant> List<P> collectParticipants(
      AppView<?> appView,
      Iterable<DexType> maybeInstantiatedParticipants,
      Iterable<DexType> definitelyUninstantiatedParticipants,
      Map<DexField, ? extends Iterable<DexField>> mergedFields,
      ParticipantFactory<P> factory) {
    Map<DexType, Map<DexField, DexField>> mergedFieldsByParticipant =
        buildMergedFieldsByParticipant(appView, mergedFields);
    List<P> participants = new ArrayList<>();
    addParticipants(
        appView,
        maybeInstantiatedParticipants,
        true,
        mergedFieldsByParticipant,
        factory,
        participants);
    addParticipants(
        appView,
        definitelyUninstantiatedParticipants,
        false,
        mergedFieldsByParticipant,
        factory,
        participants);
    assert !participants.isEmpty();
    assert assertNoDuplicateTypes(participants);
    assert mergedFieldsByParticipant.isEmpty()
        : "Merged fields reference non-participant holders: " + mergedFieldsByParticipant.keySet();
    return participants;
  }

  private <P extends Participant> void addParticipants(
      AppView<?> appView,
      Iterable<DexType> participantTypes,
      boolean instantiated,
      Map<DexType, Map<DexField, DexField>> mergedFieldsByParticipant,
      ParticipantFactory<P> factory,
      List<P> result) {
    if (participantTypes == null) {
      return;
    }
    for (DexType source : participantTypes) {
      DexType originalType = getOriginalType(appView, source);
      Map<DexField, DexField> participantMergedFields =
          mergedFieldsByParticipant.isEmpty()
              ? null
              : mergedFieldsByParticipant.remove(originalType);
      result.add(
          factory.create(
              source, removeOrCreateNode(originalType), instantiated, participantMergedFields));
    }
  }

  private static Map<DexType, Map<DexField, DexField>> buildMergedFieldsByParticipant(
      AppView<?> appView, Map<DexField, ? extends Iterable<DexField>> mergedFields) {
    if (mergedFields == null || mergedFields.isEmpty()) {
      return Collections.emptyMap();
    }
    Set<DexField> seenOriginalTargets =
        AssertionUtils.assertionsEnabled() && mergedFields.size() > 1
            ? SetUtils.newIdentityHashSet(mergedFields.size())
            : null;
    Map<DexType, Map<DexField, DexField>> result = new IdentityHashMap<>();
    for (Map.Entry<DexField, ? extends Iterable<DexField>> entry : mergedFields.entrySet()) {
      DexField originalTargetField = getOriginalFieldSignature(appView, entry.getKey());
      assert seenOriginalTargets == null || seenOriginalTargets.add(originalTargetField)
          : "Duplicate merged field target " + originalTargetField;
      int contributorCount = 0;
      for (DexField currentSourceField : entry.getValue()) {
        DexType originalHolder = getOriginalType(appView, currentSourceField.getHolderType());
        DexField originalField = getOriginalFieldSignature(appView, currentSourceField);
        Map<DexField, DexField> participantMergedFields =
            result.computeIfAbsent(originalHolder, k -> new IdentityHashMap<>());
        DexField existing = participantMergedFields.put(originalTargetField, originalField);
        assert existing == null
            : "Multiple fields from participant "
                + originalHolder
                + " merged into "
                + originalTargetField;
        contributorCount++;
      }
      assert contributorCount > 1 : "Expected at least 2 merged fields for " + originalTargetField;
    }
    return result;
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

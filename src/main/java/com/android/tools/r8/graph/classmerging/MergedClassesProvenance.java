// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.IterableUtils;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.SetUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.android.tools.r8.verticalclassmerging.VerticallyMergedClasses;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Records when and how classes and instance fields are merged across compilation.
 *
 * <p>Provenance is recorded in terms of original types and field signatures (including
 * pre-finalization synthetic names). Only merges are recorded, renamings and moves are tracked
 * separately. This structure alone is insufficient to generate merged mapping information.
 *
 * <p>Each merge group is either identified (distinguishing participants via a {@code classIdField})
 * or unidentified. Instantiation is tracked conservatively per participant: uninstantiated is
 * definite (never instantiated directly, though possibly indirectly by subclass instantiation),
 * whereas instantiated means it might be instantiated directly. When a merge combines instance
 * fields from multiple participants into a single target field, each contributing immediate
 * participant records the mapping from the merged target field's original signature to the original
 * signature of the field it contributed. Unmerged fields are not recorded.
 *
 * <p>Merges are recorded as immutable events. A child's instantiation status may become
 * inconsistent with a later merge (e.g., if a class is later proven uninstantiated). When they
 * conflict, the enclosing parent is the source of truth since it happened later in time.
 *
 * <p>For example, given input classes {@code A} (with {@code A.ax}), {@code B}, and {@code C} (with
 * {@code C.cx}):
 *
 * <ol>
 *   <li>{@code A} is merged into {@code B}, moving {@code A.ax} to {@code B.ax}: provenance records
 *       {@code B -> [B, A]} with no merged fields.
 *   <li>{@code B} and {@code C} are renamed to {@code B'} and {@code C'} ({@code B.ax} to {@code
 *       B'.ax'}): provenance is unchanged.
 *   <li>{@code C'} is merged into {@code B'}, combining {@code B'.ax'} and {@code C'.cx} into
 *       {@code B'.ax'}: provenance resolves back to original names and records {@code B -> [[B, A],
 *       C]} where participant {@code [B, A]} maps {@code A.ax -> A.ax} and participant {@code C}
 *       maps {@code A.ax -> C.cx}.
 *   <li>{@code B'} is renamed to {@code X} and {@code B'.ax'} to {@code X.f}: when emitting the
 *       map, provenance is queried by original names {@code B} and {@code A.ax}.
 * </ol>
 *
 * <p>{@link InternalOptions#isMergedClassInformationSupported()} controls whether tracking is
 * enabled.
 */
public abstract class MergedClassesProvenance {

  public static class ClassIdInfo {

    private final DexField field;
    private final Reference2IntMap<DexType> classIdentifiers;

    public ClassIdInfo(DexField field, Reference2IntMap<DexType> classIdentifiers) {
      assert field != null;
      assert classIdentifiers != null;
      this.field = field;
      this.classIdentifiers = classIdentifiers;
    }

    public static ClassIdInfo createForTesting(
        DexField field, Map<DexType, Integer> classIdentifiers) {
      assert classIdentifiers != null;
      Reference2IntMap<DexType> map = new Reference2IntOpenHashMap<>(classIdentifiers.size());
      classIdentifiers.forEach((type, id) -> map.put(type, id.intValue()));
      return new ClassIdInfo(field, map);
    }

    public DexField getField() {
      return field;
    }

    public int getClassId(DexType type) {
      assert classIdentifiers.containsKey(type);
      return classIdentifiers.getInt(type);
    }
  }

  public abstract ProvenanceGroup getGroupForOriginalType(DexType originalType);

  public final ProvenanceGroup getGroup(AppView<?> appView, DexType currentType) {
    return getGroupForOriginalType(
        appView
            .graphLens()
            .getOriginalTypeThroughSyntheticFinalization(currentType, GraphLens.getIdentityLens()));
  }

  /**
   * Records a class merge into {@code target}. Either participant iterable may be {@code null};
   * {@code classIdInfo} covers all participants, or is {@code null} for an unidentified merge;
   * {@code mergedFields} maps each merged target field to the fields combined into it, or is {@code
   * null} (or empty) if no fields were merged.
   */
  public abstract void recordMerge(
      AppView<?> appView,
      DexType target,
      Iterable<DexType> maybeInstantiatedParticipants,
      Iterable<DexType> definitelyUninstantiatedParticipants,
      ClassIdInfo classIdInfo,
      Map<DexField, ? extends Iterable<DexField>> mergedFields);

  public void recordMerges(AppView<?> appView, VerticallyMergedClasses verticallyMergedClasses) {
    verticallyMergedClasses.forEachMergeGroup(
        (sources, target) -> {
          if (isInstantiable(appView, target)) {
            recordMerge(appView, target, Collections.singleton(target), sources, null, null);
          } else {
            recordMerge(
                appView, target, null, IterableUtils.prependSingleton(target, sources), null, null);
          }
        });
  }

  public void recordMerges(AppView<?> appView, Collection<HorizontalMergeGroup> mergeGroups) {
    for (HorizontalMergeGroup group : mergeGroups) {
      List<DexType> maybeInstantiated = new ArrayList<>();
      List<DexType> definitelyUninstantiated = new ArrayList<>();
      for (DexProgramClass clazz : group.getClasses()) {
        if (isInstantiable(clazz)) {
          maybeInstantiated.add(clazz.getType());
        } else {
          definitelyUninstantiated.add(clazz.getType());
        }
      }
      ClassIdInfo classIdInfo =
          group.hasClassIdField()
              ? new ClassIdInfo(group.getClassIdField(), group.getClassIdentifiers())
              : null;
      recordMerge(
          appView,
          group.getTarget().getType(),
          maybeInstantiated,
          definitelyUninstantiated,
          classIdInfo,
          extractMergedFields(group));
    }
  }

  private static Map<DexField, List<DexField>> extractMergedFields(HorizontalMergeGroup group) {
    if (!group.hasInstanceFieldMap() || group.getInstanceFieldMap().isEmpty()) {
      return Collections.emptyMap();
    }
    Map<DexField, List<DexField>> mergedFields = new IdentityHashMap<>();
    group
        .getInstanceFieldMap()
        .forEachManyToOneMapping(
            (fieldsToMerge, targetField) -> {
              if (fieldsToMerge.size() > 1) {
                mergedFields.put(
                    targetField.getReference(),
                    ListUtils.map(fieldsToMerge, DexEncodedField::getReference));
              }
            });
    return mergedFields;
  }

  private static boolean isInstantiable(AppView<?> appView, DexType type) {
    return isInstantiable(appView.definitionFor(type).asProgramClass());
  }

  private static boolean isInstantiable(DexProgramClass clazz) {
    return !clazz.isInterface() && !clazz.isAbstract();
  }

  public static MergedClassesProvenance create(AppView<?> appView) {
    return appView.options().isMergedClassInformationSupported()
        ? new MergedClassesProvenanceImpl()
        : empty();
  }

  public static MergedClassesProvenance empty() {
    return new MergedClassesProvenance() {
      @Override
      public ProvenanceGroup getGroupForOriginalType(DexType originalType) {
        throw new Unreachable("Unexpected query on empty MergedClassesProvenance.");
      }

      @Override
      public void recordMerge(
          AppView<?> appView,
          DexType target,
          Iterable<DexType> maybeInstantiatedParticipants,
          Iterable<DexType> definitelyUninstantiatedParticipants,
          ClassIdInfo classIdInfo,
          Map<DexField, ? extends Iterable<DexField>> mergedFields) {
        // Intentionally empty.
      }

      @Override
      public void recordMerges(
          AppView<?> appView, VerticallyMergedClasses verticallyMergedClasses) {
        // Intentionally empty.
      }

      @Override
      public void recordMerges(AppView<?> appView, Collection<HorizontalMergeGroup> mergeGroups) {
        // Intentionally empty.
      }
    };
  }

  public static MergedClassesProvenance throwing() {
    return new MergedClassesProvenance() {
      private Unreachable fail() {
        throw new Unreachable("MergedClassesProvenance has already been cleared.");
      }

      @Override
      public ProvenanceGroup getGroupForOriginalType(DexType originalType) {
        throw fail();
      }

      @Override
      public void recordMerge(
          AppView<?> appView,
          DexType target,
          Iterable<DexType> maybeInstantiatedParticipants,
          Iterable<DexType> definitelyUninstantiatedParticipants,
          ClassIdInfo classIdInfo,
          Map<DexField, ? extends Iterable<DexField>> mergedFields) {
        throw fail();
      }

      @Override
      public void recordMerges(
          AppView<?> appView, VerticallyMergedClasses verticallyMergedClasses) {
        throw fail();
      }

      @Override
      public void recordMerges(AppView<?> appView, Collection<HorizontalMergeGroup> mergeGroups) {
        throw fail();
      }
    };
  }

  public abstract static class ProvenanceNode {

    private final DexType type;

    protected ProvenanceNode(DexType type) {
      assert type != null;
      this.type = type;
    }

    public DexType getType() {
      return type;
    }

    public boolean isClass() {
      return false;
    }

    public ClassProvenance asClass() {
      return null;
    }

    public boolean isGroup() {
      return false;
    }

    public ProvenanceGroup asGroup() {
      return null;
    }
  }

  public static class ClassProvenance extends ProvenanceNode {

    public ClassProvenance(DexType type) {
      super(type);
    }

    @Override
    public boolean isClass() {
      return true;
    }

    @Override
    public ClassProvenance asClass() {
      return this;
    }
  }

  public abstract static class ProvenanceGroup extends ProvenanceNode {

    protected ProvenanceGroup(DexType target) {
      super(target);
    }

    public DexType getTarget() {
      return getType();
    }

    /**
     * Returns the participants in this merge group. Iteration order is unspecified; consumers
     * requiring deterministic order should sort explicitly.
     */
    public abstract Collection<? extends Participant> getParticipants();

    public boolean hasMergedFields() {
      for (Participant participant : getParticipants()) {
        if (participant.hasMergedFields()) {
          return true;
        }
      }
      return false;
    }

    static boolean assertMergedFieldsHaveMultipleParticipants(
        Collection<? extends Participant> participants) {
      Reference2IntOpenHashMap<DexField> targetCounts = null;
      for (Participant participant : participants) {
        if (!participant.hasMergedFields()) {
          continue;
        }
        if (targetCounts == null) {
          targetCounts = new Reference2IntOpenHashMap<>();
        }
        for (DexField targetField : participant.mergedFields.keySet()) {
          targetCounts.addTo(targetField, 1);
        }
      }
      if (targetCounts != null) {
        for (Reference2IntMap.Entry<DexField> entry : targetCounts.reference2IntEntrySet()) {
          assert entry.getIntValue() > 1
              : "Merged field target "
                  + entry.getKey()
                  + " only referenced by "
                  + entry.getIntValue()
                  + " participant";
        }
      }
      return true;
    }

    @Override
    public boolean isGroup() {
      return true;
    }

    @Override
    public ProvenanceGroup asGroup() {
      return this;
    }

    public boolean isIdentified() {
      return false;
    }

    public IdentifiedProvenanceGroup asIdentified() {
      return null;
    }

    public boolean isUnidentified() {
      return false;
    }

    public UnidentifiedProvenanceGroup asUnidentified() {
      return null;
    }
  }

  public static class IdentifiedProvenanceGroup extends ProvenanceGroup {

    private final DexField classIdField;
    private final Collection<IdentifiedParticipant> participants;

    public IdentifiedProvenanceGroup(
        DexType target, DexField classIdField, Collection<IdentifiedParticipant> participants) {
      super(target);
      assert classIdField != null;
      assert participants != null;
      assert assertMergedFieldsHaveMultipleParticipants(participants);
      this.classIdField = classIdField;
      this.participants = participants;
    }

    public DexField getClassIdField() {
      return classIdField;
    }

    @Override
    public Collection<IdentifiedParticipant> getParticipants() {
      return participants;
    }

    @Override
    public boolean isIdentified() {
      return true;
    }

    @Override
    public IdentifiedProvenanceGroup asIdentified() {
      return this;
    }
  }

  public static class UnidentifiedProvenanceGroup extends ProvenanceGroup {

    private final Collection<UnidentifiedParticipant> participants;

    public UnidentifiedProvenanceGroup(
        DexType target, Collection<UnidentifiedParticipant> participants) {
      super(target);
      assert participants != null;
      assert assertMergedFieldsHaveMultipleParticipants(participants);
      this.participants = participants;
    }

    @Override
    public Collection<UnidentifiedParticipant> getParticipants() {
      return participants;
    }

    @Override
    public boolean isUnidentified() {
      return true;
    }

    @Override
    public UnidentifiedProvenanceGroup asUnidentified() {
      return this;
    }
  }

  public abstract static class Participant {

    private final ProvenanceNode node;
    private final boolean instantiated;
    private final Map<DexField, DexField> mergedFields;

    protected Participant(
        ProvenanceNode node, boolean instantiated, Map<DexField, DexField> mergedFields) {
      assert node != null;
      assert assertValidMergedFields(mergedFields);
      this.node = node;
      this.instantiated = instantiated;
      this.mergedFields =
          mergedFields != null && !mergedFields.isEmpty() ? mergedFields : Collections.emptyMap();
    }

    private static boolean assertValidMergedFields(Map<DexField, DexField> mergedFields) {
      if (mergedFields == null || mergedFields.isEmpty()) {
        return true;
      }
      Set<DexField> seenOrigins =
          mergedFields.size() > 1 ? SetUtils.newIdentityHashSet(mergedFields.size()) : null;
      for (Map.Entry<DexField, DexField> entry : mergedFields.entrySet()) {
        assert entry.getKey() != null;
        assert entry.getValue() != null;
        if (seenOrigins != null) {
          assert seenOrigins.add(entry.getValue())
              : "Original field "
                  + entry.getValue()
                  + " merged into multiple target fields on same participant";
        }
      }
      return true;
    }

    public ProvenanceNode getNode() {
      return node;
    }

    public DexType getType() {
      return node.getType();
    }

    /**
     * Applies to the entire subtree rooted at {@link #getNode()}: {@code false} acts as a pending
     * demotion when wrapping a {@link ProvenanceGroup}. A leaf class may be directly instantiated
     * iff every participant on the path from root to leaf returns {@code true}.
     */
    public boolean isInstantiated() {
      return instantiated;
    }

    public final boolean isUninstantiated() {
      return !isInstantiated();
    }

    public boolean hasMergedFields() {
      return !mergedFields.isEmpty();
    }

    /**
     * Maps each merged target field's original signature to the original {@link DexField}
     * contributed by this participant. Iteration order is unspecified; consumers requiring
     * deterministic order should sort explicitly.
     */
    public Map<DexField, DexField> getMergedFields() {
      return Collections.unmodifiableMap(mergedFields);
    }

    /**
     * Returns the original {@link DexField} contributed by this participant to {@code
     * originalTarget}, or {@code null} if this participant did not contribute to it.
     */
    public DexField getMergedField(DexField originalTarget) {
      return mergedFields.get(originalTarget);
    }

    /**
     * Invokes {@code consumer} with {@code (originalTarget, originalField)} for each merged field
     * contributed by this participant.
     */
    public void forEachMergedField(BiConsumer<DexField, DexField> consumer) {
      mergedFields.forEach(consumer);
    }

    public boolean isIdentified() {
      return false;
    }

    public IdentifiedParticipant asIdentified() {
      return null;
    }

    public boolean isUnidentified() {
      return false;
    }

    public UnidentifiedParticipant asUnidentified() {
      return null;
    }
  }

  public static class IdentifiedParticipant extends Participant {

    private final int classId;

    public IdentifiedParticipant(
        ProvenanceNode node,
        int classId,
        boolean instantiated,
        Map<DexField, DexField> mergedFields) {
      super(node, instantiated, mergedFields);
      this.classId = classId;
    }

    public int getClassId() {
      return classId;
    }

    @Override
    public boolean isIdentified() {
      return true;
    }

    @Override
    public IdentifiedParticipant asIdentified() {
      return this;
    }
  }

  public static class UnidentifiedParticipant extends Participant {

    public UnidentifiedParticipant(
        ProvenanceNode node, boolean instantiated, Map<DexField, DexField> mergedFields) {
      super(node, instantiated, mergedFields);
    }

    @Override
    public boolean isUnidentified() {
      return true;
    }

    @Override
    public UnidentifiedParticipant asUnidentified() {
      return this;
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.IterableUtils;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.android.tools.r8.verticalclassmerging.VerticallyMergedClasses;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Records when and how classes are merged across compilation.
 *
 * <p>Provenance is recorded in terms of original types so that all nodes directly reference classes
 * from the input program (or initial synthetic names). This means that this structure itself is
 * insufficient to generate the merged class mapping information, as the final residual name is
 * tracked separately.
 *
 * <p>Each merge group is either identified (distinguishing participants via a {@code classIdField})
 * or unidentified. Instantiation is tracked conservatively per participant: uninstantiated is
 * definite (never instantiated directly, though possibly indirectly by a subclass instantiation),
 * whereas instantiated means it might be instantiated directly.
 *
 * <p>Merges are recorded as immutable events rather than mutating prior groups. Consequently, a
 * child's instantiation status may become inconsistent with a later merge (e.g., if a class is
 * later proven uninstantiated); when they conflict, the enclosing parent is the source of truth (an
 * uninstantiated parent overrides maybe-instantiated children; see {@link
 * #compress(ProvenanceGroup)}).
 *
 * <p>For example, given input classes {@code A}, {@code B}, and {@code C}:
 *
 * <ol>
 *   <li>{@code A} is merged into {@code B}: provenance records {@code B -> [B, A]}.
 *   <li>{@code B} and {@code C} are renamed to {@code B'} and {@code C'}: provenance is unchanged.
 *   <li>{@code C'} is merged into {@code B'}: {@code B'} and {@code C'} are resolved to their
 *       original names {@code B} and {@code C}, consuming the existing entry for {@code B} to
 *       record {@code B -> [[B, A], C]}.
 *   <li>{@code B'} is renamed to {@code X}: when emitting {@code B -> X} in the map, provenance is
 *       queried by the original name {@code B}.
 * </ol>
 *
 * <p>{@link InternalOptions#isMergedClassInformationSupported()} controls the enabling of tracking.
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
   * {@code classIdInfo} covers all participants, or is {@code null} for an unidentified merge.
   */
  public abstract void recordMerge(
      AppView<?> appView,
      DexType target,
      Iterable<DexType> maybeInstantiatedParticipants,
      Iterable<DexType> definitelyUninstantiatedParticipants,
      ClassIdInfo classIdInfo);

  public void recordMerges(AppView<?> appView, VerticallyMergedClasses verticallyMergedClasses) {
    verticallyMergedClasses.forEachMergeGroup(
        (sources, target) -> {
          if (isInstantiable(appView, target)) {
            recordMerge(appView, target, Collections.singleton(target), sources, null);
          } else {
            recordMerge(
                appView, target, null, IterableUtils.prependSingleton(target, sources), null);
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
          classIdInfo);
    }
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
          ClassIdInfo classIdInfo) {
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
          ClassIdInfo classIdInfo) {
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

    protected Participant(ProvenanceNode node, boolean instantiated) {
      assert node != null;
      this.node = node;
      this.instantiated = instantiated;
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

    public IdentifiedParticipant(ProvenanceNode node, int classId, boolean instantiated) {
      super(node, instantiated);
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

    public UnidentifiedParticipant(ProvenanceNode node, boolean instantiated) {
      super(node, instantiated);
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

  /**
   * Flattens nested {@link UnidentifiedProvenanceGroup}s into their enclosing unidentified parent
   * and flushes pending uninstantiated demotions down the tree so every participant reflects its
   * effective status. {@link IdentifiedProvenanceGroup}s are always preserved.
   */
  public static ProvenanceGroup compress(ProvenanceGroup group) {
    return compress(group, false);
  }

  private static ProvenanceGroup compress(ProvenanceGroup group, boolean demoteToUninstantiated) {
    if (group.isIdentified()) {
      return compressIdentified(group.asIdentified(), demoteToUninstantiated);
    }
    if (group.isUnidentified()) {
      return compressUnidentified(group.asUnidentified(), demoteToUninstantiated);
    }
    throw new Unreachable();
  }

  private static IdentifiedProvenanceGroup compressIdentified(
      IdentifiedProvenanceGroup identified, boolean demoteToUninstantiated) {
    return new IdentifiedProvenanceGroup(
        identified.getTarget(),
        identified.getClassIdField(),
        ListUtils.unmodifiableForTesting(
            ListUtils.map(
                identified.getParticipants(),
                participant -> {
                  boolean uninstantiated = demoteToUninstantiated || participant.isUninstantiated();
                  return new IdentifiedParticipant(
                      compressNode(participant.getNode(), uninstantiated),
                      participant.getClassId(),
                      !uninstantiated);
                })));
  }

  private static UnidentifiedProvenanceGroup compressUnidentified(
      UnidentifiedProvenanceGroup unidentified, boolean demoteToUninstantiated) {
    List<UnidentifiedParticipant> compressedParticipants = new ArrayList<>();
    for (UnidentifiedParticipant participant : unidentified.getParticipants()) {
      boolean uninstantiated = demoteToUninstantiated || participant.isUninstantiated();
      ProvenanceNode node = participant.getNode();
      if (node.isClass()) {
        compressedParticipants.add(new UnidentifiedParticipant(node, !uninstantiated));
      } else if (node.isGroup()) {
        ProvenanceGroup nestedGroup = compress(node.asGroup(), uninstantiated);
        if (nestedGroup.isIdentified()) {
          compressedParticipants.add(new UnidentifiedParticipant(nestedGroup, !uninstantiated));
        } else if (nestedGroup.isUnidentified()) {
          compressedParticipants.addAll(nestedGroup.asUnidentified().getParticipants());
        } else {
          throw new Unreachable();
        }
      } else {
        throw new Unreachable();
      }
    }
    return new UnidentifiedProvenanceGroup(
        unidentified.getTarget(), ListUtils.unmodifiableForTesting(compressedParticipants));
  }

  private static ProvenanceNode compressNode(ProvenanceNode node, boolean demoteToUninstantiated) {
    if (node.isClass()) {
      return node;
    }
    if (node.isGroup()) {
      return compress(node.asGroup(), demoteToUninstantiated);
    }
    throw new Unreachable();
  }
}

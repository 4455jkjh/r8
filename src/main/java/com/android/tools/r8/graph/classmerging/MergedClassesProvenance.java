// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontalMergeGroup;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.IterableUtils;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.android.tools.r8.verticalclassmerging.VerticallyMergedClasses;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Records when and how classes are merged across compilation.
 *
 * <p>Provenance is recorded in terms of original types so that all nodes directly reference classes
 * from the input program (or initial synthetic names). This means that this structure itself is
 * insufficient to generate the merged class mapping information, as the final residual name is
 * tracked separately.
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

  public abstract ProvenanceGroup getGroupForOriginalType(DexType originalType);

  public abstract void recordVerticalMerge(
      AppView<?> appView, DexType target, Iterable<DexType> sources);

  public abstract void recordHorizontalMerge(
      AppView<?> appView,
      DexType target,
      Iterable<DexType> sources,
      DexField classIdField,
      ToIntFunction<DexType> classIdGetter);

  public final void recordVerticalMerges(
      AppView<?> appView, VerticallyMergedClasses verticallyMergedClasses) {
    verticallyMergedClasses.forEachMergeGroup(
        (sources, target) ->
            recordVerticalMerge(appView, target, IterableUtils.prependSingleton(target, sources)));
  }

  public final void recordHorizontalMerges(
      AppView<?> appView, Collection<HorizontalMergeGroup> mergeGroups) {
    for (HorizontalMergeGroup group : mergeGroups) {
      recordHorizontalMerge(
          appView,
          group.getTarget().getType(),
          Iterables.transform(group.getClasses(), DexProgramClass::getType),
          group.hasClassIdField() ? group.getClassIdField() : null,
          group::getClassId);
    }
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
      public void recordVerticalMerge(
          AppView<?> appView, DexType target, Iterable<DexType> sources) {
        // Intentionally empty.
      }

      @Override
      public void recordHorizontalMerge(
          AppView<?> appView,
          DexType target,
          Iterable<DexType> sources,
          DexField classIdField,
          ToIntFunction<DexType> classIdGetter) {
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
      public void recordVerticalMerge(
          AppView<?> appView, DexType target, Iterable<DexType> sources) {
        throw fail();
      }

      @Override
      public void recordHorizontalMerge(
          AppView<?> appView,
          DexType target,
          Iterable<DexType> sources,
          DexField classIdField,
          ToIntFunction<DexType> classIdGetter) {
        throw fail();
      }
    };
  }

  public abstract static class ProvenanceNode {

    private final DexType type;

    protected ProvenanceNode(DexType type) {
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

    public abstract List<? extends Participant> getParticipants();

    @Override
    public boolean isGroup() {
      return true;
    }

    @Override
    public ProvenanceGroup asGroup() {
      return this;
    }

    public boolean isVertical() {
      return false;
    }

    public VerticalProvenanceGroup asVertical() {
      return null;
    }

    public boolean isHorizontal() {
      return false;
    }

    public HorizontalProvenanceGroup asHorizontal() {
      return null;
    }
  }

  public static class VerticalProvenanceGroup extends ProvenanceGroup {

    private final List<UnidentifiedParticipant> participants;

    public VerticalProvenanceGroup(DexType target, List<UnidentifiedParticipant> participants) {
      super(target);
      this.participants = participants;
    }

    @Override
    public List<UnidentifiedParticipant> getParticipants() {
      return participants;
    }

    @Override
    public boolean isVertical() {
      return true;
    }

    @Override
    public VerticalProvenanceGroup asVertical() {
      return this;
    }
  }

  public abstract static class HorizontalProvenanceGroup extends ProvenanceGroup {

    protected HorizontalProvenanceGroup(DexType target) {
      super(target);
    }

    @Override
    public boolean isHorizontal() {
      return true;
    }

    @Override
    public HorizontalProvenanceGroup asHorizontal() {
      return this;
    }

    public boolean isIdentified() {
      return false;
    }

    public IdentifiedHorizontalProvenanceGroup asIdentified() {
      return null;
    }

    public boolean isUnidentified() {
      return false;
    }

    public UnidentifiedHorizontalProvenanceGroup asUnidentified() {
      return null;
    }
  }

  public static class IdentifiedHorizontalProvenanceGroup extends HorizontalProvenanceGroup {

    private final DexField classIdField;
    private final List<Participant> participants;

    public IdentifiedHorizontalProvenanceGroup(
        DexType target, DexField classIdField, List<Participant> participants) {
      super(target);
      assert classIdField != null;
      this.classIdField = classIdField;
      this.participants = participants;
    }

    public DexField getClassIdField() {
      return classIdField;
    }

    @Override
    public List<Participant> getParticipants() {
      return participants;
    }

    @Override
    public boolean isIdentified() {
      return true;
    }

    @Override
    public IdentifiedHorizontalProvenanceGroup asIdentified() {
      return this;
    }
  }

  public static class UnidentifiedHorizontalProvenanceGroup extends HorizontalProvenanceGroup {

    private final List<UnidentifiedParticipant> participants;

    public UnidentifiedHorizontalProvenanceGroup(
        DexType target, List<UnidentifiedParticipant> participants) {
      super(target);
      this.participants = participants;
    }

    @Override
    public List<UnidentifiedParticipant> getParticipants() {
      return participants;
    }

    @Override
    public boolean isUnidentified() {
      return true;
    }

    @Override
    public UnidentifiedHorizontalProvenanceGroup asUnidentified() {
      return this;
    }
  }

  public abstract static class Participant {

    private final ProvenanceNode node;

    protected Participant(ProvenanceNode node) {
      this.node = node;
    }

    public ProvenanceNode getNode() {
      return node;
    }

    public DexType getType() {
      return node.getType();
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

    public IdentifiedParticipant(ProvenanceNode node, int classId) {
      super(node);
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

    public UnidentifiedParticipant(ProvenanceNode node) {
      super(node);
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

  public static ProvenanceGroup compress(ProvenanceGroup group) {
    if (group.isVertical()) {
      return compressVertical(group.asVertical());
    }
    if (group.isHorizontal()) {
      return compressHorizontal(group.asHorizontal());
    }
    throw new Unreachable();
  }

  private static VerticalProvenanceGroup compressVertical(VerticalProvenanceGroup verticalGroup) {
    ImmutableList.Builder<UnidentifiedParticipant> compressedParticipants = ImmutableList.builder();
    for (UnidentifiedParticipant participant : verticalGroup.getParticipants()) {
      ProvenanceNode node = participant.getNode();
      if (node.isClass()) {
        compressedParticipants.add(participant);
      } else if (node.isGroup()) {
        ProvenanceGroup nestedGroup = compress(node.asGroup());
        if (nestedGroup.isVertical()) {
          compressedParticipants.addAll(nestedGroup.asVertical().getParticipants());
        } else if (nestedGroup.isHorizontal()) {
          compressedParticipants.add(new UnidentifiedParticipant(nestedGroup));
        } else {
          throw new Unreachable();
        }
      } else {
        throw new Unreachable();
      }
    }
    return new VerticalProvenanceGroup(verticalGroup.getTarget(), compressedParticipants.build());
  }

  private static HorizontalProvenanceGroup compressHorizontal(
      HorizontalProvenanceGroup horizontalGroup) {
    if (horizontalGroup.isIdentified()) {
      return compressIdentifiedHorizontal(horizontalGroup.asIdentified());
    }
    if (horizontalGroup.isUnidentified()) {
      return compressUnidentifiedHorizontal(horizontalGroup.asUnidentified());
    }
    throw new Unreachable();
  }

  private static IdentifiedHorizontalProvenanceGroup compressIdentifiedHorizontal(
      IdentifiedHorizontalProvenanceGroup identified) {
    return new IdentifiedHorizontalProvenanceGroup(
        identified.getTarget(),
        identified.getClassIdField(),
        ListUtils.map(identified.getParticipants(), MergedClassesProvenance::compressParticipant));
  }

  private static UnidentifiedHorizontalProvenanceGroup compressUnidentifiedHorizontal(
      UnidentifiedHorizontalProvenanceGroup unidentified) {
    ImmutableList.Builder<UnidentifiedParticipant> compressedParticipants = ImmutableList.builder();
    for (UnidentifiedParticipant participant : unidentified.getParticipants()) {
      ProvenanceNode node = participant.getNode();
      if (node.isClass()) {
        compressedParticipants.add(participant);
      } else if (node.isGroup()) {
        ProvenanceGroup nestedGroup = compress(node.asGroup());
        if (nestedGroup.isVertical()) {
          compressedParticipants.add(new UnidentifiedParticipant(nestedGroup));
        } else if (nestedGroup.isHorizontal()) {
          HorizontalProvenanceGroup nestedHorizontal = nestedGroup.asHorizontal();
          if (nestedHorizontal.isIdentified()) {
            compressedParticipants.add(new UnidentifiedParticipant(nestedHorizontal));
          } else if (nestedHorizontal.isUnidentified()) {
            compressedParticipants.addAll(nestedHorizontal.asUnidentified().getParticipants());
          } else {
            throw new Unreachable();
          }
        } else {
          throw new Unreachable();
        }
      } else {
        throw new Unreachable();
      }
    }
    return new UnidentifiedHorizontalProvenanceGroup(
        unidentified.getTarget(), compressedParticipants.build());
  }

  private static Participant compressParticipant(Participant participant) {
    if (participant.isIdentified()) {
      IdentifiedParticipant identified = participant.asIdentified();
      return new IdentifiedParticipant(compressNode(identified.getNode()), identified.getClassId());
    }
    if (participant.isUnidentified()) {
      return compressUnidentifiedParticipant(participant.asUnidentified());
    }
    throw new Unreachable();
  }

  private static UnidentifiedParticipant compressUnidentifiedParticipant(
      UnidentifiedParticipant participant) {
    return new UnidentifiedParticipant(compressNode(participant.getNode()));
  }

  private static ProvenanceNode compressNode(ProvenanceNode node) {
    if (node.isClass()) {
      return node;
    }
    if (node.isGroup()) {
      return compress(node.asGroup());
    }
    throw new Unreachable();
  }
}

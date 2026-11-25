// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.identifiedGroup;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.identifiedParticipant;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.matchesFieldEntries;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.unidentifiedGroup;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.unidentifiedParticipant;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ClassIdInfo;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ClassProvenance;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.IdentifiedParticipant;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.IdentifiedProvenanceGroup;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.Participant;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ProvenanceGroup;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.UnidentifiedParticipant;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.UnidentifiedProvenanceGroup;
import com.android.tools.r8.graph.lens.NestedGraphLens;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.collections.BidirectionalManyToOneRepresentativeHashMap;
import com.android.tools.r8.utils.internal.collections.EmptyBidirectionalOneToOneMap;
import com.android.tools.r8.utils.internal.collections.MutableBidirectionalManyToOneRepresentativeMap;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergedClassesProvenanceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  private static AppView<?> createTestAppView() {
    InternalOptions options = new InternalOptions();
    options.getTestingOptions().enableExperimentalMapFileVersion = true;
    AppInfo appInfo =
        AppInfo.createInitialAppInfo(
            DexApplication.builder(options).build(Timing.empty()),
            GlobalSyntheticsStrategy.forNonSynthesizing());
    return AppView.createForD8(appInfo);
  }

  private static DexType type(DexItemFactory factory, String name) {
    return factory.createType("L" + name + ";");
  }

  private static DexField intField(DexItemFactory factory, DexType holder, String name) {
    return factory.createField(holder, factory.intType, name);
  }

  private static ClassIdInfo classIdInfo(
      DexField classIdField, DexType first, int firstId, DexType second, int secondId) {
    return ClassIdInfo.createForTesting(
        classIdField, ImmutableMap.of(first, firstId, second, secondId));
  }

  private static void pushLens(
      AppView<?> appView,
      Map<DexType, DexType> typeMoves,
      Map<DexField, DexField> fieldMoves,
      Map<DexField, DexField> fieldRepresentatives) {
    MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> typeMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    typeMoves.forEach(typeMap::put);
    MutableBidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    fieldMoves.forEach(fieldMap::put);
    fieldRepresentatives.forEach(fieldMap::setRepresentative);
    appView.setGraphLens(
        new NestedGraphLens(appView, fieldMap, new EmptyBidirectionalOneToOneMap<>(), typeMap));
  }

  private static void pushSyntheticFinalizationLikeLens(
      AppView<?> appView,
      Map<DexType, DexType> typeMoves,
      Map<DexType, DexType> typeRepresentatives,
      Map<DexField, DexField> fieldMoves,
      Map<DexField, DexField> fieldRepresentatives) {
    MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> typeMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    typeMoves.forEach(typeMap::put);
    typeRepresentatives.forEach(typeMap::setRepresentative);
    MutableBidirectionalManyToOneRepresentativeMap<DexField, DexField> fieldMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    fieldMoves.forEach(fieldMap::put);
    fieldRepresentatives.forEach(fieldMap::setRepresentative);
    appView.setGraphLens(
        new NestedGraphLens(appView, fieldMap, new EmptyBidirectionalOneToOneMap<>(), typeMap) {
          @Override
          public DexType getPreviousClassType(DexType type) {
            return type;
          }

          @Override
          public DexType getPreviousClassTypeThroughSyntheticFinalization(DexType type) {
            return super.getPreviousClassType(type);
          }
        });
  }

  @Test
  public void testEmptyAndThrowing() {
    AppView<?> appView = createTestAppView();
    DexItemFactory factory = appView.dexItemFactory();
    DexType a = type(factory, "A");
    DexType b = type(factory, "B");

    MergedClassesProvenance empty = MergedClassesProvenance.empty();
    empty.recordMerge(appView, a, Collections.singleton(a), Collections.singleton(b), null, null);
    empty.recordMerge(appView, a, ImmutableList.of(a, b), null, null, Collections.emptyMap());
    assertThrows(Unreachable.class, () -> empty.getGroupForOriginalType(a));
    assertThrows(Unreachable.class, () -> empty.getGroup(appView, a));

    MergedClassesProvenance throwing = MergedClassesProvenance.throwing();
    assertThrows(Unreachable.class, () -> throwing.getGroupForOriginalType(a));
    assertThrows(Unreachable.class, () -> throwing.getGroup(appView, a));
    assertThrows(
        Unreachable.class,
        () ->
            throwing.recordMerge(
                appView, a, Collections.singleton(a), Collections.singleton(b), null, null));
    assertThrows(
        Unreachable.class,
        () ->
            throwing.recordMerge(
                appView, a, ImmutableList.of(a, b), null, null, Collections.emptyMap()));
  }

  @Test
  public void testSingleRoundMergeWithFields() {
    AppView<?> appView = createTestAppView();
    DexItemFactory factory = appView.dexItemFactory();
    MergedClassesProvenance provenance = new MergedClassesProvenanceImpl();

    DexType a = type(factory, "A");
    DexType b = type(factory, "B");
    DexField ax = intField(factory, a, "ax");
    DexField ay = intField(factory, a, "ay");
    DexField az = intField(factory, a, "az");
    DexField bx = intField(factory, b, "bx");
    DexField by = intField(factory, b, "by");

    provenance.recordMerge(
        appView,
        a,
        ImmutableList.of(b, a),
        null,
        null,
        ImmutableMap.of(ay, ImmutableList.of(by, ay), ax, ImmutableList.of(bx, ax)));

    ProvenanceGroup group = provenance.getGroupForOriginalType(a);
    assertNotNull(group);
    assertTrue(group.hasMergedFields());
    assertThat(
        group,
        unidentifiedGroup(
            a,
            unidentifiedParticipant(a, true, ImmutableMap.of(ax, ax, ay, ay)),
            unidentifiedParticipant(b, true, ImmutableMap.of(ax, bx, ay, by))));

    for (Participant participant : group.getParticipants()) {
      assertNull(participant.getMergedField(az));
      Map<DexField, DexField> visited = new IdentityHashMap<>();
      participant.forEachMergedField(visited::put);
      assertThat(visited.entrySet(), matchesFieldEntries(participant.getMergedFields()));
      if (participant.getType().isIdenticalTo(a)) {
        assertSame(ax, participant.getMergedField(ax));
      } else {
        assertSame(bx, participant.getMergedField(ax));
      }
    }
  }

  @Test
  public void testMultiRoundPairsImmediateParticipantWithOriginalField() {
    AppView<?> appView = createTestAppView();
    DexItemFactory factory = appView.dexItemFactory();
    MergedClassesProvenance provenance = new MergedClassesProvenanceImpl();

    DexType a2 = type(factory, "A2");
    DexType a1 = type(factory, "A1");
    DexType b = type(factory, "B");
    DexType a1Repackaged = type(factory, "r/A1");
    DexType bRepackaged = type(factory, "r/B");

    DexField a2f = intField(factory, a2, "f");
    DexField a1fMoved = intField(factory, a1, "f$1");
    DexField a1fRepackaged = intField(factory, a1Repackaged, "f$1");
    DexField bg = intField(factory, b, "g");
    DexField bgRepackaged = intField(factory, bRepackaged, "g");
    DexField classIdField = intField(factory, a1Repackaged, "$r8$classId");

    // Round 1: Vertical merge A2 into A1 (moves A2.f -> A1.f$1).
    provenance.recordMerge(
        appView, a1, Collections.singleton(a1), Collections.singleton(a2), null, null);
    pushLens(appView, ImmutableMap.of(), ImmutableMap.of(a2f, a1fMoved), ImmutableMap.of());

    // Repackaging renames A1 -> r/A1 and B -> r/B.
    pushLens(
        appView,
        ImmutableMap.of(a1, a1Repackaged, b, bRepackaged),
        ImmutableMap.of(a1fMoved, a1fRepackaged, bg, bgRepackaged),
        ImmutableMap.of());

    // Round 2: Identified horizontal merge of r/A1 and r/B into r/A1, merging r/A1.f$1 and r/B.g.
    provenance.recordMerge(
        appView,
        a1Repackaged,
        ImmutableList.of(a1Repackaged, bRepackaged),
        null,
        classIdInfo(classIdField, a1Repackaged, 0, bRepackaged, 1),
        ImmutableMap.of(a1fRepackaged, ImmutableList.of(a1fRepackaged, bgRepackaged)));

    ProvenanceGroup group = provenance.getGroupForOriginalType(a1);
    assertNotNull(group);
    // Immediate participant is A1 (the vertical group), paired with original field A2.f.
    assertThat(
        group,
        identifiedGroup(
            a1,
            classIdField,
            identifiedParticipant(
                unidentifiedGroup(
                    a1,
                    unidentifiedParticipant(a1, true, Collections.emptyMap()),
                    unidentifiedParticipant(a2, false, Collections.emptyMap())),
                0,
                true,
                ImmutableMap.of(a2f, a2f)),
            identifiedParticipant(b, 1, true, ImmutableMap.of(a2f, bg))));
  }

  @Test
  public void testNonParticipantTargetClassAndField() {
    AppView<?> appView = createTestAppView();
    DexItemFactory factory = appView.dexItemFactory();
    MergedClassesProvenance provenance = new MergedClassesProvenanceImpl();

    DexType a = type(factory, "A");
    DexType b = type(factory, "B");
    DexType c = type(factory, "C");
    DexType d = type(factory, "D");
    DexType e = type(factory, "E");
    DexField ax = intField(factory, a, "x");
    DexField bx = intField(factory, b, "x");
    DexField cx = intField(factory, c, "x");
    DexField dx = intField(factory, d, "x");
    DexField ex = intField(factory, e, "x");

    // Step 1: A and B merge into new target class C, merging {A.x, B.x} -> C.x.
    provenance.recordMerge(
        appView,
        c,
        ImmutableList.of(a, b),
        null,
        null,
        ImmutableMap.of(cx, ImmutableList.of(ax, bx)));

    ProvenanceGroup step1 = provenance.getGroupForOriginalType(c);
    assertNotNull(step1);
    assertThat(
        step1,
        unidentifiedGroup(
            c,
            unidentifiedParticipant(a, true, ImmutableMap.of(cx, ax)),
            unidentifiedParticipant(b, true, ImmutableMap.of(cx, bx))));

    // Step 2: C and D merge into new target class E, merging {C.x, D.x} -> E.x.
    provenance.recordMerge(
        appView,
        e,
        ImmutableList.of(c, d),
        null,
        null,
        ImmutableMap.of(ex, ImmutableList.of(cx, dx)));

    ProvenanceGroup step2 = provenance.getGroupForOriginalType(e);
    assertNotNull(step2);
    assertThat(
        step2,
        unidentifiedGroup(
            e,
            unidentifiedParticipant(sameInstance(step1), true, ImmutableMap.of(ex, cx)),
            unidentifiedParticipant(d, true, ImmutableMap.of(ex, dx))));
  }

  @Test
  public void testAssertionsForMergedFieldConsistency() {
    AppView<?> appView = createTestAppView();
    DexItemFactory factory = appView.dexItemFactory();

    DexType a = type(factory, "A");
    DexType b = type(factory, "B");
    DexType c = type(factory, "C");
    DexField ax = intField(factory, a, "ax");
    DexField ay = intField(factory, a, "ay");
    DexField cx = intField(factory, c, "cx");
    DexField classIdField = intField(factory, a, "$r8$classId");

    // Non-participant holder in recordMerge.
    assertThrows(
        AssertionError.class,
        () ->
            new MergedClassesProvenanceImpl()
                .recordMerge(
                    appView,
                    a,
                    ImmutableList.of(a, b),
                    null,
                    null,
                    ImmutableMap.of(ax, ImmutableList.of(ax, cx))));

    // Fewer than 2 source fields in recordMerge.
    assertThrows(
        AssertionError.class,
        () ->
            new MergedClassesProvenanceImpl()
                .recordMerge(
                    appView,
                    a,
                    ImmutableList.of(a, b),
                    null,
                    null,
                    ImmutableMap.of(ax, ImmutableList.of(ax))));

    // Multiple fields from the same participant merged into one target field.
    assertThrows(
        AssertionError.class,
        () ->
            new MergedClassesProvenanceImpl()
                .recordMerge(
                    appView,
                    a,
                    ImmutableList.of(a, b),
                    null,
                    null,
                    ImmutableMap.of(ax, ImmutableList.of(ax, ay))));

    // Two current target fields unwinding to the same original target field.
    AppView<?> duplicateTargetAppView = createTestAppView();
    DexItemFactory dupFactory = duplicateTargetAppView.dexItemFactory();
    DexType dupA = type(dupFactory, "A");
    DexType dupB = type(dupFactory, "B");
    DexField dupAx = intField(dupFactory, dupA, "ax");
    DexField dupAxAlias = intField(dupFactory, dupA, "axAlias");
    DexField dupBx = intField(dupFactory, dupB, "bx");
    pushLens(
        duplicateTargetAppView,
        ImmutableMap.of(),
        ImmutableMap.of(dupAx, dupAxAlias),
        ImmutableMap.of());
    assertThrows(
        AssertionError.class,
        () ->
            new MergedClassesProvenanceImpl()
                .recordMerge(
                    duplicateTargetAppView,
                    dupA,
                    ImmutableList.of(dupA, dupB),
                    null,
                    null,
                    ImmutableMap.of(
                        dupAx,
                        ImmutableList.of(dupAx, dupBx),
                        dupAxAlias,
                        ImmutableList.of(dupAxAlias, dupBx))));

    // Duplicate original field mapped to two target fields on the same participant.
    assertThrows(
        AssertionError.class,
        () ->
            new UnidentifiedParticipant(
                new ClassProvenance(a), true, ImmutableMap.of(ax, ax, ay, ax)));

    // Singleton target field only referenced by one participant in an unidentified group.
    UnidentifiedParticipant soloA =
        new UnidentifiedParticipant(new ClassProvenance(a), true, ImmutableMap.of(ax, ax));
    UnidentifiedParticipant emptyB =
        new UnidentifiedParticipant(new ClassProvenance(b), true, null);
    assertThrows(
        AssertionError.class,
        () -> new UnidentifiedProvenanceGroup(a, ImmutableList.of(soloA, emptyB)));

    // Singleton target field only referenced by one participant in an identified group.
    IdentifiedParticipant idSoloA =
        new IdentifiedParticipant(new ClassProvenance(a), 0, true, ImmutableMap.of(ax, ax));
    IdentifiedParticipant idEmptyB =
        new IdentifiedParticipant(new ClassProvenance(b), 1, true, null);
    assertThrows(
        AssertionError.class,
        () -> new IdentifiedProvenanceGroup(a, classIdField, ImmutableList.of(idSoloA, idEmptyB)));
  }

  @Test
  public void testTraversalThroughSyntheticFinalizationLens() {
    AppView<?> appView = createTestAppView();
    DexItemFactory factory = appView.dexItemFactory();
    MergedClassesProvenance provenance = new MergedClassesProvenanceImpl();

    DexType iface = type(factory, "I");
    DexType internalLambda0 = type(factory, "Main$$InternalSyntheticLambda0");
    DexType externalLambda0 = type(factory, "Main$$ExternalSyntheticLambda0");
    DexType repackagedLambda0 = type(factory, "r/Main$$ExternalSyntheticLambda0");
    DexType internalLambda1 = type(factory, "Main$$InternalSyntheticLambda1");
    DexType externalLambda1 = type(factory, "Main$$ExternalSyntheticLambda1");
    DexType repackagedLambda1 = type(factory, "r/Main$$ExternalSyntheticLambda1");
    DexField internalField0 = intField(factory, internalLambda0, "f$0");
    DexField externalField0 = intField(factory, externalLambda0, "f$0");
    DexField repackagedField0 = intField(factory, repackagedLambda0, "f$0");
    DexField internalField1 = intField(factory, internalLambda1, "f$0");
    DexField externalField1 = intField(factory, externalLambda1, "f$0");
    DexField repackagedField1 = intField(factory, repackagedLambda1, "f$0");
    DexField classIdField = intField(factory, repackagedLambda0, "$r8$classId");

    // Initial vertical merge of I into unfinalized internalLambda0.
    provenance.recordMerge(
        appView,
        internalLambda0,
        Collections.singleton(internalLambda0),
        Collections.singleton(iface),
        null,
        null);
    ProvenanceGroup vcmGroup = provenance.getGroupForOriginalType(internalLambda0);
    assertNotNull(vcmGroup);

    // Synthetic finalization renames internalLambda{0,1} -> externalLambda{0,1}; provenance is
    // untouched.
    pushSyntheticFinalizationLikeLens(
        appView,
        ImmutableMap.of(internalLambda0, externalLambda0, internalLambda1, externalLambda1),
        ImmutableMap.of(),
        ImmutableMap.of(internalField0, externalField0, internalField1, externalField1),
        ImmutableMap.of());

    // Repackaging renames externalLambda{0,1} -> repackagedLambda{0,1}.
    pushLens(
        appView,
        ImmutableMap.of(externalLambda0, repackagedLambda0, externalLambda1, repackagedLambda1),
        ImmutableMap.of(externalField0, repackagedField0, externalField1, repackagedField1),
        ImmutableMap.of());

    // Query traverses through Repackaging + SyntheticFinalization back to internalLambda0.
    assertSame(vcmGroup, provenance.getGroup(appView, repackagedLambda0));
    assertNull(provenance.getGroupForOriginalType(externalLambda0));

    // Subsequent horizontal merge nests the existing VCM group without mutating it and aligns
    // participant types with original field holders.
    provenance.recordMerge(
        appView,
        repackagedLambda0,
        ImmutableList.of(repackagedLambda0, repackagedLambda1),
        null,
        classIdInfo(classIdField, repackagedLambda0, 0, repackagedLambda1, 1),
        ImmutableMap.of(repackagedField0, ImmutableList.of(repackagedField0, repackagedField1)));

    ProvenanceGroup combinedGroup = provenance.getGroup(appView, repackagedLambda0);
    assertNotNull(combinedGroup);
    assertSame(combinedGroup, provenance.getGroupForOriginalType(internalLambda0));
    assertThat(
        combinedGroup,
        identifiedGroup(
            internalLambda0,
            classIdField,
            identifiedParticipant(
                sameInstance(vcmGroup), 0, true, ImmutableMap.of(internalField0, internalField0)),
            identifiedParticipant(
                internalLambda1, 1, true, ImmutableMap.of(internalField0, internalField1))));
  }

  @Test
  public void testTraversalThroughSharedSyntheticAndSwap() {
    AppView<?> appView = createTestAppView();
    DexItemFactory factory = appView.dexItemFactory();
    MergedClassesProvenance provenance = new MergedClassesProvenanceImpl();

    DexType i0 = type(factory, "I0");
    DexType i1 = type(factory, "I1");
    DexType s0 = type(factory, "S0");
    DexType s1 = type(factory, "S1");
    DexType shared0 = type(factory, "SharedInternal0");
    DexType shared1 = type(factory, "SharedInternal1");
    DexType sharedExt = type(factory, "SharedExternal");
    DexType otherInternal = type(factory, "OtherInternal");
    DexType otherExt = type(factory, "OtherExternal");
    DexField sharedField0 = intField(factory, shared0, "f$0");
    DexField sharedField1 = intField(factory, shared1, "f$0");
    DexField sharedExtField = intField(factory, sharedExt, "f$0");
    DexField otherField = intField(factory, otherInternal, "f$0");
    DexField otherExtField = intField(factory, otherExt, "f$0");

    provenance.recordMerge(
        appView, s0, Collections.singleton(s0), Collections.singleton(i0), null, null);
    provenance.recordMerge(
        appView, s1, Collections.singleton(s1), Collections.singleton(i1), null, null);

    // Synthetic finalization swaps s0 <-> s1, shares {shared0, shared1} -> sharedExt with
    // representative shared0, and renames otherInternal -> otherExt.
    pushSyntheticFinalizationLikeLens(
        appView,
        ImmutableMap.of(
            s0, s1, s1, s0, shared0, sharedExt, shared1, sharedExt, otherInternal, otherExt),
        ImmutableMap.of(sharedExt, shared0),
        ImmutableMap.of(
            sharedField0, sharedExtField, sharedField1, sharedExtField, otherField, otherExtField),
        ImmutableMap.of(sharedExtField, sharedField0));

    assertThat(
        provenance.getGroup(appView, s1),
        unidentifiedGroup(
            s0,
            unidentifiedParticipant(s0, true, Collections.emptyMap()),
            unidentifiedParticipant(i0, false, Collections.emptyMap())));
    assertThat(
        provenance.getGroup(appView, s0),
        unidentifiedGroup(
            s1,
            unidentifiedParticipant(s1, true, Collections.emptyMap()),
            unidentifiedParticipant(i1, false, Collections.emptyMap())));

    // Horizontal merge involving shared synthetic uses representative shared0 for both class and
    // field.
    provenance.recordMerge(
        appView,
        sharedExt,
        ImmutableList.of(sharedExt, otherExt),
        null,
        null,
        ImmutableMap.of(sharedExtField, ImmutableList.of(sharedExtField, otherExtField)));

    assertThat(
        provenance.getGroup(appView, sharedExt),
        unidentifiedGroup(
            shared0,
            unidentifiedParticipant(shared0, true, ImmutableMap.of(sharedField0, sharedField0)),
            unidentifiedParticipant(
                otherInternal, true, ImmutableMap.of(sharedField0, otherField))));
  }
}

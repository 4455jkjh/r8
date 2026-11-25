// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.identifiedGroup;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.identifiedParticipant;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.unidentifiedGroup;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.unidentifiedParticipant;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

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
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ProvenanceGroup;
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

  private static void pushLens(AppView<?> appView, Map<DexType, DexType> typeMoves) {
    MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> typeMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    typeMoves.forEach(typeMap::put);
    appView.setGraphLens(
        new NestedGraphLens(
            appView,
            new EmptyBidirectionalOneToOneMap<>(),
            new EmptyBidirectionalOneToOneMap<>(),
            typeMap));
  }

  private static void pushSyntheticFinalizationLikeLens(
      AppView<?> appView,
      Map<DexType, DexType> typeMoves,
      Map<DexType, DexType> typeRepresentatives) {
    MutableBidirectionalManyToOneRepresentativeMap<DexType, DexType> typeMap =
        BidirectionalManyToOneRepresentativeHashMap.newIdentityHashMap();
    typeMoves.forEach(typeMap::put);
    typeRepresentatives.forEach(typeMap::setRepresentative);
    appView.setGraphLens(
        new NestedGraphLens(
            appView,
            new EmptyBidirectionalOneToOneMap<>(),
            new EmptyBidirectionalOneToOneMap<>(),
            typeMap) {
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
    empty.recordMerge(appView, a, Collections.singleton(a), Collections.singleton(b), null);
    assertThrows(Unreachable.class, () -> empty.getGroupForOriginalType(a));
    assertThrows(Unreachable.class, () -> empty.getGroup(appView, a));

    MergedClassesProvenance throwing = MergedClassesProvenance.throwing();
    assertThrows(Unreachable.class, () -> throwing.getGroupForOriginalType(a));
    assertThrows(Unreachable.class, () -> throwing.getGroup(appView, a));
    assertThrows(
        Unreachable.class,
        () ->
            throwing.recordMerge(
                appView, a, Collections.singleton(a), Collections.singleton(b), null));
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
    DexField classIdField = intField(factory, repackagedLambda0, "$r8$classId");

    // Initial vertical merge of I into unfinalized internalLambda0.
    provenance.recordMerge(
        appView,
        internalLambda0,
        Collections.singleton(internalLambda0),
        Collections.singleton(iface),
        null);
    ProvenanceGroup vcmGroup = provenance.getGroupForOriginalType(internalLambda0);
    assertNotNull(vcmGroup);

    // Synthetic finalization renames internalLambda{0,1} -> externalLambda{0,1}; provenance is
    // untouched.
    pushSyntheticFinalizationLikeLens(
        appView,
        ImmutableMap.of(internalLambda0, externalLambda0, internalLambda1, externalLambda1),
        ImmutableMap.of());

    // Repackaging renames externalLambda{0,1} -> repackagedLambda{0,1}.
    pushLens(
        appView,
        ImmutableMap.of(externalLambda0, repackagedLambda0, externalLambda1, repackagedLambda1));

    // Query traverses through Repackaging + SyntheticFinalization back to internalLambda0.
    assertSame(vcmGroup, provenance.getGroup(appView, repackagedLambda0));
    assertNull(provenance.getGroupForOriginalType(externalLambda0));

    // Subsequent horizontal merge nests the existing VCM group without mutating it.
    provenance.recordMerge(
        appView,
        repackagedLambda0,
        ImmutableList.of(repackagedLambda0, repackagedLambda1),
        null,
        classIdInfo(classIdField, repackagedLambda0, 0, repackagedLambda1, 1));

    ProvenanceGroup combinedGroup = provenance.getGroup(appView, repackagedLambda0);
    assertNotNull(combinedGroup);
    assertSame(combinedGroup, provenance.getGroupForOriginalType(internalLambda0));
    assertThat(
        combinedGroup,
        identifiedGroup(
            internalLambda0,
            classIdField,
            identifiedParticipant(sameInstance(vcmGroup), 0, true),
            identifiedParticipant(internalLambda1, 1, true)));
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

    provenance.recordMerge(appView, s0, Collections.singleton(s0), Collections.singleton(i0), null);
    provenance.recordMerge(appView, s1, Collections.singleton(s1), Collections.singleton(i1), null);

    // Synthetic finalization swaps s0 <-> s1, shares {shared0, shared1} -> sharedExt with
    // representative shared0, and renames otherInternal -> otherExt.
    pushSyntheticFinalizationLikeLens(
        appView,
        ImmutableMap.of(
            s0, s1, s1, s0, shared0, sharedExt, shared1, sharedExt, otherInternal, otherExt),
        ImmutableMap.of(sharedExt, shared0));

    assertThat(
        provenance.getGroup(appView, s1),
        unidentifiedGroup(
            s0, unidentifiedParticipant(s0, true), unidentifiedParticipant(i0, false)));
    assertThat(
        provenance.getGroup(appView, s0),
        unidentifiedGroup(
            s1, unidentifiedParticipant(s1, true), unidentifiedParticipant(i1, false)));

    // Horizontal merge involving shared synthetic resolves through representative shared0.
    provenance.recordMerge(appView, sharedExt, ImmutableList.of(sharedExt, otherExt), null, null);

    assertThat(
        provenance.getGroup(appView, sharedExt),
        unidentifiedGroup(
            shared0,
            unidentifiedParticipant(shared0, true),
            unidentifiedParticipant(otherInternal, true)));
  }
}

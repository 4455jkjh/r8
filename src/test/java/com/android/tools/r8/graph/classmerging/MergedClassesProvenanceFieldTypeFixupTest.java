// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.matchesParticipants;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.participant;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ProvenanceGroup;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergedClassesProvenanceFieldTypeFixupTest extends MergedClassesProvenanceTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    BooleanBox checked = new BooleanBox();
    testForR8(parameters)
        .addProgramClassesAndInnerClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .addOptionsModification(
            options -> {
              options.getTestingOptions().enableExperimentalMapFileVersion = true;
              options.getTestingOptions().horizontalClassMergingTarget =
                  (appView, candidates, target) -> {
                    DexProgramClass preferred =
                        pinTarget(appView, candidates, null, Main.ValA.class);
                    if (preferred != null) {
                      return preferred;
                    }
                    return pinTarget(appView, candidates, target, Main.HolderA.class);
                  };
              options.getTestingOptions().mergedClassesProvenanceConsumer =
                  (appView, provenance) -> {
                    checked.set();
                    DexItemFactory factory = appView.dexItemFactory();
                    DexType valA = toDexType(Main.ValA.class, factory);
                    DexType valB = toDexType(Main.ValB.class, factory);
                    DexType holderA = toDexType(Main.HolderA.class, factory);
                    DexType holderB = toDexType(Main.HolderB.class, factory);

                    DexField valAv = factory.createField(valA, factory.intType, "v");
                    DexField valBw = factory.createField(valB, factory.intType, "w");
                    DexField holderARef = factory.createField(holderA, valA, "refA");
                    DexField holderBRef = factory.createField(holderB, valB, "refB");

                    ProvenanceGroup valGroup = provenance.getGroupForOriginalType(valA);
                    assertNotNull(valGroup);
                    assertThat(
                        valGroup.getParticipants(),
                        matchesParticipants(
                            participant(valA, ImmutableMap.of(valAv, valAv)),
                            participant(valB, ImmutableMap.of(valAv, valBw))));

                    ProvenanceGroup holderGroup = provenance.getGroupForOriginalType(holderA);
                    assertNotNull(holderGroup);
                    assertThat(
                        holderGroup.getParticipants(),
                        matchesParticipants(
                            participant(holderA, ImmutableMap.of(holderARef, holderARef)),
                            participant(holderB, ImmutableMap.of(holderARef, holderBRef))));
                  };
            })
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(Main.ValA.class, Main.ValB.class)
                    .assertIsCompleteMergeGroup(Main.HolderA.class, Main.HolderB.class)
                    .assertNoOtherClassesMerged())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("7", "8");
    assertTrue(checked.isTrue());
  }

  public static class Main {

    @NeverClassInline
    static class ValA {
      int v;

      @NeverInline
      ValA(int v) {
        this.v = v;
      }

      @NeverInline
      int get() {
        return v;
      }
    }

    @NeverClassInline
    static class ValB {
      int w;

      @NeverInline
      ValB(int w) {
        this.w = w;
      }

      @NeverInline
      int get() {
        return w;
      }
    }

    @NeverClassInline
    static class HolderA {
      ValA refA;

      @NeverInline
      HolderA(ValA refA) {
        this.refA = refA;
      }

      @NeverInline
      void print() {
        System.out.println(refA.get());
      }
    }

    @NeverClassInline
    static class HolderB {
      ValB refB;

      @NeverInline
      HolderB(ValB refB) {
        this.refB = refB;
      }

      @NeverInline
      void print() {
        System.out.println(refB.get());
      }
    }

    public static void main(String[] args) {
      new HolderA(new ValA(args.length + 7)).print();
      new HolderB(new ValB(args.length + 8)).print();
    }
  }
}

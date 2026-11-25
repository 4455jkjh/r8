// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.matchesParticipants;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.participant;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.unidentifiedGroup;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.unidentifiedParticipant;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ProvenanceGroup;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.google.common.collect.ImmutableMap;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergedClassesProvenanceVerticalThenHorizontalFieldTest
    extends MergedClassesProvenanceTestBase {

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
                  (appView, candidates, target) ->
                      pinTarget(appView, candidates, target, Main.Sub.class);
              options.getTestingOptions().mergedClassesProvenanceConsumer =
                  (appView, provenance) -> {
                    checked.set();
                    DexItemFactory factory = appView.dexItemFactory();
                    DexType superType = toDexType(Main.Super.class, factory);
                    DexType subType = toDexType(Main.Sub.class, factory);
                    DexType otherType = toDexType(Main.Other.class, factory);
                    DexField superField =
                        factory.createField(superType, factory.intType, "superField");
                    DexField otherField =
                        factory.createField(otherType, factory.intType, "otherField");

                    ProvenanceGroup group = provenance.getGroupForOriginalType(subType);
                    assertNotNull(group);
                    // Immediate participant is Sub's vertical group; original field is
                    // Super.superField.
                    assertThat(
                        group.getParticipants(),
                        matchesParticipants(
                            participant(
                                unidentifiedGroup(
                                    subType,
                                    unidentifiedParticipant(subType, true, Collections.emptyMap()),
                                    unidentifiedParticipant(
                                        superType, false, Collections.emptyMap())),
                                ImmutableMap.of(superField, superField)),
                            participant(otherType, ImmutableMap.of(superField, otherField))));
                  };
            })
        .addVerticallyMergedClassesInspector(
            inspector ->
                inspector.assertMergedIntoSubtype(Main.Super.class).assertNoOtherClassesMerged())
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(Main.Sub.class, Main.Other.class)
                    .assertNoOtherClassesMerged())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "99");
    assertTrue(checked.isTrue());
  }

  public static class Main {

    static class Super {
      int superField;

      @NeverInline
      Super(int superField) {
        this.superField = superField;
      }
    }

    @NeverClassInline
    static class Sub extends Super {
      @NeverInline
      Sub(int superField) {
        super(superField);
      }

      @NeverInline
      void print() {
        System.out.println(superField);
      }
    }

    @NeverClassInline
    static class Other {
      int otherField;

      @NeverInline
      Other(int otherField) {
        this.otherField = otherField;
      }

      @NeverInline
      void print() {
        System.out.println(otherField);
      }
    }

    public static void main(String[] args) {
      new Sub(args.length + 42).print();
      new Other(args.length + 99).print();
    }
  }
}

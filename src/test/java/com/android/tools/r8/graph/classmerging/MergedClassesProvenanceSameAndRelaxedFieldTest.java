// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.matchesParticipants;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.participant;
import static org.hamcrest.CoreMatchers.hasItems;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.ProvenanceGroup;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergedClassesProvenanceSameAndRelaxedFieldTest
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
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addOptionsModification(
            options -> {
              options.getTestingOptions().enableExperimentalMapFileVersion = true;
              options.getTestingOptions().horizontalClassMergingTarget =
                  (appView, candidates, target) ->
                      pinTarget(appView, candidates, target, Main.A.class);
              options.getTestingOptions().mergedClassesProvenanceConsumer =
                  (appView, provenance) -> {
                    checked.set();
                    DexItemFactory factory = appView.dexItemFactory();
                    DexType typeA = toDexType(Main.A.class, factory);
                    DexType typeB = toDexType(Main.B.class, factory);
                    DexType typeSub1 = toDexType(Main.Sub1.class, factory);
                    DexType typeSub2 = toDexType(Main.Sub2.class, factory);

                    DexField aInt = factory.createField(typeA, factory.intType, "aInt");
                    DexField bInt = factory.createField(typeB, factory.intType, "bInt");
                    DexField aRef = factory.createField(typeA, typeSub1, "aRef");
                    DexField bRef = factory.createField(typeB, typeSub2, "bRef");

                    ProvenanceGroup group = provenance.getGroupForOriginalType(typeA);
                    assertNotNull(group);
                    // Unmerged instance fields (aOnly, bOnly) and static fields (aStatic, bStatic)
                    // are not recorded as merged fields.
                    assertThat(
                        group.getParticipants(),
                        matchesParticipants(
                            participant(typeA, ImmutableMap.of(aInt, aInt, aRef, aRef)),
                            participant(typeB, ImmutableMap.of(aInt, bInt, aRef, bRef))));

                    // Surviving fields on the final target class unwind via GraphLens to each
                    // merged field target.
                    DexProgramClass finalA =
                        asProgramClassOrNull(
                            appView.contextIndependentDefinitionFor(
                                appView.graphLens().lookupType(typeA)));
                    assertNotNull(finalA);
                    Set<DexField> survivingOriginals = Sets.newIdentityHashSet();
                    for (DexEncodedField field : finalA.instanceFields()) {
                      survivingOriginals.add(
                          appView
                              .graphLens()
                              .getOriginalFieldSignature(
                                  field.getReference(), GraphLens.getIdentityLens()));
                    }
                    assertThat(survivingOriginals, hasItems(aInt, aRef));
                  };
            })
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(Main.A.class, Main.B.class)
                    .assertNoOtherClassesMerged())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "s1", "10", "100", "2", "s2", "20", "200");
    assertTrue(checked.isTrue());
  }

  @Test
  public void testDisabledWhenExperimentalMapVersionNotEnabled() throws Exception {
    BooleanBox checked = new BooleanBox();
    testForR8(parameters)
        .addProgramClassesAndInnerClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .addOptionsModification(
            options ->
                options.getTestingOptions().mergedClassesProvenanceConsumer =
                    (appView, provenance) -> {
                      checked.set();
                      DexType typeA = toDexType(Main.A.class, appView.dexItemFactory());
                      assertThrows(
                          Unreachable.class, () -> provenance.getGroupForOriginalType(typeA));
                    })
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1", "s1", "10", "100", "2", "s2", "20", "200");
    assertTrue(checked.isTrue());
  }

  public static class Main {

    @NoVerticalClassMerging
    static class Base {
      final String label;

      Base(String label) {
        this.label = label;
      }
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class Sub1 extends Base {
      @NeverInline
      Sub1(String label) {
        super(label);
      }
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class Sub2 extends Base {
      @NeverInline
      Sub2(String label) {
        super(label);
      }
    }

    @NeverClassInline
    static class A {
      static int aStatic;
      int aInt;
      Sub1 aRef;
      short aOnly;

      @NeverInline
      A(int aInt, Sub1 aRef, short aOnly) {
        this.aInt = aInt;
        this.aRef = aRef;
        this.aOnly = aOnly;
      }

      @NeverInline
      void print() {
        System.out.println(aInt);
        System.out.println(aRef.label);
        System.out.println(aOnly);
        System.out.println(aStatic);
      }
    }

    @NeverClassInline
    static class B {
      static int bStatic;
      int bInt;
      Sub2 bRef;
      byte bOnly;

      @NeverInline
      B(int bInt, Sub2 bRef, byte bOnly) {
        this.bInt = bInt;
        this.bRef = bRef;
        this.bOnly = bOnly;
      }

      @NeverInline
      void print() {
        System.out.println(bInt);
        System.out.println(bRef.label);
        System.out.println(bOnly);
        System.out.println(bStatic);
      }
    }

    public static void main(String[] args) {
      A.aStatic = args.length + 100;
      B.bStatic = args.length + 200;
      A a = new A(args.length + 1, new Sub1("s1"), (short) (args.length + 10));
      B b = new B(args.length + 2, new Sub2("s2"), (byte) (args.length + 20));
      a.print();
      b.print();
    }
  }
}

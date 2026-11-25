// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.matchesParticipants;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.participant;
import static org.hamcrest.CoreMatchers.anyOf;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergedClassesProvenanceSourceOnlyFieldMergeTest
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
                      pinTarget(appView, candidates, target, Main.Target.class);
              options.getTestingOptions().mergedClassesProvenanceConsumer =
                  (appView, provenance) -> {
                    checked.set();
                    DexItemFactory factory = appView.dexItemFactory();
                    DexType targetType = toDexType(Main.Target.class, factory);
                    DexType s1Type = toDexType(Main.Source1.class, factory);
                    DexType s2Type = toDexType(Main.Source2.class, factory);
                    DexField tLong = factory.createField(targetType, factory.longType, "tLong");
                    DexField s1Long = factory.createField(s1Type, factory.longType, "s1Long");
                    DexField s2Long = factory.createField(s2Type, factory.longType, "s2Long");
                    DexField s1Field = factory.createField(s1Type, factory.intType, "s1");
                    DexField s2Field = factory.createField(s2Type, factory.intType, "s2");

                    ProvenanceGroup group = provenance.getGroupForOriginalType(targetType);
                    assertNotNull(group);
                    assertThat(
                        group.getParticipants(),
                        anyOf(
                            matchesParticipants(
                                participant(targetType, ImmutableMap.of(tLong, tLong)),
                                participant(
                                    s1Type, ImmutableMap.of(tLong, s1Long, s1Field, s1Field)),
                                participant(
                                    s2Type, ImmutableMap.of(tLong, s2Long, s1Field, s2Field))),
                            matchesParticipants(
                                participant(targetType, ImmutableMap.of(tLong, tLong)),
                                participant(
                                    s1Type, ImmutableMap.of(tLong, s1Long, s2Field, s1Field)),
                                participant(
                                    s2Type, ImmutableMap.of(tLong, s2Long, s2Field, s2Field)))));
                  };
            })
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(
                        Main.Target.class, Main.Source1.class, Main.Source2.class)
                    .assertNoOtherClassesMerged())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("100:1", "200:11", "300:22:true");
    assertTrue(checked.isTrue());
  }

  public static class Main {

    @NeverClassInline
    static class Target {
      long tLong;
      byte tByte;

      @NeverInline
      Target(long tLong, byte tByte) {
        this.tLong = tLong;
        this.tByte = tByte;
      }

      @NeverInline
      void printTarget() {
        System.out.println(tLong + ":" + tByte);
      }
    }

    @NeverClassInline
    static class Source1 {
      long s1Long;
      int s1;

      @NeverInline
      Source1(long s1Long, int s1) {
        this.s1Long = s1Long;
        this.s1 = s1;
      }

      @NeverInline
      void print1() {
        System.out.println(s1Long + ":" + s1);
      }
    }

    @NeverClassInline
    static class Source2 {
      long s2Long;
      int s2;
      boolean s2Bool;

      @NeverInline
      Source2(long s2Long, int s2, boolean s2Bool) {
        this.s2Long = s2Long;
        this.s2 = s2;
        this.s2Bool = s2Bool;
      }

      @NeverInline
      void print2() {
        System.out.println(s2Long + ":" + s2 + ":" + s2Bool);
      }
    }

    public static void main(String[] args) {
      new Target(args.length + 100L, (byte) (args.length + 1)).printTarget();
      new Source1(args.length + 200L, args.length + 11).print1();
      new Source2(args.length + 300L, args.length + 22, args.length == 0).print2();
    }
  }
}

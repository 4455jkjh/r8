// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.group;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.internalSyntheticVerticalGroup;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.matchesGroups;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.participant;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.internal.BooleanBox;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergedClassesProvenanceVerticalThenHorizontalLambdaTest
    extends MergedClassesProvenanceTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    BooleanBox checked = new BooleanBox();
    testForR8(parameters)
        .addProgramClassesAndInnerClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .addVerticallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertMergedIntoSubtype(Main.I.class, Main.J.class)
                    .assertNoOtherClassesMerged())
        .addOptionsModification(
            withProvenanceConsumer(
                checked,
                (appView, provenance) -> {
                  DexType typeI = toDexType(Main.I.class, appView.dexItemFactory());
                  DexType typeJ = toDexType(Main.J.class, appView.dexItemFactory());
                  assertThat(
                      allProvenanceGroups(appView, provenance),
                      matchesGroups(
                          group(
                              participant(internalSyntheticVerticalGroup(typeI)),
                              participant(internalSyntheticVerticalGroup(typeJ)))));
                }))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("I:3", "J:4");
    assertTrue(checked.isTrue());
  }

  public static class Main {

    interface I {
      String runI();
    }

    interface J {
      String runJ();
    }

    @NeverInline
    static I makeI(int x) {
      return () -> "I:" + x;
    }

    @NeverInline
    static J makeJ(int y) {
      return () -> "J:" + y;
    }

    @NeverInline
    static void callI(I i) {
      System.out.println(i.runI());
    }

    @NeverInline
    static void callJ(J j) {
      System.out.println(j.runJ());
    }

    public static void main(String[] args) {
      callI(makeI(args.length + 3));
      callJ(makeJ(args.length + 4));
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.internalSyntheticClassNode;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.matchesMergedFieldContributors;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.matchesParticipants;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.participantWithOwnMergedField;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.classmerging.MergedClassesProvenance.Participant;
import com.android.tools.r8.utils.internal.BooleanBox;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergedClassesProvenanceCapturingLambdaFieldMergeTest
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
        .addOptionsModification(
            withProvenanceConsumer(
                checked,
                (appView, provenance) -> {
                  MergedClassAndGroup merged = findGroupWithMergedFields(appView, provenance);
                  DexField mergedField = singleOriginalInstanceField(appView, merged);
                  Matcher<Participant> syntheticParticipant =
                      participantWithOwnMergedField(internalSyntheticClassNode(), mergedField);
                  assertThat(
                      merged.group.getParticipants(),
                      matchesParticipants(syntheticParticipant, syntheticParticipant));
                  assertThat(
                      mergedFieldContributors(merged.group, mergedField),
                      matchesMergedFieldContributors(mergedField));
                }))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A:3", "B:4");
    assertTrue(checked.isTrue());
  }

  public static class Main {

    interface Printer {
      void print();
    }

    @NeverInline
    static void runAll(Printer[] printers) {
      for (Printer p : printers) {
        p.print();
      }
    }

    public static void main(String[] args) {
      int x = args.length + 3;
      int y = args.length + 4;
      runAll(
          new Printer[] {
            () -> System.out.println("A:" + x), () -> System.out.println("B:" + y),
          });
    }
  }
}

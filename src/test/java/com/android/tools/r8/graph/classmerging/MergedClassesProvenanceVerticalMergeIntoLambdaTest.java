// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.classmerging;

import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.internalSyntheticVerticalGroup;
import static com.android.tools.r8.graph.classmerging.MergedClassesProvenanceMatchers.matchesGroups;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.internal.BooleanBox;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergedClassesProvenanceVerticalMergeIntoLambdaTest
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
        .addVerticallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertMergedIntoSubtype(Main.I.class, Main.J.class)
                    .assertNoOtherClassesMerged())
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .addOptionsModification(
            withProvenanceConsumer(
                checked,
                (appView, provenance) -> {
                  DexType typeI = toDexType(Main.I.class, appView.dexItemFactory());
                  DexType typeJ = toDexType(Main.J.class, appView.dexItemFactory());
                  assertThat(
                      allProvenanceGroups(appView, provenance),
                      matchesGroups(
                          internalSyntheticVerticalGroup(typeI),
                          internalSyntheticVerticalGroup(typeJ)));
                }))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("2");
    assertTrue(checked.isTrue());
  }

  public static class Main {

    interface I {
      void f();
    }

    interface J {
      void g();
    }

    public static void main(String[] args) {
      Map<Class<?>, Object> map = new HashMap<>();
      map.put(I.class, (I) () -> System.out.println("I"));
      map.put(J.class, (J) () -> System.out.println("J"));
      System.out.println(map.size());
    }
  }
}

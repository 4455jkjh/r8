// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.UnorderedCollectionMatcher;
import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.androidapi.ComputedApiLevel;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotation.SynthesizedAnnotationClassInfo;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SyntheticMarkerDexTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Hello, world");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.B).build();
  }

  public SyntheticMarkerDexTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    Path out =
        testForD8(parameters.getBackend())
            .setMinApi(parameters)
            .setIntermediate(true)
            .addProgramClasses(TestClass.class)
            .compile()
            .inspect(this::checkSyntheticClassIsMarked)
            .writeToZip();
    testForD8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramFiles(out)
        // Use intermediate again to preserve synthetics.
        .setIntermediate(true)
        // Disable desugaring so we are sure the lambda synthetic is created in the first round.
        .disableDesugaring()
        .compile()
        .inspect(this::checkSyntheticClassIsMarked)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testApiLevelSerializationRoundTrip() {
    DexItemFactory factory = new DexItemFactory();
    SyntheticNaming naming = factory.getSyntheticNaming();
    AndroidApiLevelCompute apiLevelCompute = AndroidApiLevelCompute.noAndroidApiLevelCompute();
    for (AndroidApiLevel apiLevel : AndroidApiLevel.getAndroidApiLevelsSorted()) {
      ComputedApiLevel expected = apiLevelCompute.of(apiLevel);
      DexAnnotation annotation =
          DexAnnotation.createAnnotationSynthesizedClass(
              naming.API_MODEL_OUTLINE, factory, expected);
      SynthesizedAnnotationClassInfo info =
          DexAnnotation.getSynthesizedClassAnnotationInfo(
              DexAnnotationSet.create(new DexAnnotation[] {annotation}),
              factory,
              naming,
              apiLevelCompute);
      assertNotNull(info);
      assertSame(naming.API_MODEL_OUTLINE, info.getSyntheticKind());
      assertEquals(expected, info.getComputedApiLevel());
    }
    for (ComputedApiLevel expected :
        ImmutableList.of(ComputedApiLevel.notSet(), ComputedApiLevel.unknown())) {
      DexAnnotation annotation =
          DexAnnotation.createAnnotationSynthesizedClass(naming.LAMBDA, factory, expected);
      SynthesizedAnnotationClassInfo info =
          DexAnnotation.getSynthesizedClassAnnotationInfo(
              DexAnnotationSet.create(new DexAnnotation[] {annotation}),
              factory,
              naming,
              apiLevelCompute);
      assertNotNull(info);
      assertSame(naming.LAMBDA, info.getSyntheticKind());
      assertEquals(expected, info.getComputedApiLevel());
    }
  }

  private void checkSyntheticClassIsMarked(CodeInspector inspector) {
    // Compilation gives rise to the main class plus one lambda.
    assertEquals(2, inspector.allClasses().size());
    inspector.forAllClasses(
        clazz -> {
          if (!clazz.getFinalReference().equals(Reference.classFromClass(TestClass.class))) {
            // This should be the lambda class.
            assertThat(
                ListUtils.map(clazz.annotations(), a -> a.getType().getTypeName()),
                UnorderedCollectionMatcher.matchesItemsOneToOne(
                    ImmutableList.of("com.android.tools.r8.annotations.SynthesizedClassV2")));
            DexEncodedAnnotation annotation = clazz.annotations().get(0).getAnnotation();
            assertEquals(
                ImmutableList.of("apiLevelMajor", "apiLevelMinor", "kind", "versionHash"),
                ListUtils.map(annotation.elements, element -> element.name.toString()));
          }
        });
  }

  public static class TestClass {

    public static void run(Runnable r) {
      r.run();
    }

    public static void main(String[] args) {
      run(() -> System.out.println("Hello, world"));
    }
  }
}

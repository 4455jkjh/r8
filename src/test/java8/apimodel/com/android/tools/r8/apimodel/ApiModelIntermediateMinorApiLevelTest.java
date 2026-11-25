// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.UnorderedCollectionMatcher;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.internal.Box;
import com.android.tools.r8.utils.internal.ListUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelIntermediateMinorApiLevelTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ApiModelIntermediateMinorApiLevelTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testIntermediateMergePreservesMinorApiLevel() throws Exception {
    Box<ClassReference> outline36 = new Box<>();
    Box<ClassReference> outline36_1A = new Box<>();
    Box<ClassReference> outline36_1B = new Box<>();
    Box<ClassReference> outlineMain = new Box<>();
    Box<ClassReference> outlineExtension = new Box<>();

    Path intermediateZip =
        testForD8()
            .release()
            .setIntermediate(true)
            .addProgramClasses(
                Caller36.class,
                Caller36_1A.class,
                Caller36_1B.class,
                CallerMain.class,
                CallerExtension.class)
            .addLibraryClasses(
                LibraryClass36.class,
                LibraryClass36_1.class,
                LibraryClass36_1Other.class,
                LibraryClassMain.class,
                LibraryClassExtension.class)
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .setMinApi(AndroidApiLevel.B)
            .apply(ApiModelingTestHelper::enableApiCallerIdentification)
            .apply(ApiModelingTestHelper::enableOutliningOfMethods)
            .apply(setMockApiLevelForClass(LibraryClass36.class, AndroidApiLevel.BAKLAVA))
            .apply(setMockApiLevelForClass(LibraryClass36_1.class, AndroidApiLevel.BAKLAVA_1))
            .apply(setMockApiLevelForClass(LibraryClass36_1Other.class, AndroidApiLevel.BAKLAVA_1))
            .apply(setMockApiLevelForClass(LibraryClassMain.class, AndroidApiLevel.MAIN))
            .apply(setMockApiLevelForClass(LibraryClassExtension.class, AndroidApiLevel.EXTENSION))
            .apply(
                setMockApiLevelForMethod(
                    LibraryClass36.class.getDeclaredMethod("foo"), AndroidApiLevel.BAKLAVA))
            .apply(
                setMockApiLevelForMethod(
                    LibraryClass36_1.class.getDeclaredMethod("bar"), AndroidApiLevel.BAKLAVA_1))
            .apply(
                setMockApiLevelForMethod(
                    LibraryClass36_1Other.class.getDeclaredMethod("baz"),
                    AndroidApiLevel.BAKLAVA_1))
            .apply(
                setMockApiLevelForMethod(
                    LibraryClassMain.class.getDeclaredMethod("qux"), AndroidApiLevel.MAIN))
            .apply(
                setMockApiLevelForMethod(
                    LibraryClassExtension.class.getDeclaredMethod("ext"),
                    AndroidApiLevel.EXTENSION))
            .collectSyntheticItems()
            .compile()
            // Verify SynthesizedClassV2 annotations record both major and minor API levels
            // (including EXTENSION as (Integer.MAX_VALUE, 0) and MAIN).
            .inspectWithSyntheticItems(
                (inspector, syntheticItems) -> {
                  outline36.set(syntheticItems.syntheticApiOutlineClass(Caller36.class, 0));
                  outline36_1A.set(syntheticItems.syntheticApiOutlineClass(Caller36_1A.class, 0));
                  outline36_1B.set(syntheticItems.syntheticApiOutlineClass(Caller36_1B.class, 0));
                  outlineMain.set(syntheticItems.syntheticApiOutlineClass(CallerMain.class, 0));
                  outlineExtension.set(
                      syntheticItems.syntheticApiOutlineClass(CallerExtension.class, 0));
                  assertThat(
                      ListUtils.map(inspector.allClasses(), FoundClassSubject::getFinalReference),
                      UnorderedCollectionMatcher.matchesItemsOneToOne(
                          ImmutableList.of(
                              Reference.classFromClass(Caller36.class),
                              Reference.classFromClass(Caller36_1A.class),
                              Reference.classFromClass(Caller36_1B.class),
                              Reference.classFromClass(CallerMain.class),
                              Reference.classFromClass(CallerExtension.class),
                              outline36.get(),
                              outline36_1A.get(),
                              outline36_1B.get(),
                              outlineMain.get(),
                              outlineExtension.get())));
                  assertSynthesizedClassV2Annotation(
                      inspector.syntheticClass(outline36.get()), AndroidApiLevel.BAKLAVA);
                  assertSynthesizedClassV2Annotation(
                      inspector.syntheticClass(outline36_1A.get()), AndroidApiLevel.BAKLAVA_1);
                  assertSynthesizedClassV2Annotation(
                      inspector.syntheticClass(outline36_1B.get()), AndroidApiLevel.BAKLAVA_1);
                  assertSynthesizedClassV2Annotation(
                      inspector.syntheticClass(outlineMain.get()), AndroidApiLevel.MAIN);
                  assertSynthesizedClassV2Annotation(
                      inspector.syntheticClass(outlineExtension.get()), AndroidApiLevel.EXTENSION);
                })
            .writeToZip();

    // In the merge step, the two 36.1 outlines may merge with each other, while the 36.0 outline
    // must not merge with the 36.1 outlines and the EXTENSION outline must not merge with MAIN.
    Box<ClassReference> mergedOutline36_1 = new Box<>();
    testForD8()
        .release()
        .setUseDefaultRuntimeLibrary(false)
        .addProgramFiles(intermediateZip)
        .setMinApi(AndroidApiLevel.B)
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .addHorizontallyMergedClassesInspector(
            inspector -> {
              inspector
                  .assertIsCompleteMergeGroup(outline36_1A.get(), outline36_1B.get())
                  .assertNoOtherClassesMerged();
              mergedOutline36_1.set(inspector.getTarget(outline36_1A.get()));
            })
        .compile()
        .inspect(
            inspector -> {
              assertThat(
                  ListUtils.map(inspector.allClasses(), FoundClassSubject::getFinalReference),
                  UnorderedCollectionMatcher.matchesItemsOneToOne(
                      ImmutableList.of(
                          Reference.classFromClass(Caller36.class),
                          Reference.classFromClass(Caller36_1A.class),
                          Reference.classFromClass(Caller36_1B.class),
                          Reference.classFromClass(CallerMain.class),
                          Reference.classFromClass(CallerExtension.class),
                          outline36.get(),
                          mergedOutline36_1.get(),
                          outlineMain.get(),
                          outlineExtension.get())));
              inspector.forAllClasses(clazz -> assertTrue(clazz.annotations().isEmpty()));
            });
  }

  private static void assertSynthesizedClassV2Annotation(
      ClassSubject clazz, AndroidApiLevel expectedApiLevel) {
    assertEquals(
        ImmutableList.of("com.android.tools.r8.annotations.SynthesizedClassV2"),
        ListUtils.map(clazz.annotations(), a -> a.getType().getTypeName()));
    DexEncodedAnnotation annotation = clazz.annotations().get(0).getAnnotation();
    assertEquals(
        ImmutableList.of("apiLevelMajor", "apiLevelMinor", "kind", "versionHash"),
        ListUtils.map(annotation.elements, element -> element.name.toString()));
    assertTrue(annotation.elements[0].value.isDexValueInt());
    assertEquals(
        expectedApiLevel.getMajor(), annotation.elements[0].value.asDexValueInt().getValue());
    assertTrue(annotation.elements[1].value.isDexValueInt());
    assertEquals(
        expectedApiLevel.getMinor(), annotation.elements[1].value.asDexValueInt().getValue());
    assertTrue(annotation.elements[2].value.isDexValueInt());
    assertTrue(annotation.elements[3].value.isDexValueString());
  }

  public static class LibraryClass36 {
    public static void foo() {}
  }

  public static class LibraryClass36_1 {
    public static void bar() {}
  }

  public static class LibraryClass36_1Other {
    public static void baz() {}
  }

  public static class LibraryClassMain {
    public static void qux() {}
  }

  public static class LibraryClassExtension {
    public static void ext() {}
  }

  public static class Caller36 {
    public static void call() {
      LibraryClass36.foo();
    }
  }

  public static class Caller36_1A {
    public static void call() {
      LibraryClass36_1.bar();
    }
  }

  public static class Caller36_1B {
    public static void call() {
      LibraryClass36_1Other.baz();
    }
  }

  public static class CallerMain {
    public static void call() {
      LibraryClassMain.qux();
    }
  }

  public static class CallerExtension {
    public static void call() {
      LibraryClassExtension.ext();
    }
  }
}

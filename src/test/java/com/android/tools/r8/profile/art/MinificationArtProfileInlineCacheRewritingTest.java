// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.profile.art.ArtProfileInlineCacheData.EntryValue;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.profile.art.utils.ArtProfileTestingUtils;
import com.android.tools.r8.references.ArrayReference;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.Box;
import com.android.tools.r8.utils.internal.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MinificationArtProfileInlineCacheRewritingTest
    extends ArtProfileInlineCacheRewritingTestBase {

  public MinificationArtProfileInlineCacheRewritingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testMinification() throws Exception {
    ClassReference fooRef = Reference.classFromClass(MinificationProgram.Foo.class);
    ArrayReference fooArrayRef = Reference.array(fooRef, 1);
    ClassReference listRef = Reference.classFromClass(List.class);
    ClassReference arrayListRef = Reference.classFromClass(ArrayList.class);
    ClassReference stringRef = Reference.classFromClass(String.class);

    MethodReference mClassAndArrayRef =
        MethodReferenceUtils.methodFromMethod(MinificationProgram.Main.class, "mClassAndArray");
    MethodReference mLibraryTypesRef =
        MethodReferenceUtils.methodFromMethod(MinificationProgram.Main.class, "mLibraryTypes");
    MethodReference mMarkersRef =
        MethodReferenceUtils.methodFromMethod(MinificationProgram.Main.class, "mMarkers");
    MethodReference mSingleMissingRef =
        MethodReferenceUtils.methodFromMethod(MinificationProgram.Main.class, "mSingleMissing");
    MethodReference mSingleMegamorphicRef =
        MethodReferenceUtils.methodFromMethod(MinificationProgram.Main.class, "mSingleMegamorphic");

    ExternalArtProfile inputProfile =
        ExternalArtProfile.builder()
            .addMethodRule(
                mClassAndArrayRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(fooRef, EntryValue.ofTypes(fooArrayRef, stringRef))
                    .build())
            .addMethodRule(
                mLibraryTypesRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(listRef, EntryValue.ofTypes(arrayListRef, fooRef))
                    .build())
            .addMethodRule(
                mMarkersRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(fooRef, EntryValue.missingTypes())
                    .addTargetedInlineCache(listRef, EntryValue.megamorphicTypes())
                    .build())
            .addMethodRule(
                mSingleMissingRef,
                HOT_RULE,
                ArtProfileInlineCacheData.createSingle(EntryValue.missingTypes()))
            .addMethodRule(
                mSingleMegamorphicRef,
                HOT_RULE,
                ArtProfileInlineCacheData.createSingle(EntryValue.megamorphicTypes()))
            .build();

    Path outputProfile = temp.newFile("minification-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(MinificationProgram.class)
            .addKeepMainRule(MinificationProgram.Main.class)
            .addArtProfileForRewriting(
                ArtProfileTestingUtils.createArtProfileProvider(inputProfile),
                createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainSubject = inspector.clazz(MinificationProgram.Main.class);
    ClassSubject fooSubject = inspector.clazz(MinificationProgram.Foo.class);
    assertThat(fooSubject, isPresentAndRenamed());
    ClassReference finalFooRef = fooSubject.getFinalReference();
    ArrayReference finalFooArrayRef = Reference.array(finalFooRef, 1);

    MethodSubject mClassAndArray = mainSubject.uniqueMethodWithOriginalName("mClassAndArray");
    MethodSubject mLibraryTypes = mainSubject.uniqueMethodWithOriginalName("mLibraryTypes");
    MethodSubject mMarkers = mainSubject.uniqueMethodWithOriginalName("mMarkers");
    MethodSubject mSingleMissing = mainSubject.uniqueMethodWithOriginalName("mSingleMissing");
    MethodSubject mSingleMegamorphic =
        mainSubject.uniqueMethodWithOriginalName("mSingleMegamorphic");

    new ArtProfileInspector(residualProfileBox.get())
        .inspectMethodRule(
            mClassAndArray,
            ruleInspector ->
                ruleInspector.assertHasTargetedInlineCache(
                    finalFooRef, finalFooArrayRef, stringRef))
        .inspectMethodRule(
            mLibraryTypes,
            ruleInspector ->
                ruleInspector.assertHasTargetedInlineCache(listRef, arrayListRef, finalFooRef))
        .inspectMethodRule(
            mMarkers,
            ruleInspector ->
                ruleInspector.assertHasInlineCacheData(
                    ArtProfileInlineCacheData.builder()
                        .addTargetedInlineCache(finalFooRef, EntryValue.missingTypes())
                        .addTargetedInlineCache(listRef, EntryValue.megamorphicTypes())
                        .build()))
        .inspectMethodRule(
            mSingleMissing,
            ruleInspector ->
                ruleInspector.assertHasInlineCacheData(
                    ArtProfileInlineCacheData.createSingle(EntryValue.missingTypes())))
        .inspectMethodRule(
            mSingleMegamorphic,
            ruleInspector ->
                ruleInspector.assertHasInlineCacheData(
                    ArtProfileInlineCacheData.createSingle(EntryValue.megamorphicTypes())))
        .assertContainsNoOtherRules();

    assertEquals(
        ImmutableList.of(
            "H"
                + MethodReferenceUtils.toSmaliString(mClassAndArray.getFinalReference())
                + "+]"
                + fooSubject.getFinalDescriptor()
                + finalFooArrayRef.getDescriptor()
                + ",Ljava/lang/String;",
            "H"
                + MethodReferenceUtils.toSmaliString(mLibraryTypes.getFinalReference())
                + "+]Ljava/util/List;Ljava/util/ArrayList;,"
                + fooSubject.getFinalDescriptor(),
            "H"
                + MethodReferenceUtils.toSmaliString(mMarkers.getFinalReference())
                + "+]"
                + fooSubject.getFinalDescriptor()
                + "missing_types]Ljava/util/List;megamorphic_types",
            "H"
                + MethodReferenceUtils.toSmaliString(mSingleMissing.getFinalReference())
                + "+missing_types",
            "H"
                + MethodReferenceUtils.toSmaliString(mSingleMegamorphic.getFinalReference())
                + "+megamorphic_types"),
        FileUtils.readAllLines(outputProfile));

    compileResult
        .run(parameters.getRuntime(), MinificationProgram.Main.class)
        .assertSuccessWithOutputLines("foo", "1", "m1m2m3m4m5");
  }

  static class MinificationProgram {

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class Foo {

      @NeverInline
      void greet() {
        System.out.println("foo");
      }
    }

    static class Main {

      public static void main(String[] args) {
        new Foo().greet();
        List<String> list = new ArrayList<>();
        list.add("ok");
        System.out.println(list.size());
        mClassAndArray();
        mLibraryTypes();
        mMarkers();
        mSingleMissing();
        mSingleMegamorphic();
        System.out.println();
      }

      @NeverInline
      static void mClassAndArray() {
        System.out.print("m1");
      }

      @NeverInline
      static void mLibraryTypes() {
        System.out.print("m2");
      }

      @NeverInline
      static void mMarkers() {
        System.out.print("m3");
      }

      @NeverInline
      static void mSingleMissing() {
        System.out.print("m4");
      }

      @NeverInline
      static void mSingleMegamorphic() {
        System.out.print("m5");
      }
    }
  }
}

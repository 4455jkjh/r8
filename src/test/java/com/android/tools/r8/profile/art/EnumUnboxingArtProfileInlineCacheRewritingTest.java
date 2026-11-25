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
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.profile.art.ArtProfileInlineCacheData.EntryValue;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.profile.art.utils.ArtProfileMethodRuleInspector;
import com.android.tools.r8.profile.art.utils.ArtProfileTestingUtils;
import com.android.tools.r8.references.ArrayReference;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.Box;
import com.android.tools.r8.utils.internal.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class EnumUnboxingArtProfileInlineCacheRewritingTest
    extends ArtProfileInlineCacheRewritingTestBase {

  public EnumUnboxingArtProfileInlineCacheRewritingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    ClassReference enumRef =
        Reference.classFromClass(
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.MyEnum.class);
    ClassReference enumSub1Ref =
        Reference.classFromTypeName(
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.MyEnum.class.getTypeName()
                + "$1");
    ClassReference enumSub2Ref =
        Reference.classFromTypeName(
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.MyEnum.class.getTypeName()
                + "$2");
    ArrayReference enumArrayRef = Reference.array(enumRef, 1);
    ClassReference keptRef =
        Reference.classFromClass(
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.KeptClass.class);
    ClassReference objectRef = Reference.classFromClass(Object.class);

    MethodReference mEnumReceiverRef =
        MethodReferenceUtils.methodFromMethod(
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.Main.class, "mEnumReceiver");
    MethodReference mEnumArrayTargetedRef =
        MethodReferenceUtils.methodFromMethod(
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.Main.class, "mEnumArrayTargeted");
    MethodReference mEnumArraySingleRef =
        MethodReferenceUtils.methodFromMethod(
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.Main.class, "mEnumArraySingle");
    MethodReference mPolyMixedRef =
        MethodReferenceUtils.methodFromMethod(
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.Main.class, "mPolyMixed");
    MethodReference mPolyAllUnboxedRef =
        MethodReferenceUtils.methodFromMethod(
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.Main.class, "mPolyAllUnboxed");
    MethodReference mTargetedPartialDropRef =
        MethodReferenceUtils.methodFromMethod(
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.Main.class,
            "mTargetedPartialDrop");

    ExternalArtProfile inputProfile =
        ExternalArtProfile.builder()
            .addMethodRule(
                mEnumReceiverRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(enumRef, EntryValue.ofTypes(enumSub1Ref, enumSub2Ref))
                    .build())
            .addMethodRule(
                mEnumArrayTargetedRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(enumArrayRef, EntryValue.ofTypes(enumArrayRef))
                    .build())
            .addMethodRule(
                mEnumArraySingleRef,
                HOT_RULE,
                ArtProfileInlineCacheData.createSingle(EntryValue.ofTypes(enumArrayRef)))
            .addMethodRule(
                mPolyMixedRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(objectRef, EntryValue.ofTypes(enumRef, keptRef))
                    .build())
            .addMethodRule(
                mPolyAllUnboxedRef,
                HOT_RULE,
                ArtProfileInlineCacheData.createSingle(
                    EntryValue.ofTypes(enumSub1Ref, enumSub2Ref)))
            .addMethodRule(
                mTargetedPartialDropRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(keptRef, EntryValue.ofTypes(enumRef))
                    .addTargetedInlineCache(objectRef, EntryValue.ofTypes(enumSub1Ref, keptRef))
                    .build())
            .build();

    Path outputProfile = temp.newFile("enum-unboxing-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(EnumUnboxingArtProfileInlineCacheRewritingTestProgram.class)
            .addKeepMainRule(EnumUnboxingArtProfileInlineCacheRewritingTestProgram.Main.class)
            .addArtProfileForRewriting(
                ArtProfileTestingUtils.createArtProfileProvider(inputProfile),
                createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
            .addEnumUnboxingInspector(
                inspector ->
                    inspector.assertUnboxed(
                        EnumUnboxingArtProfileInlineCacheRewritingTestProgram.MyEnum.class))
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainSubject =
        inspector.clazz(EnumUnboxingArtProfileInlineCacheRewritingTestProgram.Main.class);
    ClassSubject keptSubject =
        inspector.clazz(EnumUnboxingArtProfileInlineCacheRewritingTestProgram.KeptClass.class);
    assertThat(keptSubject, isPresentAndRenamed());
    ClassReference finalKeptRef = keptSubject.getFinalReference();
    TypeReference intArrayRef = Reference.typeFromDescriptor("[I");

    MethodSubject mEnumReceiver = mainSubject.uniqueMethodWithOriginalName("mEnumReceiver");
    MethodSubject mEnumArrayTargeted =
        mainSubject.uniqueMethodWithOriginalName("mEnumArrayTargeted");
    MethodSubject mEnumArraySingle = mainSubject.uniqueMethodWithOriginalName("mEnumArraySingle");
    MethodSubject mPolyMixed = mainSubject.uniqueMethodWithOriginalName("mPolyMixed");
    MethodSubject mPolyAllUnboxed = mainSubject.uniqueMethodWithOriginalName("mPolyAllUnboxed");
    MethodSubject mTargetedPartialDrop =
        mainSubject.uniqueMethodWithOriginalName("mTargetedPartialDrop");

    new ArtProfileInspector(residualProfileBox.get())
        .inspectMethodRule(mEnumReceiver, ArtProfileMethodRuleInspector::assertHasNoInlineCaches)
        .inspectMethodRule(
            mEnumArrayTargeted,
            ruleInspector -> ruleInspector.assertHasTargetedInlineCache(intArrayRef, intArrayRef))
        .inspectMethodRule(
            mEnumArraySingle,
            ruleInspector -> ruleInspector.assertHasSingleInlineCache(intArrayRef))
        .inspectMethodRule(
            mPolyMixed,
            ruleInspector -> ruleInspector.assertHasTargetedInlineCache(objectRef, finalKeptRef))
        .inspectMethodRule(mPolyAllUnboxed, ArtProfileMethodRuleInspector::assertHasNoInlineCaches)
        .inspectMethodRule(
            mTargetedPartialDrop,
            ruleInspector ->
                ruleInspector.assertHasInlineCacheData(
                    ArtProfileInlineCacheData.builder()
                        .addTargetedInlineCache(objectRef, EntryValue.ofTypes(finalKeptRef))
                        .build()))
        .assertContainsNoOtherRules();

    assertEquals(
        ImmutableList.of(
            "H" + MethodReferenceUtils.toSmaliString(mEnumReceiver.getFinalReference()),
            "H"
                + MethodReferenceUtils.toSmaliString(mEnumArrayTargeted.getFinalReference())
                + "+][I[I",
            "H" + MethodReferenceUtils.toSmaliString(mEnumArraySingle.getFinalReference()) + "+[I",
            "H"
                + MethodReferenceUtils.toSmaliString(mPolyMixed.getFinalReference())
                + "+]Ljava/lang/Object;"
                + keptSubject.getFinalDescriptor(),
            "H" + MethodReferenceUtils.toSmaliString(mPolyAllUnboxed.getFinalReference()),
            "H"
                + MethodReferenceUtils.toSmaliString(mTargetedPartialDrop.getFinalReference())
                + "+]Ljava/lang/Object;"
                + keptSubject.getFinalDescriptor()),
        FileUtils.readAllLines(outputProfile));

    compileResult
        .run(
            parameters.getRuntime(),
            EnumUnboxingArtProfileInlineCacheRewritingTestProgram.Main.class)
        .assertSuccessWithOutputLines("ABkeptm1m2m3m4m5m6");
  }
}

class EnumUnboxingArtProfileInlineCacheRewritingTestProgram {

  enum MyEnum {
    A {
      @NeverInline
      @Override
      String label() {
        return "A";
      }
    },
    B {
      @NeverInline
      @Override
      String label() {
        return "B";
      }
    };

    abstract String label();
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class KeptClass {

    @NeverInline
    void value() {
      System.out.print("kept");
    }
  }

  static class Main {

    public static void main(String[] args) {
      MyEnum first = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      MyEnum second = System.currentTimeMillis() < 0 ? MyEnum.A : MyEnum.B;
      System.out.print(first.label());
      System.out.print(second.label());
      new KeptClass().value();
      mEnumReceiver();
      mEnumArrayTargeted();
      mEnumArraySingle();
      mPolyMixed();
      mPolyAllUnboxed();
      mTargetedPartialDrop();
      System.out.println();
    }

    @NeverInline
    static void mEnumReceiver() {
      System.out.print("m1");
    }

    @NeverInline
    static void mEnumArrayTargeted() {
      System.out.print("m2");
    }

    @NeverInline
    static void mEnumArraySingle() {
      System.out.print("m3");
    }

    @NeverInline
    static void mPolyMixed() {
      System.out.print("m4");
    }

    @NeverInline
    static void mPolyAllUnboxed() {
      System.out.print("m5");
    }

    @NeverInline
    static void mTargetedPartialDrop() {
      System.out.print("m6");
    }
  }
}

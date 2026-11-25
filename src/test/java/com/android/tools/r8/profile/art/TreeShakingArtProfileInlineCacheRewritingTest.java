// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
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
import com.android.tools.r8.profile.art.utils.ArtProfileMethodRuleInspector;
import com.android.tools.r8.profile.art.utils.ArtProfileTestingUtils;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
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
public class TreeShakingArtProfileInlineCacheRewritingTest
    extends ArtProfileInlineCacheRewritingTestBase {

  public TreeShakingArtProfileInlineCacheRewritingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testTreeShakingPrunedClasses() throws Exception {
    ClassReference baseRef = Reference.classFromClass(TreeShakingProgram.Base.class);
    ClassReference aliveSubRef = Reference.classFromClass(TreeShakingProgram.AliveSub.class);
    ClassReference deadSubRef = Reference.classFromClass(TreeShakingProgram.DeadSub.class);
    ClassReference deadReceiverRef =
        Reference.classFromClass(TreeShakingProgram.DeadReceiver.class);
    ClassReference otherBaseRef = Reference.classFromClass(TreeShakingProgram.OtherBase.class);

    MethodReference mPartialPruneRef =
        MethodReferenceUtils.methodFromMethod(TreeShakingProgram.Main.class, "mPartialPrune");
    MethodReference mAllTargetsPrunedRef =
        MethodReferenceUtils.methodFromMethod(TreeShakingProgram.Main.class, "mAllTargetsPruned");
    MethodReference mReceiverPrunedRef =
        MethodReferenceUtils.methodFromMethod(TreeShakingProgram.Main.class, "mReceiverPruned");
    MethodReference mMixedSegmentsRef =
        MethodReferenceUtils.methodFromMethod(TreeShakingProgram.Main.class, "mMixedSegments");

    ExternalArtProfile inputProfile =
        ExternalArtProfile.builder()
            .addMethodRule(
                mPartialPruneRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(baseRef, EntryValue.ofTypes(aliveSubRef, deadSubRef))
                    .build())
            .addMethodRule(
                mAllTargetsPrunedRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(baseRef, EntryValue.ofTypes(deadSubRef))
                    .build())
            .addMethodRule(
                mReceiverPrunedRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(deadReceiverRef, EntryValue.ofTypes(aliveSubRef))
                    .addTargetedInlineCache(deadReceiverRef, EntryValue.missingTypes())
                    .build())
            .addMethodRule(
                mMixedSegmentsRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(deadReceiverRef, EntryValue.missingTypes())
                    .addTargetedInlineCache(baseRef, EntryValue.ofTypes(aliveSubRef, deadSubRef))
                    .addTargetedInlineCache(otherBaseRef, EntryValue.ofTypes(deadSubRef))
                    .build())
            .build();

    Path outputProfile = temp.newFile("tree-shaking-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(TreeShakingProgram.class)
            .addKeepMainRule(TreeShakingProgram.Main.class)
            .addArtProfileForRewriting(
                ArtProfileTestingUtils.createArtProfileProvider(inputProfile),
                createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
            .addOptionsModification(InlinerOptions::disableInlining)
            .addOptionsModification(
                options -> options.callSiteOptimizationOptions().disableOptimization())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainSubject = inspector.clazz(TreeShakingProgram.Main.class);
    ClassSubject baseSubject = inspector.clazz(TreeShakingProgram.Base.class);
    ClassSubject aliveSubSubject = inspector.clazz(TreeShakingProgram.AliveSub.class);
    assertThat(baseSubject, isPresentAndRenamed());
    assertThat(aliveSubSubject, isPresentAndRenamed());
    assertThat(inspector.clazz(TreeShakingProgram.DeadSub.class), isAbsent());
    assertThat(inspector.clazz(TreeShakingProgram.DeadReceiver.class), isAbsent());

    MethodSubject mPartialPrune = mainSubject.uniqueMethodWithOriginalName("mPartialPrune");
    MethodSubject mAllTargetsPruned = mainSubject.uniqueMethodWithOriginalName("mAllTargetsPruned");
    MethodSubject mReceiverPruned = mainSubject.uniqueMethodWithOriginalName("mReceiverPruned");
    MethodSubject mMixedSegments = mainSubject.uniqueMethodWithOriginalName("mMixedSegments");

    new ArtProfileInspector(residualProfileBox.get())
        .inspectMethodRule(
            mPartialPrune,
            ruleInspector ->
                ruleInspector.assertHasTargetedInlineCache(
                    baseSubject.getFinalReference(), aliveSubSubject.getFinalReference()))
        .inspectMethodRule(
            mAllTargetsPruned, ArtProfileMethodRuleInspector::assertHasNoInlineCaches)
        .inspectMethodRule(mReceiverPruned, ArtProfileMethodRuleInspector::assertHasNoInlineCaches)
        .inspectMethodRule(
            mMixedSegments,
            ruleInspector ->
                ruleInspector.assertHasTargetedInlineCache(
                    baseSubject.getFinalReference(), aliveSubSubject.getFinalReference()))
        .assertContainsNoOtherRules();

    String expectedSurvivingCache =
        "+]" + baseSubject.getFinalDescriptor() + aliveSubSubject.getFinalDescriptor();
    assertEquals(
        ImmutableList.of(
            "H"
                + MethodReferenceUtils.toSmaliString(mPartialPrune.getFinalReference())
                + expectedSurvivingCache,
            "H" + MethodReferenceUtils.toSmaliString(mAllTargetsPruned.getFinalReference()),
            "H" + MethodReferenceUtils.toSmaliString(mReceiverPruned.getFinalReference()),
            "H"
                + MethodReferenceUtils.toSmaliString(mMixedSegments.getFinalReference())
                + expectedSurvivingCache),
        FileUtils.readAllLines(outputProfile));

    compileResult
        .run(parameters.getRuntime(), TreeShakingProgram.Main.class)
        .assertSuccessWithOutputLines("alive", "other", "p1p2p3p4");
  }

  static class TreeShakingProgram {

    @NeverClassInline
    @NoVerticalClassMerging
    abstract static class Base {

      abstract void action();
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    static class AliveSub extends Base {

      @NeverInline
      @Override
      void action() {
        System.out.println("alive");
      }
    }

    static class DeadSub extends Base {

      @Override
      void action() {
        System.out.println("dead");
      }
    }

    static class DeadReceiver {}

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class OtherBase {

      @NeverInline
      void ping() {
        System.out.println("other");
      }
    }

    static class Main {

      public static void main(String[] args) {
        call(new AliveSub());
        new OtherBase().ping();
        mPartialPrune();
        mAllTargetsPruned();
        mReceiverPruned();
        mMixedSegments();
        System.out.println();
      }

      @NeverInline
      static void call(Base b) {
        b.action();
      }

      @NeverInline
      static void mPartialPrune() {
        System.out.print("p1");
      }

      @NeverInline
      static void mAllTargetsPruned() {
        System.out.print("p2");
      }

      @NeverInline
      static void mReceiverPruned() {
        System.out.print("p3");
      }

      @NeverInline
      static void mMixedSegments() {
        System.out.print("p4");
      }
    }
  }
}

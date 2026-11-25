// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
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
public class VerticalClassMergingArtProfileInlineCacheRewritingTest
    extends ArtProfileInlineCacheRewritingTestBase {

  public VerticalClassMergingArtProfileInlineCacheRewritingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testVerticalClassMergingReceiverAndBridge() throws Exception {
    ClassReference superRef = Reference.classFromClass(VerticalReceiverProgram.Super.class);
    ClassReference subRef = Reference.classFromClass(VerticalReceiverProgram.Sub.class);
    MethodReference invokeMethodRef =
        MethodReferenceUtils.methodFromMethod(
            VerticalReceiverProgram.Main.class, "invoke", VerticalReceiverProgram.Super.class);
    MethodReference superMethodRef =
        MethodReferenceUtils.methodFromMethod(
            VerticalReceiverProgram.Super.class, "m", VerticalReceiverProgram.Super.class);

    ArtProfileInlineCacheData inlineCache =
        ArtProfileInlineCacheData.builder()
            .addTargetedInlineCache(superRef, EntryValue.ofTypes(subRef))
            .build();
    ExternalArtProfile inputProfile =
        ExternalArtProfile.builder()
            .addMethodRule(invokeMethodRef, HOT_RULE, inlineCache)
            .addMethodRule(superMethodRef, HOT_RULE, inlineCache)
            .build();

    Path outputProfile = temp.newFile("vertical-receiver-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(VerticalReceiverProgram.class)
            .addKeepMainRule(VerticalReceiverProgram.Main.class)
            .addArtProfileForRewriting(
                ArtProfileTestingUtils.createArtProfileProvider(inputProfile),
                createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
            .addOptionsModification(InlinerOptions::disableInlining)
            .addOptionsModification(
                options -> {
                  options.callSiteOptimizationOptions().disableOptimization();
                  options.getVerticalClassMergerOptions().setEnableBridgeAnalysis(false);
                })
            .addVerticallyMergedClassesInspector(
                inspector ->
                    inspector
                        .assertMergedIntoSubtype(VerticalReceiverProgram.Super.class)
                        .assertNoOtherClassesMerged())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainSubject = inspector.clazz(VerticalReceiverProgram.Main.class);
    assertThat(mainSubject, isPresent());
    MethodSubject invokeSubject = mainSubject.uniqueMethodWithOriginalName("invoke");
    assertThat(invokeSubject, isPresentAndRenamed());

    ClassSubject subSubject = inspector.clazz(VerticalReceiverProgram.Sub.class);
    assertThat(subSubject, isPresentAndRenamed());
    assertThat(inspector.clazz(VerticalReceiverProgram.Super.class), isAbsent());

    MethodSubject movedMethodSubject =
        subSubject.uniqueMethodThatMatches(
            method -> !method.isBridge() && !method.isSynthetic() && method.isVirtual());
    assertThat(movedMethodSubject, isPresent());

    MethodSubject syntheticBridgeMethodSubject =
        subSubject.uniqueMethodThatMatches(
            method -> method.isBridge() && method.isSynthetic() && method.isVirtual());
    assertThat(syntheticBridgeMethodSubject, isPresent());

    ClassReference finalSubRef = subSubject.getFinalReference();
    new ArtProfileInspector(residualProfileBox.get())
        .inspectMethodRule(
            invokeSubject,
            ruleInspector ->
                ruleInspector.assertIsHot().assertHasTargetedInlineCache(finalSubRef, finalSubRef))
        .inspectMethodRule(
            movedMethodSubject,
            ruleInspector ->
                ruleInspector.assertIsHot().assertHasTargetedInlineCache(finalSubRef, finalSubRef))
        .inspectMethodRule(
            syntheticBridgeMethodSubject,
            ruleInspector -> ruleInspector.assertIsHot().assertHasNoInlineCaches())
        .assertContainsNoOtherRules();

    String expectedInlineCache = "+]" + finalSubRef.getDescriptor() + finalSubRef.getDescriptor();
    assertEquals(
        ImmutableList.of(
            "H"
                + MethodReferenceUtils.toSmaliString(invokeSubject.getFinalReference())
                + expectedInlineCache,
            "H"
                + MethodReferenceUtils.toSmaliString(movedMethodSubject.getFinalReference())
                + expectedInlineCache,
            "H"
                + MethodReferenceUtils.toSmaliString(
                    syntheticBridgeMethodSubject.getFinalReference())),
        FileUtils.readAllLines(outputProfile));

    compileResult
        .run(parameters.getRuntime(), VerticalReceiverProgram.Main.class)
        .assertSuccessWithOutputLines("Super.m");
  }

  @Test
  public void testVerticalClassMergingIntermediateTarget() throws Exception {
    ClassReference baseRef = Reference.classFromClass(VerticalIntermediateProgram.Base.class);
    ClassReference midRef = Reference.classFromClass(VerticalIntermediateProgram.Mid.class);
    ClassReference subBRef = Reference.classFromClass(VerticalIntermediateProgram.SubB.class);
    MethodReference callMethodRef =
        MethodReferenceUtils.methodFromMethod(
            VerticalIntermediateProgram.Main.class, "call", VerticalIntermediateProgram.Base.class);

    ExternalArtProfile inputProfile =
        ExternalArtProfile.builder()
            .addMethodRule(
                callMethodRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(baseRef, EntryValue.ofTypes(midRef, subBRef))
                    .build())
            .build();

    Path outputProfile = temp.newFile("vertical-intermediate-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(VerticalIntermediateProgram.class)
            .addKeepMainRule(VerticalIntermediateProgram.Main.class)
            .addArtProfileForRewriting(
                ArtProfileTestingUtils.createArtProfileProvider(inputProfile),
                createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
            .addOptionsModification(InlinerOptions::disableInlining)
            .addOptionsModification(
                options -> options.callSiteOptimizationOptions().disableOptimization())
            .addVerticallyMergedClassesInspector(
                inspector ->
                    inspector
                        .assertMergedIntoSubtype(VerticalIntermediateProgram.Mid.class)
                        .assertNoOtherClassesMerged())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainSubject = inspector.clazz(VerticalIntermediateProgram.Main.class);
    MethodSubject callSubject = mainSubject.uniqueMethodWithOriginalName("call");
    assertThat(callSubject, isPresentAndRenamed());

    ClassSubject baseSubject = inspector.clazz(VerticalIntermediateProgram.Base.class);
    ClassSubject subASubject = inspector.clazz(VerticalIntermediateProgram.SubA.class);
    ClassSubject subBSubject = inspector.clazz(VerticalIntermediateProgram.SubB.class);
    assertThat(baseSubject, isPresentAndRenamed());
    assertThat(subASubject, isPresentAndRenamed());
    assertThat(subBSubject, isPresentAndRenamed());
    assertThat(inspector.clazz(VerticalIntermediateProgram.Mid.class), isAbsent());

    new ArtProfileInspector(residualProfileBox.get())
        .inspectMethodRule(
            callSubject,
            ruleInspector ->
                ruleInspector
                    .assertIsHot()
                    .assertHasTargetedInlineCache(
                        baseSubject.getFinalReference(),
                        subASubject.getFinalReference(),
                        subBSubject.getFinalReference()))
        .assertContainsNoOtherRules();

    assertEquals(
        ImmutableList.of(
            "H"
                + MethodReferenceUtils.toSmaliString(callSubject.getFinalReference())
                + "+]"
                + baseSubject.getFinalDescriptor()
                + subASubject.getFinalDescriptor()
                + ","
                + subBSubject.getFinalDescriptor()),
        FileUtils.readAllLines(outputProfile));

    compileResult
        .run(parameters.getRuntime(), VerticalIntermediateProgram.Main.class)
        .assertSuccessWithOutputLines("SubA", "SubB");
  }

  static class VerticalReceiverProgram {

    static class Super {

      public void m(Super s) {
        System.out.println("Super.m");
      }
    }

    @NeverClassInline
    static class Sub extends Super {}

    static class Main {

      public static void main(String[] args) {
        invoke(new Sub());
      }

      @NeverInline
      static void invoke(Super s) {
        s.m(s);
      }
    }
  }

  static class VerticalIntermediateProgram {

    @NeverClassInline
    abstract static class Base {

      abstract void action();
    }

    abstract static class Mid extends Base {}

    @NeverClassInline
    @NoHorizontalClassMerging
    static class SubA extends Mid {

      @NeverInline
      @Override
      void action() {
        System.out.println("SubA");
      }
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    static class SubB extends Base {

      @NeverInline
      @Override
      void action() {
        System.out.println("SubB");
      }
    }

    static class Main {

      public static void main(String[] args) {
        call(System.currentTimeMillis() > 0 ? new SubA() : new SubB());
        call(System.currentTimeMillis() < 0 ? new SubA() : new SubB());
      }

      @NeverInline
      static void call(Base b) {
        b.action();
      }
    }
  }
}

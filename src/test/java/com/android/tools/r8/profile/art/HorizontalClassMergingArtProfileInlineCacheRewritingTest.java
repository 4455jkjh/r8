// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
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
public class HorizontalClassMergingArtProfileInlineCacheRewritingTest
    extends ArtProfileInlineCacheRewritingTestBase {

  public HorizontalClassMergingArtProfileInlineCacheRewritingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testHorizontalClassMergingTargetsDeduplicated() throws Exception {
    ClassReference baseRef = Reference.classFromClass(HorizontalTargetProgram.Base.class);
    ClassReference subARef = Reference.classFromClass(HorizontalTargetProgram.SubA.class);
    ClassReference subBRef = Reference.classFromClass(HorizontalTargetProgram.SubB.class);
    MethodReference callMethodRef =
        MethodReferenceUtils.methodFromMethod(
            HorizontalTargetProgram.Main.class, "call", HorizontalTargetProgram.Base.class);

    ExternalArtProfile inputProfile =
        ExternalArtProfile.builder()
            .addMethodRule(
                callMethodRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(baseRef, EntryValue.ofTypes(subARef, subBRef))
                    .build())
            .build();

    Path outputProfile = temp.newFile("horizontal-targets-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(HorizontalTargetProgram.class)
            .addKeepMainRule(HorizontalTargetProgram.Main.class)
            .addArtProfileForRewriting(
                ArtProfileTestingUtils.createArtProfileProvider(inputProfile),
                createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
            .addOptionsModification(InlinerOptions::disableInlining)
            .addOptionsModification(
                options -> options.callSiteOptimizationOptions().disableOptimization())
            .addHorizontallyMergedClassesInspector(
                inspector ->
                    inspector
                        .assertIsCompleteMergeGroup(
                            HorizontalTargetProgram.SubA.class, HorizontalTargetProgram.SubB.class)
                        .assertNoOtherClassesMerged())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainSubject = inspector.clazz(HorizontalTargetProgram.Main.class);
    MethodSubject callSubject = mainSubject.uniqueMethodWithOriginalName("call");
    assertThat(callSubject, isPresentAndRenamed());

    ClassSubject baseSubject = inspector.clazz(HorizontalTargetProgram.Base.class);
    ClassSubject subASubject = inspector.clazz(HorizontalTargetProgram.SubA.class);
    ClassSubject subBSubject = inspector.clazz(HorizontalTargetProgram.SubB.class);
    assertEquals(1, (subASubject.isPresent() ? 1 : 0) + (subBSubject.isPresent() ? 1 : 0));
    ClassSubject mergedSubject = subASubject.isPresent() ? subASubject : subBSubject;
    assertThat(baseSubject, isPresentAndRenamed());
    assertThat(mergedSubject, isPresentAndRenamed());

    new ArtProfileInspector(residualProfileBox.get())
        .inspectMethodRule(
            callSubject,
            ruleInspector ->
                ruleInspector
                    .assertIsHot()
                    .assertHasTargetedInlineCache(
                        baseSubject.getFinalReference(), mergedSubject.getFinalReference()))
        .assertContainsNoOtherRules();

    assertEquals(
        ImmutableList.of(
            "H"
                + MethodReferenceUtils.toSmaliString(callSubject.getFinalReference())
                + "+]"
                + baseSubject.getFinalDescriptor()
                + mergedSubject.getFinalDescriptor()),
        FileUtils.readAllLines(outputProfile));

    compileResult
        .run(parameters.getRuntime(), HorizontalTargetProgram.Main.class)
        .assertSuccessWithOutputLines("SubA", "SubB");
  }

  @Test
  public void testHorizontalClassMergingCallersJoined() throws Exception {
    ClassReference baseRef = Reference.classFromClass(HorizontalCallerProgram.Base.class);
    ClassReference targetARef = Reference.classFromClass(HorizontalCallerProgram.TargetA.class);
    ClassReference targetBRef = Reference.classFromClass(HorizontalCallerProgram.TargetB.class);
    ClassReference otherRef = Reference.classFromClass(HorizontalCallerProgram.Other.class);
    MethodReference callerAInitRef =
        MethodReferenceUtils.instanceConstructor(HorizontalCallerProgram.CallerA.class);
    MethodReference callerBInitRef =
        MethodReferenceUtils.instanceConstructor(HorizontalCallerProgram.CallerB.class);

    ExternalArtProfile inputProfile =
        ExternalArtProfile.builder()
            .addMethodRule(
                callerAInitRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(baseRef, EntryValue.ofTypes(targetARef))
                    .addTargetedInlineCache(otherRef, EntryValue.ofTypes(targetARef))
                    .build())
            .addMethodRule(
                callerBInitRef,
                POST_STARTUP_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(baseRef, EntryValue.ofTypes(targetBRef))
                    .addTargetedInlineCache(otherRef, EntryValue.missingTypes())
                    .build())
            .build();

    Path outputProfile = temp.newFile("horizontal-callers-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(HorizontalCallerProgram.class)
            .addKeepMainRule(HorizontalCallerProgram.Main.class)
            .addArtProfileForRewriting(
                ArtProfileTestingUtils.createArtProfileProvider(inputProfile),
                createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
            .addOptionsModification(InlinerOptions::disableInlining)
            .addOptionsModification(
                options -> options.callSiteOptimizationOptions().disableOptimization())
            .addHorizontallyMergedClassesInspector(
                inspector ->
                    inspector
                        .assertIsCompleteMergeGroup(
                            HorizontalCallerProgram.CallerA.class,
                            HorizontalCallerProgram.CallerB.class)
                        .assertNoOtherClassesMerged())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject callerASubject = inspector.clazz(HorizontalCallerProgram.CallerA.class);
    ClassSubject callerBSubject = inspector.clazz(HorizontalCallerProgram.CallerB.class);
    assertEquals(1, (callerASubject.isPresent() ? 1 : 0) + (callerBSubject.isPresent() ? 1 : 0));
    ClassSubject mergedSubject = callerASubject.isPresent() ? callerASubject : callerBSubject;
    assertThat(mergedSubject, isPresentAndRenamed());
    MethodSubject mergedInitSubject = mergedSubject.uniqueInstanceInitializer();
    assertThat(mergedInitSubject, isPresent());

    ClassSubject baseSubject = inspector.clazz(HorizontalCallerProgram.Base.class);
    ClassSubject targetASubject = inspector.clazz(HorizontalCallerProgram.TargetA.class);
    ClassSubject targetBSubject = inspector.clazz(HorizontalCallerProgram.TargetB.class);
    ClassSubject otherSubject = inspector.clazz(HorizontalCallerProgram.Other.class);
    assertThat(baseSubject, isPresentAndRenamed());
    assertThat(targetASubject, isPresentAndRenamed());
    assertThat(targetBSubject, isPresentAndRenamed());
    assertThat(otherSubject, isPresentAndRenamed());

    ArtProfileInlineCacheData expectedInlineCache =
        ArtProfileInlineCacheData.builder()
            .addTargetedInlineCache(
                baseSubject.getFinalReference(),
                EntryValue.ofTypes(
                    targetASubject.getFinalReference(), targetBSubject.getFinalReference()))
            .addTargetedInlineCache(otherSubject.getFinalReference(), EntryValue.missingTypes())
            .build();

    new ArtProfileInspector(residualProfileBox.get())
        .inspectMethodRule(
            mergedInitSubject,
            ruleInspector ->
                ruleInspector
                    .assertIsHot()
                    .assertNotStartup()
                    .assertIsPostStartup()
                    .assertHasInlineCacheData(expectedInlineCache))
        .assertContainsNoOtherRules();

    assertEquals(
        ImmutableList.of(
            "HP"
                + MethodReferenceUtils.toSmaliString(mergedInitSubject.getFinalReference())
                + "+]"
                + baseSubject.getFinalDescriptor()
                + targetASubject.getFinalDescriptor()
                + ","
                + targetBSubject.getFinalDescriptor()
                + "]"
                + otherSubject.getFinalDescriptor()
                + "missing_types"),
        FileUtils.readAllLines(outputProfile));

    compileResult
        .run(parameters.getRuntime(), HorizontalCallerProgram.Main.class)
        .assertSuccessWithOutputLines("BaseCallerACallerBTargetATargetBOther");
  }

  static class HorizontalTargetProgram {

    @NeverClassInline
    @NoVerticalClassMerging
    abstract static class Base {

      abstract void action();
    }

    @NeverClassInline
    static class SubA extends Base {

      @NeverInline
      @Override
      void action() {
        System.out.println("SubA");
      }
    }

    @NeverClassInline
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

  static class HorizontalCallerProgram {

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class Base {

      @NeverInline
      void ping() {
        System.out.print("Base");
      }
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class TargetA extends Base {

      @NeverInline
      void name() {
        System.out.print("TargetA");
      }
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class TargetB extends Base {

      @NeverInline
      void name() {
        System.out.print("TargetB");
      }
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class Other {

      @NeverInline
      void id() {
        System.out.print("Other");
      }
    }

    @NeverClassInline
    static class CallerA {

      CallerA() {}

      @NeverInline
      void tag() {
        System.out.print("CallerA");
      }
    }

    @NeverClassInline
    static class CallerB {

      CallerB() {}

      @NeverInline
      void tag() {
        System.out.print("CallerB");
      }
    }

    static class Main {

      public static void main(String[] args) {
        new Base().ping();
        new CallerA().tag();
        new CallerB().tag();
        new TargetA().name();
        new TargetB().name();
        new Other().id();
        System.out.println();
      }
    }
  }
}

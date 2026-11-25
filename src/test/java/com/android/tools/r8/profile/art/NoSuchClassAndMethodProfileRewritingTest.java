// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.ArtProfileInlineCacheData.EntryValue;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.ArrayReference;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NoSuchClassAndMethodProfileRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(getClass())
        .addArtProfileForRewriting(getArtProfile())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
        .inspectResidualArtProfile(this::inspectResidualArtProfile)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
        .inspectResidualArtProfile(this::inspectResidualArtProfile)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testInlineCachesWithMissingTypesD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addInnerClasses(InlineCacheProgram.class)
        .addArtProfileForRewriting(getInlineCachesArtProfile())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages)
        .inspectResidualArtProfile(this::inspectInlineCacheResidualArtProfile)
        .run(parameters.getRuntime(), InlineCacheProgram.InlineCacheMain.class)
        .assertSuccessWithOutputLines("present", "m1m2m3m4");
  }

  @Test
  public void testInlineCachesWithMissingTypesR8() throws Exception {
    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(InlineCacheProgram.class)
            .addKeepMainRule(InlineCacheProgram.InlineCacheMain.class)
            .addArtProfileForRewriting(getInlineCachesArtProfile())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertNoMessages);
    compileResult
        .inspectResidualArtProfile(this::inspectInlineCacheResidualArtProfile)
        .run(parameters.getRuntime(), InlineCacheProgram.InlineCacheMain.class)
        .assertSuccessWithOutputLines("present", "m1m2m3m4");
  }

  private ExternalArtProfile getArtProfile() {
    ClassReference missingClassReference = Reference.classFromDescriptor("Lfoo/Missing;");
    return ExternalArtProfile.builder()
        .addClassRule(missingClassReference)
        .addMethodRule(Reference.methodFromDescriptor(missingClassReference, "m", "()V"))
        .build();
  }

  private ExternalArtProfile getInlineCachesArtProfile() {
    ClassReference presentRef = Reference.classFromClass(InlineCacheProgram.Present.class);
    ClassReference missingClassRef = Reference.classFromDescriptor("Lfoo/Missing;");
    ArrayReference missingArrayRef = Reference.array(missingClassRef, 1);
    TypeReference intArrayRef = Reference.typeFromDescriptor("[I");
    ArtProfileMethodRuleInfo hotRule = ArtProfileMethodRuleInfoImpl.builder().setIsHot().build();
    return ExternalArtProfile.builder()
        .addMethodRule(
            MethodReferenceUtils.methodFromMethod(
                InlineCacheProgram.InlineCacheMain.class, "mMixedSingle"),
            hotRule,
            ArtProfileInlineCacheData.createSingle(EntryValue.ofTypes(presentRef, missingClassRef)))
        .addMethodRule(
            MethodReferenceUtils.methodFromMethod(
                InlineCacheProgram.InlineCacheMain.class, "mAllMissingSingle"),
            hotRule,
            ArtProfileInlineCacheData.createSingle(
                EntryValue.ofTypes(missingClassRef, missingArrayRef)))
        .addMethodRule(
            MethodReferenceUtils.methodFromMethod(
                InlineCacheProgram.InlineCacheMain.class, "mPrimitiveAndMissingArraySingle"),
            hotRule,
            ArtProfileInlineCacheData.createSingle(
                EntryValue.ofTypes(intArrayRef, missingArrayRef)))
        .addMethodRule(
            MethodReferenceUtils.methodFromMethod(
                InlineCacheProgram.InlineCacheMain.class, "mTargetedMissing"),
            hotRule,
            ArtProfileInlineCacheData.builder()
                .addTargetedInlineCache(missingClassRef, EntryValue.ofTypes(presentRef))
                .addTargetedInlineCache(presentRef, EntryValue.ofTypes(presentRef, missingClassRef))
                .addTargetedInlineCache(missingArrayRef, EntryValue.missingTypes())
                .build())
        .build();
  }

  private void inspectResidualArtProfile(ArtProfileInspector profileInspector) {
    // None of the items in the profile exist in the app.
    profileInspector.assertEmpty();
  }

  private void inspectInlineCacheResidualArtProfile(
      ArtProfileInspector profileInspector, CodeInspector inspector) {
    ClassSubject mainSubject = inspector.clazz(InlineCacheProgram.InlineCacheMain.class);
    ClassSubject presentSubject = inspector.clazz(InlineCacheProgram.Present.class);
    ClassReference finalPresentRef = presentSubject.getFinalReference();
    TypeReference intArrayRef = Reference.typeFromDescriptor("[I");

    MethodSubject mMixedSingle = mainSubject.uniqueMethodWithOriginalName("mMixedSingle");
    MethodSubject mAllMissingSingle = mainSubject.uniqueMethodWithOriginalName("mAllMissingSingle");
    MethodSubject mPrimitiveAndMissingArraySingle =
        mainSubject.uniqueMethodWithOriginalName("mPrimitiveAndMissingArraySingle");
    MethodSubject mTargetedMissing = mainSubject.uniqueMethodWithOriginalName("mTargetedMissing");

    profileInspector
        .inspectMethodRule(
            mMixedSingle,
            ruleInspector ->
                ruleInspector.assertIsHot().assertHasSingleInlineCache(finalPresentRef))
        .inspectMethodRule(
            mAllMissingSingle,
            ruleInspector -> ruleInspector.assertIsHot().assertHasNoInlineCaches())
        .inspectMethodRule(
            mPrimitiveAndMissingArraySingle,
            ruleInspector -> ruleInspector.assertIsHot().assertHasSingleInlineCache(intArrayRef))
        .inspectMethodRule(
            mTargetedMissing,
            ruleInspector ->
                ruleInspector
                    .assertIsHot()
                    .assertHasInlineCacheData(
                        ArtProfileInlineCacheData.builder()
                            .addTargetedInlineCache(
                                finalPresentRef, EntryValue.ofTypes(finalPresentRef))
                            .build()))
        .assertContainsNoOtherRules();
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }

  static class InlineCacheProgram {

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class Present {

      @NeverInline
      void ping() {
        System.out.println("present");
      }
    }

    static class InlineCacheMain {

      public static void main(String[] args) {
        new Present().ping();
        mMixedSingle();
        mAllMissingSingle();
        mPrimitiveAndMissingArraySingle();
        mTargetedMissing();
        System.out.println();
      }

      @NeverInline
      static void mMixedSingle() {
        System.out.print("m1");
      }

      @NeverInline
      static void mAllMissingSingle() {
        System.out.print("m2");
      }

      @NeverInline
      static void mPrimitiveAndMissingArraySingle() {
        System.out.print("m3");
      }

      @NeverInline
      static void mTargetedMissing() {
        System.out.print("m4");
      }
    }
  }
}

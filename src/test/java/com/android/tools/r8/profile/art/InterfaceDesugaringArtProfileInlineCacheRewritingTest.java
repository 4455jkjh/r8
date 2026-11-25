// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.profile.art.ArtProfileInlineCacheData.EntryValue;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.profile.art.utils.ArtProfileTestingUtils;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.Box;
import com.android.tools.r8.utils.internal.FileUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InterfaceDesugaringArtProfileInlineCacheRewritingTest
    extends ArtProfileInlineCacheRewritingTestBase {

  public InterfaceDesugaringArtProfileInlineCacheRewritingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    Path outputProfile = temp.newFile("interface-desugaring-d8-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();

    TestCompileResult<?, ?> compileResult =
        testForD8()
            .addInnerClasses(InterfaceDesugaringProgram.class)
            .addArtProfileForRewriting(
                ArtProfileTestingUtils.createArtProfileProvider(getInputProfile()),
                createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
            .release()
            .setMinApi(parameters)
            .compile();

    inspectResult(compileResult, outputProfile, residualProfileBox.get(), false);
    compileResult
        .run(parameters.getRuntime(), InterfaceDesugaringProgram.Main.class)
        .assertSuccessWithOutputLines("Sub");
  }

  @Test
  public void testR8() throws Exception {
    Path outputProfile = temp.newFile("interface-desugaring-r8-out.txt").toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(InterfaceDesugaringProgram.class)
            .addKeepMainRule(InterfaceDesugaringProgram.Main.class)
            .addArtProfileForRewriting(
                ArtProfileTestingUtils.createArtProfileProvider(getInputProfile()),
                createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
            .addOptionsModification(InlinerOptions::disableInlining)
            .addOptionsModification(
                options -> options.callSiteOptimizationOptions().disableOptimization())
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile();

    inspectResult(compileResult, outputProfile, residualProfileBox.get(), true);
    compileResult
        .run(parameters.getRuntime(), InterfaceDesugaringProgram.Main.class)
        .assertSuccessWithOutputLines("Sub");
  }

  private static ExternalArtProfile getInputProfile() throws Exception {
    ClassReference baseRef = Reference.classFromClass(InterfaceDesugaringProgram.Base.class);
    ClassReference subRef = Reference.classFromClass(InterfaceDesugaringProgram.Sub.class);
    MethodReference callWithMethodRef =
        MethodReferenceUtils.methodFromMethod(
            InterfaceDesugaringProgram.I.class,
            "callWithMethodRef",
            InterfaceDesugaringProgram.Base.class);
    return ExternalArtProfile.builder()
        .addMethodRule(
            callWithMethodRef,
            HOT_RULE,
            ArtProfileInlineCacheData.builder()
                .addTargetedInlineCache(baseRef, EntryValue.ofTypes(subRef))
                .build())
        .build();
  }

  private void inspectResult(
      TestCompileResult<?, ?> compileResult,
      Path outputProfile,
      ExternalArtProfile residualProfile,
      boolean isR8)
      throws Exception {
    CodeInspector inspector = compileResult.inspector();
    ClassSubject baseSubject = inspector.clazz(InterfaceDesugaringProgram.Base.class);
    ClassSubject subSubject = inspector.clazz(InterfaceDesugaringProgram.Sub.class);
    assertThat(baseSubject, isPresent());
    assertThat(subSubject, isPresent());

    ArtProfileInspector profileInspector = new ArtProfileInspector(residualProfile);
    MethodSubject targetMethodSubject;
    if (parameters.isDexRuntime()) {
      ClassSubject companionSubject =
          inspector.clazz(
              SyntheticItemsTestUtils.syntheticCompanionClass(InterfaceDesugaringProgram.I.class));
      assertThat(companionSubject, isPresent());
      targetMethodSubject = companionSubject.uniqueMethodWithOriginalName("callWithMethodRef");
      assertThat(inspector.clazz(InterfaceDesugaringProgram.I.class), isPresentIf(!isR8));
    } else {
      ClassSubject interfaceSubject = inspector.clazz(InterfaceDesugaringProgram.I.class);
      assertThat(interfaceSubject, isPresent());
      targetMethodSubject = interfaceSubject.uniqueMethodWithOriginalName("callWithMethodRef");
    }
    assertThat(targetMethodSubject, isPresent());

    profileInspector.inspectMethodRule(
        targetMethodSubject,
        ruleInspector ->
            ruleInspector
                .assertIsHot()
                .assertHasTargetedInlineCache(
                    baseSubject.getFinalReference(), subSubject.getFinalReference()));

    if (parameters.isDexRuntime()) {
      // Verify all other method rules (such as the synthesized lambda class's <init> and run)
      // inherited the hot flag without copying inline caches.
      Box<Integer> otherMethodRuleCount = new Box<>(0);
      residualProfile.forEach(
          classRule -> {},
          methodRule -> {
            if (!methodRule.getMethodReference().equals(targetMethodSubject.getFinalReference())) {
              otherMethodRuleCount.set(otherMethodRuleCount.get() + 1);
              assertTrue(methodRule.getMethodRuleInfo().isHot());
              assertTrue(methodRule.getInlineCacheData().isEmpty());
            }
          });
      assertTrue(otherMethodRuleCount.get() > 0);
    } else {
      profileInspector.assertContainsNoOtherRules();
    }

    String expectedTargetRuleLine =
        "H"
            + MethodReferenceUtils.toSmaliString(targetMethodSubject.getFinalReference())
            + "+]"
            + baseSubject.getFinalDescriptor()
            + subSubject.getFinalDescriptor();
    List<String> outputLines = FileUtils.readAllLines(outputProfile);
    assertTrue(outputLines.contains(expectedTargetRuleLine));
    assertEquals(residualProfile.size(), outputLines.size());
    for (String line : outputLines) {
      if (!line.equals(expectedTargetRuleLine)) {
        assertEquals(-1, line.indexOf('+'));
      }
    }
  }

  static class InterfaceDesugaringProgram {

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    static class Base {

      @NeverInline
      void action() {
        System.out.println("Base");
      }
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    static class Sub extends Base {

      @NeverInline
      @Override
      void action() {
        System.out.println("Sub");
      }
    }

    interface I {

      @NeverInline
      static void callWithMethodRef(Base b) {
        Runnable r = b::action;
        r.run();
      }
    }

    static class Main {

      public static void main(String[] args) {
        I.callWithMethodRef(System.currentTimeMillis() > 0 ? new Sub() : new Base());
      }
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.startsWith;
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
public class RepackagingArtProfileInlineCacheRewritingTest
    extends ArtProfileInlineCacheRewritingTestBase {

  public RepackagingArtProfileInlineCacheRewritingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testRepackagingWithDontObfuscate() throws Exception {
    runRepackagingTest(true);
  }

  @Test
  public void testRepackagingWithMinification() throws Exception {
    runRepackagingTest(false);
  }

  private void runRepackagingTest(boolean dontObfuscate) throws Exception {
    ClassReference baseRef = Reference.classFromClass(RepackagingProgram.Base.class);
    ClassReference subARef = Reference.classFromClass(RepackagingProgram.SubA.class);
    ClassReference subBRef = Reference.classFromClass(RepackagingProgram.SubB.class);
    MethodReference callMethodRef =
        MethodReferenceUtils.methodFromMethod(
            RepackagingProgram.Main.class, "call", RepackagingProgram.Base.class);

    ExternalArtProfile inputProfile =
        ExternalArtProfile.builder()
            .addMethodRule(
                callMethodRef,
                HOT_RULE,
                ArtProfileInlineCacheData.builder()
                    .addTargetedInlineCache(baseRef, EntryValue.ofTypes(subARef, subBRef))
                    .build())
            .build();

    Path outputProfile =
        temp.newFile(dontObfuscate ? "repackage-dontobf-out.txt" : "repackage-min-out.txt")
            .toPath();
    Box<ExternalArtProfile> residualProfileBox = new Box<>();

    R8TestCompileResultBase<?> compileResult =
        testForR8(parameters)
            .addInnerClasses(RepackagingProgram.class)
            .addKeepMainRule(RepackagingProgram.Main.class)
            .addKeepRules("-repackageclasses \"repackaged\"")
            .applyIf(dontObfuscate, builder -> builder.addDontObfuscate())
            .addArtProfileForRewriting(
                ArtProfileTestingUtils.createArtProfileProvider(inputProfile),
                createCombinedArtProfileConsumer(outputProfile, residualProfileBox))
            .addOptionsModification(InlinerOptions::disableInlining)
            .addOptionsModification(
                options -> options.callSiteOptimizationOptions().disableOptimization())
            .addRepackagingInspector(
                inspector ->
                    inspector
                        .assertIsRepackaged(baseRef)
                        .assertIsRepackaged(subARef)
                        .assertIsRepackaged(subBRef))
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .compile();

    CodeInspector inspector = compileResult.inspector();
    ClassSubject mainSubject = inspector.clazz(RepackagingProgram.Main.class);
    MethodSubject callSubject = mainSubject.uniqueMethodWithOriginalName("call");
    assertThat(callSubject, isPresent());

    ClassSubject baseSubject = inspector.clazz(RepackagingProgram.Base.class);
    ClassSubject subASubject = inspector.clazz(RepackagingProgram.SubA.class);
    ClassSubject subBSubject = inspector.clazz(RepackagingProgram.SubB.class);
    assertThat(baseSubject, isPresent());
    assertThat(subASubject, isPresent());
    assertThat(subBSubject, isPresent());
    assertThat(baseSubject.getFinalDescriptor(), startsWith("Lrepackaged/"));
    assertThat(subASubject.getFinalDescriptor(), startsWith("Lrepackaged/"));
    assertThat(subBSubject.getFinalDescriptor(), startsWith("Lrepackaged/"));
    if (dontObfuscate) {
      assertThat(baseSubject.getFinalName(), startsWith("repackaged.com.android.tools.r8."));
    } else {
      assertThat(baseSubject, isPresentAndRenamed());
      assertEquals("repackaged", baseSubject.getDexProgramClass().getType().getPackageName());
    }

    new ArtProfileInspector(residualProfileBox.get())
        .inspectMethodRule(
            callSubject,
            ruleInspector ->
                ruleInspector.assertHasTargetedInlineCache(
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
        .run(parameters.getRuntime(), RepackagingProgram.Main.class)
        .assertSuccessWithOutputLines("SubA", "SubB");
  }

  public static class RepackagingProgram {

    @NeverClassInline
    @NoHorizontalClassMerging
    @NoVerticalClassMerging
    public abstract static class Base {

      public Base() {}

      public abstract void action();
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    public static class SubA extends Base {

      public SubA() {}

      @NeverInline
      @Override
      public void action() {
        System.out.println("SubA");
      }
    }

    @NeverClassInline
    @NoHorizontalClassMerging
    public static class SubB extends Base {

      public SubB() {}

      @NeverInline
      @Override
      public void action() {
        System.out.println("SubB");
      }
    }

    public static class Main {

      public static void main(String[] args) {
        call(System.currentTimeMillis() > 0 ? new SubA() : new SubB());
        call(System.currentTimeMillis() < 0 ? new SubA() : new SubB());
      }

      @NeverInline
      public static void call(Base b) {
        b.action();
      }
    }
  }
}

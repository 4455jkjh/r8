// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class MovedPrivateInterfaceMethodProfileRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enableDexVersion35UpToApi30;

  @Parameters(name = "{0}, enableDexVersion35UpToApi30={1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeFalse(enableDexVersion35UpToApi30);
    testForJvm(parameters)
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getTransformedInterface())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testD8() throws Exception {
    assumeFalse(parameters.isCfRuntime() && enableDexVersion35UpToApi30);
    testForD8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getTransformedInterface())
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(
            options ->
                options.getTestingOptions().enableDexVersion35UpToApi30 =
                    enableDexVersion35UpToApi30)
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeFalse(parameters.isCfRuntime() && enableDexVersion35UpToApi30);
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class)
        .addProgramClassFileData(getTransformedInterface())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile())
        .addOptionsModification(InlinerOptions::disableInlining)
        .addOptionsModification(
            options ->
                options.getTestingOptions().enableDexVersion35UpToApi30 =
                    enableDexVersion35UpToApi30)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(this::inspectR8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private byte[] getTransformedInterface() throws Exception {
    return transformer(I.class)
        .setPrivate(I.class.getDeclaredMethod("m"))
        .transformMethodInsnInMethod(
            "bridge",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              assertEquals(Opcodes.INVOKEINTERFACE, opcode);
              visitor.visitMethodInsn(Opcodes.INVOKESPECIAL, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  private ExternalArtProfile getArtProfile() throws Exception {
    return ExternalArtProfile.builder()
        .addMethodRule(Reference.methodFromMethod(I.class.getDeclaredMethod("m")))
        .build();
  }

  private void inspectD8(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    inspect(
        profileInspector,
        inspector,
        parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring(),
        true);
  }

  private void inspectR8(ArtProfileInspector profileInspector, CodeInspector inspector)
      throws Exception {
    inspect(
        profileInspector,
        inspector,
        parameters.isCfRuntime() || parameters.canUseDefaultAndStaticInterfaceMethods(),
        false);
  }

  private void inspect(
      ArtProfileInspector profileInspector,
      CodeInspector inspector,
      boolean canUseDefaultAndStaticInterfaceMethods,
      boolean isD8)
      throws Exception {
    if (canUseDefaultAndStaticInterfaceMethods) {
      ClassSubject iClassSubject = inspector.clazz(I.class);
      assertThat(iClassSubject, isPresent());

      boolean isStaticizedInD8 =
          isD8
              && enableDexVersion35UpToApi30
              && parameters.isDexRuntime()
              && parameters.getApiLevel().isLessThanOrEqualTo(AndroidApiLevel.R);
      MethodSubject privateInterfaceMethodSubject =
          iClassSubject.uniqueMethodWithOriginalName(isStaticizedInD8 ? "m$staticized" : "m");
      assertThat(privateInterfaceMethodSubject, isPresent());
      assertEquals(!isD8 || isStaticizedInD8, privateInterfaceMethodSubject.isStatic());

      profileInspector
          .assertContainsClassRule(iClassSubject)
          .assertContainsMethodRule(privateInterfaceMethodSubject)
          .assertContainsNoOtherRules();
    } else {
      ClassSubject companionClassSubject =
          inspector.clazz(SyntheticItemsTestUtils.syntheticCompanionClass(I.class));
      assertThat(companionClassSubject, isPresent());

      String mMethodNameOnICC =
          SyntheticItemsTestUtils.syntheticPrivateInterfaceMethodAsCompanionMethod(
                  I.class.getDeclaredMethod("m"))
              .getMethodName();
      MethodSubject privateInterfaceMethodSubject =
          companionClassSubject.uniqueMethodThatMatches(
              m -> {
                String originalName = m.getOriginalMethodName();
                return originalName.equals("m") || originalName.equals(mMethodNameOnICC);
              });
      assertThat(privateInterfaceMethodSubject, isPresent());

      profileInspector
          .assertContainsClassRule(companionClassSubject)
          .assertContainsMethodRule(privateInterfaceMethodSubject)
          .assertContainsNoOtherRules();
    }
  }

  static class Main {

    public static void main(String[] args) {
      new A().bridge();
    }
  }

  @NoVerticalClassMerging
  interface I {

    default void bridge() {
      m(); // invoke-special
    }

    /*private*/ default void m() {
      System.out.println("Hello, world!");
    }
  }

  static class A implements I {}
}

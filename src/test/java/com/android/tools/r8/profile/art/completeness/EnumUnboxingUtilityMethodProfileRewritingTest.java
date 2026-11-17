// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
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
public class EnumUnboxingUtilityMethodProfileRewritingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    runTest(true);
  }

  @Test
  public void testR8NonStartup() throws Exception {
    runTest(false);
  }

  private void runTest(boolean isStartup) throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(getArtProfile(isStartup))
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .addOptionsModification(InlinerOptions::disableInlining)
        .noHorizontalClassMergingOfSynthetics()
        .setMinApi(parameters)
        .compile()
        .inspectResidualArtProfile(
            (profileInspector, inspector) -> inspect(profileInspector, inspector, isStartup))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private ExternalArtProfile getArtProfile(boolean isStartup) throws Exception {
    ArtProfileMethodRuleInfo methodRuleInfo =
        ArtProfileMethodRuleInfoImpl.builder()
            .setIsHot()
            .setIsStartup(isStartup)
            .setIsPostStartup()
            .build();
    return ExternalArtProfile.builder()
        .addMethodRule(MethodReferenceUtils.mainMethod(Main.class), methodRuleInfo)
        .addMethodRule(
            Reference.methodFromMethod(MyEnum.class.getDeclaredMethod("greet")), methodRuleInfo)
        .addMethodRule(
            Reference.methodFromMethod(MyEnum.class.getDeclaredMethod("other")), methodRuleInfo)
        .addMethodRule(
            Reference.methodFromMethod(MyEnum.class.getDeclaredMethod("values")), methodRuleInfo)
        .build();
  }

  private void inspect(
      ArtProfileInspector profileInspector, CodeInspector inspector, boolean isStartup) {
    ClassSubject mainClassSubject = inspector.clazz(Main.class);
    assertThat(mainClassSubject, isPresent());
    assertThat(mainClassSubject.mainMethod(), isPresent());

    ClassSubject enumUnboxingLocalUtilityClassSubject =
        inspector.clazz(
            SyntheticItemsTestUtils.syntheticEnumUnboxingLocalUtilityClass(MyEnum.class));
    assertThat(enumUnboxingLocalUtilityClassSubject, isPresent());

    MethodSubject localGreetMethodSubject =
        enumUnboxingLocalUtilityClassSubject.uniqueMethodWithOriginalName("greet");
    assertThat(localGreetMethodSubject, isPresent());

    MethodSubject localOtherMethodSubject =
        enumUnboxingLocalUtilityClassSubject.uniqueMethodWithOriginalName("other");
    assertThat(localOtherMethodSubject, isPresent());

    MethodSubject localValuesMethodSubject =
        enumUnboxingLocalUtilityClassSubject.uniqueMethodWithOriginalName("values");
    assertThat(localValuesMethodSubject, isPresent());

    ClassSubject enumUnboxingSharedUtilityClassSubject =
        inspector.clazz(
            SyntheticItemsTestUtils.syntheticEnumUnboxingSharedUtilityClass(MyEnum.class));
    assertThat(enumUnboxingSharedUtilityClassSubject, isPresent());
    assertThat(enumUnboxingSharedUtilityClassSubject.clinit(), isPresent());

    MethodSubject sharedOrdinalMethodSubject =
        enumUnboxingSharedUtilityClassSubject.uniqueMethodWithOriginalName("ordinal");
    assertThat(sharedOrdinalMethodSubject, isPresent());

    MethodSubject sharedValuesMethodSubject =
        enumUnboxingSharedUtilityClassSubject.uniqueMethodWithOriginalName("values");
    assertThat(sharedValuesMethodSubject, isPresent());

    profileInspector
        .applyIf(
            isStartup,
            i ->
                i.assertContainsClassRules(
                    mainClassSubject,
                    enumUnboxingLocalUtilityClassSubject,
                    enumUnboxingSharedUtilityClassSubject))
        .assertContainsMethodRules(
            mainClassSubject.mainMethod(),
            localGreetMethodSubject,
            localOtherMethodSubject,
            localValuesMethodSubject,
            enumUnboxingSharedUtilityClassSubject.clinit(),
            sharedOrdinalMethodSubject,
            sharedValuesMethodSubject)
        .assertContainsNoOtherRules();
  }

  static class Main {

    public static void main(String[] args) {
      MyEnum a = System.currentTimeMillis() > 0 ? MyEnum.A : MyEnum.B;
      MyEnum b = a.other();
      a.greet();
      b.greet();
    }
  }

  enum MyEnum {
    A,
    B;

    void greet() {
      switch (this) {
        case A:
          System.out.print("Hello");
          break;
        case B:
          System.out.println(", world!");
          break;
      }
    }

    MyEnum other() {
      return MyEnum.values()[1 - ordinal()];
    }
  }
}

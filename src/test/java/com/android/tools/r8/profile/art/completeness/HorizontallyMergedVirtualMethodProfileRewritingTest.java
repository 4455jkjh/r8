// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.completeness;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileInspector;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.InternalOptions.InlinerOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class HorizontallyMergedVirtualMethodProfileRewritingTest extends TestBase {

  private enum ArtProfileInputOutput {
    A_METHOD,
    B_METHOD;

    public ExternalArtProfile getArtProfile() throws Exception {
      switch (this) {
        case A_METHOD:
          return ExternalArtProfile.builder()
              .addMethodRule(Reference.methodFromMethod(A.class.getDeclaredMethod("m")))
              .build();
        case B_METHOD:
          return ExternalArtProfile.builder()
              .addMethodRule(Reference.methodFromMethod(B.class.getDeclaredMethod("m")))
              .build();
        default:
          throw new RuntimeException();
      }
    }

    public void inspect(ArtProfileInspector profileInspector, CodeInspector inspector) {
      ClassSubject aClassSubject = inspector.clazz(A.class);
      assertThat(aClassSubject, isPresent());

      String stringConstInMovedMethod = this == A_METHOD ? "Hello" : ", world!";
      MethodSubject movedMethodSubject =
          aClassSubject.uniqueMethodThatMatches(
              method ->
                  method.isPrivate()
                      && method
                          .streamInstructions()
                          .anyMatch(
                              instruction -> instruction.isConstString(stringConstInMovedMethod)));
      assertThat(movedMethodSubject, isPresent());

      MethodSubject syntheticBridgeMethodSubject =
          aClassSubject.uniqueMethodThatMatches(FoundMethodSubject::isVirtual);
      assertThat(syntheticBridgeMethodSubject, isPresent());

      // TODO(b/265729283): Should contain the synthetic bridge method from above.
      profileInspector.assertContainsMethodRule(movedMethodSubject).assertContainsNoOtherRules();
    }
  }

  @Parameter(0)
  public ArtProfileInputOutput artProfileInputOutput;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, {0}")
  public static List<Object[]> data() {
    return buildParameters(
        ArtProfileInputOutput.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addArtProfileForRewriting(artProfileInputOutput.getArtProfile())
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class).assertNoOtherClassesMerged())
        .addOptionsModification(InlinerOptions::setOnlyForceInlining)
        .addOptionsModification(
            options -> options.callSiteOptimizationOptions().setEnableMethodStaticizing(false))
        .setMinApi(parameters.getApiLevel())
        .compile()
        .inspectResidualArtProfile(artProfileInputOutput::inspect)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      new A().m();
      new B().m();
    }
  }

  static class A {

    public void m() {
      System.out.print("Hello");
    }
  }

  static class B {

    public void m() {
      System.out.println(", world!");
    }
  }
}

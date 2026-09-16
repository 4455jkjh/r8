// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerPackagePrivateMethodDispatchTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(A.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("B.m()", "A.m()");
  }

  @Test
  public void testR8() throws Exception {
    // The old DEX runtimes do not respect package boundaries for virtual dispatch.
    boolean respectsPackageBoundary =
        !parameters.isDexRuntime() || !parameters.getDexRuntimeVersion().isDalvik();
    testForR8(parameters)
        .addProgramClasses(A.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            respectsPackageBoundary
                ? ImmutableList.of("B.m()", "A.m()")
                : ImmutableList.of("B.m()", "B.m()"));
  }

  private List<byte[]> getProgramClassFileData() {
    // Move B into a separate package.
    return ImmutableList.of(
        transformer(B.class).setClassDescriptor("Lother/B;").removeInnerClasses().transform(),
        transformer(C.class)
            .setSuper("Lother/B;")
            .replaceClassDescriptorInMethodInstructions(descriptor(B.class), "Lother/B;")
            .removeInnerClasses()
            .transform(),
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(descriptor(B.class), "Lother/B;")
            .transform());
  }

  static class Main {

    @SuppressWarnings("UnnecessaryLocalVariable")
    public static void main(String[] args) {
      C c = new C();
      c.m();
      A a = c;
      a.m();
    }
  }

  @NoVerticalClassMerging
  public static class A {

    @NeverInline
    void m() {
      System.out.println("A.m()");
    }
  }

  // Transformed to other.B.
  public static class B extends A {

    // Not a valid override of A.m after moving B to other package since A.m is package private.
    @NeverInline
    public void m() {
      System.out.println("B.m()");
    }
  }

  // Transformed to extend other.B.
  @NeverClassInline
  public static class C extends B {}
}

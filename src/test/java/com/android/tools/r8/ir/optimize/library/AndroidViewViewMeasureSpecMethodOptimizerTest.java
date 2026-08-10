// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolder;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidViewViewMeasureSpecMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    byte[] measureSpecBytes =
        transformer(MeasureSpecStub.class)
            .setClassDescriptor("Landroid/view/View$MeasureSpec;")
            .transform();
    byte[] mainBytes =
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(MeasureSpecStub.class), "Landroid/view/View$MeasureSpec;")
            .transform();
    testForD8(parameters)
        .addProgramClassFileData(mainBytes)
        .addLibraryClassFileData(measureSpecBytes)
        .addDefaultRuntimeLibrary(parameters)
        .release()
        .compile()
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    byte[] measureSpecBytes =
        transformer(MeasureSpecStub.class)
            .setClassDescriptor("Landroid/view/View$MeasureSpec;")
            .transform();
    byte[] mainBytes =
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(MeasureSpecStub.class), "Landroid/view/View$MeasureSpec;")
            .transform();
    testForR8(parameters)
        .addProgramClassFileData(mainBytes)
        .addLibraryClassFileData(measureSpecBytes)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    MethodSubject method = mainClass.uniqueMethodWithOriginalName("testMakeMeasureSpec");
    assertThat(method, not(invokesMethodWithHolder("android.view.View$MeasureSpec")));
  }

  public static class MeasureSpecStub {
    public static int makeMeasureSpec(int size, int mode) {
      throw new RuntimeException();
    }
  }

  static class Main {
    public static void main(String[] args) {
      testMakeMeasureSpec();
    }

    @NeverInline
    public static void testMakeMeasureSpec() {
      System.out.println(MeasureSpecStub.makeMeasureSpec(100, 0x40000000));
    }
  }
}

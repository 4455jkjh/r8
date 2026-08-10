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
public class AndroidGraphicsImageFormatMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    byte[] imageFormatBytes =
        transformer(ImageFormatStub.class)
            .setClassDescriptor("Landroid/graphics/ImageFormat;")
            .transform();
    byte[] mainBytes =
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(ImageFormatStub.class), "Landroid/graphics/ImageFormat;")
            .transform();
    testForD8(parameters)
        .addProgramClassFileData(mainBytes)
        .addLibraryClassFileData(imageFormatBytes)
        .addDefaultRuntimeLibrary(parameters)
        .release()
        .compile()
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    byte[] imageFormatBytes =
        transformer(ImageFormatStub.class)
            .setClassDescriptor("Landroid/graphics/ImageFormat;")
            .transform();
    byte[] mainBytes =
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(ImageFormatStub.class), "Landroid/graphics/ImageFormat;")
            .transform();
    testForR8(parameters)
        .addProgramClassFileData(mainBytes)
        .addLibraryClassFileData(imageFormatBytes)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    MethodSubject method = mainClass.uniqueMethodWithOriginalName("testGetBitsPerPixel");
    assertThat(method, not(invokesMethodWithHolder("android.graphics.ImageFormat")));
  }

  public static class ImageFormatStub {
    public static int getBitsPerPixel(int format) {
      throw new RuntimeException();
    }
  }

  static class Main {
    public static void main(String[] args) {
      testGetBitsPerPixel();
    }

    @NeverInline
    public static void testGetBitsPerPixel() {
      System.out.println(ImageFormatStub.getBitsPerPixel(4));
    }
  }
}

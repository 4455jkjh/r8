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
public class AndroidGraphicsColorMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    byte[] colorBytes =
        transformer(ColorStub.class).setClassDescriptor("Landroid/graphics/Color;").transform();
    byte[] mainBytes =
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(ColorStub.class), "Landroid/graphics/Color;")
            .transform();
    testForD8(parameters)
        .addProgramClassFileData(mainBytes)
        .addLibraryClassFileData(colorBytes)
        .addDefaultRuntimeLibrary(parameters)
        .addOptionsModification(options -> options.apiModelingOptions().disableOutlining())
        .release()
        .compile()
        .inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    byte[] colorBytes =
        transformer(ColorStub.class).setClassDescriptor("Landroid/graphics/Color;").transform();
    byte[] mainBytes =
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(ColorStub.class), "Landroid/graphics/Color;")
            .transform();
    testForR8(parameters)
        .addProgramClassFileData(mainBytes)
        .addLibraryClassFileData(colorBytes)
        .addDontObfuscate()
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .addOptionsModification(options -> options.apiModelingOptions().disableOutlining())
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testAlphaInt"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testAlphaLong"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testArgbInt"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testArgbFloat"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testBlueInt"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testBlueLong"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testGreenInt"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testGreenLong"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testIsSrgb"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testIsWideGamut"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testLuminanceInt"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testLuminanceLong"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testPackInt"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testPackFloat4"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testPackFloat3"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testParseColor"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testRedInt"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testRedLong"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testRgbInt"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testRgbFloat"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testToArgb"));
    verifyMethodHasNoColorInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsWideGamutLinearSrgb"));
    verifyMethodHasNoColorInvokes(mainClass.uniqueMethodWithOriginalName("testIsWideGamutBt709"));
    verifyMethodHasNoColorInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsWideGamutDisplayP3"));
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testIsWideGamutInvalidId"),
        invokesMethodWithHolder("android.graphics.Color"));
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testIsSrgbInvalidId"),
        invokesMethodWithHolder("android.graphics.Color"));
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testToArgbNonSrgb"),
        invokesMethodWithHolder("android.graphics.Color"));
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testLuminanceLongNonSrgb"),
        invokesMethodWithHolder("android.graphics.Color"));
  }

  private void verifyMethodHasNoColorInvokes(MethodSubject method) {
    assertThat(method, not(invokesMethodWithHolder("android.graphics.Color")));
  }

  public static class ColorStub {

    public static int alpha(int color) {
      throw new RuntimeException();
    }

    public static float alpha(long color) {
      throw new RuntimeException();
    }

    public static int argb(int alpha, int red, int green, int blue) {
      throw new RuntimeException();
    }

    public static int argb(float alpha, float red, float green, float blue) {
      throw new RuntimeException();
    }

    public static int blue(int color) {
      throw new RuntimeException();
    }

    public static float blue(long color) {
      throw new RuntimeException();
    }

    public static float green(long color) {
      throw new RuntimeException();
    }

    public static int green(int color) {
      throw new RuntimeException();
    }

    public static boolean isSrgb(long color) {
      throw new RuntimeException();
    }

    public static boolean isWideGamut(long color) {
      throw new RuntimeException();
    }

    public static float luminance(long color) {
      throw new RuntimeException();
    }

    public static float luminance(int color) {
      throw new RuntimeException();
    }

    public static long pack(int color) {
      throw new RuntimeException();
    }

    public static long pack(float red, float green, float blue, float alpha) {
      throw new RuntimeException();
    }

    public static long pack(float red, float green, float blue) {
      throw new RuntimeException();
    }

    public static int parseColor(String colorString) {
      throw new RuntimeException();
    }

    public static float red(long color) {
      throw new RuntimeException();
    }

    public static int red(int color) {
      throw new RuntimeException();
    }

    public static int rgb(float red, float green, float blue) {
      throw new RuntimeException();
    }

    public static int rgb(int red, int green, int blue) {
      throw new RuntimeException();
    }

    public static int toArgb(long color) {
      throw new RuntimeException();
    }
  }

  static class Main {

    public static void main(String[] args) {
      testAlphaInt();
      testAlphaLong();
      testArgbInt();
      testArgbFloat();
      testBlueInt();
      testBlueLong();
      testGreenInt();
      testGreenLong();
      testIsSrgb();
      testIsWideGamut();
      testLuminanceInt();
      testLuminanceLong();
      testPackInt();
      testPackFloat4();
      testPackFloat3();
      testParseColor();
      testRedInt();
      testRedLong();
      testRgbInt();
      testRgbFloat();
      testToArgb();
      testIsWideGamutLinearSrgb();
      testIsWideGamutBt709();
      testIsWideGamutDisplayP3();
      testIsWideGamutInvalidId();
      testIsSrgbInvalidId();
      testToArgbNonSrgb();
      testLuminanceLongNonSrgb();
    }

    @NeverInline
    public static void testIsWideGamutLinearSrgb() {
      System.out.println(ColorStub.isWideGamut(0x00000000ff112201L));
    }

    @NeverInline
    public static void testIsWideGamutBt709() {
      System.out.println(ColorStub.isWideGamut(0x00000000ff112204L));
    }

    @NeverInline
    public static void testIsWideGamutDisplayP3() {
      System.out.println(ColorStub.isWideGamut(0x00000000ff112207L));
    }

    @NeverInline
    public static void testIsWideGamutInvalidId() {
      System.out.println(ColorStub.isWideGamut(0x00000000ff11223fL));
    }

    @NeverInline
    public static void testIsSrgbInvalidId() {
      System.out.println(ColorStub.isSrgb(0x00000000ff11223fL));
    }

    @NeverInline
    public static void testToArgbNonSrgb() {
      System.out.println(ColorStub.toArgb(0x00000000ff112201L));
    }

    @NeverInline
    public static void testLuminanceLongNonSrgb() {
      System.out.println(ColorStub.luminance(0x00000000ff112201L));
    }

    @NeverInline
    public static void testAlphaInt() {
      System.out.println(ColorStub.alpha(0xff112233));
    }

    @NeverInline
    public static void testAlphaLong() {
      System.out.println(ColorStub.alpha(0xff11223344556677L));
    }

    @NeverInline
    public static void testArgbInt() {
      System.out.println(ColorStub.argb(255, 16, 32, 48));
    }

    @NeverInline
    public static void testArgbFloat() {
      System.out.println(ColorStub.argb(1.0f, 0.5f, 0.25f, 0.125f));
    }

    @NeverInline
    public static void testBlueInt() {
      System.out.println(ColorStub.blue(0xff112233));
    }

    @NeverInline
    public static void testBlueLong() {
      System.out.println(ColorStub.blue(0x00000000ff112200L));
    }

    @NeverInline
    public static void testGreenInt() {
      System.out.println(ColorStub.green(0xff112233));
    }

    @NeverInline
    public static void testGreenLong() {
      System.out.println(ColorStub.green(0x00000000ff112200L));
    }

    @NeverInline
    public static void testIsSrgb() {
      System.out.println(ColorStub.isSrgb(0x00000000ff112200L));
    }

    @NeverInline
    public static void testIsWideGamut() {
      System.out.println(ColorStub.isWideGamut(0x00000000ff112200L));
    }

    @NeverInline
    public static void testLuminanceInt() {
      System.out.println(ColorStub.luminance(0xff112233));
    }

    @NeverInline
    public static void testLuminanceLong() {
      System.out.println(ColorStub.luminance(0x00000000ff112200L));
    }

    @NeverInline
    public static void testPackInt() {
      System.out.println(ColorStub.pack(0xff112233));
    }

    @NeverInline
    public static void testPackFloat4() {
      System.out.println(ColorStub.pack(0.5f, 0.25f, 0.125f, 1.0f));
    }

    @NeverInline
    public static void testPackFloat3() {
      System.out.println(ColorStub.pack(0.5f, 0.25f, 0.125f));
    }

    @NeverInline
    public static void testParseColor() {
      System.out.println(ColorStub.parseColor("#ff112233"));
    }

    @NeverInline
    public static void testRedInt() {
      System.out.println(ColorStub.red(0xff112233));
    }

    @NeverInline
    public static void testRedLong() {
      System.out.println(ColorStub.red(0x00000000ff112200L));
    }

    @NeverInline
    public static void testRgbInt() {
      System.out.println(ColorStub.rgb(16, 32, 48));
    }

    @NeverInline
    public static void testRgbFloat() {
      System.out.println(ColorStub.rgb(0.5f, 0.25f, 0.125f));
    }

    @NeverInline
    public static void testToArgb() {
      System.out.println(ColorStub.toArgb(0x00000000ff112200L));
    }
  }
}

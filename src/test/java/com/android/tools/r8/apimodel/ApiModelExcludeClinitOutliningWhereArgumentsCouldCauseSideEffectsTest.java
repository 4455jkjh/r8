// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.apimodel;

import static com.android.tools.r8.synthesis.SyntheticItemsTestUtils.getMinimalSyntheticItemsTestUtils;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelExcludeClinitOutliningWhereArgumentsCouldCauseSideEffectsTest
    extends TestBase {

  private static final String SURFACE_TEXTURE_TYPE_NAME = "android.graphics.SurfaceTexture";
  private static final String SURFACE_TEXTURE_DESCRIPTOR =
      DescriptorUtils.javaTypeToDescriptor(SURFACE_TEXTURE_TYPE_NAME);

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Run tests with API level 21 to get <clinit> outlining (disabled below API 21).
    return getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.L).build();
  }

  // The expected output should be:
  //   StringUtils.lines("SurfaceTexture <clinit>", "getArgument", "DONE");
  // See b/441137561 for details on why the <clinit> is not called until after getArgument.
  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("getArgument", "SurfaceTexture <clinit>", "DONE");

  @Test
  public void testD8() throws Exception {
    testForD8(parameters)
        .addProgramClassFileData(getTestClass())
        .compile()
        .inspect(
            inspector ->
                assertEquals(
                    1, // Only one for android.graphics.SurfaceTexture.
                    inspector
                        .clazz(TestClass.class)
                        .uniqueMethodWithOriginalName("constructorArgumentCouldCauseSideEffects")
                        .streamInstructions()
                        .filter(InstructionSubject::isInvokeStatic)
                        .map(InstructionSubject::getMethod)
                        .filter(
                            dexMethod ->
                                SyntheticItemsTestUtils.isExternalApiOutlineClass(
                                    dexMethod.getHolderType().asClassReference()))
                        .count()))
        .addRunClasspathClassFileData(getSurfaceTexture())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getTestClass())
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> options.desugarSpecificOptions().minimizeSyntheticNames = true)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              MethodSubject constructorArgumentCouldCauseSideEffects =
                  inspector
                      .clazz(TestClass.class)
                      .uniqueMethodWithOriginalName("constructorArgumentCouldCauseSideEffects");
              assertEquals(
                  1,
                  constructorArgumentCouldCauseSideEffects
                      .streamInstructions()
                      .filter(InstructionSubject::isInvokeStatic)
                      .map(InstructionSubject::getMethod)
                      .filter(
                          dexMethod ->
                              dexMethod
                                  .getHolderType()
                                  .getTypeName()
                                  .equals(
                                      inspector.getObfuscatedTypeName(
                                          getMinimalSyntheticItemsTestUtils()
                                              .syntheticApiOutlineClass(TestClass.class, 0)
                                              .getTypeName())))
                      .count());
              // As android.graphics.SurfaceTexture was introduced in API level 11 the <clinit>
              // outline is inlined.
              assertEquals(
                  0,
                  constructorArgumentCouldCauseSideEffects
                      .streamInstructions()
                      .filter(InstructionSubject::isNewInstance)
                      .filter(
                          i ->
                              i.asDexInstruction()
                                  .getInstruction()
                                  .asNewInstance()
                                  .getType()
                                  .getTypeName()
                                  .equals("android.graphics.SurfaceTexture"))
                      .count());
            })
        .addRunClasspathClassFileData(getSurfaceTexture())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private static byte[] getTestClass() throws IOException {
    return transformer(TestClass.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(SurfaceTextureStub.class), SURFACE_TEXTURE_DESCRIPTOR)
        .transform();
  }

  private static byte[] getSurfaceTexture() throws IOException {
    return transformer(SurfaceTextureStub.class)
        .setClassDescriptor(SURFACE_TEXTURE_DESCRIPTOR)
        .transform();
  }

  // Transformed to android.graphics.SurfaceTexture
  public static class SurfaceTextureStub {
    static {
      System.out.println("SurfaceTexture <clinit>");
    }

    public SurfaceTextureStub(boolean arg) {}
  }

  static class TestClass {
    @NeverInline
    private static boolean getArgument() {
      System.out.println("getArgument");
      return System.currentTimeMillis() > 0;
    }

    @NeverInline
    private static void constructorArgumentCouldCauseSideEffects() {
      new SurfaceTextureStub(getArgument());
    }

    public static void main(String[] args) {
      constructorArgumentCouldCauseSideEffects();
      System.out.println("DONE");
    }
  }
}

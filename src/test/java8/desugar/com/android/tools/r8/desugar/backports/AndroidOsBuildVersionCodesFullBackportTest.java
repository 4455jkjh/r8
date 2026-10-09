// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.AndroidApiLevel.BAKLAVA;

import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidSdkIntFullEncoding;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Field;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AndroidOsBuildVersionCodesFullBackportTest extends AbstractBackportTest {

  private static final String ANDROID_OS_BUILD_VERSION_CODES_FULL_TYPE_NAME =
      "android.os.Build$VERSION_CODES_FULL";
  private static final String ANDROID_OS_BUILD_VERSION_CODES_FULL_DESCRIPTOR =
      DescriptorUtils.javaTypeToDescriptor(ANDROID_OS_BUILD_VERSION_CODES_FULL_TYPE_NAME);

  @Parameters(name = "{0}")
  public static Iterable<?> data() {
    return getTestParameters()
        .withDexRuntimesStartingFromExcluding(Version.V4_0_4)
        .withAllApiLevels()
        .withApiLevel(AndroidApiLevel.BAKLAVA_1)
        .build();
  }

  public AndroidOsBuildVersionCodesFullBackportTest(TestParameters parameters) throws IOException {
    super(
        parameters,
        ANDROID_OS_BUILD_VERSION_CODES_FULL_TYPE_NAME,
        ImmutableList.of(getTestRunner()));

    // android.os.Build$VERSION.SDK_INT_FULL is on API 36.
    assert AndroidApiLevel.CINNAMON_BUN_1 == AndroidApiLevel.LATEST;
    registerFieldTarget(BAKLAVA, 36);
    registerFieldTarget(AndroidApiLevel.BAKLAVA_1, 37);
    registerFieldTarget(AndroidApiLevel.CINNAMON_BUN, 38);
    registerFieldTarget(AndroidApiLevel.CINNAMON_BUN_1, 39);
  }

  @Override
  protected void configureProgram(TestBuilder<?, ?> builder) throws Exception {
    super.configureProgram(builder);
    // Use LATEST library to get API level for getMajorVersion() and getMinorVersion().
    builder.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.LATEST));
  }

  @Override
  // Add android.os.Build$VERSION_CODES_FULL class to runtime classpath.
  protected void configure(TestCompileResult<?, ?> result) throws Exception {
    // Only add when this is not backported away.
    if (parameters.frameworkHasBuildVersionCodesFull()) {
      result.addRunClasspathFiles(
          testForD8()
              .addProgramClassFileData(
                  getTransformedBuildVERSION_CODES_FULLClassForRuntimeClasspath())
              .setMinApi(parameters.getApiLevel())
              .compile()
              .writeToZip());
    }
  }

  @Override
  protected String[] configureD8RunArguments() {
    return new String[] {
      Integer.toString(AndroidSdkIntFullEncoding.encode(parameters.getApiLevel()))
    };
  }

  private static byte[] getTransformedBuildVERSION_CODES_FULLClassForRuntimeClasspath()
      throws IOException {
    ClassFileTransformer transformer =
        transformer(VERSION_CODES_FULL.class)
            .setClassDescriptor(ANDROID_OS_BUILD_VERSION_CODES_FULL_DESCRIPTOR);
    for (Field field : VERSION_CODES_FULL.class.getDeclaredFields()) {
      transformer.setAccessFlags(field, AccessFlags::setFinal);
    }
    return transformer.transform();
  }

  private static byte[] getTestRunner() throws IOException {
    return transformer(TestRunner.class)
        .replaceClassDescriptorInMethodInstructions(
            descriptor(VERSION_CODES_FULL.class), ANDROID_OS_BUILD_VERSION_CODES_FULL_DESCRIPTOR)
        .transform();
  }

  @Test
  public void checkVersionCodesFullInAndroidJar() throws Exception {
    ClassSubject versionCodesFullClass =
        new CodeInspector(parameters.getDefaultAndroidJar())
            .clazz("android.os.Build$VERSION_CODES_FULL");
    Assert.assertEquals(
        parameters.frameworkHasBuildVersionCodesFull(), versionCodesFullClass.isPresent());
    if (parameters.frameworkHasBuildVersionCodesFull()) {
      // Update test when more version codes full are added.
      assert AndroidApiLevel.CINNAMON_BUN_1 == AndroidApiLevel.LATEST;
      int expectedCount;
      if (parameters.getApiLevel().isEqualTo(BAKLAVA)) {
        expectedCount = 36;
      } else if (parameters.getApiLevel().isEqualTo(AndroidApiLevel.BAKLAVA_1)) {
        expectedCount = 37;
      } else if (parameters.getApiLevel().isEqualTo(AndroidApiLevel.CINNAMON_BUN)) {
        expectedCount = 38;
      } else {
        assert parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.CINNAMON_BUN_1);
        expectedCount = 39;
      }
      Assert.assertEquals(expectedCount, versionCodesFullClass.allFields().size());
      // Copied from BackportedMethodRewriter.
      Object[][] versionCodesFull = {
        {"BASE", 100_000},
        {"BASE_1_1", 200_000},
        {"CUPCAKE", 300_000},
        {"DONUT", 400_000},
        {"ECLAIR", 500_000},
        {"ECLAIR_0_1", 600_000},
        {"ECLAIR_MR1", 700_000},
        {"FROYO", 800_000},
        {"GINGERBREAD", 900_000},
        {"GINGERBREAD_MR1", 1000_000},
        {"HONEYCOMB", 1100_000},
        {"HONEYCOMB_MR1", 1200_000},
        {"HONEYCOMB_MR2", 1300_000},
        {"ICE_CREAM_SANDWICH", 1400_000},
        {"ICE_CREAM_SANDWICH_MR1", 1500_000},
        {"JELLY_BEAN", 1600_000},
        {"JELLY_BEAN_MR1", 1700_000},
        {"JELLY_BEAN_MR2", 1800_000},
        {"KITKAT", 1900_000},
        {"KITKAT_WATCH", 2000_000},
        {"LOLLIPOP", 2100_000},
        {"LOLLIPOP_MR1", 2200_000},
        {"M", 2300_000},
        {"N", 2400_000},
        {"N_MR1", 2500_000},
        {"O", 2600_000},
        {"O_MR1", 2700_000},
        {"P", 2800_000},
        {"Q", 2900_000},
        {"R", 3000_000},
        {"S", 3100_000},
        {"S_V2", 3200_000},
        {"TIRAMISU", 3300_000},
        {"UPSIDE_DOWN_CAKE", 3400_000},
        {"VANILLA_ICE_CREAM", 3500_000},
        {"BAKLAVA", 3600_000},
        {"BAKLAVA_1", 3600_001},
        {"CINNAMON_BUN", 3700_000},
        {"CINNAMON_BUN_1", 3700_001},
      };
      for (Object[] versionCodeFull : versionCodesFull) {
        if ((Integer) versionCodeFull[1]
            <= AndroidSdkIntFullEncoding.encode(parameters.getApiLevel())) {
          Assert.assertEquals(
              ((Integer) versionCodeFull[1]).intValue(),
              versionCodesFullClass
                  .field("int", (String) versionCodeFull[0])
                  .getField()
                  .getStaticValue()
                  .asDexValueInt()
                  .value);
        }
      }
    }
  }

  // android.os.Build$VERSION_CODES_FULL for building TestRunner and for runtime classpath.
  // Fields are not final to ensure that the static gets are not inlined by javac for the test code
  // and the field values are not relevant, as desugaring will replace them with the proper
  // constants.
  public static class /*android.os.Build$*/ VERSION_CODES_FULL {

    public static /* final */ int BASE = -1;
    public static /* final */ int BASE_1_1 = -2;
    public static /* final */ int CUPCAKE = -3;
    public static /* final */ int DONUT = -4;
    public static /* final */ int ECLAIR = -5;
    public static /* final */ int ECLAIR_0_1 = -6;
    public static /* final */ int ECLAIR_MR1 = -7;
    public static /* final */ int FROYO = -8;
    public static /* final */ int GINGERBREAD = -9;
    public static /* final */ int GINGERBREAD_MR1 = -10;
    public static /* final */ int HONEYCOMB = -11;
    public static /* final */ int HONEYCOMB_MR1 = -12;
    public static /* final */ int HONEYCOMB_MR2 = -13;
    public static /* final */ int ICE_CREAM_SANDWICH = -14;
    public static /* final */ int ICE_CREAM_SANDWICH_MR1 = -15;
    public static /* final */ int JELLY_BEAN = -16;
    public static /* final */ int JELLY_BEAN_MR1 = -17;
    public static /* final */ int JELLY_BEAN_MR2 = -18;
    public static /* final */ int KITKAT = -19;
    public static /* final */ int KITKAT_WATCH = -20;
    public static /* final */ int LOLLIPOP = -21;
    public static /* final */ int LOLLIPOP_MR1 = -22;
    public static /* final */ int M = -23;
    public static /* final */ int N = -24;
    public static /* final */ int N_MR1 = -25;
    public static /* final */ int O = -26;
    public static /* final */ int O_MR1 = -27;
    public static /* final */ int P = -28;
    public static /* final */ int Q = -29;
    public static /* final */ int R = -30;
    public static /* final */ int S = -31;
    public static /* final */ int S_V2 = -32;
    public static /* final */ int TIRAMISU = -33;
    public static /* final */ int UPSIDE_DOWN_CAKE = -34;
    public static /* final */ int VANILLA_ICE_CREAM = -35;
    public static /* final */ int BAKLAVA = -36;
    public static /* final */ int BAKLAVA_1 = -37;
    public static /* final */ int CINNAMON_BUN = -38;
    public static /* final */ int CINNAMON_BUN_1 = -39;
  }

  public static class TestRunner extends MiniAssert {

    public static void main(String[] args) throws Exception {
      int minSdkFull = Integer.parseInt(args[0]);
      int sdkFullWithVersionCodesFull = 3600_000;
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 100_000 : -1, VERSION_CODES_FULL.BASE);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 200_000 : -2, VERSION_CODES_FULL.BASE_1_1);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 300_000 : -3, VERSION_CODES_FULL.CUPCAKE);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 400_000 : -4, VERSION_CODES_FULL.DONUT);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 500_000 : -5, VERSION_CODES_FULL.ECLAIR);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 600_000 : -6, VERSION_CODES_FULL.ECLAIR_0_1);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 700_000 : -7, VERSION_CODES_FULL.ECLAIR_MR1);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 800_000 : -8, VERSION_CODES_FULL.FROYO);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 900_000 : -9, VERSION_CODES_FULL.GINGERBREAD);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 1000_000 : -10,
          VERSION_CODES_FULL.GINGERBREAD_MR1);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 1100_000 : -11, VERSION_CODES_FULL.HONEYCOMB);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 1200_000 : -12,
          VERSION_CODES_FULL.HONEYCOMB_MR1);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 1300_000 : -13,
          VERSION_CODES_FULL.HONEYCOMB_MR2);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 1400_000 : -14,
          VERSION_CODES_FULL.ICE_CREAM_SANDWICH);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 1500_000 : -15,
          VERSION_CODES_FULL.ICE_CREAM_SANDWICH_MR1);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 1600_000 : -16, VERSION_CODES_FULL.JELLY_BEAN);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 1700_000 : -17,
          VERSION_CODES_FULL.JELLY_BEAN_MR1);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 1800_000 : -18,
          VERSION_CODES_FULL.JELLY_BEAN_MR2);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 1900_000 : -19, VERSION_CODES_FULL.KITKAT);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 2000_000 : -20,
          VERSION_CODES_FULL.KITKAT_WATCH);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 2100_000 : -21, VERSION_CODES_FULL.LOLLIPOP);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 2200_000 : -22,
          VERSION_CODES_FULL.LOLLIPOP_MR1);
      assertEquals(minSdkFull < sdkFullWithVersionCodesFull ? 2300_000 : -23, VERSION_CODES_FULL.M);
      assertEquals(minSdkFull < sdkFullWithVersionCodesFull ? 2400_000 : -24, VERSION_CODES_FULL.N);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 2500_000 : -25, VERSION_CODES_FULL.N_MR1);
      assertEquals(minSdkFull < sdkFullWithVersionCodesFull ? 2600_000 : -26, VERSION_CODES_FULL.O);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 2700_000 : -27, VERSION_CODES_FULL.O_MR1);
      assertEquals(minSdkFull < sdkFullWithVersionCodesFull ? 2800_000 : -28, VERSION_CODES_FULL.P);
      assertEquals(minSdkFull < sdkFullWithVersionCodesFull ? 2900_000 : -29, VERSION_CODES_FULL.Q);
      assertEquals(minSdkFull < sdkFullWithVersionCodesFull ? 3000_000 : -30, VERSION_CODES_FULL.R);
      assertEquals(minSdkFull < sdkFullWithVersionCodesFull ? 3100_000 : -31, VERSION_CODES_FULL.S);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 3200_000 : -32, VERSION_CODES_FULL.S_V2);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 3300_000 : -33, VERSION_CODES_FULL.TIRAMISU);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 3400_000 : -34,
          VERSION_CODES_FULL.UPSIDE_DOWN_CAKE);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 3500_000 : -35,
          VERSION_CODES_FULL.VANILLA_ICE_CREAM);
      assertEquals(
          minSdkFull < sdkFullWithVersionCodesFull ? 3600_000 : -36, VERSION_CODES_FULL.BAKLAVA);
      assertEquals(minSdkFull < 3600_001 ? 3600_001 : -37, VERSION_CODES_FULL.BAKLAVA_1);
      assertEquals(minSdkFull < 3700_000 ? 3700_000 : -38, VERSION_CODES_FULL.CINNAMON_BUN);
      assertEquals(minSdkFull < 3700_001 ? 3700_001 : -39, VERSION_CODES_FULL.CINNAMON_BUN_1);
    }
  }
}

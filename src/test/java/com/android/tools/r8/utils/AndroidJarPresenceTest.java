// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AndroidJarPresenceTest extends TestBase {

  private static final Set<AndroidApiLevel> API_LEVELS_WITHOUT_ANDROID_JAR =
      ImmutableSet.of(
          AndroidApiLevel.B,
          AndroidApiLevel.B_1_1,
          AndroidApiLevel.C,
          AndroidApiLevel.D,
          AndroidApiLevel.E,
          AndroidApiLevel.E_0_1,
          AndroidApiLevel.E_MR1,
          AndroidApiLevel.F,
          AndroidApiLevel.G,
          AndroidApiLevel.G_MR1,
          AndroidApiLevel.H,
          AndroidApiLevel.H_MR1,
          AndroidApiLevel.H_MR2,
          AndroidApiLevel.J,
          AndroidApiLevel.J_MR1,
          AndroidApiLevel.J_MR2,
          AndroidApiLevel.K_WATCH,
          AndroidApiLevel.EXTENSION);

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public AndroidJarPresenceTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() {
    for (AndroidApiLevel apiLevel : AndroidApiLevel.getAndroidApiLevelsSorted()) {
      boolean expectedInTestDeps = !API_LEVELS_WITHOUT_ANDROID_JAR.contains(apiLevel);
      assertEquals(apiLevel.toString(), expectedInTestDeps, TestDeps.canHaveAndroidJar(apiLevel));

      boolean expectedInToolHelper = expectedInTestDeps || apiLevel == AndroidApiLevel.getDefault();
      assertEquals(apiLevel.toString(), expectedInToolHelper, ToolHelper.hasAndroidJar(apiLevel));
    }
  }
}

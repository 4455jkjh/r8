// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.fields;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassInitializerDefaultsDexItemBasedConstStringTest extends TestBase {

  private static final List<String> EXPECTED = ImmutableList.of("DISABLED", "true");
  private static final List<String> UNEXPECTED = ImmutableList.of("a", "true");

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
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(UNEXPECTED);
  }

  public static class Other {
    public static String NAME = Other.class.getName();
  }

  public static class Config extends Other {
    public static String TAG = Other.NAME;
    public static String OTHER_TAG = "INITIAL";

    static {
      TAG = "DISABLED";
      OTHER_TAG = Other.NAME;
    }

    @NeverInline
    public static String getTag() {
      return TAG;
    }

    @NeverInline
    public static String getOtherTag() {
      return OTHER_TAG;
    }
  }

  public static class Main {
    public static void main(String[] args) {
      System.out.println(Config.getTag());
      System.out.println(Config.getOtherTag().equals(Other.class.getName()));
    }
  }
}

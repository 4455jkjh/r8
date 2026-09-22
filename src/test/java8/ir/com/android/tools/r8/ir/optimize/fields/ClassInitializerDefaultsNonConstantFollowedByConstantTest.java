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
public class ClassInitializerDefaultsNonConstantFollowedByConstantTest extends TestBase {

  private static final List<String> EXPECTED = ImmutableList.of("DISABLED", "1");

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
        .assertSuccessWithOutputLines(EXPECTED);
  }

  public static class Config {
    public static int[] DEBUG_TAGS = new int[5];
    public static int[] OTHER_TAGS = null;

    static {
      DEBUG_TAGS = null;
      OTHER_TAGS = new int[1];
    }

    @NeverInline
    public static int[] getDebugTags() {
      return DEBUG_TAGS;
    }

    @NeverInline
    public static int[] getOtherTags() {
      return OTHER_TAGS;
    }
  }

  public static class Main {
    public static void main(String[] args) {
      int[] tags = Config.getDebugTags();
      if (tags == null) {
        System.out.println("DISABLED");
      } else {
        System.out.println("ENABLED:" + tags.length);
      }
      int[] other = Config.getOtherTags();
      if (other == null) {
        System.out.println("NULL");
      } else {
        System.out.println(other.length);
      }
    }
  }
}

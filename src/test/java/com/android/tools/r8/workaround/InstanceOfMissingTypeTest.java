// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.workaround;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
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
public class InstanceOfMissingTypeTest extends TestBase {

  private static final List<String> EXPECTED = ImmutableList.of("false", "true");
  private static final List<String> UNEXPECTED = ImmutableList.of("false", "false");

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
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(Main.class, Base.class, Sub.class)
        .compile()
        .addRunClasspathClasses(MissingInterface.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Main.class, Base.class, Sub.class)
        .addKeepMainRule(Main.class)
        .addDontWarn(MissingInterface.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .addRunClasspathClasses(MissingInterface.class)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/557273808): Sub should evaluate to "true" on DEX instead of "false".
        .applyIf(
            parameters.isDexRuntime(),
            r -> r.assertSuccessWithOutputLines(UNEXPECTED),
            r -> r.assertSuccessWithOutputLines(EXPECTED));
  }

  public interface MissingInterface {}

  @NoVerticalClassMerging
  public static class Base {}

  public static class Sub extends Base implements MissingInterface {}

  public static class Main {

    @NeverInline
    public static boolean check(Base b) {
      return b instanceof MissingInterface;
    }

    public static void main(String[] args) {
      System.out.println(check(new Base()));
      System.out.println(check(new Sub()));
    }
  }
}

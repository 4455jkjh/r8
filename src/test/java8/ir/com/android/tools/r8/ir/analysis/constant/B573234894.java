// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.constant;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B573234894 extends TestBase {

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
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  private static Collection<byte[]> getProgramClassFileData() {
    return ImmutableList.of(
        transformer(Composable.class)
            .removeInnerClasses()
            .setClassDescriptor("Landroidx/compose/runtime/Composable;")
            .transform(),
        transformer(Main.class)
            .removeInnerClasses()
            .replaceClassDescriptorInAnnotations(
                descriptor(Composable.class), "Landroidx/compose/runtime/Composable;")
            .transform());
  }

  public static class Main {

    public static void main(String[] args) {
      composableCaller(true, 0);
    }

    @Composable
    @NeverInline
    public static void composableCaller(boolean checked, int changed) {
      int dirty = changed;
      if ((changed & 6) == 0) {
        dirty |= changed(checked) ? 4 : 2;
      }
      composableCallee(null, 48 | (dirty & 14));
      if (System.currentTimeMillis() < 0) {
        composableCaller(checked, changed | 1);
      }
    }

    @Composable
    @NeverInline
    public static void composableCallee(String greeting, int changed) {
      if ((changed & 1) == 0) {
        greeting = "Hello, world!";
      }
      System.out.println(greeting);
      if (System.currentTimeMillis() < 0) {
        composableCallee(greeting, changed | 1);
      }
    }

    @NeverInline
    public static boolean changed(boolean value) {
      return System.currentTimeMillis() > 0 ? value : !value;
    }
  }

  @Retention(RetentionPolicy.CLASS)
  public @interface Composable {}
}

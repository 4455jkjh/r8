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
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JavaUtilArraysMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters).addProgramClasses(Main.class).release().compile().inspect(this::inspect);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testHashCodeNullIntArray"),
        not(invokesMethodWithHolder(Arrays.class)));
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testEqualsBothNullByteArray"),
        not(invokesMethodWithHolder(Arrays.class)));
    assertThat(
        mainClass.uniqueMethodWithOriginalName("testEqualsNullAndNonNullByteArray"),
        not(invokesMethodWithHolder(Arrays.class)));
  }

  static class Main {
    public static void main(String[] args) {
      testHashCodeNullIntArray();
      testEqualsBothNullByteArray();
      testEqualsNullAndNonNullByteArray();
    }

    @NeverInline
    public static void testHashCodeNullIntArray() {
      System.out.println(Arrays.hashCode((int[]) null));
    }

    @NeverInline
    public static void testEqualsBothNullByteArray() {
      System.out.println(Arrays.equals((byte[]) null, (byte[]) null));
    }

    @NeverInline
    public static void testEqualsNullAndNonNullByteArray() {
      System.out.println(Arrays.equals((byte[]) null, new byte[] {1, 2}));
    }
  }
}

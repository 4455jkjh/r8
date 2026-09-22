// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.initializedclasses;

import com.android.tools.r8.NeverClassInline;
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
public class InitializedClassesInInheritedInstanceMethodsTest extends TestBase {

  private static List<String> EXPECTED = ImmutableList.of("K.<clinit>", "check");
  private static List<String> UNEXPECTED = ImmutableList.of("check");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testForJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testForR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/557272380): Fails because K.<clinit> is skipped (only outputs "check").
        .assertSuccessWithOutputLines(UNEXPECTED);
  }

  @NeverClassInline
  public static class K {

    static {
      System.out.println("K.<clinit>");
    }

    static D create() {
      return new D();
    }

    static void touch() {
      System.out.print("");
    }
  }

  @NeverClassInline
  public static class D {

    @NeverInline
    public void check() {
      K.touch();
      System.out.println("check");
    }
  }

  @NeverClassInline
  public static class E extends D {}

  public static class Main {

    public static void main(String[] args) {
      D d = System.currentTimeMillis() > 0 ? new E() : K.create();
      d.check();
    }
  }
}

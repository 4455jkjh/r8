// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CrossTypeEnumArrayCopyTest extends TestBase {

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
        .assertSuccessWithOutputLines("false", "true");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addEnumUnboxingInspector(
            inspector -> {
              inspector.assertNotUnboxed(Main.Role.class, Main.Level.class);
              inspector.assertUnboxed(Main.ValidEnum.class);
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("false", "true");
  }

  public static class Main {

    enum Role {
      USER,
      ADMIN
    }

    enum Level {
      INFO,
      ERROR
    }

    enum ValidEnum {
      A,
      B
    }

    static Role[] roles = {Role.USER, Role.USER};
    static Level[] levels = {Level.INFO, Level.ERROR};
    static ValidEnum[] validSrc = {ValidEnum.A, ValidEnum.B};
    static ValidEnum[] validDest = {ValidEnum.A, ValidEnum.A};

    public static void main(String[] a) {
      try {
        System.arraycopy(levels, 0, roles, 0, 2);
      } catch (ArrayStoreException e) {
        // expected
      }
      System.out.println(roles[1] == Role.ADMIN);

      System.arraycopy(validSrc, 0, validDest, 0, 2);
      System.out.println(validDest[1] == ValidEnum.B);
    }
  }
}

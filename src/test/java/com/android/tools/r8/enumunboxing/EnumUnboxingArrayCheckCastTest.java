// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumUnboxingArrayCheckCastTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ClassCastException");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .addEnumUnboxingInspector(inspector -> inspector.assertNotUnboxed(Role.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("ClassCastException");
  }

  @NeverClassInline
  public enum Role {
    USER,
    ADMIN
  }

  public static class Main {
    @NeverInline
    public static void checkRoles(Object obj) {
      try {
        Role[] roles = (Role[]) obj;
        for (Role r : roles) {
          if (r == Role.ADMIN) {
            System.out.println("ADMIN GRANTED");
          }
        }
      } catch (ClassCastException e) {
        System.out.println("ClassCastException");
      }
    }

    public static void main(String[] args) {
      // Keep enum values live so Role is eligible for unboxing.
      if (System.currentTimeMillis() < 0) {
        System.out.println(Role.USER.name());
        System.out.println(Role.ADMIN.name());
      }
      // In unboxed representation, Role.ADMIN is ordinal 1 + 1 = 2.
      checkRoles(new int[] {2});
    }
  }
}

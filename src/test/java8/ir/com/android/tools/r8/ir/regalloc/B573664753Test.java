// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class B573664753Test extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withoutCollapsedDexRuntimes().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1, 4, 10, 20, 2");
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1, 4, 10, 20, 2");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("1, 4, 10, 20, 2");
  }

  static class Main {

    static int sInt = 1;
    static long sLong = 0;

    static int getInt() {
      return sInt;
    }

    static long getLong() {
      return ++sLong;
    }

    static void use(long a, long b) {
      if (a == -1 && b == -1) {
        System.out.println("unreachable");
      }
    }

    static void useAfter(long a, long b, boolean cond) {
      if (a == -1 && b == -1 && !cond) {
        System.out.println("unreachable");
      }
    }

    static void sink(int p0, long p1, long p2, long p3, long p4) {
      System.out.println(p0 + ", " + p1 + ", " + p2 + ", " + p3 + ", " + p4);
    }

    static void test(long a, long b, boolean cond) {
      long d0 = getLong();
      long v4 = getLong();
      long d1 = getLong();
      long v1 = getLong();
      int v0 = getInt();
      use(d0, d1);
      int p0;
      long p1, p2, p3, p4;
      if (cond) {
        p0 = v0;
        p1 = v1;
        p2 = a;
        p3 = b;
        p4 = v4;
      } else {
        p0 = getInt();
        p1 = getLong();
        p2 = getLong();
        p3 = getLong();
        p4 = getLong();
      }
      sink(p0, p1, p2, p3, p4);
      useAfter(a, b, cond);
    }

    public static void main(String[] args) {
      test(10L, 20L, args.length == 0);
    }
  }
}

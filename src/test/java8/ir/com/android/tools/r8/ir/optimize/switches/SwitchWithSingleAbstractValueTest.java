// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.switches;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

// Regression test for b/573926678.
@RunWith(Parameterized.class)
public class SwitchWithSingleAbstractValueTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SwitchWithSingleAbstractValueTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("first", "last");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("first", "last");
  }

  static class Main {

    public static void main(String[] args) {
      testFirstCaseLive(args.length);
      testLastCaseLive(args.length);
    }

    static void testFirstCaseLive(int i) {
      int zero = 0;
      // The switch is not visited by constant propagation, since the branch is dead. The abstract
      // value of the switch value is 0, but it is not a constant number.
      if (zero != 0) {
        switch (i & 0) {
          case 0:
            System.out.println("zero");
            break;
          case 1:
            System.out.println("one");
            break;
          default:
            System.out.println("default");
            break;
        }
      }
      System.out.println("first");
    }

    static void testLastCaseLive(int i) {
      int zero = 0;
      // The switch is not visited by constant propagation, since the branch is dead. The abstract
      // value of the switch value is 0, but it is not a constant number.
      if (zero != 0) {
        switch (i & 0) {
          case -1:
            System.out.println("minus one");
            break;
          case 0:
            System.out.println("zero");
            break;
          default:
            System.out.println("default");
            break;
        }
      }
      System.out.println("last");
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir;

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
public class SplitIntSwitchDeadPhiTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A1", "B2", "D");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(select("a", "A1", "B1"));
      System.out.println(select("b", "A2", "B2"));
      System.out.println(select("g", "A3", "B3"));
    }

    @NeverInline
    static String select(String s, String a, String b) {
      String deadPhi = s.equals("a") ? a : null;
      int x;
      switch (s) {
        case "a":
          x = 1;
          break;
        case "b":
          x = 2;
          break;
        default:
          x = 4;
      }
      String res;
      switch (x) {
        case 1:
          res = a;
          break;
        case 2:
          res = b;
          break;
        case 3:
          res = deadPhi;
          break;
        default:
          return "D";
      }
      return res.trim();
    }
  }
}

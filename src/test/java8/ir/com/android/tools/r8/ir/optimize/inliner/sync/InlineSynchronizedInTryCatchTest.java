// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner.sync;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/572438295. */
@RunWith(Parameterized.class)
public class InlineSynchronizedInTryCatchTest extends TestBase {

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
        .assertSuccessWithOutputLines("Hello World");
  }

  static class Main {

    private final Foobar foobar = new Foobar();

    public static void main(String[] args) {
      new Main().onCreate();
    }

    @NeverInline
    void onCreate() {
      try {
        foobar.doSomething("a");
      } catch (Exception e) {
        System.out.println("Some error: " + e);
      }
      System.out.println("Hello World");
    }
  }

  static class Foobar {

    private final Map<String, Integer> map = new HashMap<>();

    synchronized void doSomething(String key) {
      Integer value = map.get(key);
      if (value == null) {
        map.remove(key);
      } else {
        map.put(key, value + 1);
      }
    }
  }
}

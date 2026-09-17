// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StaticGetSharingRetraceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testHoist() throws Exception {
    StackTrace expectedStackTrace =
        testForRuntime(parameters)
            .addInnerClasses(getClass())
            .run(parameters.getRuntime(), MainHoist.class)
            .getStackTrace();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(MainHoist.class)
        .addKeepAttributeLineNumberTable()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), MainHoist.class)
        .inspectStackTrace(
            (stackTrace, inspector) -> assertThat(stackTrace, isSame(expectedStackTrace)));
  }

  @Test
  public void testSink() throws Exception {
    StackTrace expectedStackTrace =
        testForRuntime(parameters)
            .addInnerClasses(getClass())
            .run(parameters.getRuntime(), MainSink.class)
            .getStackTrace();
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(MainSink.class)
        .addKeepAttributeLineNumberTable()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), MainSink.class)
        .inspectStackTrace(
            (stackTrace, inspector) -> assertThat(stackTrace, isSame(expectedStackTrace)));
  }

  static class ThrowingHoist {
    static {
      if (System.currentTimeMillis() > 0) {
        throw new RuntimeException("clinit error");
      }
    }

    public static int field = 42;
  }

  static class MainHoist {
    @NeverInline
    public static int testHoist(boolean condition) {
      if (condition) {
        return ThrowingHoist.field + 1;
      } else {
        return ThrowingHoist.field + 2;
      }
    }

    public static void main(String[] args) {
      System.out.println(testHoist(args.length == 0));
    }
  }

  static class ThrowingSink {
    static {
      if (System.currentTimeMillis() > 0) {
        throw new RuntimeException("clinit error");
      }
    }

    public static int field = 42;
  }

  static class MainSink {
    @NeverInline
    static void sideEffect(int x) {
      if (System.currentTimeMillis() < 0) {
        System.out.println(x);
      }
    }

    @NeverInline
    public static int testSink(boolean condition) {
      int x;
      if (condition) {
        sideEffect(1);
        x = ThrowingSink.field;
      } else {
        sideEffect(2);
        x = ThrowingSink.field;
      }
      return x + 1;
    }

    public static void main(String[] args) {
      System.out.println(testSink(args.length > 0));
    }
  }
}

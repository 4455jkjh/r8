// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.naming.retrace.StackTrace;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SyntheticInitClassPositionTest extends TestBase {

  private final TestParameters parameters;
  private StackTrace expectedStackTrace;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestBase.getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Before
  public void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForJvm()
            .addTestClasspath()
            .run(CfRuntime.getSystemRuntime(), Main.class)
            .assertFailure()
            .map(StackTrace::extractFromJvm);
  }

  public SyntheticInitClassPositionTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(ExceptionInInitializerError.class)
        .inspectStackTrace(stackTrace -> assertThat(stackTrace, isSame(expectedStackTrace)));
  }

  static class Main {

    public static void main(String[] args) {
      A.m();
    }
  }

  static class A {

    static {
      if (true) {
        throw new RuntimeException();
      }
    }

    static void m() {
      System.out.println("Hello world!");
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis.globals;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.GlobalSyntheticsTestingConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GlobalSyntheticsConsumerFinishedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}, backend: {1}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Test
  public void testWithGlobals() throws Exception {
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    testForD8(parameters)
        .asD8TestBuilder()
        .addProgramClasses(TestClassWithGlobals.class)
        .setIntermediate(true)
        .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals))
        .compile();
    assertTrue(globals.hasGlobals());
    assertTrue(globals.isFinished());
  }

  @Test
  public void testWithoutGlobals() throws Exception {
    GlobalSyntheticsTestingConsumer globals = new GlobalSyntheticsTestingConsumer();
    testForD8(parameters)
        .asD8TestBuilder()
        .addProgramClasses(TestClassWithoutGlobals.class)
        .setIntermediate(true)
        .apply(b -> b.getBuilder().setGlobalSyntheticsConsumer(globals))
        .compile();
    assertFalse(globals.hasGlobals());
    assertTrue(globals.isFinished());
  }

  static class TestClassWithGlobals {

    public static void main(String[] args) {
      Runnable runnable = () -> System.out.println("Hello, world!");
      runnable.run();
    }
  }

  static class TestClassWithoutGlobals {

    public static void main(String[] args) {
      System.out.println("Hello, world!");
    }
  }
}

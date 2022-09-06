// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LIRRoundtripTest extends TestBase {

  static class TestClass {
    public static void main(String[] args) {
      System.out.println(args.length == 0 ? "Hello, world!" : "Oh no!");
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().build();
  }

  private final TestParameters parameters;

  public LIRRoundtripTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testRoundtrip() throws Exception {
    testForD8(parameters.getBackend())
        .release()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(TestClass.class)
        .addOptionsModification(
            o -> {
              o.testing.forceIRForCfToCfDesugar = true;
              o.testing.roundtripThroughLIR = true;
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testRoundtripDebug() throws Exception {
    testForD8(parameters.getBackend())
        .debug()
        .setMinApi(AndroidApiLevel.B)
        .addProgramClasses(TestClass.class)
        .addOptionsModification(
            o -> {
              o.testing.forceIRForCfToCfDesugar = true;
              o.testing.roundtripThroughLIR = true;
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }
}

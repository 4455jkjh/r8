// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.finalizer;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Tests stepping through a loop with two back edges from different lines in debug mode, where the
 * back edges are redirected to a loop latch.
 */
@RunWith(Parameterized.class)
public class LoopBackEdgeCanonicalizerDebugTest extends DebugTestBase {

  private static final Class<?> CLASS = LoopBackEdgeCanonicalizerDebugTestClass.class;
  private static final String METHOD = "sumPositives";

  // Line numbers in LoopBackEdgeCanonicalizerDebugTestClass.
  private static final int LOOP_HEADER_LINE = 13;
  private static final int CONTINUE_LINE = 18;
  private static final int SUM_LINE = 20;
  private static final int END_OF_BODY_LINE = 21;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultCfRuntime()
        .withDefaultDexRuntime()
        .withMinimumApiLevel()
        .build();
  }

  private DebugTestConfig getDebugConfig() throws Exception {
    if (parameters.isCfRuntime()) {
      return testForJvm(parameters).addProgramClasses(CLASS).debugConfig(parameters.getRuntime());
    }
    return testForD8(parameters)
        .addProgramClasses(CLASS)
        .debug()
        .debugConfig(parameters.getRuntime());
  }

  @Test
  public void test() throws Throwable {
    runDebugTest(
        getDebugConfig(),
        CLASS,
        // The first iteration takes the continue back edge.
        breakpoint(CLASS.getTypeName(), METHOD, CONTINUE_LINE),
        run(),
        checkLine(CONTINUE_LINE),
        stepOver(),
        checkLine(LOOP_HEADER_LINE),
        checkLocal("sum"),
        checkLocal("i"),
        checkNoLocal("v"),
        // The second iteration takes the back edge at the end of the loop body.
        breakpoint(CLASS.getTypeName(), METHOD, SUM_LINE),
        run(),
        checkLine(SUM_LINE),
        stepOver(),
        checkLine(END_OF_BODY_LINE),
        stepOver(),
        checkLine(LOOP_HEADER_LINE),
        checkLocal("sum"),
        checkLocal("i"),
        checkNoLocal("v"),
        run());
  }
}

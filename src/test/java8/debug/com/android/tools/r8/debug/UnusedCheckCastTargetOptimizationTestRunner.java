// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedCheckCastTargetOptimizationTestRunner extends DebugTestBase {

  private static final Class MAIN_CLASS = UnusedCheckCastTargetOptimizationTest.class;
  private static final Class SUPER_CLASS = UnusedCheckCastTargetOptimizationTest.Super.class;
  private static final Class SUBCLASS_CLASS = UnusedCheckCastTargetOptimizationTest.Subclass.class;
  private static final String FILE = MAIN_CLASS.getSimpleName() + ".java";
  private static final String NAME = MAIN_CLASS.getCanonicalName();

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  @Test
  public void test() throws Throwable {
    runDebugTest(
        new D8DebugTestConfig(parameters.asDexRuntime())
            .compileAndAddClasses(temp, MAIN_CLASS, SUPER_CLASS, SUBCLASS_CLASS),
        NAME,
        breakpoint(NAME, "main", 14),
        run(),
        checkLine(FILE, 14),
        checkLocal("b"),
        checkNoLocal("c"),
        stepOver(),
        checkLine(FILE, 15),
        checkLocal("b"),
        checkLocal("c"),
        run());
  }
}

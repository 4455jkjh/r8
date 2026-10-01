// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import java.util.Collection;
import java.util.function.Function;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BreakInTwoLinesFinallyTestRunner extends DebugTestBase {

  private static final Class CLASS = BreakInTwoLinesFinallyTest.class;
  private static final String FILE = CLASS.getSimpleName() + ".java";
  private static final String NAME = CLASS.getCanonicalName();

  private final DebugTestConfig config;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> setup() {
    DelayedDebugTestConfig cf =
            temp -> new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    Function<DexRuntime, DelayedDebugTestConfig> d8 =
        dexRuntime -> temp -> new D8DebugTestConfig(dexRuntime).compileAndAddClasses(temp, CLASS);
    return buildCfAndD8Parameters(cf, d8);
  }

  public BreakInTwoLinesFinallyTestRunner(
      TestParameters parameters, String name, DelayedDebugTestConfig config) {
    this.config = config.getConfig(getStaticTemp());
  }

  @Test
  public void testHitBreakpointOnNormalAndExceptionalFlow() throws Throwable {
    Assume.assumeTrue(
        config.getRuntime().isCf()
            || config.getRuntime().asDex().getVm().isNewerThan(DexVm.ART_6_0_1_HOST));
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "foo", 12),
        run(),
        checkLine(FILE, 12), // hit finally on normal flow
        run(),
        checkLine(FILE, 12), // hit finally on exceptional flow
        breakpoint(NAME, "main", 36),
        run(),
        checkLine(FILE, 36),
        run());
  }
}

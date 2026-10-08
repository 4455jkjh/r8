// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BreakAtTryAndCatchTestRunner extends DebugTestBase {

  private static final Class<?> CLASS = BreakAtTryAndCatchTest.class;
  private static final String FILE = CLASS.getSimpleName() + ".java";
  private static final String NAME = CLASS.getCanonicalName();

  private final TestParameters parameters;
  private final String name;
  private final DebugTestConfig config;

  @Parameters(name = "{0}, {1}")
  public static Collection<Object[]> setup() {
    DelayedDebugTestConfig cf =
        temp -> new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    List<Object[]> result = new ArrayList<>();
    for (TestParameters parameters :
        getTestParameters().withDefaultCfRuntime().withDexRuntimes().build()) {
      if (parameters.isCfRuntime()) {
        result.add(new Object[] {parameters, "CF", cf});
        continue;
      }
      DexRuntime dexRuntime = parameters.asDexRuntime();
      DelayedDebugTestConfig d8 =
          temp -> new D8DebugTestConfig(dexRuntime).compileAndAddClasses(temp, CLASS);
      DelayedDebugTestConfig d8Reordered =
          temp ->
              new D8DebugTestConfig(dexRuntime)
                  .compileAndAdd(
                      temp,
                      Collections.singletonList(ToolHelper.getClassFileForTestClass(CLASS)),
                      options -> options.testing.placeExceptionalBlocksLast = true);
      result.add(new Object[] {parameters, "D8", d8});
      result.add(new Object[] {parameters, "D8/reorder", d8Reordered});
    }
    return result;
  }

  public BreakAtTryAndCatchTestRunner(
      TestParameters parameters, String name, DelayedDebugTestConfig config) {
    this.parameters = parameters;
    this.name = name;
    this.config = config.getConfig(getStaticTemp());
  }

  @Test
  public void testHitOnEntryOnly() throws Throwable {
    assumeFalse("b/72933440", name.equals("D8/reorder"));
    assumeFalse("b/73803266", name.equals("D8") && parameters.getDexVm() == DexVm.ART_6_0_1_HOST);
    runDebugTest(
        config,
        NAME,
        breakpoint(NAME, "foo", 10),
        run(),
        checkLine(FILE, 10), // hit line entry, bar does not throw
        run(),
        checkLine(FILE, 10), // hit line entry, bar does throw
        breakpoint(NAME, "main", 31),
        run(),
        checkLine(FILE, 31), // No more hits on line, continue to main.
        run());
  }
}

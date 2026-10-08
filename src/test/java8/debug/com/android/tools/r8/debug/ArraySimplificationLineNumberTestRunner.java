// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import java.util.Collections;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArraySimplificationLineNumberTestRunner extends DebugTestBase {

  private static final Class CLASS = ArraySimplificationLineNumberTest.class;
  private static final String FILE = CLASS.getSimpleName() + ".java";
  private static final String NAME = CLASS.getCanonicalName();

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesStartingFromIncluding(Version.V6_0_1).build();
  }

  @Test
  public void testHitOnEntryOnly() throws Throwable {
    // TODO(b/199700280): Reenable on 12.0.0 when we have the libjdwp.so file include and the flags
    // fixed.
    Assume.assumeTrue(
        "Skipping test " + testName.getMethodName() + " because debugging not enabled in 12.0.0",
        !parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V12_0_0));
    DebugTestConfig cf = new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    DebugTestConfig d8 =
        new D8DebugTestConfig(parameters.asDexRuntime())
            .compileAndAdd(
                temp, Collections.singletonList(ToolHelper.getClassFileForTestClass(CLASS)));
    DebugTestConfig d8NoLocals =
        new D8DebugTestConfig(parameters.asDexRuntime())
            .compileAndAdd(
                temp,
                Collections.singletonList(ToolHelper.getClassFileForTestClass(CLASS)),
                options -> options.testing.noLocalsTableOnInput = true);

    new DebugStreamComparator()
        .add("CF", streamDebugTest(cf, NAME, NO_FILTER))
        .add("D8", streamDebugTest(d8, NAME, ANDROID_FILTER))
        .add("D8/nolocals", streamDebugTest(d8NoLocals, NAME, ANDROID_FILTER))
        .setFilter(s -> s.getSourceFile().equals(FILE))
        .setVerifyVariables(false)
        .compare();
  }
}

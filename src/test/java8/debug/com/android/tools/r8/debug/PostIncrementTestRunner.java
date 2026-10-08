// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.DebuggeeState;
import java.util.stream.Stream;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// See b/80385846
@RunWith(Parameterized.class)
public class PostIncrementTestRunner extends DebugTestBase {

  private static final Class CLASS = PostIncrementTest.class;
  private static final String NAME = CLASS.getCanonicalName();

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesStartingFromExcluding(Version.V5_1_1).build();
  }

  @Test
  public void test() throws Exception {
    // TODO(b/199700280): Reenable on 12.0.0 when we have the libjdwp.so file include and the flags
    // fixed.
    Assume.assumeTrue(
        "Skipping test " + testName.getMethodName() + " because debugging not enabled in 12.0.0",
        !parameters.isDexRuntimeVersionNewerThanOrEqual(Version.V12_0_0));
    DebugTestConfig cfConfig = new CfDebugTestConfig().addPaths(ToolHelper.getClassPathForTests());
    DebugTestConfig d8Config =
        new D8DebugTestConfig(parameters.asDexRuntime()).compileAndAddClasses(temp, CLASS);
    new DebugStreamComparator()
        .add("CF", createStream(cfConfig))
        .add("D8", createStream(d8Config))
        .compare();
  }

  private Stream<DebuggeeState> createStream(DebugTestConfig config) throws Exception {
    return streamDebugTest(config, NAME, ANDROID_FILTER);
  }
}

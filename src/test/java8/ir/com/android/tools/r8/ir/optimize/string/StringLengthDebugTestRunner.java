// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debug.D8DebugTestConfig;
import com.android.tools.r8.debug.DebugTestBase;
import com.android.tools.r8.debug.DebugTestBase.JUnit3Wrapper.FrameInspector;
import com.android.tools.r8.debug.DebugTestConfig;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StringLengthDebugTestRunner extends DebugTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesStartingFromIncluding(Version.V5_1_1).build();
  }

  @Test
  public void test() throws Throwable {
    Class<?> main = StringLengthDebugTest.class;
    DebugTestConfig config =
        new D8DebugTestConfig(parameters.asDexRuntime())
            .compileAndAdd(temp, ToolHelper.getClassFileForTestClass(main));
    runDebugTest(config, main.getCanonicalName(),
        breakpoint(main.getCanonicalName(), "main"),
        run(),
        stepOver(),
        // String x = "ABC";
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(0);
              frame.checkLocal("x");
              frame.checkNoLocal("l1");
              frame.checkNoLocal("l2");
            }
        ),
        // int l1 = x.length();
        stepInto(),
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(0);
              assertEquals(String.class.getCanonicalName(), frame.getClassName());
              assertEquals("length", frame.getMethodName());
            }
        ),
        stepOut(),
        stepOver(),
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(0);
              frame.checkLocal("x");
              frame.checkLocal("l1");
              frame.checkNoLocal("l2");
            }
        ),
        // System.out.println(l1);
        stepOver(),
        // int l2 = "XYZ".length();
        stepInto(),
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(0);
              assertEquals(String.class.getCanonicalName(), frame.getClassName());
              assertEquals("length", frame.getMethodName());
            }
        ),
        stepOut(),
        stepOver(),
        inspect(
            i -> {
              FrameInspector frame = i.getFrame(0);
              frame.checkLocal("x");
              frame.checkLocal("l1");
              frame.checkLocal("l2");
            }
        ),
        run()
    );
  }

}

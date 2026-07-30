// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EmbeddedPcToNativePcDebugInfoTest extends TestBase {

  private static StackTrace expectedStackTrace;

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean convertPcBasedDebugInfoToNative;

  @Parameters(name = "{0}, convert: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimesStartingFromIncluding(Version.V17_0_0).build(),
        BooleanUtils.values());
  }

  @BeforeClass
  public static void setup() throws Exception {
    // Get the expected stack trace by running on the JVM.
    expectedStackTrace =
        testForJvm(getStaticTemp())
            .addProgramClasses(Main.class)
            .run(TestRuntime.getDefaultCfRuntime(), Main.class, "")
            .assertFailure()
            .getStackTrace();
  }

  @Test
  public void test() throws Exception {
    // Compile to a min API level that does not support native PC debug info.
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addOptionsModification(
                options -> options.getTestingOptions().forcePcBasedEncoding = true)
            .setMinApi(AndroidApiLevel.N)
            .compile()
            .inspect(
                inspector -> {
                  MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
                  assertTrue(
                      mainMethod.getMethod().getCode().asDexCode().getDebugInfo().isPcBasedInfo());
                });

    compileResult
        .run(parameters.getRuntime(), Main.class, "")
        .inspectStackTrace(stackTrace -> assertThat(stackTrace, isSame(expectedStackTrace)));

    // Retarget to a min API level that does support native PC debug info.
    testForD8(parameters.getBackend())
        .addProgramFiles(compileResult.writeToZip())
        .addOptionsModification(
            options -> {
              assertFalse(options.convertPcBasedDebugInfoToNative);
              options.convertPcBasedDebugInfoToNative = convertPcBasedDebugInfoToNative;
            })
        .setMinApi(AndroidApiLevel.CINNAMON_BUN)
        .release()
        .compile()
        .inspect(
            inspector -> {
              // The debug info should not be converted to native debug info, since embedded PC
              // debug info uses DEX PC + 1 as line numbers.
              MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
              if (convertPcBasedDebugInfoToNative) {
                assertNull(mainMethod.getMethod().getCode().asDexCode().getDebugInfo());
              } else {
                assertTrue(
                    mainMethod.getMethod().getCode().asDexCode().getDebugInfo().isPcBasedInfo());
              }
            })
        .run(parameters.getRuntime(), Main.class, "")
        .inspectOriginalStackTrace(
            stackTrace ->
                assertThat(
                    stackTrace.retrace(compileResult.getProguardMap()),
                    notIf(isSame(expectedStackTrace), convertPcBasedDebugInfoToNative)));
  }

  static class Main {

    public static void main(String[] args) {
      Object nullObject = System.currentTimeMillis() > 0 ? null : new Object();
      int res = 1 / args.length;
      nullObject.notify();
      System.out.println(res);
    }
  }
}

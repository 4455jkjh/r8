// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.debug.classes.FinallyBlock;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Test single stepping behaviour of synchronized blocks. */
@RunWith(Parameterized.class)
public class FinallyBlockTest extends DebugTestBase {

  public static final String CLASS = typeName(FinallyBlock.class);
  public static final String FILE = "FinallyBlock.java";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  @Test
  public void testEmptyBlock() throws Throwable {
    Assume.assumeTrue(
        "Older runtimes incorrectly step out of function: b/67671565",
        parameters.getDexRuntimeVersion().isNewerThan(Version.V6_0_1));
    final String method = "finallyBlock";
    runDebugTest(
        testForD8(parameters.getBackend())
            .addProgramClasses(FinallyBlock.class)
            .setMinApi(parameters)
            .debugConfig(parameters.getRuntime()),
        CLASS,
        breakpoint(CLASS, method),
        run(),
        checkLine(FILE, 13),
        stepOver(),
        checkLine(FILE, 15),
        stepOver(),
        checkLine(FILE, 23),
        stepOver(),
        checkLine(FILE, 24),
        stepOver(),
        checkLine(FILE, 25),
        stepOver(),
        checkLine(FILE, 30), // return in callFinallyBlock
        run(),
        checkLine(FILE, 13),
        stepOver(),
        checkLine(FILE, 15),
        stepOver(),
        checkLine(FILE, 16),
        stepOver(),
        checkLine(FILE, 18), // catch AE
        stepOver(),
        checkLine(FILE, 19),
        stepOver(),
        checkLine(FILE, 23),
        stepOver(),
        checkLine(FILE, 24),
        stepOver(),
        checkLine(FILE, 25),
        stepOver(),
        checkLine(FILE, 30), // return in callFinallyBlock
        run(),
        checkLine(FILE, 13),
        stepOver(),
        checkLine(FILE, 15),
        stepOver(),
        checkLine(FILE, 16),
        stepOver(),
        checkLine(FILE, 20), // catch RE
        stepOver(),
        checkLine(FILE, 21),
        stepOver(),
        checkLine(FILE, 23),
        stepOver(),
        checkLine(FILE, 24),
        stepOver(),
        checkLine(FILE, 25),
        stepOver(),
        checkLine(FILE, 30), // return in callFinallyBlock
        run(),
        checkLine(FILE, 13),
        stepOver(),
        checkLine(FILE, 15),
        stepOver(),
        checkLine(FILE, 16), // throw without catch
        stepOver(),
        checkLine(FILE, 23), // finally
        // Don't single step here as some Java compilers generate line entry 19 and some don't.
        breakpoint(CLASS, "callFinallyBlock", 31),
        run(),
        checkLine(FILE, 31), // catch in callFinallyBlock
        run());
  }
}

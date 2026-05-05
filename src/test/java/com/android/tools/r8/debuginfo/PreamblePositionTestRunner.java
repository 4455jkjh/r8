// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.CompilationMode.RELEASE;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PreamblePositionTestRunner extends TestBase {

  private static final String TEST_CLASS = "PreamblePositionTestSource";
  private static final String TEST_PACKAGE = "com.android.tools.r8.debuginfo";

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean invertConditionals;

  @Parameters(name = "{0}, invertConditionals: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testBothBranches() throws Exception {
    Path testClassPath = temp.newFolder().toPath().resolve(TEST_CLASS + ".class");

    Files.write(testClassPath, PreamblePositionTestSourceDump.dump());

    TestCompileResult<?, ?> result =
        testForD8(parameters)
            .addProgramFiles(testClassPath)
            .setMode(RELEASE)
            .addOptionsModification(
                options -> options.testing.invertConditionals = invertConditionals)
            .compile();
    String fileName = TEST_CLASS + ".java";

    result
        .run(parameters.getRuntime(), TEST_PACKAGE + "." + TEST_CLASS)
        .assertFailureWithErrorThatMatches(containsString("<true-branch-exception>"))
        .assertFailure()
        // Must have either explicit line = 0 or no line info at all unless ART 17 or newer.
        .assertFailureWithErrorThatMatches(
            anyOf(
                containsString(fileName + ":0"),
                allOf(
                    containsString("at " + TEST_PACKAGE + "." + TEST_CLASS + ".main"),
                    (parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V17_0_0)
                        ? containsString(fileName + ":")
                        : (not(containsString(fileName + ":")))))));

    result
        .run(parameters.getRuntime(), TEST_PACKAGE + "." + TEST_CLASS, "1")
        .assertFailureWithErrorThatMatches(containsString("<false-branch-exception>"))
        .assertFailureWithErrorThatMatches(
            containsString(
                fileName + ":" + PreamblePositionTestSourceDump.FALSE_BRANCH_LINE_NUMBER));
  }
}

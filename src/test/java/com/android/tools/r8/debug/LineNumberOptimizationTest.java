// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debug;

import static com.android.tools.r8.naming.ClassNameMapper.MissingFileAction.MISSING_FILE_IS_EMPTY_MAP;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/** Tests source file and line numbers on inlined methods. */
@RunWith(Parameterized.class)
public class LineNumberOptimizationTest extends DebugTestBase {

  private static final int[] ORIGINAL_LINE_NUMBERS_DEBUG = {
    20, 7, 8, 28, 29, 9, 21, 12, 13, 22, 16, 17
  };

  private static final String CLASS1 = "LineNumberOptimization1";
  private static final String CLASS2 = "LineNumberOptimization2";
  private static final String FILE1 = CLASS1 + ".java";
  private static final String FILE2 = CLASS2 + ".java";
  private static final String MAIN_SIGNATURE = "([Ljava/lang/String;)V";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection setup() {
    return TestParametersBuilder.builder().withAllRuntimesAndApiLevels().build();
  }

  public LineNumberOptimizationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private DebugTestConfig makeConfig(
      LineNumberOptimization lineNumberOptimization,
      boolean writeProguardMap,
      boolean dontOptimizeByEnablingDebug)
      throws Exception {

    R8TestCompileResult result =
        testForR8(parameters.getBackend())
            .addProgramFiles(DEBUGGEE_JAR)
            .setMinApi(parameters.getApiLevel())
            .setMode(dontOptimizeByEnablingDebug ? CompilationMode.DEBUG : CompilationMode.RELEASE)
            .noTreeShaking()
            .noMinification()
            .addKeepAttributeSourceFile()
            .addKeepAttributeLineNumberTable()
            .addOptionsModification(
                options -> {
                  if (!dontOptimizeByEnablingDebug) {
                    options.lineNumberOptimization = lineNumberOptimization;
                  }
                  options.enableInlining = false;
                })
            .compile();

    DebugTestConfig config = result.debugConfig();
    if (writeProguardMap) {
      config.setProguardMap(result.writeProguardMap(), MISSING_FILE_IS_EMPTY_MAP);
    }
    return config;
  }

  private void assumeMappingIsNotToPCs() {
    assumeTrue(
        "Ignoring test when the line number table is removed.",
        parameters.isCfRuntime()
            || parameters.getApiLevel().isLessThan(apiLevelWithPcAsLineNumberSupport()));
  }

  @Test
  public void testNotOptimizedByEnablingDebug() throws Throwable {
    testDebug(makeConfig(LineNumberOptimization.OFF, false, true), ORIGINAL_LINE_NUMBERS_DEBUG);
  }

  @Test
  public void testNotOptimizedByEnablingDebugWithMap() throws Throwable {
    testDebug(makeConfig(LineNumberOptimization.OFF, true, true), ORIGINAL_LINE_NUMBERS_DEBUG);
  }

  private void testDebug(DebugTestConfig config, int[] lineNumbers) throws Throwable {
    runDebugTest(
        config,
        CLASS1,
        breakpoint(CLASS1, "main", MAIN_SIGNATURE),
        run(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[0]),
        stepInto(),
        checkMethod(CLASS1, "callThisFromSameFile", "()V"),
        checkLine(FILE1, lineNumbers[1]),
        stepOver(),
        checkMethod(CLASS1, "callThisFromSameFile", "()V"),
        checkLine(FILE1, lineNumbers[2]),
        stepInto(INTELLIJ_FILTER),
        checkMethod(CLASS2, "callThisFromAnotherFile", "()V"),
        checkLine(FILE2, lineNumbers[3]),
        stepOver(),
        checkMethod(CLASS2, "callThisFromAnotherFile", "()V"),
        checkLine(FILE2, lineNumbers[4]),
        stepOver(),
        checkMethod(CLASS1, "callThisFromSameFile", "()V"),
        checkLine(FILE1, lineNumbers[5]),
        stepOver(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[6]),
        stepInto(),
        checkMethod(CLASS1, "callThisFromSameFile", "(I)V"),
        checkLine(FILE1, lineNumbers[7]),
        stepOver(),
        checkMethod(CLASS1, "callThisFromSameFile", "(I)V"),
        checkLine(FILE1, lineNumbers[8]),
        stepOver(),
        checkMethod(CLASS1, "main", MAIN_SIGNATURE),
        checkLine(FILE1, lineNumbers[9]),
        stepInto(),
        checkMethod(CLASS1, "callThisFromSameFile", "(II)V"),
        checkLine(FILE1, lineNumbers[10]),
        stepOver(),
        checkMethod(CLASS1, "callThisFromSameFile", "(II)V"),
        checkLine(FILE1, lineNumbers[11]),
        run());
  }
}

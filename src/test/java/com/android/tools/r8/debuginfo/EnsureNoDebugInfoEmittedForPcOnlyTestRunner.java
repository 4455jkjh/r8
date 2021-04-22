// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNull;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexDebugEntry;
import com.android.tools.r8.graph.DexDebugEntryBuilder;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnsureNoDebugInfoEmittedForPcOnlyTestRunner extends TestBase {

  private static final String FILENAME_MAIN = "EnsureNoDebugInfoEmittedForPcOnlyTest.java";
  private static final Class<?> MAIN = EnsureNoDebugInfoEmittedForPcOnlyTest.class;
  private static final int INLINED_DEX_PC = 32;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public EnsureNoDebugInfoEmittedForPcOnlyTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean apiLevelSupportsPcOutput() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.O);
  }

  // TODO(b/37830524): Remove when activated.
  private void enablePcDebugInfoOutput(InternalOptions options) {
    options.enablePcDebugInfoOutput = true;
  }

  @Test
  public void testD8Debug() throws Exception {
    testForD8(parameters.getBackend())
        .debug()
        .addProgramClasses(MAIN)
        .setMinApi(parameters.getApiLevel())
        .internalEnableMappingOutput()
        .addOptionsModification(this::enablePcDebugInfoOutput)
        .run(parameters.getRuntime(), MAIN)
        // For a debug build we always expect the output to have actual line information.
        .inspectFailure(this::checkHasLineNumberInfo)
        .inspectStackTrace(this::checkExpectedStackTrace);
  }

  @Test
  public void testD8Release() throws Exception {
    testForD8(parameters.getBackend())
        .release()
        .addProgramClasses(MAIN)
        .setMinApi(parameters.getApiLevel())
        .internalEnableMappingOutput()
        .addOptionsModification(this::enablePcDebugInfoOutput)
        .run(parameters.getRuntime(), MAIN)
        .inspectFailure(
            inspector -> {
              if (apiLevelSupportsPcOutput()) {
                checkNoDebugInfo(inspector, 5);
              } else {
                checkHasLineNumberInfo(inspector);
              }
            })
        .inspectStackTrace(this::checkExpectedStackTrace);
  }

  @Test
  public void testD8ReleaseWithoutMapOutput() throws Exception {
    testForD8(parameters.getBackend())
        .release()
        .addProgramClasses(MAIN)
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(this::enablePcDebugInfoOutput)
        .run(parameters.getRuntime(), MAIN)
        // If compiling without a map output actual debug info should also be retained. Otherwise
        // there would not be any way to obtain the actual lines.
        .inspectFailure(this::checkHasLineNumberInfo)
        .inspectStackTrace(this::checkExpectedStackTrace);
  }

  @Test
  public void testNoEmittedDebugInfoR8() throws Exception {
    assumeTrue(apiLevelSupportsPcOutput());
    testForR8(parameters.getBackend())
        .addProgramClasses(MAIN)
        .addKeepMainRule(MAIN)
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters.getApiLevel())
        .addOptionsModification(this::enablePcDebugInfoOutput)
        .run(parameters.getRuntime(), MAIN)
        .inspectOriginalStackTrace(
            (stackTrace, inspector) -> {
              assertEquals(MAIN.getTypeName(), stackTrace.get(0).className);
              assertEquals("main", stackTrace.get(0).methodName);
              checkNoDebugInfo(inspector, 1);
            })
        .inspectStackTrace(this::checkExpectedStackTrace);
  }

  private void checkNoDebugInfo(CodeInspector inspector, int expectedMethodsInMain) {
    ClassSubject clazz = inspector.clazz(MAIN);
    assertEquals(expectedMethodsInMain, clazz.allMethods().size());
    MethodSubject main = clazz.uniqueMethodWithName("main");
    assertNull(main.getMethod().getCode().asDexCode().getDebugInfo());
  }

  private void checkHasLineNumberInfo(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(MAIN);
    MethodSubject main = clazz.uniqueMethodWithName("main");
    List<DexDebugEntry> entries =
        new DexDebugEntryBuilder(main.getMethod(), inspector.getFactory()).build();
    Set<Integer> lines = entries.stream().map(e -> e.line).collect(Collectors.toSet());
    // Check some of the lines in main are present (not 27 as it may be optimized out).
    assertTrue(lines.contains(22));
    assertTrue(lines.contains(23));
    assertTrue(lines.contains(25));
  }

  private void checkExpectedStackTrace(StackTrace stackTrace) {
    assertThat(
        stackTrace,
        isSameExceptForFileNameAndLineNumber(
            StackTrace.builder()
                .add(line("a", 11))
                .add(line("b", 18))
                .add(line("main", 23))
                .build()));
  }

  private StackTraceLine line(String method, int line) {
    return StackTraceLine.builder()
        .setClassName(MAIN.getTypeName())
        .setMethodName(method)
        .setLineNumber(line)
        .setFileName(FILENAME_MAIN)
        .build();
  }
}

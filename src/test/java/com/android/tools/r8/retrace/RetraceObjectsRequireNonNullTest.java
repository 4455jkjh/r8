// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForLineNumbers;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceObjectsRequireNonNullTest extends TestBase {

  static final Class<?> CLASS = RetraceObjectsRequireNonNullClass.class;
  static final Class<?> CLASS_A = RetraceObjectsRequireNonNullClass.A.class;
  static final Class<?> CLASS_MAIN = RetraceObjectsRequireNonNullClass.Main.class;

  static List<Class<?>> getInputClasses() {
    return ImmutableList.of(CLASS, CLASS_A, CLASS_MAIN);
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection parameters() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  @Parameter(0)
  public TestParameters parameters;

  private boolean isCfVmWithModulePrefix() {
    return parameters.isCfRuntime() && !parameters.isCfRuntime(CfVm.JDK8);
  }

  private boolean isApiWithRequireNonNullSupport() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K);
  }

  @Test
  public void testReference() throws Exception {
    parameters.assumeJvmTestParameters();
    boolean includeObjectsFrame = true;
    boolean includeJvmModule = isCfVmWithModulePrefix();
    boolean doNotCheckLines = true;
    testForJvm(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), CLASS_MAIN)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace ->
                checkExpectedStackTrace(
                    stackTrace, includeObjectsFrame, includeJvmModule, doNotCheckLines));
  }

  @Test
  public void testD8() throws Exception {
    boolean includeObjectsFrame = isApiWithRequireNonNullSupport();
    boolean includeJvmModule = includeObjectsFrame && isCfVmWithModulePrefix();
    boolean doNotCheckLines = includeObjectsFrame;
    testForD8(parameters.getBackend())
        .addProgramClasses(getInputClasses())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), CLASS_MAIN)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace ->
                checkExpectedStackTrace(
                    stackTrace, includeObjectsFrame, includeJvmModule, doNotCheckLines));
  }

  @Test
  public void testD8DebugRetrace() throws Exception {
    boolean includeObjectsFrame = true;
    boolean includeJvmModule = isApiWithRequireNonNullSupport() && isCfVmWithModulePrefix();
    boolean doNotCheckLines = isApiWithRequireNonNullSupport();
    testForD8(parameters.getBackend())
        .debug()
        .internalEnableMappingOutput()
        .addProgramClasses(getInputClasses())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), CLASS_MAIN)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace ->
                checkExpectedStackTrace(
                    stackTrace, includeObjectsFrame, includeJvmModule, doNotCheckLines));
  }

  @Test
  public void testD8ReleaseRetrace() throws Exception {
    boolean includeObjectsFrame = true;
    boolean includeJvmModule = isApiWithRequireNonNullSupport() && isCfVmWithModulePrefix();
    boolean doNotCheckLines = isApiWithRequireNonNullSupport();
    testForD8(parameters.getBackend())
        .release()
        .internalEnableMappingOutput()
        .addProgramClasses(getInputClasses())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), CLASS_MAIN)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace ->
                checkExpectedStackTrace(
                    stackTrace, includeObjectsFrame, includeJvmModule, doNotCheckLines));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    boolean includeObjectsFrame = true;
    boolean includeJvmModule = isCfVmWithModulePrefix();
    boolean doNotCheckLines = parameters.isCfRuntime() || isApiWithRequireNonNullSupport();
    testForR8(parameters.getBackend())
        .addProgramClasses(getInputClasses())
        .addKeepMainRule(CLASS_MAIN)
        .addKeepAttributeSourceFile()
        .addKeepAttributeLineNumberTable()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), CLASS_MAIN)
        .apply(this::checkRunResult)
        .inspectStackTrace(
            stackTrace ->
                checkExpectedStackTrace(
                    stackTrace, includeObjectsFrame, includeJvmModule, doNotCheckLines));
  }

  private void checkRunResult(SingleTestRunResult<?> runResult) {
    runResult.assertFailureWithErrorThatThrows(NullPointerException.class);
  }

  private void checkExpectedStackTrace(
      StackTrace stackTrace,
      boolean includeObjectsFrame,
      boolean includeJvmModule,
      boolean doNotCheckLines) {
    StackTrace.Builder builder = StackTrace.builder();
    if (includeObjectsFrame) {
      ClassReference objects = Reference.classFromClass(Objects.class);
      String objectsFrameFormat = (includeJvmModule ? "java.base/" : "") + objects.getTypeName();
      builder.add(
          StackTraceLine.builder()
              .setFileName("Objects.java")
              .setClassName(objectsFrameFormat)
              .setMethodName("requireNonNull")
              .setLineNumber(-1)
              .build());
    }
    String fileName = ToolHelper.getSourceFileForTestClass(CLASS).getFileName().toString();
    builder
        .add(
            StackTraceLine.builder()
                .setFileName(fileName)
                .setClassName(typeName(CLASS_A))
                .setMethodName("run")
                .setLineNumber(14)
                .build())
        .add(
            StackTraceLine.builder()
                .setFileName(fileName)
                .setClassName(typeName(CLASS_MAIN))
                .setMethodName("main")
                .setLineNumber(21)
                .build());
    if (doNotCheckLines) {
      // We can't check the line numbers when using the native support of Objects.requireNonNull.
      assertThat(stackTrace, isSameExceptForLineNumbers(builder.build()));
    } else {
      assertThat(stackTrace, isSame(builder.build()));
    }
  }
}
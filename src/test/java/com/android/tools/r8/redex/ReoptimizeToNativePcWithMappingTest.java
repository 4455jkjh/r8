// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.redex;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.Box;
import com.android.tools.r8.utils.internal.FileUtils;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReoptimizeToNativePcWithMappingTest extends TestBase {

  private final TestParameters parameters;

  // Cinnamon Bun outputs DEX version 039 which is only supported on Android 9.0 (Pie) and newer.
  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesStartingFromIncluding(Version.V9_0_0).build();
  }

  public ReoptimizeToNativePcWithMappingTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    AndroidApiLevel initialMinApi = AndroidApiLevel.N;
    AndroidApiLevel reoptimizationMinApi = AndroidApiLevel.CINNAMON_BUN;

    Box<String> currentFooMethodName = new Box<>();
    Box<String> currentTestClassName = new Box<>();
    R8TestCompileResult r8CompileResult =
        testForR8(Backend.DEX)
            .setMinApi(initialMinApi)
            .setMode(CompilationMode.RELEASE)
            .addProgramClasses(StacktracePreservationTestClass.class)
            .addKeepRules(
                "-keep,allowrepackage class "
                    + StacktracePreservationTestClass.class.getName()
                    + " { public static void main(java.lang.String[]); }",
                "-repackageclasses repackaged")
            .enableInliningAnnotations()
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject clazz =
                      inspector.clazz(
                          Reference.classFromClass(StacktracePreservationTestClass.class));
                  assertTrue(clazz.isPresent());
                  assertTrue(clazz.getFinalName().startsWith("repackaged."));
                  currentTestClassName.set(clazz.getFinalName());
                  MethodSubject method = clazz.uniqueMethodWithOriginalName("foo");
                  assertTrue(method.isPresent());
                  currentFooMethodName.set(method.getFinalName());
                  assertTrue("Expected debug info on N", method.hasLineNumberTable());
                });

    Path r8Output = r8CompileResult.writeToZip();
    String r8Mapping = r8CompileResult.getProguardMap();
    assertThat(
        r8Mapping,
        containsString(StacktracePreservationTestClass.class.getName() + " -> repackaged."));

    r8CompileResult
        .run(parameters.getRuntime(), currentTestClassName.get())
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectOriginalStackTrace(
            stacktrace -> {
              assertThat(stacktrace, not(StackTrace.isSame(getExpectedStackTrace())));
              StackTrace retracedStackTrace = stacktrace.retrace(r8Mapping);
              assertThat(retracedStackTrace, StackTrace.isSame(getExpectedStackTrace()));
            });

    Path inputMap = temp.newFolder().toPath().resolve("input.map");
    FileUtils.writeTextFile(inputMap, r8Mapping);

    D8TestCompileResult d8CompileResult =
        testForD8(Backend.DEX)
            .setMinApi(reoptimizationMinApi)
            .setExperimentalReoptimizeDex(true)
            .internalEnableMappingOutput()
            .setMode(CompilationMode.RELEASE)
            .apply(b -> b.getBuilder().setProguardMapInputFile(inputMap))
            .addProgramFiles(r8Output)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject clazz = inspector.clazz(currentTestClassName.get());
                  assertTrue(clazz.isPresent());
                  MethodSubject method =
                      clazz.uniqueMethodWithOriginalName(currentFooMethodName.get());
                  assertTrue(method.isPresent());
                  assertNull(
                      "Expected native debug info due to mapping output",
                      method.getMethod().getCode().asDexCode().getDebugInfo());
                });

    // Verify that the output mapping is correct and composed.
    String d8Mapping = d8CompileResult.getProguardMap();
    assertThat(
        d8Mapping,
        containsString(StacktracePreservationTestClass.class.getName() + " -> repackaged."));

    d8CompileResult
        .run(parameters.getRuntime(), currentTestClassName.get())
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectOriginalStackTrace(
            stacktrace -> {
              assertThat(stacktrace, not(StackTrace.isSame(getExpectedStackTrace())));
              StackTrace retracedStackTrace = stacktrace.retrace(d8Mapping);
              assertThat(retracedStackTrace, StackTrace.isSame(getExpectedStackTrace()));
            });
  }

  private StackTrace getExpectedStackTrace() {
    String className = StacktracePreservationTestClass.class.getName();
    return StackTrace.builder()
        .add(
            StackTraceLine.builder()
                .setClassName(className)
                .setMethodName("foo")
                .setFileName("StacktracePreservationTestClass.java")
                .setLineNumber(19)
                .build())
        .add(
            StackTraceLine.builder()
                .setClassName(className)
                .setMethodName("main")
                .setFileName("StacktracePreservationTestClass.java")
                .setLineNumber(12)
                .build())
        .build();
  }
}

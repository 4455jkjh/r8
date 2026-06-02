// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.redex;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.Box;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class StacktracePreservationTest extends TestBase {

  private final AndroidApiLevel compilationApiLevel;
  private final AndroidApiLevel redexApiLevel;
  private final DexVm.Version runtime;

  public StacktracePreservationTest(
      AndroidApiLevel compilationApiLevel, AndroidApiLevel redexApiLevel, Version runtime) {
    this.compilationApiLevel = compilationApiLevel;
    this.redexApiLevel = redexApiLevel;
    this.runtime = runtime;
  }

  @Parameters(name = "firstApi:{0}, redexApi:{1}, runtime:{2}")
  public static Collection<Object[]> data() {
    // Test a re-dex API level upgrade from api to target with some runtime.
    // { (api, target, runtime) |
    //    target in {LATEST, Sv2} &
    //    api <= target &
    //    runtime in {target.minVm, target.maxVm} &
    // }

    // The test assumes that all targets are >= 24
    List<AndroidApiLevel> targets = ImmutableList.of(AndroidApiLevel.LATEST, AndroidApiLevel.Sv2);

    Version maxVm = Version.V17_0_0;

    List<Object[]> parametersList = new ArrayList<>();
    for (AndroidApiLevel target : targets) {
      for (AndroidApiLevel api : AndroidApiLevel.getAndroidApiLevelsSorted()) {
        if (api.isLessThanOrEqualTo(target)) {
          Set<Version> runtimes =
              ImmutableSet.of(ToolHelper.getDexVersionForApiLevel(target), maxVm);
          for (Version vm : runtimes) {
            parametersList.add(new Object[] {api, target, vm});
          }
        }
      }
    }
    // Verify that valid configurations are actually found.
    assert parametersList.size() == 102
        : "Unexpected configuration count: " + parametersList.size();

    return TestParametersBuilder.filterByDexVmVersion(
        parametersList, params -> (Version) params[2]);
  }

  @Test
  public void test() throws Exception {
    Box<String> currentFooMethodName = new Box<>("foo");
    R8TestCompileResultBase<?> compileResult =
        testForR8(Backend.DEX)
            .setMinApi(compilationApiLevel)
            .setMode(CompilationMode.RELEASE)
            .addProgramClasses(StacktracePreservationTestClass.class)
            .addKeepMainRule(StacktracePreservationTestClass.class)
            .enableInliningAnnotations()
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject clazz = inspector.clazz(StacktracePreservationTestClass.class);
                  MethodSubject method =
                      clazz.uniqueMethodWithOriginalName(currentFooMethodName.get());
                  assertTrue(method.isPresent());
                  currentFooMethodName.set(method.getFinalName());
                });

    Path r8Output = compileResult.writeToZip();
    String r8Mapping = compileResult.getProguardMap();

    compileResult
        .run(new DexRuntime(runtime), StacktracePreservationTestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectOriginalStackTrace(
            stacktrace -> {
              assertThat(stacktrace, not(StackTrace.isSame(getExpectedStackTrace())));
              StackTrace retracedStackTrace = stacktrace.retrace(r8Mapping);
              assertThat(retracedStackTrace, StackTrace.isSame(getExpectedStackTrace()));
            });

    testForD8(Backend.DEX)
        .setMinApi(redexApiLevel)
        // Make const-strings instructions larger to "bump" the pc.
        .addOptionsModification(options -> options.testing.forceJumboStringProcessing = true)
        .setMode(CompilationMode.RELEASE)
        .setExperimentalReoptimizeDex(true)
        .addProgramFiles(r8Output)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(StacktracePreservationTestClass.class);
              MethodSubject method = clazz.uniqueMethodWithOriginalName(currentFooMethodName.get());
              assertTrue(method.isPresent());
            })
        .run(new DexRuntime(runtime), StacktracePreservationTestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectOriginalStackTrace(
            stacktrace -> {
              assertThat(stacktrace, not(StackTrace.isSame(getExpectedStackTrace())));
              StackTrace retracedStackTrace = stacktrace.retrace(r8Mapping);
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

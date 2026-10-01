// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.internal.StringUtils;
import com.android.tools.r8.utils.internal.ThrowingConsumer;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.function.Predicate;
import org.hamcrest.Matcher;

public class SingleTestRunResult extends TestRunResult<SingleTestRunResult> {
  private final TestRuntime runtime;
  private final ProcessResult result;

  public SingleTestRunResult(
      AndroidApp app, TestRuntime runtime, ProcessResult result, TestState state) {
    this(app, runtime, result, null, state);
  }

  public SingleTestRunResult(
      AndroidApp app,
      TestRuntime runtime,
      ProcessResult result,
      String proguardMap,
      TestState state) {
    super(app, proguardMap, state);
    this.runtime = runtime;
    this.result = result;
  }

  @Override
  protected SingleTestRunResult self() {
    return this;
  }

  @Override
  public SingleTestRunResult asSingleRuntimeResult() {
    return self();
  }

  public TestRuntime runtime() {
    return runtime;
  }

  public ProcessResult getResult() {
    return result;
  }

  public String getStdOut() {
    return result.stdout;
  }

  public String getStdErr() {
    return result.stderr;
  }

  public int getExitCode() {
    return result.exitCode;
  }

  public StackTrace getStackTrace() {
    StackTrace original = getOriginalStackTrace();
    return proguardMap == null ? original : original.retraceAllowExperimentalMapping(proguardMap);
  }

  public StackTrace getOriginalStackTrace() {
    if (runtime.isDex()) {
      return StackTrace.extractFromArt(getStdErr(), runtime.asDex().getVm());
    } else {
      return StackTrace.extractFromJvm(getStdErr());
    }
  }

  @Override
  public <E extends Throwable> SingleTestRunResult inspectStdOut(
      ThrowingConsumer<String, E> consumer) throws E {
    consumer.accept(getStdOut());
    return self();
  }

  @Override
  public <E extends Throwable> SingleTestRunResult inspectStackTrace(
      ThrowingConsumer<StackTrace, E> consumer) throws E {
    consumer.accept(getStackTrace());
    return self();
  }

  @Override
  public <E extends Throwable> SingleTestRunResult inspectOriginalStackTrace(
      ThrowingConsumer<StackTrace, E> consumer) throws E {
    consumer.accept(getOriginalStackTrace());
    return self();
  }

  @Override
  public SingleTestRunResult assertSuccess() {
    assertEquals(errorMessage("Expected run to succeed."), 0, result.exitCode);
    return self();
  }

  @Override
  public SingleTestRunResult assertStdoutMatches(Matcher<String> matcher) {
    assertThat(errorMessage("Run stdout incorrect.", matcher.toString()), result.stdout, matcher);
    return self();
  }

  @Override
  public SingleTestRunResult assertStdoutLinesMatchesUnordered(Iterable<Matcher<String>> lines) {
    assertThat(
        StringUtils.splitLines(result.stdout), UnorderedCollectionMatcher.matchesOneToOne(lines));
    return self();
  }

  @Override
  public SingleTestRunResult assertFailure() {
    assertNotEquals(errorMessage("Expected run to fail."), 0, result.exitCode);
    return self();
  }

  @Override
  public SingleTestRunResult assertStderrMatches(Matcher<String> matcher) {
    assertThat(errorMessage("Run stderr incorrect.", matcher.toString()), result.stderr, matcher);
    return self();
  }

  @Override
  void appendProcessResult(StringBuilder builder) {
    builder.append("COMMAND: ").append(result.command).append('\n').append(result);
  }

  @Override
  public SingleTestRunResult writeProcessResult(PrintStream ps) {
    StringBuilder sb = new StringBuilder();
    appendProcessResult(sb);
    ps.println(sb.toString());
    return self();
  }

  private boolean matchesDexVersion(Predicate<DexVm.Version> predicate) {
    return runtime.isDex() && predicate.test(runtime.asDex().getVm().getVersion());
  }

  @Override
  public <
          S extends Throwable,
          T extends Throwable,
          U extends Throwable,
          V extends Throwable,
          W extends Throwable>
      SingleTestRunResult applyIfDexRuntime(
          Predicate<DexVm.Version> condition1,
          ThrowingConsumer<? super SingleTestRunResult, S> thenConsumer1,
          Predicate<DexVm.Version> condition2,
          ThrowingConsumer<? super SingleTestRunResult, T> thenConsumer2,
          Predicate<DexVm.Version> condition3,
          ThrowingConsumer<? super SingleTestRunResult, U> thenConsumer3,
          Predicate<DexVm.Version> condition4,
          ThrowingConsumer<? super SingleTestRunResult, W> thenConsumer4,
          ThrowingConsumer<? super SingleTestRunResult, V> elseConsumer)
          throws S, T, U, V, W {
    if (matchesDexVersion(condition1)) {
      thenConsumer1.accept(this);
    } else if (matchesDexVersion(condition2)) {
      thenConsumer2.accept(this);
    } else if (matchesDexVersion(condition3)) {
      thenConsumer3.accept(this);
    } else if (matchesDexVersion(condition4)) {
      thenConsumer4.accept(this);
    } else {
      elseConsumer.accept(this);
    }
    return self();
  }

  @Override
  public <E extends Throwable> SingleTestRunResult debugger(
      ThrowingConsumer<DebugTestConfig, E> consumer) throws E, IOException {
    Path out = getState().getNewTempFolder().resolve("out.zip");
    app.writeToZipForTesting(out, runtime.isCf() ? OutputMode.ClassFile : OutputMode.DexIndexed);
    DebugTestConfig config = DebugTestConfig.create(runtime, out);
    consumer.accept(config);
    return self();
  }
}

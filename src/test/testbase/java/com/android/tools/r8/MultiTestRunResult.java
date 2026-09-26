// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.internal.ThrowingConsumer;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import org.hamcrest.Matcher;

public abstract class MultiTestRunResult<RR extends MultiTestRunResult<RR>>
    extends TestRunResult<RR> {

  private final List<SingleTestRunResult> singleRunResults;

  public MultiTestRunResult(
      AndroidApp app,
      String proguardMap,
      TestState state,
      List<SingleTestRunResult> singleRunResults) {
    super(app, proguardMap, state);
    assert !singleRunResults.isEmpty();
    this.singleRunResults = singleRunResults;
  }

  public List<SingleTestRunResult> getSingleRunResults() {
    return singleRunResults;
  }

  @Override
  public SingleTestRunResult asSingleRuntimeResult() {
    if (singleRunResults.size() != 1) {
      throw new Unreachable(
          "Cannot access single-VM result on CollapsedDexRuntimes; use"
              + " withoutCollapsedDexRuntimes()");
    }
    return singleRunResults.get(0);
  }

  @Override
  public <E extends Throwable> RR inspectStdOut(ThrowingConsumer<String, E> consumer) throws E {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.inspectStdOut(consumer);
    }
    return self();
  }

  @Override
  public RR assertSuccess() {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.assertSuccess();
    }
    return self();
  }

  @Override
  public RR assertStdoutMatches(Matcher<String> matcher) {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.assertStdoutMatches(matcher);
    }
    return self();
  }

  @Override
  public RR assertStdoutLinesMatchesUnordered(Iterable<Matcher<String>> lines) {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.assertStdoutLinesMatchesUnordered(lines);
    }
    return self();
  }

  @Override
  public RR assertFailure() {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.assertFailure();
    }
    return self();
  }

  @Override
  public RR assertStderrMatches(Matcher<String> matcher) {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.assertStderrMatches(matcher);
    }
    return self();
  }

  @Override
  public <E extends Throwable> RR inspectStackTrace(ThrowingConsumer<StackTrace, E> consumer)
      throws E {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.inspectStackTrace(consumer);
    }
    return self();
  }

  @Override
  public <E extends Throwable> RR inspectOriginalStackTrace(
      ThrowingConsumer<StackTrace, E> consumer) throws E {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.inspectOriginalStackTrace(consumer);
    }
    return self();
  }

  @Override
  void appendProcessResult(StringBuilder builder) {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.appendProcessResult(builder);
    }
  }

  @Override
  public RR writeProcessResult(PrintStream ps) {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.writeProcessResult(ps);
    }
    return self();
  }

  @Override
  public <E extends Throwable> RR debugger(ThrowingConsumer<DebugTestConfig, E> consumer)
      throws E, IOException {
    for (SingleTestRunResult singleResult : singleRunResults) {
      singleResult.debugger(consumer);
    }
    return self();
  }
}

// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.internal.ThrowingConsumer;
import com.android.tools.r8.utils.internal.collections.Pair;
import com.android.tools.r8.utils.internal.exceptions.Unimplemented;
import com.google.common.base.Predicates;
import com.google.common.base.Strings;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import org.hamcrest.Matcher;

/** Checking container for checking the same properties of multiple run results. */
public abstract class TestRunResultCollection<
        C extends Enum<C>, RR extends TestRunResultCollection<C, RR>>
    extends TestRunResult<RR> {

  private final List<Pair<C, TestRunResult<?>>> runs;

  public TestRunResultCollection(List<Pair<C, TestRunResult<?>>> runs) {
    this.runs = runs;
  }

  private <E extends Throwable> RR forEach(ThrowingConsumer<TestRunResult<?>, E> fn) throws E {
    for (Pair<C, TestRunResult<?>> run : runs) {
      fn.accept(run.getSecond());
    }
    return self();
  }

  @Override
  public RR assertSuccess() {
    return forEach(TestRunResult::assertSuccess);
  }

  @Override
  public RR assertFailure() {
    return forEach(TestRunResult::assertFailure);
  }

  @Override
  public RR assertStdoutMatches(Matcher<String> matcher) {
    return forEach(r -> r.assertStdoutMatches(matcher));
  }

  @Override
  public RR assertStdoutLinesMatchesUnordered(Iterable<Matcher<String>> lines) {
    return forEach(r -> r.assertStdoutLinesMatchesUnordered(lines));
  }

  @Override
  public RR assertStderrMatches(Matcher<String> matcher) {
    return forEach(r -> r.assertStderrMatches(matcher));
  }

  @Override
  public <E extends Throwable> RR inspectStdOut(ThrowingConsumer<String, E> consumer) throws E {
    return forEach(r -> r.inspectStdOut(consumer));
  }

  @Override
  public <E extends Throwable> RR inspectStackTrace(ThrowingConsumer<StackTrace, E> consumer)
      throws E {
    return forEach(r -> r.inspectStackTrace(consumer));
  }

  @Override
  public <E extends Throwable> RR inspectOriginalStackTrace(
      ThrowingConsumer<StackTrace, E> consumer) throws E {
    return forEach(r -> r.inspectOriginalStackTrace(consumer));
  }

  @Override
  public <
          S extends Throwable,
          T extends Throwable,
          U extends Throwable,
          V extends Throwable,
          W extends Throwable>
      RR applyIfDexRuntime(
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
    for (Pair<C, TestRunResult<?>> run : runs) {
      run.getSecond()
          .applyIfDexRuntime(
              condition1,
              thenConsumer1,
              condition2,
              thenConsumer2,
              condition3,
              thenConsumer3,
              condition4,
              thenConsumer4,
              elseConsumer);
    }
    return self();
  }

  @Override
  public <E extends Throwable> RR inspect(ThrowingConsumer<CodeInspector, E> consumer)
      throws IOException, ExecutionException, E {
    return inspectIf(Predicates.alwaysTrue(), consumer);
  }

  @Override
  public <E extends Throwable> RR inspectFailure(ThrowingConsumer<CodeInspector, E> consumer)
      throws E {
    throw new Unimplemented();
  }

  public RR applyIf(Predicate<C> filter, Consumer<TestRunResult<?>> thenConsumer) {
    return applyIf(filter, thenConsumer, r -> {});
  }

  public RR applyIf(
      Predicate<C> filter,
      Consumer<TestRunResult<?>> thenConsumer,
      Consumer<TestRunResult<?>> elseConsumer) {
    return applyIf(
        filter,
        thenConsumer,
        c -> true,
        elseConsumer,
        r -> {
          assert false;
        });
  }

  public RR applyIf(
      Predicate<C> filter1,
      Consumer<TestRunResult<?>> thenConsumer1,
      Predicate<C> filter2,
      Consumer<TestRunResult<?>> thenConsumer2,
      Consumer<TestRunResult<?>> elseConsumer) {
    return applyIf(
        filter1,
        thenConsumer1,
        filter2,
        thenConsumer2,
        c -> true,
        elseConsumer,
        r -> {
          assert false;
        });
  }

  public RR applyIf(
      Predicate<C> filter1,
      Consumer<TestRunResult<?>> thenConsumer1,
      Predicate<C> filter2,
      Consumer<TestRunResult<?>> thenConsumer2,
      Predicate<C> filter3,
      Consumer<TestRunResult<?>> thenConsumer3,
      Consumer<TestRunResult<?>> elseConsumer) {
    for (Pair<C, TestRunResult<?>> run : runs) {
      if (filter1.test(run.getFirst())) {
        thenConsumer1.accept(run.getSecond());
      } else if (filter2.test(run.getFirst())) {
        thenConsumer2.accept(run.getSecond());
      } else if (filter3.test(run.getFirst())) {
        thenConsumer3.accept(run.getSecond());
      } else {
        elseConsumer.accept(run.getSecond());
      }
    }
    return self();
  }

  public <E extends Throwable> RR inspectIf(
      Predicate<C> filter, ThrowingConsumer<CodeInspector, E> consumer)
      throws IOException, ExecutionException, E {
    for (Pair<C, TestRunResult<?>> run : runs) {
      if (filter.test(run.getFirst())) {
        run.getSecond().inspect(consumer);
      }
    }
    return self();
  }

  @Override
  public RR disassemble() throws IOException, ExecutionException {
    for (Pair<C, TestRunResult<?>> run : runs) {
      String name = run.getFirst().name();
      System.out.println(name + " " + Strings.repeat("=", 80 - name.length() - 1));
      run.getSecond().disassemble();
    }
    return self();
  }
}

// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.internal.ThrowingBiConsumer;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

public class R8TestRunResult extends MultiTestRunResult<R8TestRunResult> {

  public interface GraphInspectorSupplier {
    GraphInspector get() throws IOException;
  }

  private final GraphInspectorSupplier graphInspector;

  public R8TestRunResult(
      AndroidApp app,
      String proguardMap,
      GraphInspectorSupplier graphInspector,
      TestState state,
      List<SingleTestRunResult> singleRunResults) {
    super(app, proguardMap, state, singleRunResults);
    this.graphInspector = graphInspector;
  }

  @Override
  public boolean isR8TestRunResult() {
    return true;
  }

  @Override
  protected R8TestRunResult self() {
    return this;
  }

  public <E extends Throwable> R8TestRunResult inspectOriginalStackTrace(
      ThrowingBiConsumer<StackTrace, CodeInspector, E> consumer) throws E, IOException {
    CodeInspector inspector = internalGetCodeInspector();
    for (SingleTestRunResult singleResult : getSingleRunResults()) {
      consumer.accept(singleResult.getOriginalStackTrace(), inspector);
    }
    return self();
  }

  public GraphInspector graphInspector() throws IOException, ExecutionException {
    assertSuccess();
    return graphInspector.get();
  }

  public R8TestRunResult inspectGraph(Consumer<GraphInspector> consumer)
      throws IOException, ExecutionException {
    consumer.accept(graphInspector());
    return self();
  }

  public <E extends Throwable> R8TestRunResult inspectStackTrace(
      ThrowingBiConsumer<StackTrace, CodeInspector, E> consumer) throws E, IOException {
    CodeInspector inspector = internalGetCodeInspector();
    for (SingleTestRunResult singleResult : getSingleRunResults()) {
      consumer.accept(singleResult.getStackTrace(), inspector);
    }
    return self();
  }

  public String proguardMap() {
    return proguardMap;
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences.internal;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.origin.MethodOrigin;
import com.android.tools.r8.tracereferences.TraceReferencesNativeReferencesConsumer;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

public class TraceReferencesNativesPrinter
    extends TraceReferencesNativeReferencesConsumer.ForwardingConsumer {

  private final TraceReferencesNativeResult.Builder resultBuilder;
  private final StringConsumer consumer;

  private TraceReferencesNativesPrinter(
      TraceReferencesNativeResult.Builder resultBuilder, StringConsumer consumer) {
    super(resultBuilder);
    this.resultBuilder = resultBuilder;
    this.consumer = consumer;
  }

  public static class Builder {
    private StringConsumer consumer;

    public Builder setOutputPath(Path output) {
      this.consumer = new FileConsumer(output);
      return this;
    }

    public Builder setOutputConsumer(StringConsumer consumer) {
      this.consumer = consumer;
      return this;
    }

    public TraceReferencesNativesPrinter build() {
      return new TraceReferencesNativesPrinter(TraceReferencesNativeResult.builder(), consumer);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    super.finished(handler);
    TraceReferencesNativeResult result = resultBuilder.build();
    if (isCalled(
        "System.loadLibrary",
        result.getLoadLibraryAnyCalls(),
        result.getLoadLibraryCalls(),
        handler)) {
      unknownArguments("System.loadLibrary", result.getLoadLibraryAnyCalls(), handler);
      knownArguments("System.loadLibrary", result.getLoadLibraryCalls(), handler);
    }
    if (isCalled("System.load", result.getLoadAnyCalls(), result.getLoadCalls(), handler)) {
      unknownArguments("System.load", result.getLoadAnyCalls(), handler);
      knownArguments("System.load", result.getLoadCalls(), handler);
    }
    if (!result.getNativeMethods().isEmpty()) {
      consumer.accept("Native methods.", handler);
      result
          .getNativeMethods()
          .forEach(
              method ->
                  consumer.accept("  " + MethodReferenceUtils.toSourceString(method), handler));
    } else {
      consumer.accept("No native methods.", handler);
    }
    consumer.finished(handler);
  }

  private boolean isCalled(
      String method,
      Set<MethodOrigin> unknownCalls,
      Map<String, Set<MethodOrigin>> knownCalls,
      DiagnosticsHandler handler) {
    if (unknownCalls.isEmpty() && knownCalls.isEmpty()) {
      consumer.accept(method + " not called.", handler);
      return false;
    }
    return true;
  }

  private void knownArguments(
      String method, Map<String, Set<MethodOrigin>> result, DiagnosticsHandler handler) {
    if (!result.isEmpty()) {
      consumer.accept(method + " called with these known arguments:", handler);
      result.keySet().stream()
          .sorted()
          .forEach(
              name ->
                  result.get(name).stream()
                      .sorted()
                      .forEach(
                          origin ->
                              consumer.accept(
                                  "  "
                                      + name
                                      + ": "
                                      + MethodReferenceUtils.toSourceString(origin.getMethod()),
                                  handler)));
    }
  }

  private void unknownArguments(
      String method, Set<MethodOrigin> result, DiagnosticsHandler handler) {
    if (!result.isEmpty()) {
      consumer.accept(
          method + " called with unknown argument in " + result.size() + " places", handler);
      result.stream()
          .sorted()
          .forEach(
              origin ->
                  consumer.accept(
                      "  " + MethodReferenceUtils.toSourceString(origin.getMethod()), handler));
    }
  }
}

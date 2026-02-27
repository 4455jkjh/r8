// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.MethodOrigin;
import com.android.tools.r8.references.MethodReference;

// TODO(b/481400921): Remove experimental.
/**
 * Consumer interface for recording references.
 *
 * <p>THIS IS STILL AN EXPERIMENTAL API!
 */
@Deprecated
@KeepForApi
public interface TraceReferencesNativeReferencesConsumer {
  /** Registers a call to System.loadLibrary with a known string value. */
  void acceptLoadLibrary(String name, MethodOrigin origin, DiagnosticsHandler handler);

  /** Registers a call to System.loadLibrary where the argument value is unknown. */
  void acceptLoadLibraryAny(MethodOrigin origin, DiagnosticsHandler handler);

  /** Registers a call to System.load with a known string value. */
  void acceptLoad(String path, MethodOrigin origin, DiagnosticsHandler handler);

  /** Registers a call to System.load where the argument value is unknown. */
  void acceptLoadAny(MethodOrigin origin, DiagnosticsHandler handler);

  /** Registers a native method. */
  void acceptNativeMethod(MethodReference method, DiagnosticsHandler handler);

  /**
   * Tracing natives has finished. There will be no more calls to any of the <code>acceptXXX</code>
   * methods.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then trace references guaranties to exit with an error.
   *
   * @param handler Diagnostics handler for reporting.
   */
  default void finished(DiagnosticsHandler handler) {}

  static TraceReferencesNativeReferencesConsumer emptyConsumer() {
    return TraceReferencesNativeReferencesConsumer.ForwardingConsumer.EMPTY_CONSUMER;
  }

  /** Forwarding consumer to delegate to an optional existing consumer. */
  @KeepForApi
  class ForwardingConsumer implements TraceReferencesNativeReferencesConsumer {

    private static final TraceReferencesNativeReferencesConsumer EMPTY_CONSUMER =
        new ForwardingConsumer(null);

    private final TraceReferencesNativeReferencesConsumer consumer;

    public ForwardingConsumer(TraceReferencesNativeReferencesConsumer consumer) {
      this.consumer = consumer;
    }

    @Override
    public void acceptLoadLibrary(String name, MethodOrigin origin, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.acceptLoadLibrary(name, origin, handler);
      }
    }

    @Override
    public void acceptLoadLibraryAny(MethodOrigin origin, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.acceptLoadLibraryAny(origin, handler);
      }
    }

    @Override
    public void acceptLoad(String path, MethodOrigin origin, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.acceptLoad(path, origin, handler);
      }
    }

    @Override
    public void acceptLoadAny(MethodOrigin origin, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.acceptLoadAny(origin, handler);
      }
    }

    @Override
    public void acceptNativeMethod(MethodReference method, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.acceptNativeMethod(method, handler);
      }
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.finished(handler);
      }
    }
  }
}

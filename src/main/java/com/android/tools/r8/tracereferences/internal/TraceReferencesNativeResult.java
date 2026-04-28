// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences.internal;

import static com.android.tools.r8.utils.internal.MapUtils.ignoreKey;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.MethodOrigin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.tracereferences.TraceReferencesNativeReferencesConsumer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TraceReferencesNativeResult {

  private final Map<String, Set<MethodOrigin>> loadLibraryCalls;
  private final Set<MethodOrigin> loadLibraryAnyCalls;
  private final Map<String, Set<MethodOrigin>> loadCalls;
  private final Set<MethodOrigin> loadAnyCalls;
  private final Set<MethodReference> nativeMethods;

  TraceReferencesNativeResult(
      Map<String, Set<MethodOrigin>> loadLibraryCalls,
      Set<MethodOrigin> loadLibraryAnyCalls,
      Map<String, Set<MethodOrigin>> loadCalls,
      Set<MethodOrigin> loadAnyCalls,
      Set<MethodReference> nativeMethods) {
    this.loadLibraryCalls = loadLibraryCalls;
    this.loadLibraryAnyCalls = loadLibraryAnyCalls;
    this.loadCalls = loadCalls;
    this.loadAnyCalls = loadAnyCalls;
    this.nativeMethods = nativeMethods;
  }

  public Map<String, Set<MethodOrigin>> getLoadLibraryCalls() {
    return loadLibraryCalls;
  }

  public Set<MethodOrigin> getLoadLibraryAnyCalls() {
    return loadLibraryAnyCalls;
  }

  public Map<String, Set<MethodOrigin>> getLoadCalls() {
    return loadCalls;
  }

  public Set<MethodOrigin> getLoadAnyCalls() {
    return loadAnyCalls;
  }

  public Set<MethodReference> getNativeMethods() {
    return nativeMethods;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder implements TraceReferencesNativeReferencesConsumer {
    private final Map<String, Set<MethodOrigin>> loadLibraryCalls = new ConcurrentHashMap<>();
    private final Set<MethodOrigin> loadLibraryAnyCalls = ConcurrentHashMap.newKeySet();
    private final Map<String, Set<MethodOrigin>> loadCalls = new ConcurrentHashMap<>();
    private final Set<MethodOrigin> loadAnyCalls = ConcurrentHashMap.newKeySet();
    private final Set<MethodReference> nativeMethods = ConcurrentHashMap.newKeySet();

    @Override
    public void acceptLoadLibrary(String name, MethodOrigin origin, DiagnosticsHandler handler) {
      loadLibraryCalls.computeIfAbsent(name, ignoreKey(ConcurrentHashMap::newKeySet)).add(origin);
    }

    @Override
    public void acceptLoadLibraryAny(MethodOrigin origin, DiagnosticsHandler handler) {
      loadLibraryAnyCalls.add(origin);
    }

    @Override
    public void acceptLoad(String path, MethodOrigin origin, DiagnosticsHandler handler) {
      loadCalls.computeIfAbsent(path, ignoreKey(ConcurrentHashMap::newKeySet)).add(origin);
    }

    @Override
    public void acceptLoadAny(MethodOrigin origin, DiagnosticsHandler handler) {
      loadAnyCalls.add(origin);
    }

    @Override
    public void acceptNativeMethod(MethodReference method, DiagnosticsHandler handler) {
      nativeMethods.add(method);
    }

    public TraceReferencesNativeResult build() {
      return new TraceReferencesNativeResult(
          loadLibraryCalls, loadLibraryAnyCalls, loadCalls, loadAnyCalls, nativeMethods);
    }
  }
}

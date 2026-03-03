// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences.internal;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.MethodOrigin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.tracereferences.TraceReferencesNativeReferencesConsumer;
import com.android.tools.r8.utils.SetUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

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
    private final Map<String, Set<MethodOrigin>> loadLibraryCalls = new HashMap<>();
    private Set<MethodOrigin> loadLibraryAnyCalls = SetUtils.newHashSet();
    private final Map<String, Set<MethodOrigin>> loadCalls = new HashMap<>();
    private Set<MethodOrigin> loadAnyCalls = SetUtils.newHashSet();
    private final Set<MethodReference> nativeMethods = SetUtils.newHashSet();

    @Override
    public void acceptLoadLibrary(String name, MethodOrigin origin, DiagnosticsHandler handler) {
      loadLibraryCalls.computeIfAbsent(name, ignoreKey(SetUtils::newHashSet)).add(origin);
    }

    @Override
    public void acceptLoadLibraryAny(MethodOrigin origin, DiagnosticsHandler handler) {
      loadLibraryAnyCalls.add(origin);
    }

    @Override
    public void acceptLoad(String path, MethodOrigin origin, DiagnosticsHandler handler) {
      loadCalls.computeIfAbsent(path, ignoreKey(SetUtils::newHashSet)).add(origin);
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

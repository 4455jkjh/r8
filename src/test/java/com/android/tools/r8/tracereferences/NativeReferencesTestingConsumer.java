// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.tracereferences;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.MethodOrigin;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.collections.Pair;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class NativeReferencesTestingConsumer implements TraceReferencesNativeReferencesConsumer {

  final List<Pair<String, MethodOrigin>> loadLibraryKnown = new ArrayList<>();
  final List<MethodOrigin> loadLibraryAny = new ArrayList<>();
  final List<Pair<String, MethodOrigin>> loadKnown = new ArrayList<>();
  final List<MethodOrigin> loadAny = new ArrayList<>();
  final List<MethodReference> nativeMethods = new ArrayList<>();
  boolean finished = false;

  @Override
  public void acceptLoadLibrary(String name, MethodOrigin origin, DiagnosticsHandler handler) {
    synchronized (loadLibraryKnown) {
      loadLibraryKnown.add(new Pair<>(name, origin));
    }
  }

  @Override
  public void acceptLoadLibraryAny(MethodOrigin origin, DiagnosticsHandler handler) {
    synchronized (loadLibraryAny) {
      loadLibraryAny.add(origin);
    }
  }

  @Override
  public void acceptLoad(String name, MethodOrigin origin, DiagnosticsHandler handler) {
    synchronized (loadKnown) {
      loadKnown.add(new Pair<>(name, origin));
    }
  }

  @Override
  public void acceptLoadAny(MethodOrigin origin, DiagnosticsHandler handler) {
    synchronized (loadAny) {
      loadAny.add(origin);
    }
  }

  @Override
  public void acceptNativeMethod(MethodReference methodReference, DiagnosticsHandler handler) {
    synchronized (nativeMethods) {
      nativeMethods.add(methodReference);
    }
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    assert !finished;
    finished = true;
  }

  public NativeReferencesTestingConsumer expectLoadLibrary(String name, Predicate<Origin> origin) {
    for (int i = 0; i < loadLibraryKnown.size(); i++) {
      Pair<String, MethodOrigin> element = loadLibraryKnown.get(i);
      if (element.getFirst().equals(name) && origin.test(element.getSecond())) {
        loadLibraryKnown.remove(i);
        return this;
      }
    }
    fail(
        "No predicate match. Content was: ["
            + loadLibraryKnown.stream()
                .map(o -> o.getFirst() + ": " + o.getSecond())
                .collect(Collectors.joining(", "))
            + "]");
    return this;
  }

  public NativeReferencesTestingConsumer expectLoadLibrary(String name, Origin origin) {
    return expectLoadLibrary(name, origin::equals);
  }

  public NativeReferencesTestingConsumer expectLoadLibraryAny(Predicate<Origin> origin) {
    for (int i = 0; i < loadLibraryAny.size(); i++) {
      if (origin.test(loadLibraryAny.get(i))) {
        loadLibraryAny.remove(i);
        return this;
      }
    }
    fail(
        "No predicate match. Content was: ["
            + loadLibraryAny.stream().map(Object::toString).collect(Collectors.joining(", "))
            + "]");
    return this;
  }

  public NativeReferencesTestingConsumer expectLoadLibraryAny(Origin origin) {
    return expectLoadLibraryAny(origin::equals);
  }

  public NativeReferencesTestingConsumer expectLoad(String name, Predicate<Origin> origin) {
    for (int i = 0; i < loadKnown.size(); i++) {
      Pair<String, MethodOrigin> element = loadKnown.get(i);
      if (element.getFirst().equals(name) && origin.test(element.getSecond())) {
        loadKnown.remove(i);
        return this;
      }
    }
    fail(
        "No predicate match. Content was: ["
            + loadKnown.stream()
                .map(o -> o.getFirst() + ": " + o.getSecond())
                .collect(Collectors.joining(", "))
            + "]");
    return this;
  }

  public NativeReferencesTestingConsumer expectLoad(String name, Origin origin) {
    return expectLoad(name, origin::equals);
  }

  public NativeReferencesTestingConsumer expectLoadAny(Predicate<Origin> origin) {
    for (int i = 0; i < loadAny.size(); i++) {
      if (origin.test(loadAny.get(i))) {
        loadAny.remove(i);
        return this;
      }
    }
    fail(
        "No predicate match. Content was: ["
            + loadAny.stream().map(Object::toString).collect(Collectors.joining(", "))
            + "]");
    return this;
  }

  public NativeReferencesTestingConsumer expectLoadAny(Origin origin) {
    return expectLoadAny(origin::equals);
  }

  public NativeReferencesTestingConsumer expectNativeMethod(MethodReference methodReference) {
    for (int i = 0; i < nativeMethods.size(); i++) {
      if (nativeMethods.get(i).equals(methodReference)) {
        nativeMethods.remove(i);
        return this;
      }
    }
    fail(
        "Expected to contain "
            + methodReference
            + ", but did not. Content was: ["
            + nativeMethods.stream().map(Object::toString).collect(Collectors.joining(", "))
            + "]");
    return this;
  }

  public void thatsAll() {
    assertTrue(
        "Expected no more load library calls traced.",
        loadLibraryKnown.isEmpty()
            && loadLibraryAny.isEmpty()
            && loadKnown.isEmpty()
            && loadAny.isEmpty()
            && finished);
  }
}

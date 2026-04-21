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
import com.android.tools.r8.utils.ObjectUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

class NativeReferencesTestingConsumer implements TraceReferencesNativeReferencesConsumer {

  private static class LibraryAndOrigin {
    private final String library;
    private final MethodOrigin origin;

    LibraryAndOrigin(String library, MethodOrigin origin) {
      this.library = library;
      this.origin = origin;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      LibraryAndOrigin that = (LibraryAndOrigin) o;
      return library.equals(that.library) && origin.equals(that.origin);
    }

    public String getLibrary() {
      return library;
    }

    public MethodOrigin getOrigin() {
      return origin;
    }

    @Override
    public int hashCode() {
      return ObjectUtils.hashLL(library, origin);
    }

    @Override
    public String toString() {
      return library + ": " + origin;
    }
  }

  final List<LibraryAndOrigin> loadLibraryKnown = new ArrayList<>();
  final List<MethodOrigin> loadLibraryAny = new ArrayList<>();
  final List<LibraryAndOrigin> loadKnown = new ArrayList<>();
  final List<MethodOrigin> loadAny = new ArrayList<>();
  final Set<MethodReference> nativeMethods = ConcurrentHashMap.newKeySet();
  private volatile boolean finished = false;

  @Override
  public void acceptLoadLibrary(String name, MethodOrigin origin, DiagnosticsHandler handler) {
    synchronized (loadLibraryKnown) {
      loadLibraryKnown.add(new LibraryAndOrigin(name, origin));
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
      loadKnown.add(new LibraryAndOrigin(name, origin));
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
    boolean added = nativeMethods.add(methodReference);
    assert added;
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    assert !finished;
    finished = true;
  }

  public NativeReferencesTestingConsumer expectLoadLibrary(String name, Predicate<Origin> origin) {
    for (LibraryAndOrigin element : loadLibraryKnown) {
      if (element.getLibrary().equals(name) && origin.test(element.getOrigin())) {
        loadLibraryKnown.remove(element);
        return this;
      }
    }
    fail(
        "No predicate match. Content was: ["
            + loadLibraryKnown.stream().map(Object::toString).collect(Collectors.joining(", "))
            + "]");
    return this;
  }

  public NativeReferencesTestingConsumer expectLoadLibrary(String name, Origin origin) {
    return expectLoadLibrary(name, origin::equals);
  }

  public NativeReferencesTestingConsumer expectLoadLibraryAny(Predicate<Origin> origin) {
    for (MethodOrigin element : loadLibraryAny) {
      if (origin.test(element)) {
        loadLibraryAny.remove(element);
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
    for (LibraryAndOrigin element : loadKnown) {
      if (element.getLibrary().equals(name) && origin.test(element.getOrigin())) {
        loadKnown.remove(element);
        return this;
      }
    }
    fail(
        "No predicate match. Content was: ["
            + loadKnown.stream().map(Object::toString).collect(Collectors.joining(", "))
            + "]");
    return this;
  }

  public NativeReferencesTestingConsumer expectLoad(String name, Origin origin) {
    return expectLoad(name, origin::equals);
  }

  public NativeReferencesTestingConsumer expectLoadAny(Predicate<Origin> origin) {
    for (MethodOrigin element : loadAny) {
      if (origin.test(element)) {
        loadAny.remove(element);
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
    for (MethodReference element : nativeMethods) {
      if (element.equals(methodReference)) {
        nativeMethods.remove(element);
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
            && nativeMethods.isEmpty()
            && finished);
  }
}

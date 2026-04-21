// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.references.PackageReference;
import com.android.tools.r8.tracereferences.TraceReferencesConsumer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class TraceReferencesInspector implements TraceReferencesConsumer {

  private final Set<TracedClass> classes = ConcurrentHashMap.newKeySet();
  private final Set<TracedField> fields = ConcurrentHashMap.newKeySet();
  private final Set<TracedMethod> methods = ConcurrentHashMap.newKeySet();
  private final Set<PackageReference> packages = ConcurrentHashMap.newKeySet();

  @Override
  public void acceptType(TracedClass tracedClass, DiagnosticsHandler handler) {
    classes.add(tracedClass);
  }

  @Override
  public void acceptField(TracedField tracedField, DiagnosticsHandler handler) {
    fields.add(tracedField);
  }

  @Override
  public void acceptMethod(TracedMethod tracedMethod, DiagnosticsHandler handler) {
    methods.add(tracedMethod);
  }

  @Override
  public void acceptPackage(PackageReference pkg, DiagnosticsHandler handler) {
    packages.add(pkg);
  }

  public Set<PackageReference> getPackages() {
    return packages;
  }
}

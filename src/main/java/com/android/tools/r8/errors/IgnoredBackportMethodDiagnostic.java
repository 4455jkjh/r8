// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.UncheckedApiLevel;

@KeepForApi
public class IgnoredBackportMethodDiagnostic implements DesugarDiagnostic {

  private final DexMember<?, ?> backport;
  private final Origin origin;
  private final Position position;
  private final UncheckedApiLevel minApiLevel;

  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public IgnoredBackportMethodDiagnostic(
      DexMember<?, ?> backport, Origin origin, Position position, int minApiLevel) {
    this(backport, origin, position, minApiLevel, 0);
  }

  public IgnoredBackportMethodDiagnostic(
      DexMember<?, ?> backport,
      Origin origin,
      Position position,
      int minApiLevelMajor,
      int minApiLevelMinor) {
    this.backport = backport;
    this.origin = origin;
    this.position = position;
    this.minApiLevel = new UncheckedApiLevel(minApiLevelMajor, minApiLevelMinor);
  }

  public MethodReference getIgnoredBackportMethod() {
    return backport.isDexMethod() ? backport.asDexMethod().asMethodReference() : null;
  }

  public FieldReference getIgnoredBackportField() {
    return backport.isDexField() ? backport.asDexField().asFieldReference() : null;
  }

  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public int getConfiguredMinApiLevel() {
    return minApiLevel.getMajor();
  }

  public int getConfiguredMinApiLevelMajor() {
    return minApiLevel.getMajor();
  }

  public int getConfiguredMinApiLevelMinor() {
    return minApiLevel.getMinor();
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  @Override
  public String getDiagnosticMessage() {
    return "Ignored reference to backport "
        + backport.toSourceString()
        + ". The compiler is compiling for min-api "
        + minApiLevel
        + " which includes runtimes that do not support "
        + backport.toSourceString()
        + " but this method will be retained as is (i.e., it is not backported).";
  }
}

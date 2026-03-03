// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.origin;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.MethodReference;

/** Origin representing a method. */
@KeepForApi
public class MethodOrigin extends Origin {

  private final MethodReference method;

  public MethodOrigin(MethodReference method, Origin parent) {
    super(parent);
    this.method = method;
  }

  @Override
  public String part() {
    return method.getMethodDescriptor();
  }

  public MethodReference getMethod() {
    return method;
  }
}

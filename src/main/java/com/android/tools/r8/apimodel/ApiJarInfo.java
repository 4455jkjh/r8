// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import java.util.Map;
import java.util.Set;

public class ApiJarInfo {

  private final Map<ClassReference, Boolean> isInterfaceMap;
  private final Map<MethodReference, Boolean> isStaticMap;
  private final Set<FieldTypelessReference> fields;

  public ApiJarInfo(
      Map<ClassReference, Boolean> isInterfaceMap,
      Map<MethodReference, Boolean> isStaticMap,
      Set<FieldTypelessReference> fields) {
    this.isInterfaceMap = isInterfaceMap;
    this.isStaticMap = isStaticMap;
    this.fields = fields;
  }

  public boolean isClassDefined(ClassReference ref) {
    return isInterfaceMap.containsKey(ref);
  }

  /** {@link #isClassDefined} must be checked first. */
  public boolean isInterface(ClassReference ref) {
    assert isInterfaceMap.containsKey(ref);
    return isInterfaceMap.get(ref);
  }

  public boolean isMethodDefined(MethodReference ref) {
    return isStaticMap.containsKey(ref);
  }

  /** {@link #isMethodDefined} must be checked first. */
  public boolean isStatic(MethodReference ref) {
    assert isStaticMap.containsKey(ref);
    return isStaticMap.get(ref);
  }

  public boolean isFieldDefined(FieldTypelessReference ref) {
    return fields.contains(ref);
  }
}

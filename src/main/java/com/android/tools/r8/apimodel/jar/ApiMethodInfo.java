// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel.jar;

import java.util.Objects;

public class ApiMethodInfo {
  public final String name;
  public final String desc;
  public final boolean isStatic;

  public ApiMethodInfo(String name, String desc, boolean isStatic) {
    this.name = name;
    this.desc = desc;
    this.isStatic = isStatic;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof ApiMethodInfo)) {
      return false;
    }
    ApiMethodInfo methodSig = (ApiMethodInfo) o;
    return name.equals(methodSig.name)
        && desc.equals(methodSig.desc)
        && isStatic == methodSig.isStatic;
  }

  @Override
  public int hashCode() {
    return Objects.hash(name, desc, isStatic);
  }
}

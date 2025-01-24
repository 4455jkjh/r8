// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.graph.DexProgramClass;
import java.util.Collection;

public class R8PartialD8Result {

  private final Collection<DexProgramClass> dexedClasses;
  private final Collection<DexProgramClass> desugaredClasses;

  public R8PartialD8Result(
      Collection<DexProgramClass> dexedClasses, Collection<DexProgramClass> desugaredClasses) {
    this.dexedClasses = dexedClasses;
    this.desugaredClasses = desugaredClasses;
  }

  public Collection<DexProgramClass> getDexedClasses() {
    return dexedClasses;
  }

  public Collection<DexProgramClass> getDesugaredClasses() {
    return desugaredClasses;
  }
}

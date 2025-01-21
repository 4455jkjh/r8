// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial;

import com.android.tools.r8.graph.DexProgramClass;
import java.util.Collection;

public class R8PartialD8DexResult {

  private final Collection<DexProgramClass> outputClasses;

  public R8PartialD8DexResult(Collection<DexProgramClass> outputClasses) {
    this.outputClasses = outputClasses;
  }

  public Collection<DexProgramClass> getOutputClasses() {
    return outputClasses;
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.smallmethodinliner;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Sets;
import java.util.Set;

public class SmallMethodInlinerResult {

  public Set<DexType> classesWithFullyInlinedInstanceInitializers;

  public SmallMethodInlinerResult() {
    this(Sets.newIdentityHashSet());
  }

  public SmallMethodInlinerResult(Set<DexType> classesWithFullyInlinedInstanceInitializers) {
    this.classesWithFullyInlinedInstanceInitializers = classesWithFullyInlinedInstanceInitializers;
  }

  public boolean hasFullyInlinedInstanceInitializers() {
    return !classesWithFullyInlinedInstanceInitializers.isEmpty();
  }

  public boolean hasFullyInlinedInstanceInitializers(DexProgramClass clazz) {
    return classesWithFullyInlinedInstanceInitializers.contains(clazz.getType());
  }

  public SmallMethodInlinerResult rewrittenWithLens(GraphLens lens, GraphLens appliedLens) {
    return new SmallMethodInlinerResult(
        SetUtils.mapIdentityHashSet(
            classesWithFullyInlinedInstanceInitializers,
            type -> lens.lookupClassType(type, appliedLens)));
  }
}

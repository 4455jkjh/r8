// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging.policies;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.verticalclassmerging.VerticalMergeGroup;

public class WellformedHierarchyPolicy extends VerticalClassMergerPolicy {

  @Override
  public boolean canMerge(VerticalMergeGroup group) {
    DexProgramClass source = group.getSource();
    DexProgramClass target = group.getTarget();
    // Don't merge classes into interfaces.
    if (target.isInterface() && !source.isInterface()) {
      return false;
    }
    // Don't merge interfaces into classes when the subclass extends the interface.
    if (!target.isInterface()
        && target.hasSuperType()
        && target.getSuperType().isIdenticalTo(source.getType())
        && source.isInterface()) {
      return false;
    }
    // Don't merge classes into classes when the subclass implements the class.
    if (!target.isInterface()
        && !source.isInterface()
        && target.getInterfaces().contains(source.getType())) {
      return false;
    }
    return true;
  }

  @Override
  public String getName() {
    return "WellformedHierarchyPolicy";
  }
}

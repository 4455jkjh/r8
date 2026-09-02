// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.WorkList;
import java.util.Set;

public class DestructivePhiTypeUpdater {

  public static void recomputePhiTypes(
      AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code, Set<Phi> affectedPhis) {
    if (affectedPhis.isEmpty()) {
      return;
    }

    // We have updated at least one type lattice element which can cause phis to narrow to a more
    // precise type. Because cycles in phis can occur, we have to reset all phis before computing
    // the new types.
    WorkList<Value> worklist = WorkList.newIdentityWorkList(affectedPhis);
    worklist.process(
        value -> {
          value.setType(TypeElement.getBottom());
          worklist.addIfNotSeen(value.uniqueUsers(Instruction::isAssume), Instruction::outValue);
          worklist.addIfNotSeen(value.uniquePhiUsers());
        });

    // Now that the types of all transitively type affected phis have been reset, we can
    // perform a narrowing, starting from the values that are affected by those phis.
    AffectedValues affectedValues = new AffectedValues(affectedPhis.size());
    affectedValues.addAll(affectedPhis);
    affectedValues.propagateWithAssumeRemoval(appView, code, typeAnalysis -> {});
  }
}

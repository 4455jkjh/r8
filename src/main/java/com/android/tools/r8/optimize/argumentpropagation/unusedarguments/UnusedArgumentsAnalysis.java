// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.unusedarguments;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.utils.timing.Timing;
import java.util.BitSet;

public class UnusedArgumentsAnalysis {

  public static void computeUnusedArguments(ProgramMethod method, IRCode code) {
    computeUnusedArguments(method, code, getSimpleFeedback(), Timing.empty());
  }

  public static void computeUnusedArguments(
      ProgramMethod method, IRCode code, OptimizationFeedback feedback, Timing timing) {
    timing.begin("Compute unused arguments");
    internalComputeUnusedArguments(method, code, feedback);
    timing.end();
  }

  public static void internalComputeUnusedArguments(
      ProgramMethod method, IRCode code, OptimizationFeedback feedback) {
    BitSet unusedArguments = new BitSet(method.getDefinition().getNumberOfArguments());
    InstructionIterator instructionIterator = code.entryBlock().iterator();
    Argument argument = instructionIterator.next().asArgument();
    while (argument != null) {
      Value argumentValue = argument.outValue();
      if (!argumentValue.hasAnyUsers()
          || (!argumentValue.hasPhiUsers()
              && !argumentValue.hasDebugUsers()
              && argumentValue.hasSingleUniqueUser()
              && argumentValue.singleUniqueUser().isAssume()
              && !argumentValue.singleUniqueUser().outValue().hasAnyUsers())) {
        unusedArguments.set(argument.getIndex());
      }
      argument = instructionIterator.next().asArgument();
    }
    if (!unusedArguments.isEmpty()) {
      feedback.setUnusedArguments(method, unusedArguments);
    }
  }
}

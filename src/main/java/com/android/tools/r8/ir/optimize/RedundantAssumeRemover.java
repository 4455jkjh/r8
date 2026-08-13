// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.AssumeIntRange;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Consumer;

/**
 * When we have Assume instructions we generally verify that the Assume instructions contribute with
 * non-trivial information to the IR (e.g., the dynamic type should be more precise than the static
 * type).
 *
 * <p>Therefore, when this property may no longer hold for an Assume instruction, we need to remove
 * it.
 *
 * <p>This class is a helper class to remove these instructions. Unlike {@link
 * com.android.tools.r8.ir.conversion.passes.AssumeRemover} this class does not unconditionally
 * remove all Assume instructions.
 */
public class RedundantAssumeRemover {

  private final AppView<?> appView;
  private final IRCode code;

  private final Set<Instruction> affectedAssumeInstructions = Sets.newIdentityHashSet();

  public RedundantAssumeRemover(AppView<?> appView, IRCode code) {
    this.appView = appView;
    this.code = code;
  }

  public void addAffectedAssumeInstruction(Instruction assumeInstruction) {
    affectedAssumeInstructions.add(assumeInstruction);
  }

  public boolean hasAffectedAssumeInstructions() {
    return !affectedAssumeInstructions.isEmpty();
  }

  private boolean removeAssumeInstructionIfRedundant(
      Assume assumeInstruction,
      InstructionListIterator instructionIterator,
      Set<Value> newAffectedValues,
      Consumer<Instruction> redundantAssumeConsumer) {
    if (!affectedAssumeInstructions.remove(assumeInstruction)) {
      return false;
    }
    if (assumeInstruction.src().isConstant()) {
      removeRedundantAssumeInstruction(
          assumeInstruction, instructionIterator, newAffectedValues, redundantAssumeConsumer);
      return true;
    }
    if (assumeInstruction.hasDynamicTypeIgnoringNullability()
        && assumeInstruction
            .getDynamicType()
            .asDynamicTypeWithUpperBound()
            .getDynamicUpperBoundType()
            .strictlyLessThan(assumeInstruction.src().getType(), appView)) {
      assert assumeInstruction
          .getDynamicType()
          .getNullability()
          .lessThanOrEqual(assumeInstruction.src().getType().nullability());
      return false;
    }
    if (assumeInstruction.hasNonNullAssumption()
        && !assumeInstruction.src().isConstant()
        && assumeInstruction.src().getType().isNullable()
        && !assumeInstruction.src().getType().isDefinitelyNull()) {
      assumeInstruction.clearDynamicTypeAssumption();
      return false;
    }
    removeRedundantAssumeInstruction(
        assumeInstruction, instructionIterator, newAffectedValues, redundantAssumeConsumer);
    return true;
  }

  private void removeRedundantAssumeInstruction(
      Instruction assumeInstruction,
      InstructionListIterator instructionIterator,
      Set<Value> newAffectedValues,
      Consumer<Instruction> redundantAssumeConsumer) {
    Value inValue = assumeInstruction.getFirstOperand();
    Value outValue = assumeInstruction.outValue();
    if (outValue == null) {
      // Already removed.
      return;
    }

    // Check if we need to run the type analysis for the affected values of the out-value.
    outValue.replaceUsers(inValue, newAffectedValues);
    redundantAssumeConsumer.accept(assumeInstruction);
    instructionIterator.removeOrReplaceByDebugLocalRead();
  }

  private boolean removeAssumeIntRangeInstructionIfRedundant(
      AssumeIntRange assumeInstruction,
      InstructionListIterator instructionIterator,
      Set<Value> newAffectedValues,
      Consumer<Instruction> redundantAssumeConsumer) {
    if (!affectedAssumeInstructions.remove(assumeInstruction)) {
      return false;
    }
    if (assumeInstruction.getFirstOperand().isConstant()) {
      removeRedundantAssumeInstruction(
          assumeInstruction, instructionIterator, newAffectedValues, redundantAssumeConsumer);
      return true;
    }
    return false;
  }

  public boolean removeRedundantAssumeInstructions(
      Set<Value> newAffectedValues, Consumer<Instruction> redundantAssumeConsumer) {
    if (affectedAssumeInstructions.isEmpty()) {
      return false;
    }
    boolean changed = false;
    for (BasicBlock block : code.getBlocks()) {
      InstructionListIterator instructionIterator = block.listIterator();
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (instruction.isAssume()) {
          changed |=
              removeAssumeInstructionIfRedundant(
                  instruction.asAssume(),
                  instructionIterator,
                  newAffectedValues,
                  redundantAssumeConsumer);
        } else if (instruction.isAssumeIntRange()) {
          changed |=
              removeAssumeIntRangeInstructionIfRedundant(
                  instruction.asAssumeIntRange(),
                  instructionIterator,
                  newAffectedValues,
                  redundantAssumeConsumer);
        }
      }
    }
    affectedAssumeInstructions.clear();
    return changed;
  }
}

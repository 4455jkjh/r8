// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.errors.Unreachable;
import java.util.ListIterator;

public interface InstructionIterator
    extends ListIterator<Instruction>, NextUntilIterator<Instruction> {

  default InstructionIterator recordChangesToMetadata(IRCode code) {
    return recordChangesToMetadata(code.metadata());
  }

  default InstructionIterator recordChangesToMetadata(IRMetadata metadata) {
    throw new Unreachable(
        "Method recordChangesToMetadata(IRMetadata) not implemented for "
            + getClass().getTypeName());
  }
  /**
   * Replace the current instruction (aka the {@link Instruction} returned by the previous call to
   * {@link #next} with the passed in <code>newInstruction</code>.
   * <p>
   * The current instruction will be completely detached from the instruction stream with uses
   * of its in-values removed.
   * <p>
   * If the current instruction produces an out-value the new instruction must also produce
   * an out-value, and all uses of the current instructions out-value will be replaced by the
   * new instructions out-value.
   * <p>
   * The debug information of the current instruction will be attached to the new instruction.
   *
   * @param newInstruction the instruction to insert instead of the current.
   */
  void replaceCurrentInstruction(Instruction newInstruction);


  /**
   * Safe removal function that will insert a DebugLocalRead to take over the debug values if any
   * are associated with the current instruction.
   */
  void removeOrReplaceByDebugLocalRead();
}

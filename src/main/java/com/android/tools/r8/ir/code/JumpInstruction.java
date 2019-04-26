// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import java.util.List;

public abstract class JumpInstruction extends Instruction {

  public JumpInstruction(Value out) {
    super(null);
  }

  public JumpInstruction(Value out, Value in) {
    super(out, in);
  }

  public JumpInstruction(Value out, List<? extends Value> ins) {
    super(out, ins);
  }

  public BasicBlock fallthroughBlock() {
    return null;
  }

  public void setFallthroughBlock(BasicBlock block) {
    assert false : "We should not change the fallthrough of a JumpInstruction with no fallthrough.";
  }

  @Override
  public boolean canBeDeadCode(AppView<?> appView, IRCode code) {
    return false;
  }

  @Override
  public boolean isJumpInstruction() {
    return true;
  }

  @Override
  public JumpInstruction asJumpInstruction() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forJumpInstruction();
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }
}

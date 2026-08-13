// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;

public class AssumeIntRange extends Instruction {

  private static final String ERROR_MESSAGE =
      "Expected AssumeIntRange instructions to be removed after IR processing.";

  private final int minInclusive;
  private final int maxInclusive;

  public AssumeIntRange(Value dest, Value src, int minInclusive, int maxInclusive) {
    super(dest, src);
    this.minInclusive = minInclusive;
    this.maxInclusive = maxInclusive;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public String getInstructionName() {
    return "AssumeIntRange";
  }

  public int getMinInclusive() {
    return minInclusive;
  }

  public int getMaxInclusive() {
    return maxInclusive;
  }

  @Override
  public boolean isAssumeIntRange() {
    return true;
  }

  @Override
  public AssumeIntRange asAssumeIntRange() {
    return this;
  }

  @Override
  public int opcode() {
    return Opcodes.ASSUME_INT_RANGE;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addAssumeIntRange(getFirstOperand(), minInclusive, maxInclusive);
  }

  @Override
  public int maxInValueRegister() {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public int maxOutValueRegister() {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public boolean isOutConstant() {
    return false;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (this == other) {
      return true;
    }
    if (!other.isAssumeIntRange()) {
      return false;
    }
    AssumeIntRange assumeInstruction = other.asAssumeIntRange();
    return minInclusive == assumeInstruction.minInclusive
        && maxInclusive == assumeInstruction.maxInclusive;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    return inliningConstraints.forAssumeIntRange();
  }

  @Override
  public TypeElement evaluate(AppView<?> appView) {
    return TypeElement.getInt();
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public void insertLoadAndStores(LoadStoreHelper helper) {
    throw new Unreachable(ERROR_MESSAGE);
  }

  @Override
  public boolean instructionMayTriggerMethodInvocation(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public boolean verifyTypes(
      AppView<?> appView, ProgramMethod context, VerifyTypesHelper verifyTypesHelper) {
    assert !getFirstOperand().isConstant();
    return true;
  }

  @Override
  public String toString() {
    // `origin` could become obsolete:
    //   1) during branch simplification, the origin `if` could be simplified, which means the
    //     assumption became "truth."
    //   2) invoke-interface could be devirtualized, while its dynamic type and/or non-null receiver
    //     are still valid.
    return super.toString() + "; [" + minInclusive + "; " + maxInclusive + "]";
  }
}

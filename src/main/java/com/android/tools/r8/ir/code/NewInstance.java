// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.AnalysisAssumption;
import com.android.tools.r8.ir.analysis.ClassInitializationAnalysis.Query;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;

public class NewInstance extends Instruction {

  public final DexType clazz;
  private boolean allowSpilling = true;

  public NewInstance(DexType clazz, Value dest) {
    super(dest);
    assert clazz != null;
    this.clazz = clazz;
  }

  public Value dest() {
    return outValue;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(dest(), getNumber());
    builder.add(this, new com.android.tools.r8.code.NewInstance(dest, clazz));
  }

  @Override
  public String toString() {
    return super.toString() + " " + clazz;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isNewInstance() && other.asNewInstance().clazz == clazz;
  }

  @Override
  public int maxInValueRegister() {
    assert false : "NewInstance has no register arguments";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public boolean instructionTypeCanThrow() {
    // Creating a new instance can throw if the type is not found, or on out-of-memory.
    return true;
  }

  @Override
  public boolean isNewInstance() {
    return true;
  }

  @Override
  public NewInstance asNewInstance() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forNewInstance(clazz, invocationContext);
  }

  @Override
  public boolean hasInvariantOutType() {
    return true;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfNew(clazz));
  }

  @Override
  public DexType computeVerificationType(
      AppView<? extends AppInfo> appView, TypeVerificationHelper helper) {
    return clazz;
  }

  @Override
  public TypeLatticeElement evaluate(AppView<? extends AppInfo> appView) {
    return TypeLatticeElement.fromDexType(clazz, Nullability.definitelyNotNull(), appView);
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      AppView<? extends AppInfo> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forNewInstance(
        this, clazz, appView, mode, assumption);
  }

  public void markNoSpilling() {
    allowSpilling = false;
  }

  public boolean isSpillingAllowed() {
    return allowSpilling;
  }
}

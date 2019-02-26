// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfDexItemBasedConstString;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.ReflectionOptimizer.ClassNameComputationInfo;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;

public class DexItemBasedConstString extends ConstInstruction {

  private final DexReference item;
  private final ClassNameComputationInfo classNameComputationInfo;
  private final ThrowingInfo throwingInfo;

  public DexItemBasedConstString(Value dest, DexReference item, ThrowingInfo throwingInfo) {
    this(dest, item, throwingInfo, ClassNameComputationInfo.none());
  }

  public DexItemBasedConstString(
      Value dest,
      DexReference item,
      ThrowingInfo throwingInfo,
      ClassNameComputationInfo classNameComputationInfo) {
    super(dest);
    this.item = item;
    this.classNameComputationInfo = classNameComputationInfo;
    this.throwingInfo = throwingInfo;
  }

  public static DexItemBasedConstString copyOf(Value newValue, DexItemBasedConstString original) {
    return new DexItemBasedConstString(
        newValue, original.getItem(), original.throwingInfo, original.classNameComputationInfo);
  }

  public DexReference getItem() {
    return item;
  }

  public ClassNameComputationInfo getClassNameComputationInfo() {
    return classNameComputationInfo;
  }

  @Override
  public boolean isDexItemBasedConstString() {
    return true;
  }

  @Override
  public DexItemBasedConstString asDexItemBasedConstString() {
    return this;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int dest = builder.allocatedRegister(outValue(), getNumber());
    builder.add(
        this,
        new com.android.tools.r8.code.DexItemBasedConstString(
            dest, item, classNameComputationInfo));
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isDexItemBasedConstString() && other.asDexItemBasedConstString().item == item;
  }

  @Override
  public int maxInValueRegister() {
    assert false : "DexItemBasedConstString has no register arguments.";
    return 0;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public String toString() {
    return super.toString() + " \"" + item.toSourceString() + "\"";
  }

  @Override
  public boolean instructionTypeCanThrow() {
    return throwingInfo == ThrowingInfo.CAN_THROW;
  }

  @Override
  public boolean isOutConstant() {
    return true;
  }

  @Override
  public boolean instructionInstanceCanThrow() {
    // A const-string instruction can usually throw an exception if the decoding of the string
    // fails. Since this string corresponds to a type or member name, though, decoding cannot fail.
    return false;
  }

  @Override
  public boolean canBeDeadCode(
      AppView<? extends AppInfoWithLiveness> appView, AppInfo appInfo, IRCode code) {
    // No side-effect, such as throwing an exception, in CF.
    return true;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfDexItemBasedConstString(item, classNameComputationInfo));
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return helper.getFactory().stringType;
  }

  @Override
  public TypeLatticeElement evaluate(AppInfo appInfo) {
    return TypeLatticeElement.stringClassType(appInfo, Nullability.definitelyNotNull());
  }
}

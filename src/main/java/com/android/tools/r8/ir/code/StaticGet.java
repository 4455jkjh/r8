// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import static com.android.tools.r8.optimize.MemberRebindingAnalysis.isVisibleFromOriginalContext;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.TypeVerificationHelper;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.code.Sget;
import com.android.tools.r8.code.SgetBoolean;
import com.android.tools.r8.code.SgetByte;
import com.android.tools.r8.code.SgetChar;
import com.android.tools.r8.code.SgetObject;
import com.android.tools.r8.code.SgetShort;
import com.android.tools.r8.code.SgetWide;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
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
import org.objectweb.asm.Opcodes;

public class StaticGet extends FieldInstruction {

  public StaticGet(Value dest, DexField field) {
    super(field, dest, (Value) null);
  }

  public Value dest() {
    return outValue;
  }

  @Override
  public boolean couldIntroduceAnAlias() {
    return true;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    com.android.tools.r8.code.Instruction instruction;
    int dest = builder.allocatedRegister(dest(), getNumber());
    DexField field = getField();
    switch (getType()) {
      case INT:
      case FLOAT:
        instruction = new Sget(dest, field);
        break;
      case LONG:
      case DOUBLE:
        instruction = new SgetWide(dest, field);
        break;
      case OBJECT:
        instruction = new SgetObject(dest, field);
        break;
      case BOOLEAN:
        instruction = new SgetBoolean(dest, field);
        break;
      case BYTE:
        instruction = new SgetByte(dest, field);
        break;
      case CHAR:
        instruction = new SgetChar(dest, field);
        break;
      case SHORT:
        instruction = new SgetShort(dest, field);
        break;
      case INT_OR_FLOAT:
      case LONG_OR_DOUBLE:
        throw new Unreachable("Unexpected imprecise type: " + getType());
      default:
        throw new Unreachable("Unexpected type: " + getType());
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean instructionTypeCanThrow() {
    // This can cause <clinit> to run.
    return true;
  }

  @Override
  public boolean canBeDeadCode(AppInfo appInfo, IRCode code) {
    // static-get can be dead as long as it cannot have any of the following:
    // * NoSuchFieldError (resolution failure)
    // * IllegalAccessError (not visible from the access context)
    // * side-effects in <clinit>
    // TODO(b/123857022): Should be possible to use definitionFor().
    DexEncodedField resolvedField = appInfo.resolveFieldOn(getField().getHolder(), getField());
    if (resolvedField == null) {
      return false;
    }
    if (code == null
        || !isVisibleFromOriginalContext(appInfo, code.method.method.getHolder(), resolvedField)) {
      return false;
    }
    return appInfo.hasSubtyping()
        && !getField().getHolder().classInitializationMayHaveSideEffects(appInfo);
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isStaticGet()) {
      return false;
    }
    StaticGet o = other.asStaticGet();
    return o.getField() == getField() && o.getType() == getType();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, DexType invocationContext) {
    return inliningConstraints.forStaticGet(getField(), invocationContext);
  }

  @Override
  public String toString() {
    return super.toString() + "; field: " + getField().toSourceString();
  }

  @Override
  public boolean isStaticGet() {
    return true;
  }

  @Override
  public StaticGet asStaticGet() {
    return this;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    helper.storeOutValue(this, it);
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(
        new CfFieldInstruction(Opcodes.GETSTATIC, getField(), builder.resolveField(getField())));
  }

  @Override
  public DexType computeVerificationType(TypeVerificationHelper helper) {
    return getField().type;
  }

  @Override
  public TypeLatticeElement evaluate(AppInfo appInfo) {
    return TypeLatticeElement.fromDexType(getField().type, Nullability.maybeNull(), appInfo);
  }

  @Override
  public boolean definitelyTriggersClassInitialization(
      DexType clazz,
      AppView<? extends AppInfoWithSubtyping> appView,
      Query mode,
      AnalysisAssumption assumption) {
    return ClassInitializationAnalysis.InstructionUtils.forStaticGet(
        this, clazz, appView, mode, assumption);
  }
}

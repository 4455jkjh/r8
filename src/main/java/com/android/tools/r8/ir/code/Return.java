// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexReturn;
import com.android.tools.r8.dex.code.DexReturnObject;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.dex.code.DexReturnWide;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.TypeChecker;
import com.android.tools.r8.ir.analysis.VerifyTypesHelper;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import java.util.List;

public class Return extends JumpInstruction {

  public Return() {
    super();
  }

  public Return(Value value) {
    super(value);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public int opcode() {
    return Opcodes.RETURN;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  public boolean isReturnVoid() {
    return inValues.size() == 0;
  }

  public TypeElement getReturnType() {
    assert !isReturnVoid();
    return returnValue().getType();
  }

  public boolean hasReturnValue() {
    return !isReturnVoid();
  }

  public Value getReturnValueOrDefault(Value defaultValue) {
    return hasReturnValue() ? returnValue() : defaultValue;
  }

  public Value getReturnValueOrNull() {
    return getReturnValueOrDefault(null);
  }

  public Value returnValue() {
    assert !isReturnVoid();
    return inValues.get(0);
  }

  public DexInstruction createDexInstruction(DexBuilder builder) {
    if (isReturnVoid()) {
      return new DexReturnVoid();
    }
    int register = builder.allocatedRegister(returnValue(), getNumber());
    TypeElement returnType = getReturnType();
    if (returnType.isReferenceType()) {
      return new DexReturnObject(register);
    }
    if (returnType.isSinglePrimitive()) {
      return new DexReturn(register);
    }
    if (returnType.isWidePrimitive()) {
      return new DexReturnWide(register);
    }
    throw new Unreachable();
  }

  @Override
  public void buildDex(DexBuilder builder) {
    builder.add(this, createDexInstruction(builder));
  }

  @Override
  public boolean identicalAfterRegisterAllocation(
      Instruction other, RegisterAllocator allocator, MethodConversionOptions conversionOptions) {
    if (!super.identicalAfterRegisterAllocation(other, allocator, conversionOptions)) {
      return false;
    }
    if (isReturnVoid()) {
      return true;
    }
    Value otherReturnValue = other.asReturn().returnValue();
    return (!shouldOnlyMergeIdenticalReturnValues(allocator.options(), allocator.getProgramMethod())
            || identicalArrayValuesAfterRegisterAllocation(
                returnValue(), otherReturnValue, allocator))
        && identicalClassValuesAfterRegisterAllocation(returnValue(), otherReturnValue, allocator);
  }

  /**
   * Returns true if the return values {@code a} and {@code b} may be merged when sharing two
   * returns.
   *
   * <p>If one of the values is not a subtype of the method return type, for example because it is
   * based on a missing class, then the two values must be the exact same value. Otherwise, the join
   * of the merged return values, which is java.lang.Object for missing classes, may not type check
   * against the method return type.
   *
   * <p>Note that it is not sufficient to check that the join of {@code a} and {@code b} is
   * assignable to the return type, since that is not transitive when one of the values is null: for
   * a method returning Base and a missing class C, the joins of null and C, and of null and Base,
   * are assignable to Base, but the join of C and Base is java.lang.Object. The relation must be
   * transitive as it is used as an {@link com.google.common.base.Equivalence} when deduplicating
   * blocks in the IR finalizer.
   */
  @SuppressWarnings("ReferenceEquality")
  private static boolean identicalClassValuesAfterRegisterAllocation(
      Value a, Value b, RegisterAllocator allocator) {
    if (a == b) {
      return true;
    }
    AppView<?> appView = allocator.getAppView();
    ProgramMethod method = allocator.getProgramMethod();
    if (!appView.enableWholeProgramOptimizations() || !method.getReturnType().isClassType()) {
      return true;
    }
    TypeElement returnType =
        TypeElement.fromDexType(method.getReturnType(), Nullability.maybeNull(), appView);
    TypeChecker typeChecker =
        new TypeChecker(appView.withClassHierarchy(), VerifyTypesHelper.create(appView));
    return typeChecker.isAssignableToReturnType(
            a.getType().join(returnType, appView), method.getDefinition())
        && typeChecker.isAssignableToReturnType(
            b.getType().join(returnType, appView), method.getDefinition());
  }

  /**
   * Returns true if returns of distinct values must not be merged in the given method.
   *
   * <p>When ART may compute an incorrect join for arrays of interfaces, merging distinct array
   * return values can lead to verification errors.
   *
   * <p>When the return values are based on missing classes, their join is java.lang.Object, which
   * may not type check against the method return type.
   */
  public static boolean shouldOnlyMergeIdenticalReturnValues(
      AppView<?> appView, ProgramMethod method, List<BasicBlock> normalExits) {
    return shouldOnlyMergeIdenticalReturnValues(appView.options(), method)
        || !isJoinOfReturnValuesAssignableToReturnType(appView, method, normalExits);
  }

  private static boolean shouldOnlyMergeIdenticalReturnValues(
      InternalOptions options, ProgramMethod method) {
    return options.canHaveIncorrectJoinForArrayOfInterfacesBug()
        && method.getReturnType().isArrayType();
  }

  private static boolean isJoinOfReturnValuesAssignableToReturnType(
      AppView<?> appView, ProgramMethod method, List<BasicBlock> normalExits) {
    if (normalExits.size() < 2
        || !appView.enableWholeProgramOptimizations()
        || !method.getReturnType().isClassType()) {
      return true;
    }
    TypeElement joinType = TypeElement.getBottom();
    for (BasicBlock exitBlock : normalExits) {
      joinType = joinType.join(exitBlock.exit().asReturn().returnValue().getType(), appView);
    }
    return new TypeChecker(appView.withClassHierarchy(), VerifyTypesHelper.create(appView))
        .isAssignableToReturnType(joinType, method.getDefinition());
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    if (!other.isReturn()) {
      return false;
    }
    Return o = other.asReturn();
    if (isReturnVoid()) {
      return o.isReturnVoid();
    }
    return getReturnType().isValueTypeCompatible(o.getReturnType());
  }

  @Override
  public int maxInValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public int maxOutValueRegister() {
    assert false : "Return defines no values.";
    return 0;
  }

  @Override
  public boolean isReturn() {
    return true;
  }

  @Override
  public Return asReturn() {
    return this;
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, ProgramMethod context) {
    if (hasReturnValue()) {
      return inliningConstraints.forReturn(returnValue().getType(), context);
    }
    return inliningConstraints.forReturnVoid();
  }

  @Override
  public void insertLoadAndStores(LoadStoreHelper helper) {
    if (!isReturnVoid()) {
      helper.loadInValues(this);
    }
  }

  @Override
  public boolean isAllowedAfterThrowingInstruction() {
    return true;
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(
        isReturnVoid() ? new CfReturnVoid() : new CfReturn(ValueType.fromType(getReturnType())),
        this);
  }

  public static class Builder extends BuilderBase<Builder, Return> {

    private Value returnValue = null;

    public Builder setReturnValue(Value returnValue) {
      this.returnValue = returnValue;
      return self();
    }

    @Override
    public Return build() {
      return amend(returnValue == null ? new Return() : new Return(returnValue));
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    protected boolean verifyInstructionTypeCannotThrow() {
      return true;
    }
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    if (hasReturnValue()) {
      builder.addReturn(returnValue());
    } else {
      builder.addReturnVoid();
    }
  }
}

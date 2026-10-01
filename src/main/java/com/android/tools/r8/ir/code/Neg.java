// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfNeg;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexNegDouble;
import com.android.tools.r8.dex.code.DexNegFloat;
import com.android.tools.r8.dex.code.DexNegInt;
import com.android.tools.r8.dex.code.DexNegLong;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.internal.LongUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;

public class Neg extends Unop {

  public final NumericType type;

  public Neg(NumericType type, Value dest, Value source) {
    super(dest, source);
    this.type = type;
  }

  @Override
  public int opcode() {
    return Opcodes.NEG;
  }

  @Override
  public <T> T accept(InstructionVisitor<T> visitor) {
    return visitor.visit(this);
  }

  @Override
  public boolean canBeFolded() {
    return (type == NumericType.INT
            || type == NumericType.LONG
            || type == NumericType.FLOAT
            || type == NumericType.DOUBLE)
        && source().isConstant();
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return other.isNeg() && other.asNeg().type == type;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    DexInstruction instruction;
    int dest = builder.allocatedRegister(dest(), getNumber());
    int src = builder.allocatedRegister(source(), getNumber());
    switch (type) {
      case INT:
        instruction = new DexNegInt(dest, src);
        break;
      case LONG:
        instruction = new DexNegLong(dest, src);
        break;
      case FLOAT:
        instruction = new DexNegFloat(dest, src);
        break;
      case DOUBLE:
        instruction = new DexNegDouble(dest, src);
        break;
      default:
        throw new Unreachable("Unexpected type " + type);
    }
    builder.add(this, instruction);
  }

  @Override
  public boolean isNeg() {
    return true;
  }

  @Override
  public Neg asNeg() {
    return this;
  }

  @Override
  public AbstractValue getAbstractValue(
      AppView<?> appView, ProgramMethod context, AbstractValueSupplier abstractValueSupplier) {
    if (outValue.hasLocalInfo()) {
      return AbstractValue.unknown();
    }
    AbstractValue sourceLattice =
        abstractValueSupplier.getAbstractValue(source(), appView, context);
    if (sourceLattice.isSingleNumberValue()) {
      SingleNumberValue sourceConst = sourceLattice.asSingleNumberValue();
      long newConst;
      if (type == NumericType.INT) {
        newConst = -sourceConst.getIntValue();
      } else if (type == NumericType.LONG) {
        newConst = -sourceConst.getLongValue();
      } else if (type == NumericType.FLOAT) {
        newConst = LongUtils.encodeFloat(-sourceConst.getFloatValue());
      } else {
        assert type == NumericType.DOUBLE;
        newConst = LongUtils.encodeDouble(-sourceConst.getDoubleValue());
      }
      return appView.abstractValueFactory().createSingleNumberValue(newConst, getOutType());
    }
    if (type == NumericType.INT && sourceLattice.hasDefinitelySetAndUnsetBitsInformation()) {
      // Although the exact value of the source is unknown, we may still be able to determine the
      // sign of the result from the known bits of the source, since negation flips the sign of all
      // int values except 0 and Integer.MIN_VALUE (which both negate to themselves).
      //
      // If the sign bit of the source is definitely unset, and at least one other bit is definitely
      // set, then the source is strictly positive, and therefore the result is strictly negative,
      // i.e., the sign bit of the result is set.
      if ((sourceLattice.getDefinitelyUnsetIntBits() & Integer.MIN_VALUE) != 0
          && sourceLattice.getDefinitelySetIntBits() != 0) {
        return appView
            .abstractValueFactory()
            .createDefiniteBitsIntNumberValue(Integer.MIN_VALUE, 0);
      }
      // If the sign bit of the source is definitely set, and at least one other bit is definitely
      // set, then the source is strictly negative and different from Integer.MIN_VALUE, and
      // therefore the result is strictly positive, i.e., the sign bit of the result is unset.
      if ((sourceLattice.getDefinitelySetIntBits() & Integer.MIN_VALUE) != 0
          && (sourceLattice.getDefinitelySetIntBits() & ~Integer.MIN_VALUE) != 0) {
        return appView
            .abstractValueFactory()
            .createDefiniteBitsIntNumberValue(0, Integer.MIN_VALUE);
      }
    }
    return AbstractValue.unknown();
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfNeg(type), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addNeg(type, source());
  }
}

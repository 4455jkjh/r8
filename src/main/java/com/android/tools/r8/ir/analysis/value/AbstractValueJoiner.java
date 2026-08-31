// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import static com.android.tools.r8.ir.analysis.value.AbstractValue.unknown;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;

public class AbstractValueJoiner {

  protected final AppView<?> appView;
  protected final AbstractValueJoinerConfig config;

  public AbstractValueJoiner(AppView<?> appView, AbstractValueJoinerConfig config) {
    this.appView = appView;
    this.config = config;
  }

  private AbstractValueFactory factory() {
    return appView.abstractValueFactory();
  }

  public AbstractValue join(
      AbstractValue abstractValue, AbstractValue otherAbstractValue, ProgramField field) {
    return join(abstractValue, otherAbstractValue, field.getType().toTypeElement(appView));
  }

  public AbstractValue join(
      AbstractValue abstractValue, AbstractValue otherAbstractValue, TypeElement type) {
    AbstractValue result = internalJoin(abstractValue, otherAbstractValue, type);
    assert result.equals(internalJoin(otherAbstractValue, abstractValue, type));
    return result;
  }

  public boolean lessThanOrEqualTo(
      AbstractValue abstractValue, AbstractValue otherAbstractValue, TypeElement type) {
    return join(abstractValue, otherAbstractValue, type).equals(otherAbstractValue);
  }

  final AbstractValue internalJoin(
      AbstractValue abstractValue, AbstractValue otherAbstractValue, TypeElement type) {
    if (abstractValue.isBottom() || otherAbstractValue.isUnknown()) {
      return otherAbstractValue;
    }
    if (abstractValue.isUnknown()
        || otherAbstractValue.isBottom()
        || abstractValue.equals(otherAbstractValue)) {
      return abstractValue;
    }
    if (abstractValue.hasWitness() || otherAbstractValue.hasWitness()) {
      // TODO(b/334822108): Implement support for actually joining values with witness.
      assert !(abstractValue.hasWitness() && otherAbstractValue.hasWitness());
      return unknown();
    }
    if (type.isReferenceType()) {
      return joinReference(abstractValue, otherAbstractValue);
    } else {
      assert type.isPrimitiveType();
      return joinPrimitive(abstractValue, otherAbstractValue, type.asPrimitiveType());
    }
  }

  private AbstractValue joinPrimitive(
      AbstractValue abstractValue, AbstractValue otherAbstractValue, PrimitiveTypeElement type) {
    assert !abstractValue.isNullOrAbstractValue();
    assert !otherAbstractValue.isNullOrAbstractValue();

    if (config.canUseNumberSetAbstraction()
        && abstractValue.isConstantOrNonConstantNumberValue()
        && otherAbstractValue.isConstantOrNonConstantNumberValue()) {
      NumberFromSetValue.Builder numberFromSetValueBuilder;
      if (abstractValue.isSingleNumberValue()) {
        numberFromSetValueBuilder = NumberFromSetValue.builder(abstractValue.asSingleNumberValue());
      } else {
        assert abstractValue.isNumberFromSetValue();
        numberFromSetValueBuilder = abstractValue.asNumberFromSetValue().instanceBuilder();
      }
      if (otherAbstractValue.isSingleNumberValue()) {
        numberFromSetValueBuilder.addInt(otherAbstractValue.asSingleNumberValue().getIntValue());
      } else {
        assert otherAbstractValue.isNumberFromSetValue();
        numberFromSetValueBuilder.addInts(otherAbstractValue.asNumberFromSetValue());
      }
      return numberFromSetValueBuilder.build(factory());
    }

    if (config.canUseDefiniteBitsAbstraction()) {
      if (type.isInt()) {
        return joinPrimitiveToDefiniteBitsIntNumberValue(abstractValue, otherAbstractValue);
      } else if (type.isLong()) {
        return joinPrimitiveToDefiniteBitsLongNumberValue(abstractValue, otherAbstractValue);
      }
    }

    return unknown();
  }

  private AbstractValue joinPrimitiveToDefiniteBitsIntNumberValue(
      AbstractValue abstractValue, AbstractValue otherAbstractValue) {
    if (!abstractValue.hasDefinitelySetAndUnsetBitsInformation()
        || !otherAbstractValue.hasDefinitelySetAndUnsetBitsInformation()) {
      return unknown();
    }
    // Normalize order.
    if (!abstractValue.isSingleNumberValue() && otherAbstractValue.isSingleNumberValue()) {
      AbstractValue tmp = abstractValue;
      abstractValue = otherAbstractValue;
      otherAbstractValue = tmp;
    }
    if (abstractValue.isSingleNumberValue()) {
      SingleNumberValue singleNumberValue = abstractValue.asSingleNumberValue();
      if (otherAbstractValue.isSingleNumberValue()) {
        SingleNumberValue otherSingleNumberValue = otherAbstractValue.asSingleNumberValue();
        return factory()
            .createDefiniteBitsIntNumberValue(
                singleNumberValue.getDefinitelySetIntBits()
                    & otherSingleNumberValue.getDefinitelySetIntBits(),
                singleNumberValue.getDefinitelyUnsetIntBits()
                    & otherSingleNumberValue.getDefinitelyUnsetIntBits());
      } else {
        assert otherAbstractValue.isDefiniteBitsIntNumberValue();
        DefiniteBitsIntNumberValue otherDefiniteBitsIntNumberValue =
            otherAbstractValue.asDefiniteBitsIntNumberValue();
        return otherDefiniteBitsIntNumberValue.join(factory(), singleNumberValue);
      }
    } else {
      // Both are guaranteed to be non-const due to normalization.
      assert abstractValue.isDefiniteBitsIntNumberValue();
      assert otherAbstractValue.isDefiniteBitsIntNumberValue();
      DefiniteBitsIntNumberValue definiteBitsIntNumberValue =
          abstractValue.asDefiniteBitsIntNumberValue();
      DefiniteBitsIntNumberValue otherDefiniteBitsIntNumberValue =
          otherAbstractValue.asDefiniteBitsIntNumberValue();
      return definiteBitsIntNumberValue.join(factory(), otherDefiniteBitsIntNumberValue);
    }
  }

  private AbstractValue joinPrimitiveToDefiniteBitsLongNumberValue(
      AbstractValue abstractValue, AbstractValue otherAbstractValue) {
    if (!abstractValue.hasDefinitelySetAndUnsetBitsInformation()
        || !otherAbstractValue.hasDefinitelySetAndUnsetBitsInformation()) {
      return unknown();
    }
    // Normalize order.
    if (!abstractValue.isSingleNumberValue() && otherAbstractValue.isSingleNumberValue()) {
      AbstractValue tmp = abstractValue;
      abstractValue = otherAbstractValue;
      otherAbstractValue = tmp;
    }
    if (abstractValue.isSingleNumberValue()) {
      SingleNumberValue singleNumberValue = abstractValue.asSingleNumberValue();
      if (otherAbstractValue.isSingleNumberValue()) {
        SingleNumberValue otherSingleNumberValue = otherAbstractValue.asSingleNumberValue();
        return factory()
            .createDefiniteBitsLongNumberValue(
                singleNumberValue.getDefinitelySetLongBits()
                    & otherSingleNumberValue.getDefinitelySetLongBits(),
                singleNumberValue.getDefinitelyUnsetLongBits()
                    & otherSingleNumberValue.getDefinitelyUnsetLongBits());
      } else {
        assert otherAbstractValue.isDefiniteBitsLongNumberValue();
        DefiniteBitsLongNumberValue otherDefiniteBitsLongNumberValue =
            otherAbstractValue.asDefiniteBitsLongNumberValue();
        return otherDefiniteBitsLongNumberValue.join(factory(), singleNumberValue);
      }
    } else {
      // Both are guaranteed to be non-const due to normalization.
      assert abstractValue.isDefiniteBitsLongNumberValue();
      assert otherAbstractValue.isDefiniteBitsLongNumberValue();
      DefiniteBitsLongNumberValue definiteBitsLongNumberValue =
          abstractValue.asDefiniteBitsLongNumberValue();
      DefiniteBitsLongNumberValue otherDefiniteBitsLongNumberValue =
          otherAbstractValue.asDefiniteBitsLongNumberValue();
      return definiteBitsLongNumberValue.join(factory(), otherDefiniteBitsLongNumberValue);
    }
  }

  private AbstractValue joinReference(
      AbstractValue abstractValue, AbstractValue otherAbstractValue) {
    if (abstractValue.isNull()) {
      return NullOrAbstractValue.create(otherAbstractValue);
    }
    if (otherAbstractValue.isNull()) {
      return NullOrAbstractValue.create(abstractValue);
    }
    if (abstractValue.isNullOrAbstractValue()
        && abstractValue.asNullOrAbstractValue().getNonNullValue().equals(otherAbstractValue)) {
      return abstractValue;
    }
    if (otherAbstractValue.isNullOrAbstractValue()
        && otherAbstractValue.asNullOrAbstractValue().getNonNullValue().equals(abstractValue)) {
      return otherAbstractValue;
    }
    return unknown();
  }

  public static class AbstractValueJoinerConfig {

    private boolean canUseDefiniteBitsAbstraction;
    private boolean canUseNumberSetAbstraction;

    boolean canUseDefiniteBitsAbstraction() {
      return canUseDefiniteBitsAbstraction;
    }

    public AbstractValueJoinerConfig setCanUseDefiniteBitsAbstraction() {
      canUseDefiniteBitsAbstraction = true;
      return this;
    }

    boolean canUseNumberSetAbstraction() {
      return canUseNumberSetAbstraction;
    }

    public AbstractValueJoinerConfig setCanUseNumberSetAbstraction() {
      canUseNumberSetAbstraction = true;
      return this;
    }
  }
}

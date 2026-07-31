// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.value.arithmetic;

import static com.android.tools.r8.utils.internal.BitUtils.INTEGER_SHIFT_MASK;
import static com.android.tools.r8.utils.internal.BitUtils.LONG_SHIFT_MASK;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueFactory;
import com.android.tools.r8.utils.internal.BitUtils;

public class AbstractCalculator {

  public static AbstractValue addIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return AbstractValue.unknown();
  }

  public static AbstractValue addLongs(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return AbstractValue.unknown();
  }

  public static AbstractValue andIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return andIntegers(appView.abstractValueFactory(), left, right);
  }

  public static AbstractValue andIntegers(
      AbstractValueFactory abstractValueFactory, AbstractValue left, AbstractValue right) {
    if (left.isZero()) {
      return left;
    }
    if (right.isZero()) {
      return right;
    }
    if (left.isSingleNumberValue() && right.isSingleNumberValue()) {
      int result =
          left.asSingleNumberValue().getIntValue() & right.asSingleNumberValue().getIntValue();
      return abstractValueFactory.createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()
        && right.hasDefinitelySetAndUnsetBitsInformation()) {
      return abstractValueFactory.createDefiniteBitsIntNumberValue(
          left.getDefinitelySetIntBits() & right.getDefinitelySetIntBits(),
          left.getDefinitelyUnsetIntBits() | right.getDefinitelyUnsetIntBits());
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      return abstractValueFactory.createDefiniteBitsIntNumberValue(
          0, left.getDefinitelyUnsetIntBits());
    }
    if (right.hasDefinitelySetAndUnsetBitsInformation()) {
      return abstractValueFactory.createDefiniteBitsIntNumberValue(
          0, right.getDefinitelyUnsetIntBits());
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue andLongs(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return andLongs(appView.abstractValueFactory(), left, right);
  }

  public static AbstractValue andLongs(
      AbstractValueFactory abstractValueFactory, AbstractValue left, AbstractValue right) {
    if (left.isZero()) {
      return left;
    }
    if (right.isZero()) {
      return right;
    }
    if (left.isSingleNumberValue() && right.isSingleNumberValue()) {
      long result =
          left.asSingleNumberValue().getLongValue() & right.asSingleNumberValue().getLongValue();
      return abstractValueFactory.createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()
        && right.hasDefinitelySetAndUnsetBitsInformation()) {
      return abstractValueFactory.createDefiniteBitsLongNumberValue(
          left.getDefinitelySetLongBits() & right.getDefinitelySetLongBits(),
          left.getDefinitelyUnsetLongBits() | right.getDefinitelyUnsetLongBits());
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      return abstractValueFactory.createDefiniteBitsLongNumberValue(
          0, left.getDefinitelyUnsetLongBits());
    }
    if (right.hasDefinitelySetAndUnsetBitsInformation()) {
      return abstractValueFactory.createDefiniteBitsLongNumberValue(
          0, right.getDefinitelyUnsetLongBits());
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue divIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return AbstractValue.unknown();
  }

  public static AbstractValue divLongs(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return AbstractValue.unknown();
  }

  public static AbstractValue mulIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return AbstractValue.unknown();
  }

  public static AbstractValue mulLongs(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return AbstractValue.unknown();
  }

  public static AbstractValue orIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    if (left.isZero()) {
      return right;
    }
    if (right.isZero()) {
      return left;
    }
    if (left.isSingleNumberValue() && right.isSingleNumberValue()) {
      int result =
          left.asSingleNumberValue().getIntValue() | right.asSingleNumberValue().getIntValue();
      return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()
        && right.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsIntNumberValue(
              left.getDefinitelySetIntBits() | right.getDefinitelySetIntBits(),
              left.getDefinitelyUnsetIntBits() & right.getDefinitelyUnsetIntBits());
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsIntNumberValue(left.getDefinitelySetIntBits(), 0);
    }
    if (right.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsIntNumberValue(right.getDefinitelySetIntBits(), 0);
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue orIntegers(
      AppView<?> appView,
      AbstractValue first,
      AbstractValue second,
      AbstractValue third,
      AbstractValue fourth) {
    return orIntegers(
        appView, first, orIntegers(appView, second, orIntegers(appView, third, fourth)));
  }

  public static AbstractValue orLongs(AppView<?> appView, AbstractValue left, AbstractValue right) {
    if (left.isZero()) {
      return right;
    }
    if (right.isZero()) {
      return left;
    }
    if (left.isSingleNumberValue() && right.isSingleNumberValue()) {
      long result =
          left.asSingleNumberValue().getLongValue() | right.asSingleNumberValue().getLongValue();
      return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()
        && right.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsLongNumberValue(
              left.getDefinitelySetLongBits() | right.getDefinitelySetLongBits(),
              left.getDefinitelyUnsetLongBits() & right.getDefinitelyUnsetLongBits());
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsLongNumberValue(left.getDefinitelySetLongBits(), 0);
    }
    if (right.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsLongNumberValue(right.getDefinitelySetLongBits(), 0);
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue remIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return AbstractValue.unknown();
  }

  public static AbstractValue remLongs(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return AbstractValue.unknown();
  }

  public static AbstractValue shlIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    if (!right.isSingleNumberValue()) {
      return AbstractValue.unknown();
    }
    int rightConst = right.asSingleNumberValue().getIntValue();
    return shlIntegers(appView, left, rightConst);
  }

  public static AbstractValue shlIntegers(AppView<?> appView, AbstractValue left, int right) {
    int rightConst = right & INTEGER_SHIFT_MASK;
    if (rightConst == 0) {
      return left;
    }
    if (left.isSingleNumberValue()) {
      int result = left.asSingleNumberValue().getIntValue() << rightConst;
      return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      // Shift the known bits and add that we now know that the lowermost n bits are definitely
      // unset. Note that when rightConst is 31, 1 << rightConst is Integer.MIN_VALUE. When
      // subtracting 1 we overflow and get 0111...111, as desired.
      return appView
          .abstractValueFactory()
          .createDefiniteBitsIntNumberValue(
              left.getDefinitelySetIntBits() << rightConst,
              (left.getDefinitelyUnsetIntBits() << rightConst) | ((1 << rightConst) - 1));
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue shlLongs(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    if (!right.isSingleNumberValue()) {
      return AbstractValue.unknown();
    }
    int rightConst = right.asSingleNumberValue().getIntValue();
    return shlLongs(appView, left, rightConst);
  }

  public static AbstractValue shlLongs(AppView<?> appView, AbstractValue left, int right) {
    int rightConst = right & LONG_SHIFT_MASK;
    if (rightConst == 0) {
      return left;
    }
    if (left.isSingleNumberValue()) {
      long result = left.asSingleNumberValue().getLongValue() << rightConst;
      return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      // Shift the known bits and add that we now know that the lowermost n bits are definitely
      // unset. Note that when rightConst is 63, 1 << rightConst is Long.MIN_VALUE. When
      // subtracting 1 we overflow and get 0111...111, as desired.
      return appView
          .abstractValueFactory()
          .createDefiniteBitsLongNumberValue(
              left.getDefinitelySetLongBits() << rightConst,
              (left.getDefinitelyUnsetLongBits() << rightConst) | ((1L << rightConst) - 1));
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue shrIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    if (!right.isSingleNumberValue()) {
      return AbstractValue.unknown();
    }
    int rightConst = right.asSingleNumberValue().getIntValue();
    return shrIntegers(appView, left, rightConst);
  }

  public static AbstractValue shrIntegers(AppView<?> appView, AbstractValue left, int right) {
    int rightConst = right & INTEGER_SHIFT_MASK;
    if (rightConst == 0) {
      return left;
    }
    if (left.isSingleNumberValue()) {
      int result = left.asSingleNumberValue().getIntValue() >> rightConst;
      return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsIntNumberValue(
              left.getDefinitelySetIntBits() >> rightConst,
              left.getDefinitelyUnsetIntBits() >> rightConst);
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue shrLongs(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    if (!right.isSingleNumberValue()) {
      return AbstractValue.unknown();
    }
    int rightConst = right.asSingleNumberValue().getIntValue();
    return shrLongs(appView, left, rightConst);
  }

  public static AbstractValue shrLongs(AppView<?> appView, AbstractValue left, int right) {
    int rightConst = right & LONG_SHIFT_MASK;
    if (rightConst == 0) {
      return left;
    }
    if (left.isSingleNumberValue()) {
      long result = left.asSingleNumberValue().getLongValue() >> rightConst;
      return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsLongNumberValue(
              left.getDefinitelySetLongBits() >> rightConst,
              left.getDefinitelyUnsetLongBits() >> rightConst);
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue subIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return AbstractValue.unknown();
  }

  public static AbstractValue subLongs(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    return AbstractValue.unknown();
  }

  public static AbstractValue ushrIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    if (!right.isSingleNumberValue()) {
      return AbstractValue.unknown();
    }
    int rightConst = right.asSingleNumberValue().getIntValue() & INTEGER_SHIFT_MASK;
    if (rightConst == 0) {
      return left;
    }
    if (left.isSingleNumberValue()) {
      int result = left.asSingleNumberValue().getIntValue() >>> rightConst;
      return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      // Shift the known bits information and add that we now know that the uppermost n bits are
      // definitely unset.
      return appView
          .abstractValueFactory()
          .createDefiniteBitsIntNumberValue(
              left.getDefinitelySetIntBits() >>> rightConst,
              (left.getDefinitelyUnsetIntBits() >>> rightConst)
                  | (BitUtils.ONLY_SIGN_BIT_SET_INTEGER_MASK >> (rightConst - 1)));
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue ushrLongs(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    if (!right.isSingleNumberValue()) {
      return AbstractValue.unknown();
    }
    int rightConst = right.asSingleNumberValue().getIntValue() & LONG_SHIFT_MASK;
    if (rightConst == 0) {
      return left;
    }
    if (left.isSingleNumberValue()) {
      long result = left.asSingleNumberValue().getLongValue() >>> rightConst;
      return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()) {
      // Shift the known bits information and add that we now know that the uppermost n bits are
      // definitely unset.
      return appView
          .abstractValueFactory()
          .createDefiniteBitsLongNumberValue(
              left.getDefinitelySetLongBits() >>> rightConst,
              (left.getDefinitelyUnsetLongBits() >>> rightConst)
                  | (BitUtils.ONLY_SIGN_BIT_SET_LONG_MASK >> (rightConst - 1)));
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue xorIntegers(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    if (left.isSingleNumberValue() && right.isSingleNumberValue()) {
      int result =
          left.asSingleNumberValue().getIntValue() ^ right.asSingleNumberValue().getIntValue();
      return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()
        && right.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsIntNumberValue(
              (left.getDefinitelySetIntBits() & right.getDefinitelyUnsetIntBits())
                  | (left.getDefinitelyUnsetIntBits() & right.getDefinitelySetIntBits()),
              (left.getDefinitelySetIntBits() & right.getDefinitelySetIntBits())
                  | (left.getDefinitelyUnsetIntBits() & right.getDefinitelyUnsetIntBits()));
    }
    return AbstractValue.unknown();
  }

  public static AbstractValue xorLongs(
      AppView<?> appView, AbstractValue left, AbstractValue right) {
    if (left.isSingleNumberValue() && right.isSingleNumberValue()) {
      long result =
          left.asSingleNumberValue().getLongValue() ^ right.asSingleNumberValue().getLongValue();
      return appView.abstractValueFactory().createUncheckedSingleNumberValue(result);
    }
    if (left.hasDefinitelySetAndUnsetBitsInformation()
        && right.hasDefinitelySetAndUnsetBitsInformation()) {
      return appView
          .abstractValueFactory()
          .createDefiniteBitsLongNumberValue(
              (left.getDefinitelySetLongBits() & right.getDefinitelyUnsetLongBits())
                  | (left.getDefinitelyUnsetLongBits() & right.getDefinitelySetLongBits()),
              (left.getDefinitelySetLongBits() & right.getDefinitelySetLongBits())
                  | (left.getDefinitelyUnsetLongBits() & right.getDefinitelyUnsetLongBits()));
    }
    return AbstractValue.unknown();
  }
}

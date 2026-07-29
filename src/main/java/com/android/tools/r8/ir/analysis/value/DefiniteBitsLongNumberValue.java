// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.internal.ObjectUtils;
import com.android.tools.r8.utils.internal.OptionalBool;

public class DefiniteBitsLongNumberValue extends NonConstantNumberValue {

  private final long definitelySetBits;
  private final long definitelyUnsetBits;

  public DefiniteBitsLongNumberValue(long definitelySetBits, long definitelyUnsetBits) {
    assert (definitelySetBits & definitelyUnsetBits) == 0;
    this.definitelySetBits = definitelySetBits;
    this.definitelyUnsetBits = definitelyUnsetBits;
  }

  @Override
  public boolean maybeContainsInt(int value) {
    // If a definitely set bit is unset in value, then no.
    if ((definitelySetBits & ~value) != 0) {
      return false;
    }
    // If a definitely unset bit is set in value, then no.
    if ((definitelyUnsetBits & value) != 0) {
      return false;
    }
    return getMinInclusiveLong() <= value && value <= getMaxInclusiveLong();
  }

  @Override
  public long getAbstractionSize() {
    return Long.MAX_VALUE;
  }

  @Override
  public long getDefinitelySetLongBits() {
    return definitelySetBits;
  }

  @Override
  public long getDefinitelyUnsetLongBits() {
    return definitelyUnsetBits;
  }

  @Override
  public long getMinInclusive() {
    return getMinInclusiveLong();
  }

  public long getMinInclusiveLong() {
    return (definitelySetBits & 0x7FFFFFFFFFFFFFFFL) | (~definitelyUnsetBits & 0x8000000000000000L);
  }

  public long getMaxInclusiveLong() {
    return (definitelySetBits & 0x8000000000000000L) | (~definitelyUnsetBits & 0x7FFFFFFFFFFFFFFFL);
  }

  @Override
  public boolean hasDefinitelySetAndUnsetBitsInformation() {
    return true;
  }

  @Override
  public boolean isDefiniteBitsLongNumberValue() {
    return true;
  }

  @Override
  public DefiniteBitsLongNumberValue asDefiniteBitsLongNumberValue() {
    return this;
  }

  @Override
  public boolean isNonTrivial() {
    return true;
  }

  @Override
  public OptionalBool isSubsetOf(int[] values) {
    return OptionalBool.unknown();
  }

  public AbstractValue join(
      AbstractValueFactory abstractValueFactory,
      DefiniteBitsLongNumberValue definiteBitsNumberValue) {
    return join(
        abstractValueFactory,
        definiteBitsNumberValue.definitelySetBits,
        definiteBitsNumberValue.definitelyUnsetBits);
  }

  public AbstractValue join(
      AbstractValueFactory abstractValueFactory, SingleNumberValue singleNumberValue) {
    return join(
        abstractValueFactory,
        singleNumberValue.getDefinitelySetLongBits(),
        singleNumberValue.getDefinitelyUnsetLongBits());
  }

  public AbstractValue join(
      AbstractValueFactory abstractValueFactory,
      long otherDefinitelySetBits,
      long otherDefinitelyUnsetBits) {
    if (definitelySetBits == otherDefinitelySetBits
        && definitelyUnsetBits == otherDefinitelyUnsetBits) {
      return this;
    }
    return abstractValueFactory.createDefiniteBitsLongNumberValue(
        definitelySetBits & otherDefinitelySetBits, definitelyUnsetBits & otherDefinitelyUnsetBits);
  }

  @Override
  public boolean mayOverlapWith(ConstantOrNonConstantNumberValue other) {
    return true;
  }

  @Override
  public AbstractValue rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, DexType newType, GraphLens lens, GraphLens codeLens) {
    return this;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || o.getClass() != getClass()) {
      return false;
    }
    DefiniteBitsLongNumberValue definiteBitsNumberValue = (DefiniteBitsLongNumberValue) o;
    return definitelySetBits == definiteBitsNumberValue.definitelySetBits
        && definitelyUnsetBits == definiteBitsNumberValue.definitelyUnsetBits;
  }

  @Override
  public int hashCode() {
    return ObjectUtils.hashJJ(definitelySetBits, definitelyUnsetBits);
  }

  @Override
  public String toString() {
    return "DefiniteBitsLongNumberValue(set: "
        + Long.toBinaryString(definitelySetBits)
        + "; unset: "
        + Long.toBinaryString(definitelyUnsetBits)
        + ")";
  }
}

// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.regalloc;

import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import java.util.Arrays;
import java.util.BitSet;

public class RegisterPositionsImpl extends RegisterPositions {

  private static final int INITIAL_SIZE = 16;
  private final int limit;
  private int[] backing;
  private final BitSet registerHoldsConstant;
  private final BitSet registerHoldsMonitor;
  private final BitSet registerHoldsNewStringInstanceDisallowingSpilling;
  private final BitSet blockedRegisters;

  public RegisterPositionsImpl(int limit) {
    this(limit, true);
  }

  // When trackTypes is false, the register types are not recorded, and hasType() must only be
  // called with RegisterType.ANY. This saves allocating three BitSets of size limit.
  public RegisterPositionsImpl(int limit, boolean trackTypes) {
    this.limit = limit;
    backing = new int[INITIAL_SIZE];
    for (int i = 0; i < INITIAL_SIZE; i++) {
      backing[i] = Integer.MAX_VALUE;
    }
    if (trackTypes) {
      registerHoldsConstant = new BitSet(limit);
      registerHoldsMonitor = new BitSet(limit);
      registerHoldsNewStringInstanceDisallowingSpilling = new BitSet(limit);
    } else {
      registerHoldsConstant = null;
      registerHoldsMonitor = null;
      registerHoldsNewStringInstanceDisallowingSpilling = null;
    }
    blockedRegisters = new BitSet(limit);
  }

  private boolean isTrackingTypes() {
    return registerHoldsConstant != null;
  }

  @Override
  public boolean hasType(int index, RegisterType type) {
    assert !isBlocked(index);
    switch (type) {
      case MONITOR:
        return holdsMonitor(index);
      case CONST_NUMBER:
        return holdsConstant(index);
      case OTHER:
        return !holdsMonitor(index)
            && !holdsConstant(index)
            && !holdsNewStringInstanceDisallowingSpilling(index);
      case ANY:
        return true;
      default:
        throw new Unreachable("Unexpected register position type: " + type);
    }
  }

  private boolean holdsConstant(int index) {
    return registerHoldsConstant.get(index);
  }

  private boolean holdsMonitor(int index) {
    return registerHoldsMonitor.get(index);
  }

  private boolean holdsNewStringInstanceDisallowingSpilling(int index) {
    return registerHoldsNewStringInstanceDisallowingSpilling.get(index);
  }

  private void set(int index, int value) {
    if (index >= backing.length) {
      grow(index + 1);
    }
    backing[index] = value;
  }

  @Override
  public void set(int index, int value, LiveIntervals intervals) {
    set(index, value);
    if (isTrackingTypes()) {
      registerHoldsConstant.set(index, intervals.isConstantNumberInterval());
      registerHoldsMonitor.set(index, intervals.usedInMonitorOperation());
      registerHoldsNewStringInstanceDisallowingSpilling.set(
          index, intervals.isNewStringInstanceDisallowingSpilling());
    }
  }

  @Override
  public int get(int index) {
    assert !isBlocked(index);
    if (index < backing.length) {
      return backing[index];
    }
    assert index < limit;
    return Integer.MAX_VALUE;
  }

  @Override
  public int getLimit() {
    return limit;
  }

  @Override
  public void setBlocked(int index) {
    blockedRegisters.set(index);
  }

  @Override
  public boolean isBlocked(int index) {
    return blockedRegisters.get(index);
  }

  private void grow(int minSize) {
    int size = backing.length;
    while (size < minSize) {
      size *= 2;
    }
    size = Math.min(size, limit);
    int oldSize = backing.length;
    backing = Arrays.copyOf(backing, size);
    for (int i = oldSize; i < size; i++) {
      backing[i] = Integer.MAX_VALUE;
    }
  }
}

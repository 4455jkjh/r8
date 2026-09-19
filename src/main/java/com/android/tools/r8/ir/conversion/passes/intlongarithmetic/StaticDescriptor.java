// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes.intlongarithmetic;

import com.android.tools.r8.utils.internal.exceptions.Unreachable;

/**
 * This describes some methods in boxed primitive types and Math/StrictMath. A static descriptor
 * describes left and right identity and absorbing element of static call. <code>
 *  In a space K, for a static call *:
 * - i is left identity if for each x in K, i * x = x.
 * - i is right identity if for each x in K, x * i = x.
 * - a is left absorbing if for each x in K, a * x = a.
 * - a is right absorbing if for each x in K, x * a = a.
 * In a space K, a static call * is associative if for each x,y,z in K, (x * y) * z = x * (y * z).
 * </code>
 */
enum StaticDescriptor implements ArithmeticDescriptor {
  ADD {
    @Override
    Integer leftIdentity() {
      return 0;
    }

    @Override
    Integer rightIdentity() {
      return 0;
    }

    @Override
    boolean canThrow() {
      return false;
    }
  },
  ADD_EXACT {
    @Override
    Integer leftIdentity() {
      return 0;
    }

    @Override
    Integer rightIdentity() {
      return 0;
    }
  },
  SUB_EXACT {
    @Override
    Integer rightIdentity() {
      return 0;
    }
  },
  MUL_EXACT {
    @Override
    Integer leftIdentity() {
      return 1;
    }

    @Override
    Integer rightIdentity() {
      return 1;
    }

    @Override
    Integer leftAbsorbing() {
      return 0;
    }

    @Override
    Integer rightAbsorbing() {
      return 0;
    }
  },
  // MIN and MAX are dealt with separately. This doesn't encode difference between long and int,
  // and it's not clear Long.MAX_VALUE is widely used.
  MIN {
    @Override
    public int evaluate(int left, int right) {
      return Math.min(left, right);
    }

    @Override
    public long evaluate(long left, long right) {
      return Math.min(left, right);
    }

    @Override
    boolean canThrow() {
      return false;
    }
  },
  MAX {
    @Override
    public int evaluate(int left, int right) {
      return Math.max(left, right);
    }

    @Override
    public long evaluate(long left, long right) {
      return Math.max(left, right);
    }

    @Override
    boolean canThrow() {
      return false;
    }
  },
  FLOOR_DIV {
    @Override
    Integer rightIdentity() {
      return 1;
    }

    @Override
    boolean canThrowOnlyOnZeroDivisor() {
      return true;
    }
  },
  FLOOR_MOD {
    @Override
    boolean canThrowOnlyOnZeroDivisor() {
      return true;
    }
  },
  DIVIDE_UNSIGNED {
    @Override
    Integer rightIdentity() {
      return 1;
    }

    @Override
    boolean canThrowOnlyOnZeroDivisor() {
      return true;
    }
  },
  REMAINDER_UNSIGNED {
    @Override
    boolean canThrowOnlyOnZeroDivisor() {
      return true;
    }
  };

  @Override
  public int evaluate(int left, int right) {
    throw new Unreachable();
  }

  @Override
  public long evaluate(long left, long right) {
    throw new Unreachable();
  }

  Integer leftIdentity() {
    return null;
  }

  Integer rightIdentity() {
    return null;
  }

  Integer leftAbsorbing() {
    return null;
  }

  Integer rightAbsorbing() {
    return null;
  }

  boolean canThrow() {
    return true;
  }

  boolean canThrowOnlyOnZeroDivisor() {
    return false;
  }
}

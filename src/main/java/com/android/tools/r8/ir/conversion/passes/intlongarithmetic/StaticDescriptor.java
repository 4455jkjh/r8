// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes.intlongarithmetic;

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
enum StaticDescriptor {
  ADD {
    @Override
    Integer leftIdentity() {
      return 0;
    }

    @Override
    Integer rightIdentity() {
      return 0;
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
  MIN,
  MAX,
  FLOOR_DIV {
    @Override
    Integer rightIdentity() {
      return 1;
    }
  },
  FLOOR_MOD,
  DIVIDE_UNSIGNED {
    @Override
    Integer rightIdentity() {
      return 1;
    }
  },
  REMAINDER_UNSIGNED;

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
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.math.BigInteger;

public final class BigIntegerMethods {

  public static byte byteValueExact(BigInteger value) {
    if (value.bitLength() <= 7) {
      return value.byteValue();
    }
    throw new ArithmeticException("BigInteger out of byte range");
  }

  public static int intValueExact(BigInteger value) {
    if (value.bitLength() <= 31) {
      return value.intValue();
    }
    throw new ArithmeticException("BigInteger out of int range");
  }

  public static long longValueExact(BigInteger value) {
    if (value.bitLength() <= 63) {
      return value.longValue();
    }
    throw new ArithmeticException("BigInteger out of long range");
  }

  public static short shortValueExact(BigInteger value) {
    if (value.bitLength() <= 15) {
      return value.shortValue();
    }
    throw new ArithmeticException("BigInteger out of short range");
  }
}

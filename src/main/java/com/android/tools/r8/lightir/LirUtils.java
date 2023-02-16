// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

public class LirUtils {

  private LirUtils() {}

  public static int encodeValueIndex(int absoluteValueIndex, int referencingValueContext) {
    return referencingValueContext - absoluteValueIndex;
  }

  public static int decodeValueIndex(int encodedValueIndex, int referencingValueContext) {
    return referencingValueContext - encodedValueIndex;
  }
}

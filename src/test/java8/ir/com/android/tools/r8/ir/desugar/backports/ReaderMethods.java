// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.io.Reader;
import java.io.StringReader;

public final class ReaderMethods {

  public static Reader of(CharSequence cs) {
    return new StringReader(cs.toString());
  }
}

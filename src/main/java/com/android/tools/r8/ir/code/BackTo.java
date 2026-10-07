// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;

/**
 * The single back edge of a loop, from the loop latch to the loop header.
 *
 * <p>Created by {@link com.android.tools.r8.ir.conversion.finalizer.LoopBackEdgeCanonicalizer} when
 * generating DEX. All other branches are emitted as forward branches, such that each loop has
 * exactly one backward branch, which ART uses for hotness counting and suspend checks.
 */
public class BackTo extends Goto {

  @Override
  public boolean isBackTo() {
    return true;
  }

  @Override
  public BackTo asBackTo() {
    return this;
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable("BackTo is only used when generating DEX");
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    throw new Unreachable("BackTo is only used when generating DEX");
  }
}

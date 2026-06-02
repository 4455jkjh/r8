// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info.bridge;

import com.android.tools.r8.graph.DexMethod;

/**
 * Optimization info computed for non-static bridge methods that use an invoke-static instruction,
 * where the `this` value is not forwarded.
 *
 * <p>This typically occurs in D8/R8 synthesized lambda classes that call javac synthetic lambda
 * methods.
 */
public class StaticBridgeExcludingReceiverInfo extends BridgeInfo {

  private final boolean isInterface;

  public StaticBridgeExcludingReceiverInfo(DexMethod invokedMethod, boolean isInterface) {
    super(invokedMethod);
    this.isInterface = isInterface;
  }

  public boolean getInterfaceBit() {
    return isInterface;
  }

  @Override
  public boolean isStaticBridgeExcludingReceiverInfo() {
    return true;
  }

  @Override
  public StaticBridgeExcludingReceiverInfo asStaticBridgeExcludingReceiverInfo() {
    return this;
  }
}

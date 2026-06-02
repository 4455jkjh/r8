// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info.bridge;

import com.android.tools.r8.graph.DexMethod;

/** Optimization info computed for bridge methods that use an invoke-static instruction. */
public class StaticBridgeInfo extends BridgeInfo {

  public StaticBridgeInfo(DexMethod invokedMethod) {
    super(invokedMethod);
  }

  @Override
  public boolean isStaticBridgeInfo() {
    return true;
  }

  @Override
  public StaticBridgeInfo asStaticBridgeInfo() {
    return this;
  }
}

// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info.bridge;

import com.android.tools.r8.graph.DexMethod;

/**
 * A piece of optimization info that is computed for bridge methods. The info stores details about
 * the behavior of the bridge.
 */
public abstract class BridgeInfo {

  // The targeted method.
  private final DexMethod invokedMethod;

  public BridgeInfo(DexMethod invokedMethod) {
    this.invokedMethod = invokedMethod;
  }

  public boolean isDirectBridgeInfo() {
    return false;
  }

  public DirectBridgeInfo asDirectBridgeInfo() {
    return null;
  }

  public boolean isStaticBridgeInfo() {
    return false;
  }

  public StaticBridgeInfo asStaticBridgeInfo() {
    return null;
  }

  public boolean isStaticBridgeExcludingReceiverInfo() {
    return false;
  }

  public StaticBridgeExcludingReceiverInfo asStaticBridgeExcludingReceiverInfo() {
    return null;
  }

  public DexMethod getInvokedMethod() {
    return invokedMethod;
  }

  public boolean isSuperBridgeInfo() {
    return false;
  }

  public SuperBridgeInfo asSuperBridgeInfo() {
    return null;
  }

  public boolean isVirtualBridgeInfo() {
    return false;
  }

  public VirtualBridgeInfo asVirtualBridgeInfo() {
    return null;
  }
}

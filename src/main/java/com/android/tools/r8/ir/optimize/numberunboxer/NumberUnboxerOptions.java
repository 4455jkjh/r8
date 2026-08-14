// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

public class NumberUnboxerOptions {

  // Debugging and development.

  private boolean enabled = false;
  private boolean debugPrintNumberUnboxed = false;

  // Heuristics.

  // For each value to unbox, the number unboxer can keep track of a list of dependencies that
  // would need to be unboxed at the same time, up to this size.
  private final int maxTransitiveDependencies = 7;
  // When the number unboxer decides to unbox a value, it computes the number of boxing operations
  // that will be removed (or added) if the unboxing is performed, and executes the unboxing only
  // if that number is above the threshold.
  private final int unboxDeltaThreshold = 0;

  public NumberUnboxerOptions setDebugPrintNumberUnboxed() {
    debugPrintNumberUnboxed = true;
    return this;
  }

  public boolean shouldDebugPrintNumberUnboxed() {
    return debugPrintNumberUnboxed;
  }

  public NumberUnboxerOptions enable() {
    enabled = true;
    return this;
  }

  public boolean isEnabled() {
    return enabled;
  }

  public int getMaxTransitiveDependencies() {
    return maxTransitiveDependencies;
  }

  public int getUnboxDeltaThreshold() {
    return unboxDeltaThreshold;
  }
}

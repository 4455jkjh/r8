// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.utils.InternalOptions;
import java.util.function.Predicate;

public class VerticalClassMergerOptions {

  private final InternalOptions options;

  private boolean enabled = true;
  private boolean enableBridgeAnalysis = true;

  private Predicate<ClassMergerMode> modePredicate = null;

  public VerticalClassMergerOptions(InternalOptions options) {
    this.options = options;
  }

  public void disable() {
    setEnabled(false);
  }

  public void disableInitial() {
    if (modePredicate == null) {
      modePredicate = mode -> mode != ClassMergerMode.INITIAL;
    } else {
      modePredicate = modePredicate.and(mode -> mode != ClassMergerMode.INITIAL);
    }
  }

  public boolean isEnabled(ClassMergerMode mode) {
    if (!enabled
        || options.debug
        || options.intermediate
        || !options.isOptimizing()
        || !options.isShrinking()) {
      return false;
    }
    if (modePredicate != null && !modePredicate.test(mode)) {
      return false;
    }
    return true;
  }

  public boolean isBridgeAnalysisEnabled() {
    return enableBridgeAnalysis;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public void setEnableBridgeAnalysis(boolean enableBridgeAnalysis) {
    this.enableBridgeAnalysis = enableBridgeAnalysis;
  }

  public void setModePredicate(Predicate<ClassMergerMode> modePredicate) {
    this.modePredicate = modePredicate;
  }
}

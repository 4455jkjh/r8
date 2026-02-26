// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

public class ProguardTypeMatcherAndNegation {

  private final ProguardTypeMatcher matcher;
  private final boolean negated;

  public ProguardTypeMatcherAndNegation(ProguardTypeMatcher matcher) {
    this(matcher, false);
  }

  public ProguardTypeMatcherAndNegation(ProguardTypeMatcher matcher, boolean negated) {
    this.matcher = matcher;
    this.negated = negated;
  }

  public ProguardTypeMatcher getMatcher() {
    return matcher;
  }

  public boolean isNegated() {
    return negated;
  }
}

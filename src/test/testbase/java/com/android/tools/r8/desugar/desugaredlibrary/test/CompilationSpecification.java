// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.test;

import static com.android.tools.r8.CompilationMode.DEBUG;
import static com.android.tools.r8.CompilationMode.RELEASE;

import com.android.tools.r8.CompilationMode;
import com.google.common.collect.ImmutableSet;
import java.util.Set;

public enum CompilationSpecification {
  D8_L8DEBUG(false, false, false, DEBUG),
  D8_L8SHRINK(false, true, false, RELEASE),
  R8_PARTIAL_EXCLUDE_L8SHRINK(false, true, false, RELEASE),
  // In theory no build system uses R8_L8DEBUG, for local debugging only.
  R8_L8DEBUG(true, false, false, RELEASE),
  R8_L8SHRINK(true, true, false, RELEASE),
  R8_PARTIAL_INCLUDE_L8SHRINK(true, true, false, RELEASE),
  // The D8CFTOCF specifications can run either in CF or be dexed afterwards.
  D8CF2CF_L8DEBUG(false, false, true, DEBUG),
  D8CF2CF_L8SHRINK(false, true, true, RELEASE);

  public static Set<CompilationSpecification> DEFAULT_SPECIFICATIONS =
      ImmutableSet.of(D8_L8DEBUG, D8_L8SHRINK, R8_L8SHRINK);
  public static Set<CompilationSpecification> SPECIFICATIONS_WITH_CF2CF =
      ImmutableSet.of(D8_L8DEBUG, D8_L8SHRINK, R8_L8SHRINK, D8CF2CF_L8DEBUG, D8CF2CF_L8SHRINK);

  private final boolean programShrink;
  private final boolean l8Shrink;
  private final boolean cfToCf;
  private final CompilationMode programCompilationMode;

  CompilationSpecification(
      boolean programShrink,
      boolean l8Shrink,
      boolean cfToCf,
      CompilationMode mode) {
    this.programShrink = programShrink;
    this.l8Shrink = l8Shrink;
    this.cfToCf = cfToCf;
    this.programCompilationMode = mode;
  }

  public CompilationMode getProgramCompilationMode() {
    return programCompilationMode;
  }

  public boolean isProgramShrink() {
    return programShrink;
  }

  public boolean isL8Shrink() {
    return l8Shrink;
  }

  public boolean isCfToCf() {
    return cfToCf;
  }
}

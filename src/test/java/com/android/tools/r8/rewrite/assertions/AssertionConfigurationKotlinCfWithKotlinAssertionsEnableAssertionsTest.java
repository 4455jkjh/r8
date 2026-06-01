// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;

public class AssertionConfigurationKotlinCfWithKotlinAssertionsEnableAssertionsTest
    extends AssertionConfigurationKotlinCfTestBase {

  public AssertionConfigurationKotlinCfWithKotlinAssertionsEnableAssertionsTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(parameters, kotlinParameters, false, false, true);
  }
}

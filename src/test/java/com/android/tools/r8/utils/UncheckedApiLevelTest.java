// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UncheckedApiLevelTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public UncheckedApiLevelTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testMinorOmission() {
    // It is important for compiler dumps that the minor version can be omitted.
    assertEquals("3", UncheckedApiLevel.parse("3").toString());
    assertEquals("3", UncheckedApiLevel.parse("3.0").toString());
  }
}

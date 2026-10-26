// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BaseDefaultMethodTestRunner extends TestBase {
  static final Class CLASS = BaseDefaultMethodTest.class;
  static final Class[] CLASSES = BaseDefaultMethodTest.CLASSES;

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public BaseDefaultMethodTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClasses(CLASSES)
        .addKeepMainRule(CLASS)
        .debug()
        .run(parameters.getRuntime(), CLASS)
        .assertSuccessWithEmptyOutput();
  }
}

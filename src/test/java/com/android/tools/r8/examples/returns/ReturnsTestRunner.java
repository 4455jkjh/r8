// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.examples.returns;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.examples.ExamplesTestBase;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ReturnsTestRunner extends ExamplesTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().enableApiLevelsForCf().build();
  }

  public ReturnsTestRunner(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<?> getMainClass() {
    return Returns.class;
  }

  @Override
  public String getExpected() {
    return "";
  }

  @Test
  public void testDesugaring() throws Exception {
    runTestDesugaring();
  }

  @Test
  public void testR8() throws Exception {
    runTestR8();
  }

  @Test
  public void testDebug() throws Exception {
    runTestDebugComparator();
  }
}

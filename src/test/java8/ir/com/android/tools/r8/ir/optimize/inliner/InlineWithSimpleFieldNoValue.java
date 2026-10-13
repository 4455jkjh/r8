// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.R8TestMultiRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

class TestClass {
  public static void main(String[] args) {
    System.out.println(InlineFrom.getValue());
    InlineFrom.value = 43;
    System.out.println(InlineFrom.value);
  }
}

// Simple class, with no clinit and no static field initialization.
// We should always inline getValue()
// We ensure that we use value.
class InlineFrom {
  public static int value;

  public static int getValue() {
    return 42;
  }
}

@RunWith(Parameterized.class)
public class InlineWithSimpleFieldNoValue extends TestBase {
  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  @Test
  public void test() throws Exception {
    R8TestMultiRunResult result =
        testForR8(Backend.DEX)
            .addKeepMainRule(TestClass.class)
            .addProgramClasses(TestClass.class, InlineFrom.class)
            .run(parameters.getRuntime(), TestClass.class)
            .assertSuccessWithOutput(StringUtils.lines("42", "43"));
    assertTrue(result.inspector().clazz(InlineFrom.class).allMethods().isEmpty());
  }
}

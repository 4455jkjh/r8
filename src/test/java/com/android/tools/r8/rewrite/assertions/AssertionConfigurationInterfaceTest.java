// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.rewrite.assertions.testclasses.ClassImplementingInterfaceWithAssertions;
import com.android.tools.r8.rewrite.assertions.testclasses.InterfaceWithAssertions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssertionConfigurationInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public AssertionConfigurationInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  public static class TestClass {
    public static void main(String[] args) {
      new ClassImplementingInterfaceWithAssertions().mDefault();
      InterfaceWithAssertions.mStatic();
      System.out.println("DONE");
    }
  }

  @Test
  public void testR8() throws Exception {
    Class<?> helperClass = Class.forName(InterfaceWithAssertions.class.getName() + "$1");
    testForR8(parameters.getBackend())
        .addProgramClasses(
            InterfaceWithAssertions.class,
            ClassImplementingInterfaceWithAssertions.class,
            TestClass.class,
            helperClass)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(
            InterfaceWithAssertions.class,
            ClassImplementingInterfaceWithAssertions.class,
            helperClass)
        .setMinApi(parameters)
        .addAssertionsConfiguration(
            AssertionsConfiguration.Builder::compileTimeDisableAllAssertions)
        .compile()
        .applyIf(parameters.isCfRuntime(), b -> b.enableRuntimeAssertions(true))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("DONE");
  }
}

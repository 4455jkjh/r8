// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NotNullAfterInstanceOfTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepClassAndMembersRules(Main.class)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject mainMethodSubject = inspector.clazz(Main.class).mainMethod();
              assertThat(mainMethodSubject, isPresent());
              assertEquals(
                  2,
                  mainMethodSubject.streamInstructions().filter(InstructionSubject::isIf).count());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      Main main = System.currentTimeMillis() > 0 ? new Main() : null;
      if (main instanceof Main) {
        if (main != null) {
          main.greet();
        }
      }
    }

    void greet() {
      System.out.println("Hello, world!");
    }
  }
}

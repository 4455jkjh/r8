// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.redundantconstclasselimination;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RedundantConstClassEliminationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForD8(parameters)
        .addInnerClasses(RedundantConstClassEliminationTest.class)
        .release()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject mainMethodSubject = inspector.clazz(TestClass.class).mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertEquals(
        2, mainMethodSubject.streamInstructions().filter(InstructionSubject::isConstClass).count());
  }

  static class A {}

  static class B {}

  static class TestClass {

    public static void main(String[] args) {
      consume(A.class);
      consume(A.class);
      consume(B.class);
      consume(B.class);
    }

    static void consume(Class<?> clazz) {
      System.out.println(clazz.getName());
    }
  }
}

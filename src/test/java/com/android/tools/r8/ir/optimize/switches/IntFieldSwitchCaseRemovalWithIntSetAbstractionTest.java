// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.switches;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IntFieldSwitchCaseRemovalWithIntSetAbstractionTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              MethodSubject getMethod =
                  inspector.clazz(A.class).uniqueMethodWithOriginalName("get");
              assertThat(getMethod, isPresent());
              // TODO(b/503184789): "A", "D", and "E" should be pruned.
              assertTrue(getMethod.streamInstructions().anyMatch(x -> x.isConstString("A")));
              assertTrue(getMethod.streamInstructions().anyMatch(x -> x.isConstString("B")));
              assertTrue(getMethod.streamInstructions().anyMatch(x -> x.isConstString("C")));
              assertTrue(getMethod.streamInstructions().anyMatch(x -> x.isConstString("D")));
              assertTrue(getMethod.streamInstructions().anyMatch(x -> x.isConstString("E")));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("B", "C");
  }

  static class Main {

    public static void main(String[] args) {
      // Call A.<init> with 0b01 and 0b10 so that nothing is known about the two lowermost bits
      // after joining.
      System.out.println(new A(0b01).get());
      System.out.println(new A(0b10).get());
    }
  }

  @NeverClassInline
  static class A {

    int i;

    @NeverInline
    A(int i) {
      this.i = i;
    }

    @NeverInline
    String get() {
      switch (i) {
        case 0:
          return "A";
        case 1:
          return "B";
        case 2:
          return "C";
        case 3:
          return "D";
      }
      return "E";
    }
  }
}

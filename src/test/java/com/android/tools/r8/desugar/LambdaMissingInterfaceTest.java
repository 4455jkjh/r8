// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaMissingInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public LambdaMissingInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(ClassWithLambda.class, Main.class)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .addDontWarn(MissingInterface.class)
        .enableInliningAnnotations()
        .compile()
        .addRunClasspathClasses(MissingInterface.class)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(AbstractMethodError.class);
  }

  interface MissingInterface {

    void bar(int x);
  }

  public static class ClassWithLambda {

    @NeverInline
    public static void callWithLambda() {
      Main.foo(System.out::println);
    }
  }

  public static class Main {

    private static int argCount;

    @NeverInline
    public static void foo(MissingInterface i) {
      i.bar(argCount);
    }

    public static void main(String[] args) {
      argCount = args.length;
      ClassWithLambda.callWithLambda();
    }
  }
}

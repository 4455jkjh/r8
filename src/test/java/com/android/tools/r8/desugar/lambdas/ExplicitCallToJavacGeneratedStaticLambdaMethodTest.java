// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.lambdas;

import static com.android.tools.r8.ir.desugar.LambdaRewriter.EXPECTED_LAMBDA_METHOD_PREFIX;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.cf.CfVersion;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ExplicitCallToJavacGeneratedStaticLambdaMethodTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK9)
        .withDexRuntimes()
        .withAllApiLevels()
        .build();
  }

  public ExplicitCallToJavacGeneratedStaticLambdaMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, A.class, FunctionalInterface.class)
        .addProgramClassFileData(getProgramClassFileData())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!", "Hello world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Main.class, A.class, FunctionalInterface.class)
        .addProgramClassFileData(getProgramClassFileData())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello world!", "Hello world!");
  }

  private byte[] getProgramClassFileData() throws IOException {
    Method lambdaMethod =
        Stream.of(I.class.getDeclaredMethods())
            .filter(x -> x.getName().contains(EXPECTED_LAMBDA_METHOD_PREFIX))
            .findFirst()
            .get();
    return transformer(I.class)
        .transformMethodInsnInMethod(
            "test",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("lambdaMethod")) {
                visitor.visitMethodInsn(
                    opcode, owner, lambdaMethod.getName(), descriptor, isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .removeMethodsWithName("lambdaMethod")
        .setVersion(CfVersion.V9)
        .transform();
  }

  static class Main {

    public static void main(String[] args) {
      new A().test();
    }
  }

  interface I {

    default void test() {
      FunctionalInterface f = () -> System.out.println("Hello world!");
      f.m();
      lambdaMethod(); // Changed to lambda$test$0() by transformer.
    }

    // Removed by transformer.
    static void lambdaMethod() {}
  }

  static class A implements I {}

  interface FunctionalInterface {

    void m();
  }
}

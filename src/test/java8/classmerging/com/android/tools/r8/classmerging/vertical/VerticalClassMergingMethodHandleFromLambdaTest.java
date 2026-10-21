// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergingMethodHandleFromLambdaTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("SecurityException caught as expected");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addVerticallyMergedClassesInspector(
            inspector ->
                // When generating class files, A.m is referenced from a lambda method handle.
                inspector.applyIf(
                    parameters.isCfRuntime(),
                    i -> i.assertNoClassesMerged(),
                    i -> i.assertMergedIntoSubtype(A.class)))
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("SecurityException caught as expected");
  }

  public interface MyFunction<T, R> {
    R apply(T t);
  }

  public static class Req {}

  public static class Resp {}

  public abstract static class A {
    public abstract Resp m(Req req);
  }

  public static class B extends A {
    @Override
    @NeverInline
    public Resp m(Req req) {
      throw new SecurityException("Always fails");
    }
  }

  public static class Main {
    @NeverInline
    private static <ReqT, RespT> RespT execute(ReqT req, MyFunction<ReqT, RespT> fn) {
      return fn.apply(req);
    }

    @NeverInline
    public static Resp callM(A a, Req req) {
      return execute(req, a::m);
    }

    public static void main(String[] args) {
      try {
        callM(new B(), new Req());
      } catch (SecurityException e) {
        System.out.println("SecurityException caught as expected");
      }
    }
  }
}

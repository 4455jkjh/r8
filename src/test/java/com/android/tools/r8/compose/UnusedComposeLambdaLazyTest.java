// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compose;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedComposeLambdaLazyTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addOptionsModification(
            options -> options.horizontalClassMergerOptions().disableForTesting())
        .addKeepMainRule(TestClass.class)
        .compile()
        .inspect(
            inspector -> {
              assertTrue("Lambda0 should be present.", inspector.clazz(Lambda0.class).isPresent());
              assertTrue("Lambda1 should be present.", inspector.clazz(Lambda1.class).isPresent());
              assertFalse("Lambda2 should be deleted", inspector.clazz(Lambda2.class).isPresent());
              assertFalse("Lambda3 should be deleted", inspector.clazz(Lambda3.class).isPresent());
              assertFalse("Lambda4 should be deleted", inspector.clazz(Lambda4.class).isPresent());
              assertFalse("Lambda5 should be deleted", inspector.clazz(Lambda5.class).isPresent());
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Lambda 0", "Lambda 1");
  }

  public static class TestClass {

    public static void main(String[] args) {
      System.out.println("Lambda " + HostClass.getLambda0().invoke(null, null));
      System.out.println("Lambda " + HostClass.getLambda1().invoke(null, null));
    }
  }

  public interface Function2<A, B, C> {
    C invoke(A a, B b);
  }

  static class HostClass {

    public static Function2<Object, Object, Object> lambda0;
    public static Function2<Object, Object, Object> lambda1;
    public static Function2<Object, Object, Object> lambda2;
    public static Function2<Object, Object, Object> lambda3;
    public static Function2<Object, Object, Object> lambda4;
    public static Function2<Object, Object, Object> lambda5;

    public static Function2<Object, Object, Object> getLambda0() {
      if (lambda0 == null) {
        lambda0 = new ComposableLambdaImpl(0, false, Lambda0.INSTANCE);
      }
      return lambda0;
    }

    public static Function2<Object, Object, Object> getLambda1() {
      if (lambda1 == null) {
        lambda1 = new ComposableLambdaImpl(123, false, Lambda1.INSTANCE);
      }
      return lambda1;
    }

    public static Function2<Object, Object, Object> getLambda2() {
      if (lambda2 == null) {
        lambda2 = new ComposableLambdaImpl(456, false, Lambda2.INSTANCE);
      }
      return lambda2;
    }

    public static Function2<Object, Object, Object> getLambda3() {
      if (lambda3 == null) {
        lambda3 = new ComposableLambdaImpl(789, false, Lambda3.INSTANCE);
      }
      return lambda3;
    }

    public static Function2<Object, Object, Object> getLambda4() {
      if (lambda4 == null) {
        lambda4 = new ComposableLambdaImpl(101112, false, Lambda4.INSTANCE);
      }
      return lambda4;
    }

    public static Function2<Object, Object, Object> getLambda5() {
      if (lambda5 == null) {
        lambda5 = new ComposableLambdaImpl(131415, false, Lambda5.INSTANCE);
      }
      return lambda5;
    }
  }

  static class Lambda0 implements Function2<Object, Object, Object> {

    public static final Lambda0 INSTANCE = new Lambda0();

    public Object invoke(Object p1, Object p2) {
      return "0";
    }
  }

  static class Lambda1 implements Function2<Object, Object, Object> {

    public static final Lambda1 INSTANCE = new Lambda1();

    public Object invoke(Object p1, Object p2) {
      return "1";
    }
  }

  static class Lambda2 implements Function2<Object, Object, Object> {

    public static final Lambda2 INSTANCE = new Lambda2();

    public Object invoke(Object p1, Object p2) {
      return "2";
    }
  }

  static class Lambda3 implements Function2<Object, Object, Object> {

    public static final Lambda3 INSTANCE = new Lambda3();

    public Object invoke(Object p1, Object p2) {
      return HostClass.getLambda2().invoke(p1, p2);
    }
  }

  static class Lambda4 implements Function2<Object, Object, Object> {

    public static final Lambda4 INSTANCE = new Lambda4();

    public Object invoke(Object p1, Object p2) {
      return HostClass.getLambda3().invoke(p1, p2);
    }
  }

  static class Lambda5 implements Function2<Object, Object, Object> {

    public static final Lambda5 INSTANCE = new Lambda5();

    public Object invoke(Object p1, Object p2) {
      return HostClass.getLambda4().invoke(p1, p2);
    }
  }

  static class ComposableLambdaImpl implements Function2<Object, Object, Object> {

    final int key;
    final boolean tracked;
    Function2<Object, Object, Object> content;

    public ComposableLambdaImpl(
        int key, boolean tracked, Function2<Object, Object, Object> content) {
      this.key = key;
      this.tracked = tracked;
      this.content = content;
    }

    public Object invoke(Object p1, Object p2) {
      trackRead();
      return content.invoke(p1, p2);
    }

    private void trackRead() {
      if (tracked) {
        System.out.println("Tracking read for key " + key);
      }
    }
  }
}

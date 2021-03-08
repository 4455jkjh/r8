// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.invokevirtual;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeVirtualPrivateBaseWithDefaultTest extends TestBase {

  private final TestParameters parameters;
  private final String INVALID_EXPECTED = "I::foo";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public InvokeVirtualPrivateBaseWithDefaultTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/171550305): Should be illegal access for DEX.
        .applyIf(
            parameters.isDexRuntime() || parameters.getApiLevel().isEqualTo(AndroidApiLevel.B),
            r -> r.assertSuccessWithOutputLines(INVALID_EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(IllegalAccessError.class));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(I.class)
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters.getApiLevel())
        .compile()
        .run(parameters.getRuntime(), Main.class)
        // TODO(b/171550305): Should be illegal access.
        .applyIf(
            parameters.isDexRuntime(),
            r -> r.assertSuccessWithOutputLines(INVALID_EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(IllegalAccessError.class));
  }

  @NoVerticalClassMerging
  public interface I {

    default void foo() {
      System.out.println("I::foo");
    }
  }

  @NoVerticalClassMerging
  public static class Base {

    private void foo() {
      System.out.println("Base::foo");
    }
  }

  public static class Sub extends Base implements I {}

  public static class Main {

    public static void main(String[] args) {
      new Sub().foo();
    }
  }
}

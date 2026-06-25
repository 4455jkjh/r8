// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.vertical;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerSuperCallRewritingTest extends TestBase {

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
        .assertSuccessWithOutputLines("Hello, world!");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addVerticallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertMergedIntoSubtype(Img.class)
                    .assertMergedIntoSubtype(GenericTarget.class)
                    .assertNoOtherClassesMerged())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  public static class Main {

    public static void main(String[] args) {
      int n = args.length;
      make(n).onError(new RealImg());
      make(n).onSuccess(new RealImg());
    }

    private static Target make(int n) {
      if (n == 0) {
        return new CallbackTarget("v");
      } else if (n == 1) {
        return new ImageTarget("v");
      } else {
        return new Target() {};
      }
    }
  }

  public interface Img {}

  public static class RealImg implements Img {}

  public interface Target {

    default void onError(Img e) {}

    default void onSuccess(Img r) {}
  }

  public abstract static class GenericTarget implements Target {

    @Override
    public void onSuccess(Img r) {}

    @Override
    public void onError(Img e) {
      if (e instanceof RealImg) {
        System.out.println("Hello, world!");
      }
    }
  }

  public static class ImageTarget extends GenericTarget {

    public final Object view;

    public ImageTarget(Object view) {
      this.view = view;
    }
  }

  public static class CallbackTarget extends ImageTarget {

    public CallbackTarget(Object view) {
      super(view);
    }

    @Override
    public void onError(Img e) {
      super.onError(e);
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test for b/470510982.
@RunWith(Parameterized.class)
public class DeadLambdaAfterSdkIntOptimizationWithDontShrinkTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withApiLevel(AndroidApiLevel.M).build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getProgramClassFileData())
        .addLibraryClassFileData(getLibraryClassFileData())
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addDontObfuscate()
        .addDontOptimize()
        .addDontShrink()
        .compile()
        // We shouldn't emit unreachable lambdas, even with -dontshrink.
        .inspect(inspector -> assertEquals(1, inspector.allClasses().size()));
  }

  private static Collection<byte[]> getProgramClassFileData() {
    return ImmutableList.of(
        transformer(Main.class)
            .replaceClassDescriptorInMethodInstructions(
                descriptor(VERSION.class), "Landroid/os/Build$VERSION;")
            .transform());
  }

  private static Collection<byte[]> getLibraryClassFileData() throws NoSuchFieldException {
    return ImmutableList.of(
        transformer(VERSION.class)
            .setClassDescriptor("Landroid/os/Build$VERSION;")
            .setAccessFlags(VERSION.class.getDeclaredField("SDK_INT"), AccessFlags::setFinal)
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      // Lambda class will be synthesized, then the branch will be pruned, then the code will be
      // traced. As a result, tracing will not reach the lambda.
      if (VERSION.SDK_INT < 23) {
        Runnable r = () -> System.out.println("Hello, world!");
      }
    }
  }

  public static class /*android.os.Build$*/ VERSION {

    public static /*final*/ int SDK_INT = -1;
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.redex;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReoptimizeToNativePcTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ReoptimizeToNativePcTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    AndroidApiLevel initialMinApi = AndroidApiLevel.N;
    AndroidApiLevel reoptimizationMinApi = AndroidApiLevel.CINNAMON_BUN;

    Path r8Output =
        testForR8(Backend.DEX)
            .setMinApi(initialMinApi)
            .setMode(CompilationMode.RELEASE)
            .addProgramClasses(TestClass.class)
            .addKeepMainRule(TestClass.class)
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject clazz = inspector.clazz(TestClass.class);
                  MethodSubject method = clazz.uniqueMethodWithOriginalName("main");
                  assertTrue("Expected debug info on N", method.hasLineNumberTable());
                })
            .writeToZip();

    testForD8(Backend.DEX)
        .setMinApi(reoptimizationMinApi)
        .setExperimentalReoptimizeDex(true)
        .setMode(CompilationMode.RELEASE)
        .addProgramFiles(r8Output)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(TestClass.class);
              MethodSubject method = clazz.uniqueMethodWithOriginalName("main");
              // TODO(b/498336713): Fix reoptimizing to native PC, making this false.
              assertTrue(method.hasLineNumberTable());
            });
  }

  static class TestClass {
    public static void main(String[] args) {
      if (args.length == 0) {
        throw new RuntimeException("Crash!");
      }
    }
  }
}

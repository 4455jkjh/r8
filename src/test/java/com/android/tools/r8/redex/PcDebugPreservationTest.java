// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.redex;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.Box;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PcDebugPreservationTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public PcDebugPreservationTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    // Ensure that when non-native PC-based debug info is used, it is read as pc-based debug info.
    AndroidApiLevel initialMinAPi = AndroidApiLevel.N;
    AndroidApiLevel reoptimizationMinApi = AndroidApiLevel.N;

    Box<String> currentAMethodName = new Box<>();
    Path r8Output =
        testForR8(Backend.DEX)
            .setMinApi(initialMinAPi)
            .setMode(CompilationMode.RELEASE)
            .addProgramClasses(TestClass.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject clazz = inspector.clazz(TestClass.class);
                  MethodSubject method = clazz.uniqueMethodWithOriginalName("a");
                  currentAMethodName.set(method.getFinalName());
                  assertTrue("Expected debug info", method.hasLineNumberTable());
                  assertTrue(
                      "Expected PC-based debug info",
                      method.getMethod().getCode().asDexCode().getDebugInfo().isPcBasedInfo());
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
              MethodSubject method = clazz.uniqueMethodWithOriginalName(currentAMethodName.get());
              assertTrue("Expected debug info", method.hasLineNumberTable());
              assertTrue(
                  "Expected PC-based debug info",
                  method.getMethod().getCode().asDexCode().getDebugInfo().isPcBasedInfo());
            });
  }

  static class TestClass {
    public static void main(String[] args) {
      // Multiple methods ensure that pc-encoding is optimal because of sharing.
      a(args);
      b(args);
      c(args);
      d(args);
    }

    @NeverInline
    public static void a(String[] args) {
      if (args.length == 100) throw new RuntimeException();
    }

    @NeverInline
    public static void b(String[] args) {
      if (args.length == 100) throw new RuntimeException();
    }

    @NeverInline
    public static void c(String[] args) {
      if (args.length == 100) throw new RuntimeException();
    }

    @NeverInline
    public static void d(String[] args) {
      if (args.length == 100) throw new RuntimeException();
    }
  }
}

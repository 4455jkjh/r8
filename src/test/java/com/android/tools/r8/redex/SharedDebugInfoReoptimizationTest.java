// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.redex;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexDebugInfo;
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
public class SharedDebugInfoReoptimizationTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public SharedDebugInfoReoptimizationTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    AndroidApiLevel apiLevel = AndroidApiLevel.N;
    Box<String> m1Name = new Box<>("method1_1");
    Box<String> m2Name = new Box<>("method2_1");

    Path r8Output =
        testForR8(Backend.DEX)
            .setMinApi(apiLevel)
            .setMode(CompilationMode.RELEASE)
            .addProgramClasses(TestClass.class)
            .addKeepMainRule(TestClass.class)
            .enableInliningAnnotations()
            .compile()
            .inspect(
                inspector -> {
                  ClassSubject clazz = inspector.clazz(TestClass.class);
                  MethodSubject m1 = clazz.uniqueMethodWithOriginalName("method1_1");
                  MethodSubject m2 = clazz.uniqueMethodWithOriginalName("method2_1");
                  m1Name.set(m1.getFinalName());
                  m2Name.set(m2.getFinalName());
                  DexDebugInfo info1 = m1.getMethod().getCode().asDexCode().getDebugInfo();
                  DexDebugInfo info2 = m2.getMethod().getCode().asDexCode().getDebugInfo();
                  assertNotNull("Debug info for method1_1 should not be null", info1);
                  assertNotNull("Debug info for method2_1 should not be null", info2);
                  assertTrue("Expected PC-based debug info", info1.isPcBasedInfo());
                  assertTrue("Expected PC-based debug info", info2.isPcBasedInfo());
                  assertSame("Debug info should be shared in R8", info1, info2);
                })
            .writeToZip();

    testForD8(Backend.DEX)
        .setMinApi(apiLevel)
        .setExperimentalReoptimizeDex(true)
        .setMode(CompilationMode.RELEASE)
        .addProgramFiles(r8Output)
        // Force D8 to convert DEX to IR and back (disabling passthrough).
        // This emulates usage of SDK_INT (revisit code) where none of the checks can be optimized.
        .addOptionsModification(options -> options.passthroughDexCode = false)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(TestClass.class);
              MethodSubject m1 = clazz.uniqueMethodWithOriginalName(m1Name.get());
              MethodSubject m2 = clazz.uniqueMethodWithOriginalName(m2Name.get());
              DexDebugInfo info1 = m1.getMethod().getCode().asDexCode().getDebugInfo();
              DexDebugInfo info2 = m2.getMethod().getCode().asDexCode().getDebugInfo();
              assertNotNull("Debug info for method1_1 should not be null", info1);
              assertNotNull("Debug info for method2_1 should not be null", info2);
              // TODO(b/498336713): These should be flipped, right now revisited code is always
              // event based.
              assertFalse("Expected PC-based debug info", info1.isPcBasedInfo());
              assertFalse("Expected PC-based debug info", info2.isPcBasedInfo());
              assertNotSame("Debug info should be shared in D8", info1, info2);
            });
  }

  static class TestClass {
    public static void main(String[] args) {
      // Methods are duplicated to ensure that PC encoding is smaller than event encoding.
      method1_1();
      method1_2();
      method1_3();
      method1_4();
      method1_5();
      method2_1();
      method2_2();
      method2_3();
      method2_4();
      method2_5();
    }

    @NeverInline
    public static void method1_1() {
      if (System.currentTimeMillis() == 0) throw new RuntimeException("C1");
    }

    @NeverInline
    public static void method1_2() {
      if (System.currentTimeMillis() == 0) throw new RuntimeException("C1");
    }

    @NeverInline
    public static void method1_3() {
      if (System.currentTimeMillis() == 0) throw new RuntimeException("C1");
    }

    @NeverInline
    public static void method1_4() {
      if (System.currentTimeMillis() == 0) throw new RuntimeException("C1");
    }

    @NeverInline
    public static void method1_5() {
      if (System.currentTimeMillis() == 0) throw new RuntimeException("C1");
    }

    @NeverInline
    public static void method2_1() {
      System.out.println("H");
      System.out.println("W");
      if (System.currentTimeMillis() == 1) throw new RuntimeException("C2");
    }

    @NeverInline
    public static void method2_2() {
      System.out.println("H");
      System.out.println("W");
      if (System.currentTimeMillis() == 1) throw new RuntimeException("C2");
    }

    @NeverInline
    public static void method2_3() {
      System.out.println("H");
      System.out.println("W");
      if (System.currentTimeMillis() == 1) throw new RuntimeException("C2");
    }

    @NeverInline
    public static void method2_4() {
      System.out.println("H");
      System.out.println("W");
      if (System.currentTimeMillis() == 1) throw new RuntimeException("C2");
    }

    @NeverInline
    public static void method2_5() {
      System.out.println("H");
      System.out.println("W");
      if (System.currentTimeMillis() == 1) throw new RuntimeException("C2");
    }
  }
}

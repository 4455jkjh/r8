// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.redex;

import static org.junit.Assert.assertNotNull;
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
public class SharedDebugInfoJumboRewritingTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public SharedDebugInfoJumboRewritingTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void test() throws Exception {
    AndroidApiLevel apiLevel = AndroidApiLevel.N;
    Box<String> m1Name = new Box<>("method1");
    Box<String> m2Name = new Box<>("method2");

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
                  MethodSubject m1 = clazz.uniqueMethodWithOriginalName("method1");
                  MethodSubject m2 = clazz.uniqueMethodWithOriginalName("method2");
                  m1Name.set(m1.getFinalName());
                  m2Name.set(m2.getFinalName());
                  DexDebugInfo info1 = m1.getMethod().getCode().asDexCode().getDebugInfo();
                  DexDebugInfo info2 = m2.getMethod().getCode().asDexCode().getDebugInfo();
                  assertNotNull("Debug info for method1 should not be null", info1);
                  assertNotNull("Debug info for method2 should not be null", info2);
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
        // Force D8 to rewrite const-string to jumbo.
        // This emulates a restructuring of dex files that moves to string const to jumbo space.
        .addOptionsModification(options -> options.testing.forceJumboStringProcessing = true)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(TestClass.class);
              MethodSubject m1 = clazz.uniqueMethodWithOriginalName(m1Name.get());
              MethodSubject m2 = clazz.uniqueMethodWithOriginalName(m2Name.get());
              DexDebugInfo info1 = m1.getMethod().getCode().asDexCode().getDebugInfo();
              DexDebugInfo info2 = m2.getMethod().getCode().asDexCode().getDebugInfo();
              assertNotNull("Debug info for method1 should not be null", info1);
              assertNotNull("Debug info for method2 should not be null", info2);
              assertTrue("Expected event-based debug info", info1.isEventBasedInfo());
              assertTrue("Expected event-based debug info", info2.isEventBasedInfo());
              assertSame("Debug info should be shared in D8", info1, info2);
            });
  }

  static class TestClass {
    public static void main(String[] args) {
      // Methods are duplicated to ensure that PC encoding is smaller than event encoding.
      method1();
      method2();
      method3();
      method4();
      method5();
    }

    @NeverInline
    public static void method1() {
      if (System.currentTimeMillis() == 0) throw new RuntimeException("C");
    }

    @NeverInline
    public static void method2() {
      if (System.currentTimeMillis() == 0) throw new RuntimeException("C");
    }

    @NeverInline
    public static void method3() {
      if (System.currentTimeMillis() == 0) throw new RuntimeException("C");
    }

    @NeverInline
    public static void method4() {
      if (System.currentTimeMillis() == 0) throw new RuntimeException("C");
    }

    @NeverInline
    public static void method5() {
      if (System.currentTimeMillis() == 0) throw new RuntimeException("C");
    }
  }
}

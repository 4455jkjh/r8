// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PGSourceFileStripEmbeddedPcDebugInfoTest extends TestBase {

  @Parameter(0)
  public boolean convertPcBasedDebugInfoToNative;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, convert: {0}")
  public static List<Object[]> data() {
    return buildParameters(BooleanUtils.values(), getTestParameters().withNoneRuntime().build());
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult r8CompileResult =
        testForR8(Backend.DEX)
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addKeepAttributeSourceFile()
            .addKeepRules("-renamesourcefileattribute PG")
            .enableInliningAnnotations()
            // Something below Android O so that we can't use native debug info.
            .setMinApi(AndroidApiLevel.N)
            .compile()
            .inspect(
                inspector -> {
                  MethodSubject aMethod =
                      inspector.clazz(Main.class).uniqueMethodWithOriginalName("a");
                  assertTrue(
                      aMethod.getMethod().getCode().asDexCode().getDebugInfo().isPcBasedInfo());
                });

    // Retarget Android O. This should allow the use of native debug info.
    testForD8(Backend.DEX)
        .addProgramResourceProviders(r8CompileResult.getOutputProgramResourceProviders())
        .addOptionsModification(
            options -> {
              assertTrue(options.convertPcBasedDebugInfoToNative);
              options.convertPcBasedDebugInfoToNative = convertPcBasedDebugInfoToNative;
            })
        .release()
        .setMinApi(AndroidApiLevel.O)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject aMethod = inspector.clazz(Main.class).uniqueMethodWithOriginalName("a");
              DexDebugInfo debugInfo = aMethod.getMethod().getCode().asDexCode().getDebugInfo();
              if (convertPcBasedDebugInfoToNative) {
                assertNull(debugInfo);
              } else {
                assertTrue(debugInfo.isPcBasedInfo());
              }
            });
  }

  static class Main {

    public static void main(String[] args) {
      // Multiple methods ensure that pc-encoding is optimal because of sharing.
      a();
      b();
      c();
      d();
    }

    @NeverInline
    public static void a() {
      if (System.currentTimeMillis() == 0) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void b() {
      if (System.currentTimeMillis() == 0) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void c() {
      if (System.currentTimeMillis() == 0) {
        throw new RuntimeException();
      }
    }

    @NeverInline
    public static void d() {
      if (System.currentTimeMillis() == 0) {
        throw new RuntimeException();
      }
    }
  }
}

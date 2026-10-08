// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.utils.AndroidApiLevel;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConsolidatePcBasedDebugInfoD8MergeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().build();
  }

  private D8TestCompileResult compileWithPcBasedDebugInfo(Class<?> clazz) throws Exception {
    return testForD8(parameters.getBackend())
        .addProgramClasses(clazz)
        .addOptionsModification(options -> options.getTestingOptions().forcePcBasedEncoding = true)
        .internalEnableMappingOutput()
        .release()
        .setMinApi(AndroidApiLevel.N)
        .compile();
  }

  private static int getMaxPc(D8TestCompileResult compileResult, Class<?> clazz) throws Exception {
    return compileResult
        .inspector()
        .clazz(clazz)
        .uniqueMethodWithFinalName("work")
        .getMethod()
        .getDexCode()
        .getDebugInfo()
        .asPcBasedInfo()
        .getMaxPc();
  }

  @Test
  public void testConsolidatesPcBasedDebugInfoAcrossMergedDexInputs() throws Exception {
    D8TestCompileResult compileResultA = compileWithPcBasedDebugInfo(ClassA.class);
    D8TestCompileResult compileResultB = compileWithPcBasedDebugInfo(ClassB.class);

    int maxPcA = getMaxPc(compileResultA, ClassA.class);
    int maxPcB = getMaxPc(compileResultB, ClassB.class);
    assertNotEquals(maxPcA, maxPcB);

    testForD8(parameters.getBackend())
        .addProgramFiles(compileResultA.writeToZip(), compileResultB.writeToZip())
        .release()
        .setMinApi(AndroidApiLevel.S)
        .compile()
        .inspect(
            inspector -> {
              DexDebugInfo debugInfoA =
                  inspector
                      .clazz(ClassA.class)
                      .uniqueMethodWithOriginalName("work")
                      .getMethod()
                      .getDexCode()
                      .getDebugInfo();
              DexDebugInfo debugInfoB =
                  inspector
                      .clazz(ClassB.class)
                      .uniqueMethodWithOriginalName("work")
                      .getMethod()
                      .getDexCode()
                      .getDebugInfo();
              assertTrue(debugInfoA.isPcBasedInfo());
              assertTrue(debugInfoB.isPcBasedInfo());
              assertSame(debugInfoA, debugInfoB);
              assertEquals(Math.max(maxPcA, maxPcB), debugInfoA.asPcBasedInfo().getMaxPc());
            });
  }

  @Test
  public void testRespectsOverheadThreshold() throws Exception {
    D8TestCompileResult compileResultA = compileWithPcBasedDebugInfo(ClassA.class);
    D8TestCompileResult compileResultB = compileWithPcBasedDebugInfo(ClassB.class);
    D8TestCompileResult compileResultC = compileWithPcBasedDebugInfo(ClassC.class);

    int maxPcA = getMaxPc(compileResultA, ClassA.class);
    int maxPcB = getMaxPc(compileResultB, ClassB.class);
    int maxPcC = getMaxPc(compileResultC, ClassC.class);
    assertTrue(maxPcA < maxPcB);
    assertTrue(maxPcB < maxPcC);

    testForD8(parameters.getBackend())
        .addProgramFiles(
            compileResultA.writeToZip(), compileResultB.writeToZip(), compileResultC.writeToZip())
        .addOptionsModification(
            options -> {
              // Choose a threshold that allows consolidating ClassA and ClassB into a single
              // bucket,
              // but flushes before ClassC.
              int overheadAB = maxPcB - maxPcA;
              int overheadABC = (maxPcC - maxPcA) + (maxPcC - maxPcB);
              assertTrue(overheadAB < overheadABC);
              options.getTestingOptions().pcBasedDebugEncodingOverheadThreshold = overheadAB;
            })
        .release()
        .setMinApi(AndroidApiLevel.S)
        .compile()
        .inspect(
            inspector -> {
              DexDebugInfo debugInfoA =
                  inspector
                      .clazz(ClassA.class)
                      .uniqueMethodWithOriginalName("work")
                      .getMethod()
                      .getDexCode()
                      .getDebugInfo();
              DexDebugInfo debugInfoB =
                  inspector
                      .clazz(ClassB.class)
                      .uniqueMethodWithOriginalName("work")
                      .getMethod()
                      .getDexCode()
                      .getDebugInfo();
              DexDebugInfo debugInfoC =
                  inspector
                      .clazz(ClassC.class)
                      .uniqueMethodWithOriginalName("work")
                      .getMethod()
                      .getDexCode()
                      .getDebugInfo();
              assertTrue(debugInfoA.isPcBasedInfo());
              assertTrue(debugInfoB.isPcBasedInfo());
              assertTrue(debugInfoC.isPcBasedInfo());
              assertSame(debugInfoA, debugInfoB);
              assertEquals(maxPcB, debugInfoA.asPcBasedInfo().getMaxPc());
              assertNotSame(debugInfoB, debugInfoC);
              assertEquals(maxPcC, debugInfoC.asPcBasedInfo().getMaxPc());
            });
  }

  public static class ClassA {
    public static int work(int x) {
      if (x < 0) {
        throw new IllegalArgumentException();
      }
      return x + 1;
    }
  }

  public static class ClassB {
    public static int work(int x) {
      int sum = 0;
      for (int i = 0; i < x; i++) {
        sum += (i * 3) ^ (x - i);
        if (sum > 1000) {
          System.out.println(sum);
        }
      }
      if (sum < 0) {
        throw new IllegalStateException();
      }
      return sum;
    }
  }

  public static class ClassC {
    public static int work(int x) {
      int sum = 0;
      for (int i = 0; i < x; i++) {
        sum += (i * 3) ^ (x - i);
        if (sum > 100) {
          System.out.println(sum);
        }
        if (sum > 200) {
          System.out.println(sum + 1);
        }
        if (sum > 300) {
          System.out.println(sum + 2);
        }
        if (sum > 400) {
          System.out.println(sum + 3);
        }
      }
      if (sum < 0) {
        throw new IllegalStateException();
      }
      return sum;
    }
  }
}

// Copyright (c) 2019 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b120164595;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Regression test for art issue with multi catch-handlers.
 * The problem is that if d8/r8 creates the same target address for two different exceptions,
 * art will use that address to check if it was already handled.
 */
class TestClass {
  public static void main(String[] args) {
    for (int i = 0; i < 20000; i++) {
      toBeOptimized();
    }
  }

  private static void toBeOptimized() {
    try {
      willThrow();
    } catch (IllegalStateException | NullPointerException e) {
      if (e instanceof NullPointerException) {
        return;
      }
      throw new Error("Expected NullPointerException");
    }
  }

  private static void willThrow() {
    throw new NullPointerException();
  }
}

@RunWith(Parameterized.class)
public class B120164595 extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    TestCompileResult<?, ?> d8Result =
        testForD8(parameters.getBackend())
            .addProgramClasses(TestClass.class)
            .setMinApi(parameters)
            .compile();
    checkResult(d8Result);
  }

  @Test
  public void testR8() throws Exception {
    TestCompileResult<?, ?> r8Result =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class)
            .addKeepClassAndMembersRules(TestClass.class)
            .setMinApi(parameters)
            .compile();
    checkResult(r8Result);
  }

  private void checkResult(TestCompileResult<?, ?> result) throws Exception {
    result.inspect(
        inspector -> {
          ClassSubject classSubject = inspector.clazz(TestClass.class);
          assertThat(classSubject, isPresent());
          MethodSubject methodSubject = classSubject.uniqueMethodWithOriginalName("toBeOptimized");
          assertThat(methodSubject, isPresent());
          DexCode code = methodSubject.getMethod().getCode().asDexCode();
          assertEquals(1, code.getHandlers().length);
          TryHandler handler = code.getHandlers()[0];
          assertEquals(2, handler.pairs.length);
          if (parameters.getApiLevel().isLessThan(AndroidApiLevel.Q)) {
            assertNotEquals(handler.pairs[0].addr, handler.pairs[1].addr);
          } else {
            assertEquals(handler.pairs[0].addr, handler.pairs[1].addr);
          }
        });
    result
        .applyIf(
            parameters.getDexRuntimeVersion().isNewerThanOrEqual(Version.V7_0_0),
            r -> r.addVmArguments("-Xusejit:true"))
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithEmptyOutput();
  }
}

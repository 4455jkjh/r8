// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.regalloc;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexLongToInt;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Regression test for register hints bypassing the long-to-int workaround (see
 * InternalOptions#canHaveLongToIntBug).
 *
 * <p>The result of foo() is allocated first and takes the (then free) low register of the argument
 * a. Since both flow into the same phi, the result of the long-to-int instruction is hinted to use
 * the same register. At the long-to-int instruction the argument a dies, so its register is free,
 * and the hint must be rejected by the long-to-int workaround.
 */
@RunWith(Parameterized.class)
public class LongToIntResultOverlappingOperandHintTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimes()
        .withApiLevel(AndroidApiLevel.L)
        .withApiLevel(AndroidApiLevel.M)
        .build();
  }

  @Test
  public void test() throws Exception {
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .compile()
        .inspect(
            inspector -> {
              MethodSubject testMethodSubject =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());
              List<DexLongToInt> longToIntInstructions =
                  Arrays.stream(testMethodSubject.getMethod().getCode().asDexCode().instructions)
                      .filter(instruction -> instruction instanceof DexLongToInt)
                      .map(DexLongToInt.class::cast)
                      .collect(Collectors.toList());
              assertEquals(1, longToIntInstructions.size());
              DexLongToInt longToInt = longToIntInstructions.get(0);
              if (parameters.getApiLevel().isLessThan(AndroidApiLevel.M)) {
                // The long-to-int workaround is applied and the hint must be rejected.
                assertNotEquals(longToInt.A, longToInt.B);
              } else {
                // Without the workaround the hint is used and the result overlaps the operand.
                assertEquals(longToInt.A, longToInt.B);
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "1");
  }

  static class Main {

    static boolean flag;
    static int f;

    public static void main(String[] args) {
      flag = args.length == 0;
      System.out.println(test(1L));
      flag = !flag;
      System.out.println(test(1L));
    }

    static int test(long a) {
      int result;
      if (flag) {
        result = foo();
      } else {
        result = (int) a;
      }
      f = result;
      bar(result);
      return result;
    }

    static int foo() {
      return 42;
    }

    static void bar(int i) {}
  }
}

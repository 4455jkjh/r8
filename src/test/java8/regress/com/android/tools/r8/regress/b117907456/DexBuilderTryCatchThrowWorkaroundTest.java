// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b117907456;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexThrow;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DexBuilderTryCatchThrowWorkaroundTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public DexBuilderTryCatchThrowWorkaroundTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(C.class)
        .addKeepMainRule(C.class)
        .enableInliningAnnotations()
        .release()
        .compile()
        .inspect(
            inspect -> {
              ClassSubject clazz = inspect.clazz(C.class);
              assertThat(clazz, isPresent());
              MethodSubject method = clazz.uniqueMethodWithOriginalName("f");
              assertThat(method, isPresent());

              DexCode code = method.getMethod().getCode().asDexCode();
              DexInstruction[] instructions = code.instructions;
              DexCode.Try tryItem = code.tries[0];
              int tryEnd = tryItem.startAddress + tryItem.instructionCount;

              // The method ends with the loop back edge, so the throw is never the last instruction
              // and the dalvik tracing JIT workaround does not apply. The throw is still covered by
              // the try.
              assertTrue(instructions[instructions.length - 1] instanceof DexGoto);
              DexInstruction throwInsn =
                  Arrays.stream(instructions)
                      .filter(instruction -> instruction instanceof DexThrow)
                      .findFirst()
                      .get();
              assertTrue(throwInsn.getOffset() < tryEnd);
            })
        .run(parameters.getRuntime(), C.class)
        .assertSuccessWithOutputLines("CAUGHT");
  }

  static class C {
    static volatile boolean loop = true;
    static int count = 0;

    @NeverInline
    static void checkIteration() {
      if (count++ > 0) {
        throw new Error("CAUGHT");
      }
    }

    @NeverInline
    public static void f() {
      while (true) {
        checkIteration();
        try {
          if (loop) {
            // loop
          } else {
            throw new IllegalStateException("done");
          }
        } catch (Exception e) {
          // swallow, loop
        }
      }
    }

    public static void main(String[] args) {
      try {
        loop = args.length == 42;
        f();
        System.out.println("UNREACHABLE");
      } catch (IllegalStateException e) {
        System.out.println("ESCAPED: " + e.getMessage());
      } catch (Error e) {
        System.out.println(e.getMessage());
      }
    }
  }
}

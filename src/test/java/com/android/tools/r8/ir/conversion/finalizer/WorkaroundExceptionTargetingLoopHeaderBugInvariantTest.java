// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.finalizer;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexNop;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WorkaroundExceptionTargetingLoopHeaderBugInvariantTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public WorkaroundExceptionTargetingLoopHeaderBugInvariantTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testExceptionTargetingLoopHeaderBug() throws Exception {
    testForD8()
        .addInnerClasses(WorkaroundExceptionTargetingLoopHeaderBugInvariantTest.class)
        .release()
        .setMinApi(AndroidApiLevel.L)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject method =
                  inspector
                      .clazz(TestClass.class)
                      .uniqueMethodWithOriginalName("exceptionTargetingLoopHeader");
              assertTrue(method.isPresent());
              DexCode code = method.getMethod().getCode().asDexCode();
              int nops = 0;
              for (DexInstruction insn : code.instructions) {
                if (insn instanceof DexNop) {
                  nops++;
                }
              }
              Set<Integer> handlerOffsets = new HashSet<>();
              if (code.handlers != null) {
                for (TryHandler handler : code.handlers) {
                  for (TryHandler.TypeAddrPair pair : handler.pairs) {
                    handlerOffsets.add(pair.addr);
                  }
                }
              }
              Set<Integer> backEdgeTargets = new HashSet<>();
              for (DexInstruction insn : code.instructions) {
                if (insn instanceof DexGoto) {
                  int target = insn.getOffset() + insn.getTargets()[0];
                  if (target < insn.getOffset()) {
                    backEdgeTargets.add(target);
                  }
                }
              }
              for (int handlerOffset : handlerOffsets) {
                if (backEdgeTargets.contains(handlerOffset) && nops == 0) {
                  throw new AssertionError(
                      "Method exceptionTargetingLoopHeader violates b/111337896 invariant:"
                          + " exception handler at offset "
                          + handlerOffset
                          + " is a loop header back-edge target with 0 nops!");
                }
              }
            });
  }

  static class TestClass {
    static int sink;

    public static void doThrow() throws Exception {
      throw new Exception();
    }

    public static void exceptionTargetingLoopHeader(int x, int a, int b, int c) {
      sink = 2;
      x = (x + a) * b + c;
      while (x < 10) {
        try {
          doThrow();
        } catch (Exception e) {
          x = (x + a) * b + c;
          continue;
        }
        sink = 1;
        x = (x + a) * b + c;
      }
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.finalizer;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexAddLong;
import com.android.tools.r8.dex.code.DexAddLong2Addr;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexMulLong;
import com.android.tools.r8.dex.code.DexMulLong2Addr;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class WorkaroundDex2OatLinkedListBugInvariantTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public WorkaroundDex2OatLinkedListBugInvariantTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testDex2OatLinkedListBugInvariants() throws Exception {
    testForD8()
        .addInnerClasses(WorkaroundDex2OatLinkedListBugInvariantTest.class)
        .release()
        .setMinApi(AndroidApiLevel.M)
        .compile()
        .inspect(this::inspectDex2OatLinkedListBug);
  }

  private void inspectDex2OatLinkedListBug(CodeInspector inspector) {
    List<String> violations = new ArrayList<>();
    for (String methodName :
        new String[] {
          "prefixSharerLinkedListBug",
          "synchronizedLinkedListBug",
          "suffixSharerLinkedListBug",
          "branchInverterLinkedListBug",
          "multipleBlocksLinkedListBug"
        }) {
      MethodSubject method =
          inspector.clazz(TestClass.class).uniqueMethodWithOriginalName(methodName);
      assertTrue(method.isPresent());
      DexCode code = method.getMethod().getCode().asDexCode();
      String v = checkNoBranchTargetStartsWithLongMulThenAdd(methodName, code);
      if (v != null) {
        violations.add(v);
      }
    }
    if (!violations.isEmpty()) {
      throw new AssertionError("Found violations:\n" + String.join("\n", violations));
    }
  }

  private String checkNoBranchTargetStartsWithLongMulThenAdd(String methodName, DexCode code) {
    Set<Integer> branchTargets = new HashSet<>();
    Set<Integer> fallthroughOffsets = new HashSet<>();
    DexInstruction[] insns = code.instructions;
    for (DexInstruction insn : insns) {
      int[] targets = insn.getTargets();
      if (targets != DexInstruction.NO_TARGETS && targets != DexInstruction.EXIT_TARGET) {
        branchTargets.add(insn.getOffset() + targets[0]);
        if (targets.length > 1) {
          fallthroughOffsets.add(insn.getOffset() + targets[1]);
        }
      } else if (targets == DexInstruction.NO_TARGETS) {
        fallthroughOffsets.add(insn.getOffset() + insn.getSize());
      }
    }
    for (int i = 0; i < insns.length - 1; i++) {
      DexInstruction first = insns[i];
      DexInstruction second = insns[i + 1];
      boolean isMul = first instanceof DexMulLong || first instanceof DexMulLong2Addr;
      boolean isAdd = second instanceof DexAddLong || second instanceof DexAddLong2Addr;
      if (isMul
          && isAdd
          && branchTargets.contains(first.getOffset())
          && !fallthroughOffsets.contains(first.getOffset())) {
        return "Method "
            + methodName
            + " violates b/77842465 invariant: non-fallthrough branch target at offset "
            + first.getOffset()
            + " starts with "
            + first
            + " followed by "
            + second;
      }
    }
    return null;
  }

  static class TestClass {
    static int sink;

    public static long prefixSharerLinkedListBug(boolean cond, int x, long a, long b, long c) {
      if (cond) {
        sink = x;
        return c;
      } else {
        sink = x;
        return a * b + c;
      }
    }

    public static synchronized long synchronizedLinkedListBug(
        boolean cond, int x, long a, long b, long c) {
      if (cond) {
        sink = x;
        return c;
      } else {
        sink = x;
        return a * b + c;
      }
    }

    public static long suffixSharerLinkedListBug(boolean cond, long a, long b, long c) {
      if (cond) {
        sink = 1;
        return a * b + c;
      } else {
        sink = 2;
        return a * b + c;
      }
    }

    public static long branchInverterLinkedListBug(boolean cond, long a, long b, long c) {
      if (cond) {
        return a * b + c;
      } else {
        sink = 1;
        return a * b + c;
      }
    }

    public static long multipleBlocksLinkedListBug(
        int choice, long a, long b, long c, long d, long e) {
      if (choice == 1) {
        return a + b;
      } else if (choice == 2) {
        return a * b + c;
      } else {
        return c * d + e;
      }
    }
  }
}

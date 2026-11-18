// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Sets;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// Regression test for b/586975883 (#4b): the CF backend must not swap the loop phi registers in the
// preheader, and must not store a phi operand only to reload it for the phi move.
@RunWith(Parameterized.class)
public class CfPointerChasePhiSwapTest extends TestBase {

  private static final String[] EXPECTED = {
    "1", "-1", "-1", "14", "14", "35", "7", "14", "1", "1", "-1", "415", "39"
  };

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultCfRuntime()
        .withDefaultDexRuntime()
        .withMaximumApiLevel()
        .build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(TestClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addKeepRules(
            "-keep class " + TestClass.class.getTypeName() + " { public static *** *(...); }")
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            inspector -> {
              if (parameters.isDexRuntime()) {
                return;
              }
              CodeInspector javacInspector =
                  new CodeInspector(ToolHelper.getClassFileForTestClass(TestClass.class));
              // javac: 19 bytes. R8 does not swap the phis in the preheader and passes the
              // loaded element on the stack to the phi move: 17 bytes.
              CfCode javac = getCode(javacInspector, "pointerChaseSwap");
              CfCode code = getCode(inspector, "pointerChaseSwap");
              assertTrue(code.bytecodeSizeUpperBound() < javac.bytecodeSizeUpperBound());
              assertEquals(17, code.bytecodeSizeUpperBound());
              assertEquals(2, code.getMaxStack());
              assertEquals(3, code.getMaxLocals());
              assertEquals(0, countStoreLoadOfSameSlot(code));

              // The stored operand is moved to the second phi.
              code = getCode(inspector, "pointerChaseLocalInit");
              assertEquals(0, countStoreLoadOfSameSlot(code));
              assertTrue(
                  code.bytecodeSizeUpperBound()
                      < getCode(javacInspector, "pointerChaseLocalInit").bytecodeSizeUpperBound());

              code = getCode(inspector, "catchOperand");
              assertEquals(0, countStoreLoadOfSameSlot(code));

              // The element is also live in a coalesced phi, so it is stored and duplicated.
              code = getCode(inspector, "sharedOperand");
              assertEquals(0, countStoreLoadOfSameSlot(code));
              assertEquals(1, count(code, i -> isStackInstruction(i, Opcode.Dup)));
              assertTrue(
                  code.bytecodeSizeUpperBound()
                      < getCode(javacInspector, "sharedOperand").bytecodeSizeUpperBound());

              // In slots 0..3, dup and store is not smaller than store and load.
              code = getCode(inspector, "sharedOperandLowSlot");
              assertEquals(0, count(code, i -> i instanceof CfStackInstruction));

              code = getCode(inspector, "sharedWideOperand");
              assertEquals(0, countStoreLoadOfSameSlot(code));
              assertEquals(1, count(code, i -> isStackInstruction(i, Opcode.Dup2)));
              assertTrue(
                  code.bytecodeSizeUpperBound()
                      < getCode(javacInspector, "sharedWideOperand").bytecodeSizeUpperBound());

              // Using dup for the incremented value would prevent the iinc.
              code = getCode(inspector, "iincOperand");
              assertEquals(1, count(code, i -> i instanceof CfIinc));
              assertEquals(0, count(code, i -> i instanceof CfStackInstruction));

              // Increments that cannot be an iinc use dup.
              for (String name : new String[] {"addLargeConstantOperand", "addVariableOperand"}) {
                code = getCode(inspector, name);
                assertEquals(0, count(code, i -> i instanceof CfIinc));
                assertEquals(1, count(code, i -> isStackInstruction(i, Opcode.Dup)));
                assertEquals(0, countStoreLoadOfSameSlot(code));
              }
            });
  }

  @Test
  public void testR8Debug() throws Exception {
    parameters.assumeJvmTestParameters();
    testForR8(parameters)
        .debug()
        .addProgramClasses(TestClass.class)
        .addKeepRules(
            "-keep class " + TestClass.class.getTypeName() + " { public static *** *(...); }")
        .addKeepAllAttributes()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            inspector -> {
              // The named local nxt must remain observable and mutable in the debugger.
              CfCode code = getCode(inspector, "pointerChaseSwap");
              LocalVariableInfo nxt = getLocal(code, "nxt");
              assertNotEquals(getLocal(code, "cur").getIndex(), nxt.getIndex());
              assertNotEquals(getLocal(code, "prev").getIndex(), nxt.getIndex());
              List<CfInstruction> instructions = code.getInstructions();
              int store = indexOf(instructions, i -> isStoreOrLoad(i, true, nxt.getIndex()));
              int load = indexOf(instructions, i -> isStoreOrLoad(i, false, nxt.getIndex()));
              assertEquals(1, count(code, i -> isStoreOrLoad(i, true, nxt.getIndex())));
              assertEquals(1, count(code, i -> isStoreOrLoad(i, false, nxt.getIndex())));
              assertTrue(store < instructions.indexOf(nxt.getStart()));
              assertTrue(instructions.indexOf(nxt.getStart()) < load);
              assertTrue(load < instructions.indexOf(nxt.getEnd()));
            });
  }

  private static CfCode getCode(CodeInspector inspector, String methodName) {
    MethodSubject method =
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName(methodName);
    assertThat(method, isPresent());
    return method.getMethod().getCode().asCfCode();
  }

  private static LocalVariableInfo getLocal(CfCode code, String name) {
    return code.getLocalVariables().stream()
        .filter(local -> local.getLocal().name.toString().equals(name))
        .findFirst()
        .orElseThrow(AssertionError::new);
  }

  private static boolean isStoreOrLoad(CfInstruction instruction, boolean isStore, int index) {
    return isStore
        ? instruction.isStore() && instruction.asStore().getLocalIndex() == index
        : instruction.isLoad() && instruction.asLoad().getLocalIndex() == index;
  }

  private static int indexOf(List<CfInstruction> instructions, Predicate<CfInstruction> predicate) {
    return IntStream.range(0, instructions.size())
        .filter(i -> predicate.test(instructions.get(i)))
        .findFirst()
        .orElseThrow(AssertionError::new);
  }

  // Counts `store n` immediately followed by `load n` without incoming control flow in between.
  private static int countStoreLoadOfSameSlot(CfCode code) {
    Set<CfInstruction> targets = Sets.newIdentityHashSet();
    for (CfTryCatch tryCatch : code.getTryCatchRanges()) {
      targets.addAll(tryCatch.getTargets());
    }
    for (CfInstruction instruction : code.getInstructions()) {
      if (instruction.isJump()) {
        instruction.forEachNormalTarget(targets::add, null);
      }
    }
    List<CfInstruction> instructions = code.getInstructions();
    int count = 0;
    for (int i = 0; i < instructions.size(); i++) {
      if (!instructions.get(i).isStore()) {
        continue;
      }
      int j = i + 1;
      while (j < instructions.size()
          && (instructions.get(j).isPosition()
              || (instructions.get(j).isLabel() && !targets.contains(instructions.get(j))))) {
        j++;
      }
      if (j < instructions.size()
          && instructions.get(j).isLoad()
          && instructions.get(j).asLoad().getLocalIndex()
              == instructions.get(i).asStore().getLocalIndex()) {
        count++;
      }
    }
    return count;
  }

  private static boolean isStackInstruction(CfInstruction instruction, Opcode opcode) {
    return instruction instanceof CfStackInstruction
        && ((CfStackInstruction) instruction).getOpcode() == opcode;
  }

  private static int count(CfCode code, Predicate<CfInstruction> predicate) {
    return (int) code.getInstructions().stream().filter(predicate).count();
  }

  static class TestClass {

    public static int pointerChaseSwap(int[] next, int cur) {
      int prev = -1;
      while (cur >= 0) {
        int nxt = next[cur];
        prev = cur;
        cur = nxt;
      }
      return prev;
    }

    public static int pointerChaseLocalInit(int[] next) {
      int prev = -1;
      int cur = next[0];
      while (cur >= 0) {
        int nxt = next[cur];
        prev = cur;
        cur = nxt;
      }
      return prev;
    }

    // The loop phi has v as operand on two edges, so v must still be stored in the preheader.
    public static int duplicatePhiOperand(int[] a, int seed, int n) {
      int v = a[seed];
      int cur = v;
      int i = 0;
      while (i < n) {
        int e = a[i++];
        if (e > 0) {
          cur = v;
          continue;
        }
        cur += e;
      }
      return cur;
    }

    public static int sharedOperandLowSlot(int[] next) {
      int cur = next[0];
      int last = -1;
      while (cur >= 0) {
        cur = next[cur];
        last = cur;
      }
      return last;
    }

    // The loaded element is an operand of two loop phis, and the other locals are live across the
    // loop such that the phis are not in slots 0..3.
    public static int sharedOperand(int[] next, int p, int q, int r) {
      int cur = next[0];
      int last = -1;
      while (cur >= 0) {
        cur = next[cur];
        last = cur;
      }
      return last + p + q + r;
    }

    public static long sharedWideOperand(long[] next, long p, int q) {
      long cur = next[0];
      long last = -1;
      while (cur >= 0) {
        cur = next[(int) cur];
        last = cur;
      }
      return last + p + q;
    }

    public static int iincOperand(int n, int p, int q, int r) {
      int i = 0;
      int last = 0;
      while (i < n) {
        i++;
        last = i;
      }
      return i + last + p + q + r;
    }

    public static int addLargeConstantOperand(int n, int p, int q, int r) {
      int i = 0;
      int last = 0;
      while (i < n) {
        i += 200;
        last = i;
      }
      return i + last + p + q + r;
    }

    public static int addVariableOperand(int n, int step, int q, int r) {
      int i = 0;
      int last = 0;
      while (i < n) {
        i += step;
        last = i;
      }
      return i + last + step + q + r;
    }

    public static int catchOperand(int[] next, int cur) {
      int prev = -1;
      while (cur >= 0) {
        int nxt;
        try {
          nxt = next[cur];
        } catch (ArrayIndexOutOfBoundsException e) {
          nxt = -1;
        }
        prev = cur;
        cur = nxt;
      }
      return prev;
    }

    public static void main(String[] args) {
      int[] next = {3, -1, 1, 2};
      System.out.println(pointerChaseSwap(next, 0));
      System.out.println(pointerChaseSwap(next, -1));
      System.out.println(catchOperand(next, -1));
      System.out.println(sharedOperand(new int[] {2, -1, 3, 1}, 3, 5, 7));
      System.out.println(sharedWideOperand(new long[] {2, -1, 3, 1}, 10, 5));
      System.out.println(iincOperand(10, 3, 5, 7));
      System.out.println(catchOperand(new int[] {7}, 0));
      System.out.println(catchOperand(next, 0) + 13);
      System.out.println(pointerChaseLocalInit(next));
      System.out.println(duplicatePhiOperand(new int[] {5, -1, 3, -2}, 2, 4));
      System.out.println(sharedOperandLowSlot(new int[] {2, -1, 3, 1}));
      System.out.println(addLargeConstantOperand(10, 3, 5, 7));
      System.out.println(addVariableOperand(10, 3, 5, 7));
    }
  }
}

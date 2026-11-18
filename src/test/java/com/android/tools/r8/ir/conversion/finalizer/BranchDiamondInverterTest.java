// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import static com.android.tools.r8.utils.codeinspector.Matchers.hasLineNumberTable;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResultBase;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BranchDiamondInverterTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testBranchDiamondInversionEliminatesFallthroughGoto() throws Exception {
    testForR8(parameters)
        .addProgramClasses(TestClass.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject testClassSubject = inspector.clazz(TestClass.class);
              MethodSubject methodSubject =
                  testClassSubject.uniqueMethodWithOriginalName("computeWithSharedFallback");
              assertThat(methodSubject, isPresent());
              long gotoCount =
                  methodSubject.streamInstructions().filter(InstructionSubject::isGoto).count();
              long returnCount =
                  methodSubject.streamInstructions().filter(InstructionSubject::isReturn).count();
              if (parameters.isDexRuntime()) {
                // On DEX, ReturnBlockCanonicalizerRewriter merges the returns into a single block,
                // and BranchDiamondInverter eliminates the fallthrough goto of the branch diamond.
                assertEquals(1, gotoCount);
                assertEquals(1, returnCount);
              } else {
                // On CF, return blocks are not canonicalized, so zero gotos are emitted.
                assertEquals(0, gotoCount);
                assertEquals(2, returnCount);
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("15", "-1", "-1");
  }

  @Test
  public void testContinueLoopRelease() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(getDebugTestClassData())
        .addKeepMainRule(DebugTestClass.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(DebugTestClass.class);
              for (String name : new String[] {"sameLineContinue", "multiLineContinue"}) {
                MethodSubject method = clazz.uniqueMethodWithOriginalName(name);
                assertThat(method, isPresent());
                // Only the loop back-edge goto remains after inverting `if (v <= 0) continue;`.
                assertEquals(
                    1, method.streamInstructions().filter(InstructionSubject::isGoto).count());
                if (parameters.isCfRuntime()) {
                  assertEquals(0, countBranchOverGoto(method.getMethod().getCode().asCfCode()));
                }
              }
            })
        .run(parameters.getRuntime(), DebugTestClass.class)
        .assertSuccessWithOutputLines("9", "9");
  }

  @Test
  public void testInlinedGuardLoop() throws Exception {
    parameters.assumeCfRuntime();
    compileInlinedGuardLoop(true)
        .inspect(
            inspector -> {
              MethodSubject methodSubject =
                  inspector
                      .clazz(InlinedGuardLoopTestClass.class)
                      .uniqueMethodWithOriginalName("inlinedGuardLoop");
              assertThat(methodSubject, isPresent());
              assertEquals(0, countBranchOverGoto(methodSubject.getMethod().getCode().asCfCode()));
            })
        .run(parameters.getRuntime(), InlinedGuardLoopTestClass.class)
        .assertSuccessWithOutputLines("1147153055");
  }

  @Test
  public void testInlinedGuardLoopWithoutInverter() throws Exception {
    parameters.assumeCfRuntime();
    compileInlinedGuardLoop(false)
        .inspect(
            inspector -> {
              MethodSubject methodSubject =
                  inspector
                      .clazz(InlinedGuardLoopTestClass.class)
                      .uniqueMethodWithOriginalName("inlinedGuardLoop");
              assertThat(methodSubject, isPresent());
              assertEquals(1, countBranchOverGoto(methodSubject.getMethod().getCode().asCfCode()));
            });
  }

  @Test
  public void testCfDebug() throws Exception {
    parameters.assumeCfRuntime();
    byte[] classData = getDebugTestClassData();
    // Verify non-vacuity when BranchDiamondInverter is disabled in CF debug mode.
    testForR8(parameters)
        .addProgramClassFileData(classData)
        .addKeepAllClassesRule()
        .addKeepAllAttributes()
        .enableInliningAnnotations()
        .debug()
        .addOptionsModification(
            options -> options.getTestingOptions().enableBranchDiamondInverter = false)
        .compile()
        .inspect(
            inspector -> {
              MethodSubject sameLine =
                  inspector
                      .clazz(DebugTestClass.class)
                      .uniqueMethodWithOriginalName("sameLineContinue");
              assertThat(sameLine, isPresent());
              assertEquals(1, countBranchOverGoto(sameLine.getMethod().getCode().asCfCode()));
              assertEquals(
                  2, sameLine.streamInstructions().filter(InstructionSubject::isGoto).count());
            });

    // Verify CF debug mode inverts same-line diamonds, preserves distinct-line continue Gotos,
    // and retains LocalVariableTable and LineNumberTable attributes.
    testForR8(parameters)
        .addProgramClassFileData(classData)
        .addKeepAllClassesRule()
        .addKeepAllAttributes()
        .enableInliningAnnotations()
        .debug()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(DebugTestClass.class);
              MethodSubject sameLine = clazz.uniqueMethodWithOriginalName("sameLineContinue");
              assertThat(sameLine, isPresent());
              assertTrue(sameLine.hasLocalVariableTable());
              assertThat(sameLine, hasLineNumberTable());
              assertEquals(0, countBranchOverGoto(sameLine.getMethod().getCode().asCfCode()));
              assertEquals(
                  1, sameLine.streamInstructions().filter(InstructionSubject::isGoto).count());

              MethodSubject multiLine = clazz.uniqueMethodWithOriginalName("multiLineContinue");
              assertThat(multiLine, isPresent());
              assertTrue(multiLine.hasLocalVariableTable());
              assertThat(multiLine, hasLineNumberTable());
              assertEquals(0, countBranchOverGoto(multiLine.getMethod().getCode().asCfCode()));
              // Both the loop jump and the distinct-line `continue;` Goto are preserved in debug.
              assertEquals(
                  2, multiLine.streamInstructions().filter(InstructionSubject::isGoto).count());
            })
        .run(parameters.getRuntime(), DebugTestClass.class)
        .assertSuccessWithOutputLines("9", "9");
  }

  private static byte[] getDebugTestClassData() throws IOException {
    return transformer(DebugTestClass.class)
        .setPredictiveLineNumbering(
            (context, line) ->
                context.getReference().getMethodName().equals("sameLineContinue") ? 42 : line)
        .transform();
  }

  private R8TestCompileResultBase<?> compileInlinedGuardLoop(boolean enableInverter)
      throws Exception {
    return testForR8(parameters)
        .addProgramClasses(InlinedGuardLoopTestClass.class)
        .addKeepMainRule(InlinedGuardLoopTestClass.class)
        .enableInliningAnnotations()
        .addOptionsModification(
            options -> options.getTestingOptions().enableBranchDiamondInverter = enableInverter)
        .compile();
  }

  private static int countBranchOverGoto(CfCode code) {
    List<CfInstruction> instructions = code.getInstructions();
    Set<CfLabel> controlFlowLabels = code.getTryCatchRangeLabels();
    for (CfTryCatch tryCatch : code.getTryCatchRanges()) {
      controlFlowLabels.addAll(tryCatch.targets);
    }
    for (CfInstruction instruction : instructions) {
      if (instruction.isSwitch()) {
        controlFlowLabels.add(instruction.asSwitch().getDefaultTarget());
        controlFlowLabels.addAll(instruction.asSwitch().getSwitchTargets());
      } else if (instruction.isJumpWithNormalTarget()) {
        controlFlowLabels.add(instruction.getTarget());
      }
    }
    int count = 0;
    for (int i = 0; i < instructions.size(); i++) {
      CfInstruction instruction = instructions.get(i);
      if (!instruction.isConditionalJump()) {
        continue;
      }
      int gotoIndex = nextBytecodeInstructionIndex(instructions, controlFlowLabels, i, false);
      if (gotoIndex < instructions.size() && instructions.get(gotoIndex).isGoto()) {
        CfLabel gotoTarget = instructions.get(gotoIndex).getTarget();
        if (!isOwnBlockEntryLabel(instructions, controlFlowLabels, i, gotoTarget)
            && instructions
                .subList(
                    gotoIndex + 1,
                    nextBytecodeInstructionIndex(instructions, controlFlowLabels, gotoIndex, true))
                .contains(instruction.getTarget())) {
          count++;
        }
      }
    }
    return count;
  }

  private static boolean isOwnBlockEntryLabel(
      List<CfInstruction> instructions,
      Set<CfLabel> controlFlowLabels,
      int ifIndex,
      CfLabel target) {
    for (int j = ifIndex - 1; j >= 0; j--) {
      CfInstruction insn = instructions.get(j);
      if (insn.isJump()) {
        return false;
      }
      if (insn == target) {
        for (int k = j + 1; k < ifIndex; k++) {
          CfInstruction mid = instructions.get(k);
          if (mid.isLabel() && controlFlowLabels.contains(mid.asLabel())) {
            return false;
          }
        }
        return true;
      }
    }
    return false;
  }

  private static int nextBytecodeInstructionIndex(
      List<CfInstruction> instructions,
      Set<CfLabel> controlFlowLabels,
      int index,
      boolean acrossBlocks) {
    int i = index + 1;
    while (i < instructions.size()) {
      CfInstruction instruction = instructions.get(i);
      if (!(acrossBlocks && instruction.isPosition())
          && !(instruction.isLabel()
              && (acrossBlocks || !controlFlowLabels.contains(instruction.asLabel())))
          && !(acrossBlocks && instruction.isFrame())) {
        break;
      }
      i++;
    }
    return i;
  }

  static class TestClass {

    @NeverInline
    static int step(int value) {
      return value * 3;
    }

    @NeverInline
    static int computeWithSharedFallback(int first, int second) {
      if (first > 0) {
        if (second > 0) {
          return step(first + second);
        }
      }
      return -1;
    }

    public static void main(String[] args) {
      System.out.println(computeWithSharedFallback(2, 3));
      System.out.println(computeWithSharedFallback(2, -1));
      System.out.println(computeWithSharedFallback(-1, 3));
    }
  }

  static class DebugTestClass {

    // google-java-format wraps single-line `if (v <= 0) continue;`, so getDebugTestClassData()
    // collapses this method onto a single line via setPredictiveLineNumbering.
    @NeverInline
    static int sameLineContinue(int[] values) {
      int sum = 0;
      for (int v : values) {
        if (v <= 0) {
          continue;
        }
        sum += v;
      }
      return sum;
    }

    @NeverInline
    static int multiLineContinue(int[] values) {
      int sum = 0;
      for (int v : values) {
        if (v <= 0) {
          continue;
        }
        sum += v;
      }
      return sum;
    }

    public static void main(String[] args) {
      int[] input = {2, -1, 0, 3, 4};
      System.out.println(sameLineContinue(input));
      System.out.println(multiLineContinue(input));
    }
  }

  static class InlinedGuardLoopTestClass {

    private static void setBit(long[] interf, int words, int u, int v) {
      if (u == v) {
        return;
      }
      interf[u * words + (v >>> 6)] |= 1L << (v & 63);
    }

    @NeverInline
    public static void inlinedGuardLoop(int nVars, int words, int[] a, int[] b, long[] interf) {
      for (int u = 0; u < nVars; u++) {
        for (int v = 0; v < nVars; v++) {
          if (!(a[u] == b[v] || a[v] == b[u])) {
            setBit(interf, words, u, v);
          }
        }
      }
    }

    public static void main(String[] args) {
      int nVars = 70 + args.length;
      int words = 2;
      int[] a = new int[nVars];
      int[] b = new int[nVars];
      for (int i = 0; i < nVars; i++) {
        a[i] = i % 7;
        b[i] = i % 5;
      }
      long[] guarded = new long[nVars * words];
      inlinedGuardLoop(nVars, words, a, b, guarded);
      System.out.println(Arrays.hashCode(guarded));
    }
  }
}

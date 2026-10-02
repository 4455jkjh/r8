// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Move;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.finalizer.passes.DeadRegisterStoreEliminator;
import com.android.tools.r8.ir.regalloc.LinearScanRegisterAllocator;
import com.android.tools.r8.ir.regalloc.LiveIntervals;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Arrays;
import java.util.LinkedList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DeadRegisterStoreEliminatorTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DeadRegisterStoreEliminatorTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static class MockLinearScanRegisterAllocator extends LinearScanRegisterAllocator {
    MockLinearScanRegisterAllocator(AppView<?> appView, IRCode code) {
      super(appView, code, Timing.empty());
    }

    @Override
    public int getRegisterForValue(Value value, int instructionNumber) {
      return value.getNumber();
    }
  }

  private static class MockLiveIntervals extends LiveIntervals {
    MockLiveIntervals(Value value) {
      super(value);
    }

    @Override
    public LiveIntervals getSplitCovering(int i) {
      return this;
    }
  }

  private static Value newRegisterValue(int register) {
    Value value = new Value(register, TypeElement.getInt(), null);
    value.setNeedsRegister(true);
    new MockLiveIntervals(value);
    return value;
  }

  private static ConstNumber constInt(int register, long constant, Position position) {
    ConstNumber instruction = new ConstNumber(newRegisterValue(register), constant);
    instruction.setPosition(position);
    return instruction;
  }

  private static AppView<?> createTestAppView() {
    InternalOptions options = new InternalOptions();
    options.debug = false;
    AppInfo appInfo =
        AppInfo.createInitialAppInfo(
            DexApplication.builder(options).build(Timing.empty()),
            GlobalSyntheticsStrategy.forNonSynthesizing());
    return AppView.createForD8(appInfo);
  }

  private static ProgramMethod createMockMethod(DexItemFactory factory) {
    DexProgramClass clazz = DexProgramClass.createMockClassForTesting(factory);
    return new ProgramMethod(
        clazz,
        DexEncodedMethod.builder()
            .setMethod(
                factory.createMethod(clazz.type, factory.createProto(factory.voidType), "mock"))
            .setAccessFlags(MethodAccessFlags.fromDexAccessFlags(0))
            .disableAndroidApiLevelCheck()
            .build());
  }

  private static IRCode createTestIRCode(
      AppView<?> appView,
      LinkedList<BasicBlock> blocks,
      NumberGenerator blockNumberGenerator,
      IRMetadata metadata) {
    IRCode code =
        new IRCode(
            appView.options(),
            createMockMethod(appView.dexItemFactory()),
            Position.none(),
            blocks,
            new NumberGenerator(),
            blockNumberGenerator,
            metadata,
            MethodConversionOptions.nonConverting()) {
          @Override
          public boolean isConsistentGraph(AppView<?> appView, boolean ssa) {
            return true;
          }
        };
    for (Instruction instruction : code.instructions()) {
      if (instruction.hasOutValue()) {
        instruction.outValue().clearUsersInfo();
      }
    }
    code.resetNumbers();
    return code;
  }

  private static Value newWideRegisterValue(int register) {
    Value value = new Value(register, TypeElement.getLong(), null);
    value.setNeedsRegister(true);
    new MockLiveIntervals(value);
    return value;
  }

  private static ConstNumber constWide(int register, long constant, Position position) {
    ConstNumber instruction = new ConstNumber(newWideRegisterValue(register), constant);
    instruction.setPosition(position);
    return instruction;
  }

  @Test
  public void testDeadWriteOverwrittenInAllSuccessors() {
    // Entry block b0 defines register r0 = 42 (dead because both b1 and b2 overwrite r0 before
    // reading it) and r1 = 7 (live because b1 reads r1).
    IRMetadata metadata = IRMetadata.unknown();
    NumberGenerator blockNumberGenerator = new NumberGenerator();
    Position position = SyntheticPosition.builder().disableMethodCheck().setLine(1).build();

    BasicBlock b0 = new BasicBlock(metadata);
    b0.setNumber(blockNumberGenerator.next());
    BasicBlock b1 = new BasicBlock(metadata);
    b1.setNumber(blockNumberGenerator.next());
    BasicBlock b2 = new BasicBlock(metadata);
    b2.setNumber(blockNumberGenerator.next());

    ConstNumber deadWriteR0 = constInt(0, 42, position);
    ConstNumber liveWriteR1 = constInt(1, 7, position);
    ConstNumber condR2 = constInt(2, 0, position);
    b0.add(deadWriteR0, metadata);
    b0.add(liveWriteR1, metadata);
    b0.add(condR2, metadata);
    If branch = new If(IfType.EQ, condR2.outValue());
    branch.setPosition(position);
    b0.add(branch, metadata);
    b0.link(b1);
    b0.link(b2);
    b0.setFilledForTesting();

    // True branch b1: r0 = move r1; return r0
    Move moveR1ToR0 = new Move(newRegisterValue(0), liveWriteR1.outValue());
    moveR1ToR0.setPosition(position);
    b1.add(moveR1ToR0, metadata);
    Return ret1 = new Return(moveR1ToR0.outValue());
    ret1.setPosition(position);
    b1.add(ret1, metadata);
    b1.setFilledForTesting();

    // Fallthrough branch b2: r0 = 99; return r0
    ConstNumber overwriteR0 = constInt(0, 99, position);
    b2.add(overwriteR0, metadata);
    Return ret2 = new Return(overwriteR0.outValue());
    ret2.setPosition(position);
    b2.add(ret2, metadata);
    b2.setFilledForTesting();

    LinkedList<BasicBlock> blocks = new LinkedList<>(Arrays.asList(b0, b1, b2));
    AppView<?> appView = createTestAppView();
    IRCode code = createTestIRCode(appView, blocks, blockNumberGenerator, metadata);
    LinearScanRegisterAllocator allocator = new MockLinearScanRegisterAllocator(appView, code);

    new DeadRegisterStoreEliminator(appView).run(code, allocator, Timing.empty());

    assertFalse(deadWriteR0.hasBlock());
    assertTrue(liveWriteR1.hasBlock());
    assertEquals(2, b0.getInstructions().stream().filter(Instruction::isConstNumber).count());
  }

  @Test
  public void testLoopBackEdgePreservesLiveRegisterAndEliminatesDeadStore() {
    // Entry block b0 defines r0 = 10 (read after loop exit in b2) and r1 = 999 (never read).
    // Loop block b1 branches back to itself or exits to b2.
    IRMetadata metadata = IRMetadata.unknown();
    NumberGenerator blockNumberGenerator = new NumberGenerator();
    Position position = SyntheticPosition.builder().disableMethodCheck().setLine(1).build();

    BasicBlock b0 = new BasicBlock(metadata);
    b0.setNumber(blockNumberGenerator.next());
    BasicBlock b1 = new BasicBlock(metadata);
    b1.setNumber(blockNumberGenerator.next());
    BasicBlock b2 = new BasicBlock(metadata);
    b2.setNumber(blockNumberGenerator.next());

    ConstNumber liveAcrossLoopR0 = constInt(0, 10, position);
    ConstNumber deadStoreR1 = constInt(1, 999, position);
    b0.add(liveAcrossLoopR0, metadata);
    b0.add(deadStoreR1, metadata);
    Goto jumpToLoop = new Goto();
    jumpToLoop.setPosition(position);
    b0.add(jumpToLoop, metadata);
    b0.link(b1);
    b0.setFilledForTesting();

    // Loop header b1 with a dead move cycle (r3 = r1; r1 = r3), back-edge to b1, and exit to b2.
    Move deadMoveR1ToR3 = new Move(newRegisterValue(3), deadStoreR1.outValue());
    deadMoveR1ToR3.setPosition(position);
    b1.add(deadMoveR1ToR3, metadata);
    Move deadMoveR3ToR1 = new Move(newRegisterValue(1), deadMoveR1ToR3.outValue());
    deadMoveR3ToR1.setPosition(position);
    b1.add(deadMoveR3ToR1, metadata);
    ConstNumber loopCondR2 = constInt(2, 0, position);
    b1.add(loopCondR2, metadata);
    If loopBranch = new If(IfType.EQ, loopCondR2.outValue());
    loopBranch.setPosition(position);
    b1.add(loopBranch, metadata);
    b1.link(b1);
    b1.link(b2);
    b1.setFilledForTesting();

    // Loop exit b2 reads r0.
    Return ret = new Return(liveAcrossLoopR0.outValue());
    ret.setPosition(position);
    b2.add(ret, metadata);
    b2.setFilledForTesting();

    LinkedList<BasicBlock> blocks = new LinkedList<>(Arrays.asList(b0, b1, b2));
    AppView<?> appView = createTestAppView();
    IRCode code = createTestIRCode(appView, blocks, blockNumberGenerator, metadata);
    LinearScanRegisterAllocator allocator = new MockLinearScanRegisterAllocator(appView, code);

    new DeadRegisterStoreEliminator(appView).run(code, allocator, Timing.empty());

    assertTrue(liveAcrossLoopR0.hasBlock());
    assertFalse(deadStoreR1.hasBlock());
    assertFalse(b1.getInstructions().stream().anyMatch(Instruction::isMove));
  }

  @Test
  public void testMoreThan64Registers() {
    // Verifies that methods using registers >= 64 (and wide values straddling r63..r64) are handled
    // without errors:
    // - Dead stores to registers < 64 (r0 = 42, r10 = move r65, wide r60..r61 = 100L, wide r63..r64
    //   = 999L) are removed.
    // - Stores to registers < 64 read by instructions writing to >= 64 (r5 = 7 read by r65 = move
    //   r5) are preserved.
    // - Stores to registers >= 64 (r64 = 123, r70 = 456, wide r64..r65 = 888L) are kept untouched
    //   without errors.
    IRMetadata metadata = IRMetadata.unknown();
    NumberGenerator blockNumberGenerator = new NumberGenerator();
    Position position = SyntheticPosition.builder().disableMethodCheck().setLine(1).build();

    BasicBlock b0 = new BasicBlock(metadata);
    b0.setNumber(blockNumberGenerator.next());

    ConstNumber deadLowStoreR0 = constInt(0, 42, position);
    ConstNumber deadWideStoreR60 = constWide(60, 100L, position);
    ConstNumber deadStraddlingWideR63 = constWide(63, 999L, position);
    ConstNumber liveLowStoreR5 = constInt(5, 7, position);
    Move moveLowToHighR65 = new Move(newRegisterValue(65), liveLowStoreR5.outValue());
    moveLowToHighR65.setPosition(position);
    Move deadMoveHighToLowR10 = new Move(newRegisterValue(10), moveLowToHighR65.outValue());
    deadMoveHighToLowR10.setPosition(position);
    ConstNumber highStoreR64 = constInt(64, 123, position);
    ConstNumber highWideStoreR64 = constWide(64, 888L, position);
    ConstNumber highStoreR70 = constInt(70, 456, position);
    Return ret = new Return(moveLowToHighR65.outValue());
    ret.setPosition(position);

    b0.add(deadLowStoreR0, metadata);
    b0.add(deadWideStoreR60, metadata);
    b0.add(deadStraddlingWideR63, metadata);
    b0.add(liveLowStoreR5, metadata);
    b0.add(moveLowToHighR65, metadata);
    b0.add(deadMoveHighToLowR10, metadata);
    b0.add(highStoreR64, metadata);
    b0.add(highWideStoreR64, metadata);
    b0.add(highStoreR70, metadata);
    b0.add(ret, metadata);
    b0.setFilledForTesting();

    LinkedList<BasicBlock> blocks = new LinkedList<>(Arrays.asList(b0));
    AppView<?> appView = createTestAppView();
    IRCode code = createTestIRCode(appView, blocks, blockNumberGenerator, metadata);
    LinearScanRegisterAllocator allocator = new MockLinearScanRegisterAllocator(appView, code);

    new DeadRegisterStoreEliminator(appView).run(code, allocator, Timing.empty());

    assertFalse(deadLowStoreR0.hasBlock());
    assertFalse(deadWideStoreR60.hasBlock());
    assertFalse(deadStraddlingWideR63.hasBlock());
    assertFalse(deadMoveHighToLowR10.hasBlock());
    assertTrue(liveLowStoreR5.hasBlock());
    assertTrue(moveLowToHighR65.hasBlock());
    assertTrue(highStoreR64.hasBlock());
    assertTrue(highWideStoreR64.hasBlock());
    assertTrue(highStoreR70.hasBlock());
  }

  @Test
  public void testTryCatchHandlerLiveness() {
    // Try block b0 has normal successor bNormal and catch handler bCatch:
    // - deadStoreR0 (r0 = 1) is overwritten by liveStoreR0 (r0 = 2) before the throwing Div, so
    //   deadStoreR0 is eliminated while liveStoreR0 (read in bCatch) is preserved.
    // - deadStoreR1 (r1 = 3) is overwritten by throwingDiv (r1 = r2 / r2) on the normal path and
    //   unread in bCatch, so deadStoreR1 is eliminated.
    IRMetadata metadata = IRMetadata.unknown();
    NumberGenerator blockNumberGenerator = new NumberGenerator();
    Position position = SyntheticPosition.builder().disableMethodCheck().setLine(1).build();
    AppView<?> appView = createTestAppView();

    BasicBlock b0 = new BasicBlock(metadata);
    b0.setNumber(blockNumberGenerator.next());
    BasicBlock bNormal = new BasicBlock(metadata);
    bNormal.setNumber(blockNumberGenerator.next());
    BasicBlock bCatch = new BasicBlock(metadata);
    bCatch.setNumber(blockNumberGenerator.next());

    ConstNumber deadStoreR0 = constInt(0, 1, position);
    ConstNumber liveStoreR0 = constInt(0, 2, position);
    ConstNumber deadStoreR1 = constInt(1, 3, position);
    ConstNumber divisorR2 = constInt(2, 5, position);
    Div throwingDiv =
        new Div(NumericType.INT, newRegisterValue(1), divisorR2.outValue(), divisorR2.outValue());
    throwingDiv.setPosition(position);
    Goto jumpToNormal = new Goto();
    jumpToNormal.setPosition(position);

    b0.add(deadStoreR0, metadata);
    b0.add(liveStoreR0, metadata);
    b0.add(deadStoreR1, metadata);
    b0.add(divisorR2, metadata);
    b0.add(throwingDiv, metadata);
    b0.add(jumpToNormal, metadata);
    b0.link(bNormal);
    b0.appendCatchHandler(bCatch, appView.dexItemFactory().throwableType);
    b0.setFilledForTesting();

    Return retNormal = new Return(throwingDiv.outValue());
    retNormal.setPosition(position);
    bNormal.add(retNormal, metadata);
    bNormal.setFilledForTesting();

    Return retCatch = new Return(liveStoreR0.outValue());
    retCatch.setPosition(position);
    bCatch.add(retCatch, metadata);
    bCatch.setFilledForTesting();

    LinkedList<BasicBlock> blocks = new LinkedList<>(Arrays.asList(b0, bNormal, bCatch));
    IRCode code = createTestIRCode(appView, blocks, blockNumberGenerator, metadata);
    LinearScanRegisterAllocator allocator = new MockLinearScanRegisterAllocator(appView, code);

    new DeadRegisterStoreEliminator(appView).run(code, allocator, Timing.empty());

    assertFalse(deadStoreR0.hasBlock());
    assertTrue(liveStoreR0.hasBlock());
    assertFalse(deadStoreR1.hasBlock());
    assertTrue(divisorR2.hasBlock());
    assertTrue(throwingDiv.hasBlock());
  }
}

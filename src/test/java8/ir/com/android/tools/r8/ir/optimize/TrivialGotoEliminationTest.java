// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.DebugPosition;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Throw;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.finalizer.passes.TrivialGotosCollapser;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import java.util.LinkedList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TrivialGotoEliminationTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public TrivialGotoEliminationTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private final IRMetadata metadata = IRMetadata.unknown();

  @Test
  public void trivialGotoInEntryBlock() throws Exception {
    AppView<AppInfo> appView = computeAppView(AndroidApp.builder().build());
    InternalOptions options = appView.options();
    // Setup silly block structure:
    //
    // block0:
    //   goto block2
    // block1:
    //   v0 = const-number 0
    //   throw v0
    // block2:
    //   return
    final NumberGenerator basicBlockNumberGenerator = new NumberGenerator();
    Position position = SyntheticPosition.builder().setLine(0).disableMethodCheck().build();
    BasicBlock block2 = new BasicBlock(metadata);
    BasicBlock block0 =
        BasicBlock.createGotoBlock(basicBlockNumberGenerator.next(), position, metadata, block2);
    BasicBlock block1 = new BasicBlock(metadata);
    block1.setNumber(basicBlockNumberGenerator.next());
    block2.setNumber(basicBlockNumberGenerator.next());
    block0.setFilledForTesting();
    block2.getMutablePredecessors().add(block0);
    Instruction ret = new Return();
    ret.setPosition(position);
    block2.add(ret, metadata);
    block2.setFilledForTesting();
    Value value = new Value(0, TypeElement.getInt(), null);
    Instruction number = new ConstNumber(value, 0);
    number.setPosition(position);
    block1.add(number, metadata);
    Instruction throwing = new Throw(value);
    throwing.setPosition(position);
    block1.add(throwing, metadata);
    block1.setFilledForTesting();
    LinkedList<BasicBlock> blocks = new LinkedList<>();
    blocks.add(block0);
    blocks.add(block1);
    blocks.add(block2);
    // Check that the goto in block0 remains. There was a bug in the trivial goto elimination
    // that ended up removing that goto changing the code to start with the unreachable
    // throw.
    options.debug = true;
    IRCode code =
        new IRCode(
            options,
            null,
            Position.none(),
            blocks,
            new NumberGenerator(),
            basicBlockNumberGenerator,
            IRMetadata.unknown(),
            MethodConversionOptions.forD8(appView));
    new TrivialGotosCollapser(appView).run(code, null, Timing.empty());
    assertTrue(code.entryBlock().isTrivialGoto());
    assertTrue(blocks.contains(block0));
    assertTrue(blocks.contains(block1));
    assertTrue(blocks.contains(block2));
  }

  @Test
  public void trivialGotoLoopAsFallthrough() throws Exception {
    AppView<AppInfo> appView = computeAppView(AndroidApp.builder().build());
    InternalOptions options = appView.options();
    // Setup block structure:
    // block0:
    //   v0 <- argument
    //   if ne v0 block2
    //
    // block1:
    //   goto block3
    //
    // block2:
    //   return
    //
    // block3:
    //   goto block3
    final NumberGenerator basicBlockNumberGenerator = new NumberGenerator();
    Position position = SyntheticPosition.builder().setLine(0).disableMethodCheck().build();
    BasicBlock block0 = new BasicBlock(metadata);
    block0.setNumber(basicBlockNumberGenerator.next());
    BasicBlock block2 = new BasicBlock(metadata);
    BasicBlock block1 =
        BasicBlock.createGotoBlock(basicBlockNumberGenerator.next(), position, metadata);
    block2.setNumber(basicBlockNumberGenerator.next());
    Instruction ret = new Return();
    ret.setPosition(position);
    block2.add(ret, metadata);
    block2.setFilledForTesting();

    BasicBlock block3 = new BasicBlock(metadata);
    block3.setNumber(basicBlockNumberGenerator.next());
    Instruction instruction = new Goto();
    instruction.setPosition(position);
    block3.add(instruction, metadata);
    block3.setFilledForTesting();
    block3.getMutableSuccessors().add(block3);

    block1.getMutableSuccessors().add(block3);
    block1.setFilledForTesting();

    Value value =
        new Value(
            0,
            TypeElement.fromDexType(
                options.itemFactory.throwableType, Nullability.definitelyNotNull(), appView),
            null);
    instruction = new Argument(value, 0, false);
    instruction.setPosition(position);
    block0.add(instruction, metadata);
    instruction = new If(IfType.EQ, value);
    instruction.setPosition(position);
    block0.add(instruction, metadata);
    block0.getMutableSuccessors().add(block2);
    block0.getMutableSuccessors().add(block1);
    block0.setFilledForTesting();

    block1.getMutablePredecessors().add(block0);
    block2.getMutablePredecessors().add(block0);
    block3.getMutablePredecessors().add(block1);
    block3.getMutablePredecessors().add(block3);

    LinkedList<BasicBlock> blocks = new LinkedList<>();
    blocks.add(block0);
    blocks.add(block1);
    blocks.add(block2);
    blocks.add(block3);
    // Check that the goto in block0 remains. There was a bug in the trivial goto elimination
    // that ended up removing that goto changing the code to start with the unreachable
    // throw.
    options.debug = true;
    IRCode code =
        new IRCode(
            options,
            null,
            Position.none(),
            blocks,
            new NumberGenerator(),
            basicBlockNumberGenerator,
            IRMetadata.unknown(),
            MethodConversionOptions.forD8(appView));
    new TrivialGotosCollapser(appView).run(code, null, Timing.empty());
    assertTrue(block0.getInstructions().getFirst().getNext().isIf());
    assertEquals(block1, block0.getInstructions().getFirst().getNext().asIf().fallthroughBlock());
    assertTrue(blocks.containsAll(ImmutableList.of(block0, block1, block2, block3)));
  }

  @Test
  public void redundantDebugPositionGotos() throws Exception {
    AppView<AppInfo> appView = computeAppView(AndroidApp.builder().build());
    InternalOptions options = appView.options();
    // Setup block structure:
    // block0:
    //   v0 <- argument
    //   if eq v0 block4
    //
    // block1:
    //   debug-position
    //   goto block3
    //
    // block2:
    //   return
    //
    // block3:
    //   debug-position
    //   goto block2
    //
    // block4:
    //   return
    //
    // The positions are already active at the exit of all predecessors, so block1 and block3 can be
    // removed. Block1 is a fallthrough, so it can only be removed once block3 is removed and it
    // targets the next block.
    final NumberGenerator basicBlockNumberGenerator = new NumberGenerator();
    Position position = SyntheticPosition.builder().setLine(0).disableMethodCheck().build();
    BasicBlock block0 = new BasicBlock(metadata);
    BasicBlock block1 = new BasicBlock(metadata);
    BasicBlock block2 = new BasicBlock(metadata);
    BasicBlock block3 = new BasicBlock(metadata);
    BasicBlock block4 = new BasicBlock(metadata);
    for (BasicBlock block : ImmutableList.of(block0, block1, block2, block3, block4)) {
      block.setNumber(basicBlockNumberGenerator.next());
    }

    Value value =
        new Value(
            0,
            TypeElement.fromDexType(
                options.itemFactory.throwableType, Nullability.definitelyNotNull(), appView),
            null);
    Instruction instruction = new Argument(value, 0, false);
    instruction.setPosition(position);
    block0.add(instruction, metadata);
    instruction = new If(IfType.EQ, value);
    instruction.setPosition(position);
    block0.add(instruction, metadata);
    block0.getMutableSuccessors().add(block4);
    block0.getMutableSuccessors().add(block1);
    block0.setFilledForTesting();

    addDebugPositionAndGoto(block1, block3, position);
    addDebugPositionAndGoto(block3, block2, position);

    for (BasicBlock block : ImmutableList.of(block2, block4)) {
      instruction = new Return();
      instruction.setPosition(position);
      block.add(instruction, metadata);
      block.setFilledForTesting();
    }

    block1.getMutablePredecessors().add(block0);
    block2.getMutablePredecessors().add(block3);
    block3.getMutablePredecessors().add(block1);
    block4.getMutablePredecessors().add(block0);

    LinkedList<BasicBlock> blocks = new LinkedList<>();
    blocks.add(block0);
    blocks.add(block1);
    blocks.add(block2);
    blocks.add(block3);
    blocks.add(block4);
    options.debug = true;
    IRCode code =
        new IRCode(
            options,
            null,
            Position.none(),
            blocks,
            new NumberGenerator(),
            basicBlockNumberGenerator,
            IRMetadata.unknown(),
            MethodConversionOptions.forD8(appView));
    new TrivialGotosCollapser(appView).run(code, null, Timing.empty());
    assertEquals(ImmutableList.of(block0, block2, block4), blocks);
    assertEquals(block4, block0.exit().asIf().getTrueTarget());
    assertEquals(block2, block0.exit().asIf().fallthroughBlock());
  }

  private void addDebugPositionAndGoto(BasicBlock block, BasicBlock target, Position position) {
    DebugPosition debugPosition = new DebugPosition();
    debugPosition.setPosition(position);
    block.add(debugPosition, metadata);
    Instruction instruction = new Goto();
    instruction.setPosition(position);
    block.add(instruction, metadata);
    block.getMutableSuccessors().add(target);
    block.setFilledForTesting();
  }
}

// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.LinearFlowInstructionListIterator;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.ListIterator;
import org.junit.Test;

public class LinearFlowIteratorTest extends TestBase {

  private IRCode branchingCode() throws Exception {

    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder clazz = jasminBuilder.addClass("foo");
    clazz.addStaticMethod(
        "bar",
        ImmutableList.of("I"),
        "V",
        ".limit stack 2",
        ".limit locals 2",
        ".var 1 is x Ljava/lang/Object; from L1 to L2",
        "  aconst_null",
        "  astore 1",
        "L1:",
        "  iload 0",
        "  ifeq L3",
        "L2:",
        "  goto L5",
        "L3:",
        "  aload 1",
        "  iconst_0",
        "  aaload",
        "  pop",
        "L5:",
        "  return");
    AndroidApp.Builder appBuilder = AndroidApp.builder();
    appBuilder.addClassProgramData(jasminBuilder.buildClasses());

    // Build the code, and split the block with the array access into two blocks, so that the
    // const-number and the array-get are in two blocks connected by linear flow. The block is
    // located by its content, since the block order in the code compiled by D8 is an artifact of
    // the code layout chosen by the DEX code finalizer.
    AndroidApp app = compileWithD8(appBuilder.build());
    MethodSubject methodSubject =
        getMethodSubject(app, "foo", "void", "bar", ImmutableList.of("int"));
    IRCode code = methodSubject.buildIR();
    ListIterator<BasicBlock> blocks = code.listIterator();
    BasicBlock block = blocks.next();
    while (!hasArrayGet(block)) {
      block = blocks.next();
    }
    InstructionListIterator iter = block.listIterator();
    iter.nextUntil(i -> !i.isConstNumber());
    iter.previous();
    iter.split(code, blocks);
    return code;
  }

  /** Returns the block with the array access, which is split in two by {@link #branchingCode()}. */
  private static BasicBlock arrayGetBlock(IRCode code) {
    for (BasicBlock block : code.blocks) {
      if (hasArrayGet(block)) {
        return block;
      }
    }
    throw new AssertionError("Expected a block with an array-get instruction");
  }

  /** Returns the block with the const-number defining the index of the array access. */
  private static BasicBlock constNumberBlock(IRCode code) {
    BasicBlock arrayGetBlock = arrayGetBlock(code);
    assertEquals(1, arrayGetBlock.getPredecessors().size());
    return arrayGetBlock.getPredecessors().get(0);
  }

  private static boolean hasArrayGet(BasicBlock block) {
    for (Instruction instruction : block.getInstructions()) {
      if (instruction.isArrayGet()) {
        return true;
      }
    }
    return false;
  }

  private IRCode simpleCode() throws Exception {

    JasminBuilder jasminBuilder = new JasminBuilder();

    ClassBuilder clazz = jasminBuilder.addClass("foo");
    clazz.addStaticMethod(
        "bar",
        ImmutableList.of("I"),
        "V",
        ".limit stack 2",
        ".limit locals 2",
        ".var 0 is x I from L1 to L2",
        "L1:",
        "  aconst_null",
        "  iload 0",
        "  aaload",
        "  pop",
        "L2:",
        "  return");
    AndroidApp.Builder appBuilder = AndroidApp.builder();
    appBuilder.addClassProgramData(jasminBuilder.buildClasses());

    // Build the code, and split the code into three blocks.
    AndroidApp app = compileWithD8(appBuilder.build());
    MethodSubject method = getMethodSubject(app, "foo", "void", "bar", ImmutableList.of("int"));
    IRCode code = method.buildIR();
    ListIterator<BasicBlock> blocks = code.listIterator();
    InstructionListIterator iter = blocks.next().listIterator();
    iter.nextUntil(i -> !i.isArgument());
    iter.split(code, 0, blocks);
    return code;
  }

  @Test
  public void hasNextWillCheckNextBlock() throws Exception {
    IRCode code = simpleCode();
    InstructionListIterator it = new LinearFlowInstructionListIterator(code.entryBlock());
    it.next();
    it.next();
    assert it.hasNext();
  }

  @Test
  public void nextWillContinueThroughGotoBlocks() throws Exception {
    IRCode code = simpleCode();
    InstructionListIterator it = new LinearFlowInstructionListIterator(code.entryBlock());
    assertTrue(it.next().isArgument());
    assertTrue(it.next().isConstNumber());
    assertTrue(it.next().isThrow());
  }

  @Test
  public void hasPreviousWillCheckPreviousBlock() throws Exception {
    IRCode code = simpleCode();
    InstructionListIterator it = new LinearFlowInstructionListIterator(code.blocks.get(2));
    assert it.hasPrevious();
  }

  @Test
  public void hasPreviousWillJumpOverGotos() throws Exception {
    IRCode code = simpleCode();
    InstructionListIterator it = new LinearFlowInstructionListIterator(code.blocks.get(2));
    assert it.previous().isConstNumber();
  }

  @Test
  public void GoToFrontAndBackIsSameAmountOfInstructions() throws Exception {
    IRCode code = simpleCode();
    int moves = 0;
    InstructionListIterator it = new LinearFlowInstructionListIterator(code.entryBlock());
    while (it.hasNext()) {
      it.next();
      moves++;
    }
    Instruction current = null;
    for (int i = 0; i < moves; i++) {
      current = it.previous();
    }
    assert !it.hasPrevious();
    assert current.isArgument();
  }

  @Test
  public void moveFromEmptyBlock() throws Exception {
    IRCode code = simpleCode();
    InstructionListIterator it = new LinearFlowInstructionListIterator(code.blocks.get(1));
    Instruction current = it.previous();
    assertTrue(current.isConstNumber() && current.getOutType().isReferenceType());
    assertTrue(it.next().isConstNumber());
    assertTrue(it.next().isThrow());
  }

  @Test
  public void doNotChangeToNextBlockWhenNotLinearFlow() throws Exception {
    IRCode code = branchingCode();
    InstructionListIterator it = new LinearFlowInstructionListIterator(code.entryBlock());
    it.nextUntil((i) -> !i.isArgument());
    it.next();
    assert !it.hasNext();
  }

  @Test
  public void doNotChangeToPreviousBlockWhenNotLinearFlow() throws Exception {
    IRCode code = branchingCode();
    // The const-number block is only reachable through the if-instruction in the entry block.
    InstructionListIterator it = new LinearFlowInstructionListIterator(constNumberBlock(code));
    assert !it.hasPrevious();
  }

  @Test
  public void followLinearSubPathDown() throws Exception {
    IRCode code = branchingCode();
    InstructionListIterator it = new LinearFlowInstructionListIterator(constNumberBlock(code));
    Instruction current = null;
    while (it.hasNext()) {
      current = it.next();
    }
    assert current.isGoto();
  }

  @Test
  public void followLinearSubPathUp() throws Exception {
    IRCode code = branchingCode();
    InstructionListIterator it = new LinearFlowInstructionListIterator(arrayGetBlock(code));
    Instruction current = null;
    while (it.hasPrevious()) {
      current = it.previous();
    }
    assert current.isConstNumber();
  }
}

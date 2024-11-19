// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionList;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.google.common.base.Equivalence;
import java.util.Arrays;
import java.util.List;

class BasicBlockInstructionsEquivalence extends Equivalence<BasicBlock> {
  private static final int UNKNOW_HASH = -1;
  private static final int MAX_HASH_INSTRUCTIONS = 5;
  private final RegisterAllocator allocator;
  private final MethodConversionOptions conversionOptions;
  private final int[] hashes;

  BasicBlockInstructionsEquivalence(IRCode code, RegisterAllocator allocator) {
    this.allocator = allocator;
    this.conversionOptions = code.getConversionOptions();
    hashes = new int[code.getCurrentBlockNumber() + 1];
    Arrays.fill(hashes, UNKNOW_HASH);
  }

  private boolean hasIdenticalInstructions(BasicBlock first, BasicBlock second) {
    InstructionList instructions0 = first.getInstructions();
    InstructionList instructions1 = second.getInstructions();
    if (instructions0.size() != instructions1.size()) {
      return false;
    }
    Instruction i0 = instructions0.getFirstOrNull();
    Instruction i1 = instructions1.getFirstOrNull();
    while (i0 != null) {
      if (!i0.identicalAfterRegisterAllocation(i1, allocator, conversionOptions)) {
        return false;
      }
      i0 = i0.getNext();
      i1 = i1.getNext();
    }

    if (!allocator.hasEqualTypesAtEntry(first, second)) {
      return false;
    }

    CatchHandlers<BasicBlock> handlers0 = first.getCatchHandlers();
    CatchHandlers<BasicBlock> handlers1 = second.getCatchHandlers();
    if (!handlers0.equals(handlers1)) {
      return false;
    }
    // Normal successors are equal based on equality of the instruction stream. Verify that here.
    assert verifyAllSuccessors(first.getSuccessors(), second.getSuccessors());
    return true;
  }

  private boolean verifyAllSuccessors(List<BasicBlock> successors0, List<BasicBlock> successors1) {
    if (successors0.size() != successors1.size()) {
      return false;
    }
    for (int i = 0; i < successors0.size(); i++) {
      if (successors0.get(i) != successors1.get(i)) {
        return false;
      }
    }
    return true;
  }

  @Override
  protected boolean doEquivalent(BasicBlock a, BasicBlock b) {
    return hasIdenticalInstructions(a, b);
  }

  void clearComputedHash(BasicBlock basicBlock) {
    hashes[basicBlock.getNumber()] = UNKNOW_HASH;
  }

  @Override
  protected int doHash(BasicBlock basicBlock) {
    int hash = hashes[basicBlock.getNumber()];
    if (hash != UNKNOW_HASH) {
      assert hash == computeHash(basicBlock);
      return hash;
    }
    hash = computeHash(basicBlock);
    hashes[basicBlock.getNumber()] = hash;
    return hash;
  }

  private int computeHash(BasicBlock basicBlock) {
    InstructionList instructions = basicBlock.getInstructions();
    int hash = instructions.size();
    int i = 0;
    for (Instruction inst = instructions.getFirstOrNull(); inst != null; inst = inst.getNext()) {
      if (++i > MAX_HASH_INSTRUCTIONS) {
        break;
      }
      int hashPart = 0;
      if (inst.outValue() != null && inst.outValue().needsRegister()) {
        hashPart += allocator.getRegisterForValue(inst.outValue(), inst.getNumber());
      }
      for (Value inValue : inst.inValues()) {
        hashPart = hashPart << 4;
        if (inValue.needsRegister()) {
          hashPart += allocator.getRegisterForValue(inValue, inst.getNumber());
        }
      }
      hash = hash * 3 + hashPart;
    }
    return hash;
  }
}
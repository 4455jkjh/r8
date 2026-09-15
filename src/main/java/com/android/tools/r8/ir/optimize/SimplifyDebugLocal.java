// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;

public class SimplifyDebugLocal extends CodeRewriterPass<AppInfo> {

  public SimplifyDebugLocal(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "SimplifyDebugLocal";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return true;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean changed = false;
    for (BasicBlock block : code.blocks) {
      InstructionListIterator iterator = block.listIterator();
      while (iterator.hasNext()) {
        Instruction prevInstruction = iterator.peekPrevious();
        Instruction instruction = iterator.next();
        if (instruction.isDebugLocalWrite()) {
          assert instruction.inValues().size() == 1;
          Value inValue = instruction.inValues().get(0);
          DebugLocalInfo localInfo = instruction.outValue().getLocalInfo();
          DexString localName = localInfo.name;
          if (!inValue.hasLocalInfo()
              && inValue.numberOfAllUsers() == 1
              && inValue.definition != null
              && !hasLocalOrLineChangeBetween(inValue.definition, instruction, localName)) {
            changed = true;
            inValue.setLocalInfo(localInfo);
            instruction.outValue().replaceUsers(inValue);
            Value overwrittenLocal = instruction.removeDebugValue(localInfo);
            if (overwrittenLocal != null) {
              overwrittenLocal.addDebugLocalEnd(inValue.definition);
            }
            if (prevInstruction != null
                && (prevInstruction.outValue() == null
                    || !prevInstruction.outValue().hasLocalInfo()
                    || !instruction.getDebugValues().contains(prevInstruction.outValue()))) {
              instruction.moveDebugValues(prevInstruction);
            }
            iterator.removeOrReplaceByDebugLocalRead();
          }
        }
      }
    }
    if (changed) {
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  // TODO(mikaelpeltier) Manage that from and to instruction do not belong to the same block.
  private static boolean hasLocalOrLineChangeBetween(
      Instruction from, Instruction to, DexString localVar) {
    if (from.getBlock() != to.getBlock()) {
      return true;
    }
    if (from.getPosition().isSome()
        && to.getPosition().isSome()
        && !from.getPosition().equals(to.getPosition())) {
      return true;
    }
    Position position = null;
    for (Instruction instruction : from.getBlock().instructionsAfter(from)) {
      if (position == null) {
        if (instruction.getPosition().isSome()) {
          position = instruction.getPosition();
        }
      } else if (instruction.getPosition().isSome()
          && !position.equals(instruction.getPosition())) {
        return true;
      }
      if (instruction == to) {
        return false;
      }
      if (instruction.outValue() != null && instruction.outValue().hasLocalInfo()) {
        if (instruction.outValue().getLocalInfo().name.isIdenticalTo(localVar)) {
          return true;
        }
      }
    }
    throw new Unreachable();
  }
}

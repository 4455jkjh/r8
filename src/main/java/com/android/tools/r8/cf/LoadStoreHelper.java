// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.Dup;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Load;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Pop;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.StackValue;
import com.android.tools.r8.ir.code.Store;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class LoadStoreHelper {

  private final AppView<?> appView;
  private final IRCode code;
  private final TypeVerificationHelper typesHelper;

  private Map<Value, ConstInstruction> clonableConstants;

  // The active block and instruction iterator used by the pass. These are stored in fields to avoid
  // needing to pass them back and forth through Instruction#insertLoadAndStores.
  private BasicBlockIterator blockIterator;
  private InstructionListIterator instructionIterator;

  public LoadStoreHelper(AppView<?> appView, IRCode code, TypeVerificationHelper typesHelper) {
    this.appView = appView;
    this.code = code;
    this.typesHelper = typesHelper;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean hasLocalInfoOrUsersOutsideThisBlock(Value value, BasicBlock block) {
    if (value.hasLocalInfo()) {
      return true;
    }
    if (value.numberOfPhiUsers() > 0) {
      return true;
    }
    for (Instruction instruction : value.uniqueUsers()) {
      if (instruction.getBlock() != block) {
        return true;
      }
    }
    return false;
  }

  private static boolean isConstInstructionAlwaysThreeBytes(ConstInstruction instr) {
    if (instr.isConstNumber()) {
      ConstNumber constNumber = instr.asConstNumber();
      switch (instr.outType()) {
        case OBJECT:
        case INT:
        case FLOAT:
          return false;
        case LONG:
          {
            long number = constNumber.getLongValue();
            return number != 0 && number != 1;
          }
        case DOUBLE:
          {
            double number = constNumber.getDoubleValue();
            return number != 0.0f && number != 1.0f;
          }
        default:
          throw new Unreachable();
      }
    }
    assert instr.isConstClass()
        || instr.isConstMethodHandle()
        || instr.isConstMethodType()
        || instr.isConstString()
        || instr.isDexItemBasedConstString();
    return false;
  }

  private boolean canRemoveConstInstruction(ConstInstruction instr, BasicBlock block) {
    Value value = instr.outValue();
    return !hasLocalInfoOrUsersOutsideThisBlock(value, block)
        && (value.numberOfUsers() <= 1 || !isConstInstructionAlwaysThreeBytes(instr))
        && !instr.instructionInstanceCanThrow(appView, code.context());
  }

  public void insertLoadsAndStores() {
    clonableConstants = new IdentityHashMap<>();
    blockIterator = code.listIterator();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      instructionIterator = block.listIterator();
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        instruction.insertLoadAndStores(this);
      }
      clonableConstants.clear();
    }
    clonableConstants = null;
    blockIterator = null;
  }

  @SuppressWarnings("ReferenceEquality")
  public void insertPhiMoves(CfRegisterAllocator allocator) {
    // Insert phi stores in all predecessors.
    for (BasicBlock block : code.blocks) {
      if (!block.getPhis().isEmpty()) {
        // TODO(zerny): Phi's at an exception block must be dealt with at block entry.
        assert !block.entry().isMoveException();
        for (int predIndex = 0; predIndex < block.getPredecessors().size(); predIndex++) {
          BasicBlock pred = block.getPredecessors().get(predIndex);
          List<Phi> phis = block.getPhis();
          List<PhiMove> moves = new ArrayList<>(phis.size());
          for (Phi phi : phis) {
            if (!phi.needsRegister()) {
              continue;
            }
            Value value = phi.getOperand(predIndex);
            if (allocator.getRegisterForValue(phi) != allocator.getRegisterForValue(value)) {
              moves.add(new PhiMove(phi, value));
            }
          }
          instructionIterator = pred.listIterator(pred.getInstructions().size());
          Instruction exit = instructionIterator.previous();
          assert pred.exit() == exit;
          movePhis(moves, exit, allocator);
        }
        allocator.addToLiveAtEntrySet(block, block.getPhis());
      }
    }
    code.blocks.forEach(BasicBlock::clearUserInfo);
  }

  private StackValue createStackValue(Value value, int height) {
    return StackValue.create(typesHelper.getTypeInfo(value), height, appView);
  }

  private StackValue createStackValue(DexType type, int height) {
    return StackValue.create(typesHelper.createInitializedType(type), height, appView);
  }

  public void loadInValues(Instruction instruction) {
    int topOfStack = 0;
    instructionIterator.previous();
    for (int i = 0; i < instruction.inValues().size(); i++) {
      Value value = instruction.inValues().get(i);
      StackValue stackValue = createStackValue(value, topOfStack++);
      assert clonableConstants != null;
      ConstInstruction constInstruction = clonableConstants.get(value);
      if (constInstruction != null) {
        ConstInstruction clonedConstInstruction =
            ConstInstruction.copyOf(stackValue, constInstruction);
        add(clonedConstInstruction, instruction);
      } else {
        add(load(stackValue, value), instruction);
      }
      instruction.replaceValue(i, stackValue);
    }
    instructionIterator.next();
  }

  public void storeOrPopOutValue(DexType type, Instruction instruction) {
    if (instruction.hasOutValue()) {
      assert instruction.outValue().isUsed();
      storeOutValue(instruction);
    } else {
      popOutType(type, instruction);
    }
  }

  public void storeOutValue(Instruction instruction) {
    assert instruction.hasOutValue();
    assert !(instruction.outValue() instanceof StackValue);
    if (instruction.isConstInstruction()) {
      ConstInstruction constInstruction = instruction.asConstInstruction();
      if (canRemoveConstInstruction(constInstruction, instruction.getBlock())) {
        assert !constInstruction.isDexItemBasedConstString()
            || constInstruction.outValue().numberOfUsers() == 1;
        clonableConstants.put(instruction.outValue(), constInstruction);
        instruction.outValue().clearUsers();
        instructionIterator.removeOrReplaceByDebugLocalRead();
        return;
      }
      assert instruction.outValue().isUsed()
              || instruction.instructionInstanceCanThrow(appView, code.context())
          : "Expected instruction to be removed: " + instruction;
    }
    if (!instruction.outValue().isUsed()) {
      popOutValue(instruction.outValue(), instruction);
      return;
    }
    StackValue newOutValue = createStackValue(instruction.outValue(), 0);
    Value oldOutValue = instruction.swapOutValue(newOutValue);
    Store store = new Store(oldOutValue, newOutValue);
    // Move the debugging-locals liveness pertaining to the instruction to the store.
    instruction.moveDebugValues(store);
    BasicBlock storeBlock = instruction.getBlock();
    // If the block has catch-handlers we are not allowed to modify the locals of the block. If the
    // instruction is throwing, the action should be moved to a new block - otherwise, the store
    // should be inserted and the remaining instructions should be moved along with the handlers to
    // the new block.
    boolean hasCatchHandlers = storeBlock.hasCatchHandlers();
    if (hasCatchHandlers && instruction.instructionTypeCanThrow()) {
      storeBlock = instructionIterator.split(this.code, this.blockIterator);
      instructionIterator = storeBlock.listIterator();
    }
    add(store, instruction.getPosition());
    if (hasCatchHandlers && !instruction.instructionTypeCanThrow()) {
      splitAfterStoredOutValue();
    }
  }

  // DebugLocalWrite encodes a store and it needs to consistently split out the catch range after
  // its store.
  public void splitAfterStoredOutValue() {
    instructionIterator.split(this.code, this.blockIterator);
    blockIterator.previous();
  }

  public void popOutType(DexType type, Instruction instruction) {
    popOutValue(createStackValue(type, 0), instruction);
  }

  private void popOutValue(Value value, Instruction instruction) {
    popOutValue(createStackValue(value, 0), instruction);
  }

  private void popOutValue(StackValue newOutValue, Instruction instruction) {
    BasicBlock insertBlock = instruction.getBlock();
    if (insertBlock.hasCatchHandlers() && instruction.instructionTypeCanThrow()) {
      insertBlock = instructionIterator.split(this.code, this.blockIterator);
      instructionIterator = insertBlock.listIterator();
    }
    instruction.swapOutValue(newOutValue);
    add(new Pop(newOutValue), instruction.getPosition());
  }

  private static class PhiMove {
    final Phi phi;
    final Value operand;

    PhiMove(Phi phi, Value operand) {
      this.phi = phi;
      this.operand = operand;
    }
  }

  private void movePhis(List<PhiMove> moves, Instruction exit, CfRegisterAllocator allocator) {
    if (moves.isEmpty()) {
      return;
    }
    // TODO(zerny): Accounting for non-interfering phis would lower the max stack size.
    Position position = exit.getPosition();
    StackValue storedOperand = takeStoredOperandFromStack(moves, exit, allocator);
    int topOfStack = 0;
    List<StackValue> temps = new ArrayList<>(moves.size());
    for (PhiMove move : moves) {
      StackValue tmp;
      if (topOfStack == 0 && storedOperand != null) {
        tmp = storedOperand;
      } else {
        tmp = createStackValue(move.phi, topOfStack);
        add(load(tmp, move.operand), position);
      }
      topOfStack++;
      temps.add(tmp);
      move.operand.removePhiUser(move.phi);
    }
    for (int i = moves.size() - 1; i >= 0; i--) {
      PhiMove move = moves.get(i);
      StackValue tmp = temps.get(i);
      FixedLocalValue out = new FixedLocalValue(move.phi);
      add(new Store(out, tmp), position);
      move.phi.replaceUsers(out);
    }
  }

  // If the block ends with `Store v <- s; Goto` and v is moved to a phi, then moves v to the front
  // of the (parallel) moves and returns a stack value holding v, which replaces the load of v.
  // The store is removed if v is only used by this phi operand. Otherwise, it is rewritten to
  // `Dup [s', s''] <- s; Store v <- s''` when that saves a byte and does not prevent an iinc.
  @SuppressWarnings("ReferenceEquality")
  private StackValue takeStoredOperandFromStack(
      List<PhiMove> moves, Instruction exit, CfRegisterAllocator allocator) {
    Instruction previous = exit.getPrev();
    if (!exit.isGoto() || previous == null || !previous.isStore()) {
      return null;
    }
    Store store = previous.asStore();
    Value value = store.outValue();
    StackValue src = (StackValue) store.src();
    if (value.hasLocalInfo() || !store.getDebugValues().isEmpty() || src.getHeight() != 0) {
      return null;
    }
    int index = ListUtils.firstIndexMatching(moves, move -> move.operand == value);
    if (index < 0) {
      return null;
    }
    if (value.numberOfAllUsers() == 1 && moves.get(index).phi.usesValueOneTime(value)) {
      instructionIterator.previous();
      // The store is removed while v still has its phi user, so detach the source manually.
      src.removeUser(store);
      instructionIterator.removeInstructionIgnoreOutValue();
    } else if (allocator.getRegisterForValue(value) >= 4 && !isPotentialIinc(store, allocator)) {
      // Slots 0..3 have 1-byte <t>store_<n> and <t>load_<n>, so store and load is as small as dup
      // and store. Slots >= 4 need 2-byte <t>load <n>, so dup and store saves a byte.
      StackValue bottom = src.duplicate(0);
      StackValue top = src.duplicate(1);
      instructionIterator.previous();
      add(new Dup(bottom, top, src), store);
      store.replaceValue(0, top);
      instructionIterator.next();
      src = bottom;
    } else {
      return null;
    }
    Collections.swap(moves, 0, index);
    return src;
  }

  // Returns true if CfBuilder.rewriteIincPatterns() rewrites `Load v; ConstNumber c; Add; Store v`
  // (or with the constant first) to `iinc v c`, which inserting a Dup before the store prevents.
  @SuppressWarnings("ReferenceEquality")
  private static boolean isPotentialIinc(Store store, CfRegisterAllocator allocator) {
    Instruction add = store.getPrev();
    Instruction second = add == null ? null : add.getPrev();
    Instruction first = second == null ? null : second.getPrev();
    if (first == null || !add.isAdd()) {
      return false;
    }
    Load load = first.isLoad() ? first.asLoad() : second.asLoad();
    ConstNumber constNumber = first.isLoad() ? second.asConstNumber() : first.asConstNumber();
    if (load == null || constNumber == null || constNumber.getOutType() != TypeElement.getInt()) {
      return false;
    }
    int increment = constNumber.getIntValue();
    Position position = add.getPosition();
    return Byte.MIN_VALUE <= increment
        && increment <= Byte.MAX_VALUE
        && allocator.getRegisterForValue(load.src())
            == allocator.getRegisterForValue(store.outValue())
        && position == load.getPosition()
        && position == constNumber.getPosition()
        && position == store.getPosition();
  }

  private Instruction load(StackValue stackValue, Value value) {
    if (value.isConstant()) {
      ConstInstruction constant = value.getConstInstruction();
      if (constant.isConstNumber()) {
        return new ConstNumber(stackValue, constant.asConstNumber().getRawValue());
      } else if (constant.isConstString()) {
        return new ConstString(stackValue, constant.asConstString().getValue());
      } else if (constant.isDexItemBasedConstString()) {
        DexItemBasedConstString computedConstant = constant.asDexItemBasedConstString();
        return new DexItemBasedConstString(
            stackValue, computedConstant.getItem(), computedConstant.getNameComputationInfo());
      } else if (constant.isConstClass()) {
        return new ConstClass(stackValue, constant.asConstClass().getType());
      } else {
        throw new Unreachable("Unexpected constant value: " + value);
      }
    }
    return new Load(stackValue, value);
  }

  private void add(Instruction newInstruction, Instruction existingInstruction) {
    add(newInstruction, existingInstruction.getPosition());
  }

  private void add(Instruction newInstruction, Position position) {
    newInstruction.setPosition(position);
    instructionIterator.add(newInstruction);
  }
}

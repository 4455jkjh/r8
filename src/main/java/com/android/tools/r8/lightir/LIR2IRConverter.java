// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.ArrayLength;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DebugPosition;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Phi.RegisterReadType;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SyntheticPosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.lightir.LIRCode.PositionEntry;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class LIR2IRConverter {

  private LIR2IRConverter() {}

  public static IRCode translate(ProgramMethod method, LIRCode lirCode, AppView<?> appView) {
    Parser parser = new Parser(lirCode, method.getReference(), appView);
    parser.parseArguments(method);
    lirCode.forEach(view -> view.accept(parser));
    return parser.getIRCode(method);
  }

  /**
   * When building IR the structured LIR parser is used to obtain the decoded operand indexes. The
   * below parser subclass handles translation of indexes to SSA values.
   */
  private static class Parser extends LIRParsedInstructionCallback {

    private static final int ENTRY_BLOCK_INDEX = -1;

    private final AppView<?> appView;
    private final LIRCode code;
    private final NumberGenerator valueNumberGenerator = new NumberGenerator();
    private final NumberGenerator basicBlockNumberGenerator = new NumberGenerator();

    private final Value[] values;
    private final Int2ReferenceMap<BasicBlock> blocks = new Int2ReferenceOpenHashMap<>();

    private BasicBlock currentBlock = null;
    private int nextInstructionIndex = 0;

    private Position currentPosition;
    private PositionEntry nextPositionEntry = null;
    private int nextIndexInPositionsTable = 0;

    public Parser(LIRCode code, DexMethod method, AppView<?> appView) {
      super(code);
      assert code.getPositionTable().length > 0;
      assert code.getPositionTable()[0].fromInstructionIndex == 0;
      this.appView = appView;
      this.code = code;
      values = new Value[code.getArgumentCount() + code.getInstructionCount()];
      // Recreate the preamble position. This is active for arguments and code with no positions.
      currentPosition = SyntheticPosition.builder().setLine(0).setMethod(method).build();
    }

    private void closeCurrentBlock() {
      currentBlock = null;
    }

    private void ensureCurrentBlock() {
      // Control instructions must close the block, thus the current block is null iff the
      // instruction denotes a new block.
      if (currentBlock == null) {
        currentBlock = blocks.computeIfAbsent(nextInstructionIndex, k -> new BasicBlock());
      } else {
        assert !blocks.containsKey(nextInstructionIndex);
      }
    }

    private void ensureCurrentPosition() {
      if (nextPositionEntry != null
          && nextPositionEntry.fromInstructionIndex <= nextInstructionIndex) {
        currentPosition = nextPositionEntry.position;
        advanceNextPositionEntry();
      }
    }

    private void advanceNextPositionEntry() {
      nextPositionEntry =
          nextIndexInPositionsTable < code.getPositionTable().length
              ? code.getPositionTable()[nextIndexInPositionsTable++]
              : null;
    }

    public void parseArguments(ProgramMethod method) {
      currentBlock = getBasicBlock(ENTRY_BLOCK_INDEX);
      boolean hasReceiverArgument = !method.getDefinition().isStatic();
      assert code.getArgumentCount()
          == method.getParameters().size() + (hasReceiverArgument ? 1 : 0);
      if (hasReceiverArgument) {
        addThisArgument(method.getHolderType());
      }
      int index = hasReceiverArgument ? 1 : 0;
      for (DexType parameter : method.getParameters()) {
        addArgument(parameter, index++);
      }
      // Set up position state after adding arguments.
      advanceNextPositionEntry();
    }

    public IRCode getIRCode(ProgramMethod method) {
      LinkedList<BasicBlock> blockList = new LinkedList<>();
      IntList blockIndices = new IntArrayList(blocks.keySet());
      blockIndices.sort(Integer::compare);
      for (int i = 0; i < blockIndices.size(); i++) {
        BasicBlock block = blocks.get(blockIndices.getInt(i));
        block.setFilled();
        blockList.add(block);
      }
      return new IRCode(
          appView.options(),
          method,
          Position.syntheticNone(),
          blockList,
          valueNumberGenerator,
          basicBlockNumberGenerator,
          code.getMetadata(),
          method.getOrigin(),
          new MutableMethodConversionOptions(appView.options()));
    }

    public BasicBlock getBasicBlock(int instructionIndex) {
      return blocks.computeIfAbsent(
          instructionIndex,
          k -> {
            BasicBlock block = new BasicBlock();
            block.setNumber(basicBlockNumberGenerator.next());
            return block;
          });
    }

    public Value getValue(int index) {
      Value value = values[index];
      if (value == null) {
        value = new Value(valueNumberGenerator.next(), TypeElement.getBottom(), null);
        values[index] = value;
      }
      return value;
    }

    public List<Value> getValues(IntList indices) {
      List<Value> arguments = new ArrayList<>(indices.size());
      for (int i = 0; i < indices.size(); i++) {
        arguments.add(getValue(indices.getInt(i)));
      }
      return arguments;
    }

    public int peekNextInstructionIndex() {
      return nextInstructionIndex;
    }

    public Value getOutValueForNextInstruction(TypeElement type) {
      // TODO(b/225838009): Support debug locals.
      DebugLocalInfo localInfo = null;
      int index = peekNextInstructionIndex() + code.getArgumentCount();
      Value value = values[index];
      if (value == null) {
        value = new Value(valueNumberGenerator.next(), type, localInfo);
        values[index] = value;
      } else {
        value.setType(type);
        if (localInfo != null) {
          value.setLocalInfo(localInfo);
        }
      }
      return value;
    }

    public Phi getPhiForNextInstructionAndAdvanceState(TypeElement type) {
      int index = peekNextInstructionIndex() + code.getArgumentCount();
      // TODO(b/225838009): The phi constructor implicitly adds to the block, so we need to ensure
      //  the block. However, we must grab the index above. Find a way to clean this up so it is
      //  uniform with instructions.
      advanceInstructionState();
      // Creating the phi implicitly adds it to currentBlock.
      DebugLocalInfo localInfo = null;
      Phi phi =
          new Phi(
              valueNumberGenerator.next(), currentBlock, type, localInfo, RegisterReadType.NORMAL);
      Value value = values[index];
      if (value != null) {
        // A fake ssa value has already been created, replace the users by the actual phi.
        // TODO(b/225838009): We could consider encoding the value type as a bit in the value index
        //  and avoid the overhead of replacing users at phi-definition time.
        assert !value.isPhi();
        value.replaceUsers(phi);
      }
      values[index] = phi;
      return phi;
    }

    private void advanceInstructionState() {
      ensureCurrentBlock();
      ensureCurrentPosition();
      ++nextInstructionIndex;
    }

    private void addInstruction(Instruction instruction) {
      advanceInstructionState();
      instruction.setPosition(currentPosition);
      currentBlock.getInstructions().add(instruction);
      instruction.setBlock(currentBlock);
    }

    private void addThisArgument(DexType type) {
      Argument argument = addArgument(type, 0);
      argument.outValue().markAsThis();
    }

    private Argument addArgument(DexType type, int index) {
      // Arguments are not included in the "instructions" so this does not call "addInstruction"
      // which would otherwise advance the state.
      Value dest = getValue(index);
      dest.setType(type.toTypeElement(appView));
      Argument argument = new Argument(dest, index, type.isBooleanType());
      assert currentBlock != null;
      assert currentPosition.isSyntheticPosition();
      argument.setPosition(currentPosition);
      currentBlock.getInstructions().add(argument);
      argument.setBlock(currentBlock);
      return argument;
    }

    @Override
    public void onConstNull() {
      Value dest = getOutValueForNextInstruction(TypeElement.getNull());
      addInstruction(new ConstNumber(dest, 0));
    }

    @Override
    public void onConstString(DexString string) {
      Value dest = getOutValueForNextInstruction(TypeElement.stringClassType(appView));
      addInstruction(new ConstString(dest, string));
    }

    @Override
    public void onIf(Type ifKind, int blockIndex, int valueIndex) {
      BasicBlock targetBlock = getBasicBlock(blockIndex);
      Value value = getValue(valueIndex);
      addInstruction(new If(ifKind, value));
      currentBlock.link(targetBlock);
      currentBlock.link(getBasicBlock(nextInstructionIndex));
      closeCurrentBlock();
    }

    @Override
    public void onFallthrough() {
      int nextBlockIndex = peekNextInstructionIndex() + 1;
      onGoto(nextBlockIndex);
    }

    @Override
    public void onGoto(int blockIndex) {
      BasicBlock targetBlock = getBasicBlock(blockIndex);
      addInstruction(new Goto());
      currentBlock.link(targetBlock);
      closeCurrentBlock();
    }

    @Override
    public void onInvokeDirect(DexMethod target, IntList arguments) {
      // TODO(b/225838009): Maintain is-interface bit.
      Value dest = getInvokeInstructionOutputValue(target);
      List<Value> ssaArgumentValues = getValues(arguments);
      InvokeDirect instruction = new InvokeDirect(target, dest, ssaArgumentValues);
      addInstruction(instruction);
    }

    @Override
    public void onInvokeVirtual(DexMethod target, IntList arguments) {
      // TODO(b/225838009): Maintain is-interface bit.
      Value dest = getInvokeInstructionOutputValue(target);
      List<Value> ssaArgumentValues = getValues(arguments);
      InvokeVirtual instruction = new InvokeVirtual(target, dest, ssaArgumentValues);
      addInstruction(instruction);
    }

    private Value getInvokeInstructionOutputValue(DexMethod target) {
      return target.getReturnType().isVoidType()
          ? null
          : getOutValueForNextInstruction(target.getReturnType().toTypeElement(appView));
    }

    @Override
    public void onStaticGet(DexField field) {
      Value dest = getOutValueForNextInstruction(field.getTypeElement(appView));
      addInstruction(new StaticGet(dest, field));
    }

    @Override
    public void onReturnVoid() {
      addInstruction(new Return());
      closeCurrentBlock();
    }

    @Override
    public void onArrayLength(int arrayIndex) {
      Value dest = getOutValueForNextInstruction(TypeElement.getInt());
      Value arrayValue = getValue(arrayIndex);
      addInstruction(new ArrayLength(dest, arrayValue));
    }

    @Override
    public void onDebugPosition() {
      addInstruction(new DebugPosition());
    }

    @Override
    public void onPhi(DexType type, IntList operands) {
      Phi phi = getPhiForNextInstructionAndAdvanceState(type.toTypeElement(appView));
      List<Value> values = new ArrayList<>(operands.size());
      for (int i = 0; i < operands.size(); i++) {
        values.add(getValue(operands.getInt(i)));
      }
      phi.addOperands(values);
    }
  }
}

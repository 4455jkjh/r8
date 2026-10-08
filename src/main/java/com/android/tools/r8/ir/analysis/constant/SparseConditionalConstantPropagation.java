// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.constant;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.AbstractValueJoiner;
import com.android.tools.r8.ir.analysis.value.DefiniteBitsIntNumberValue;
import com.android.tools.r8.ir.code.AbstractValueSupplier;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.JumpInstruction;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StringSwitch;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.utils.NumberUtils;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.android.tools.r8.utils.internal.collections.WorkList;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Implementation of Sparse Conditional Constant Propagation from the paper of Wegman and Zadeck
 * "Constant Propagation with Conditional Branches".
 * https://www.cs.utexas.edu/users/lin/cs380c/wegman.pdf
 */
public class SparseConditionalConstantPropagation extends CodeRewriterPass<AppInfo> {

  private final AbstractValueJoiner joiner;

  public SparseConditionalConstantPropagation(AppView<?> appView) {
    super(appView);
    joiner = appView.getDefaultAbstractValueJoiner();
  }

  @Override
  protected String getRewriterId() {
    return "SparseConditionalConstantPropagation";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return true;
  }

  public Map<Value, AbstractValue> analyze(IRCode code) {
    return new SparseConditionalConstantPropagationOnCode(code).analyze().mapping;
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    return new SparseConditionalConstantPropagationOnCode(code).analyze().run();
  }

  private class SparseConditionalConstantPropagationOnCode implements AbstractValueSupplier {

    private final IRCode code;
    private final Map<Value, AbstractValue> mapping = new IdentityHashMap<>();

    private final WorkList<Value> ssaEdges = WorkList.newIdentityWorkList();

    private final WorkList<BasicBlock> flowEdges = WorkList.newIdentityWorkList();

    private final BitSet[] executableFlowEdges;
    private final BitSet visitedBlocks;

    private final Map<IntSwitch, Int2ReferenceSortedMap<BasicBlock>> intSwitchKeyToTargetMapCache =
        new IdentityHashMap<>();
    private final Map<StringSwitch, Map<DexString, BasicBlock>> stringSwitchKeyToTargetMapCache =
        new IdentityHashMap<>();

    private SparseConditionalConstantPropagationOnCode(IRCode code) {
      this.code = code;
      int maxBlockNumber = code.getCurrentBlockNumber() + 1;
      executableFlowEdges = new BitSet[maxBlockNumber];
      visitedBlocks = new BitSet(maxBlockNumber);
    }

    SparseConditionalConstantPropagationOnCode analyze() {
      BasicBlock firstBlock = code.entryBlock();
      visitInstructions(firstBlock);

      while (flowEdges.hasNext() || ssaEdges.hasNext()) {
        while (flowEdges.hasNext()) {
          BasicBlock block = flowEdges.removeSeen();
          for (Phi phi : block.getPhis()) {
            visitPhi(phi);
          }
          if (!visitedBlocks.get(block.getNumber())) {
            visitInstructions(block);
          }
        }
        while (ssaEdges.hasNext()) {
          Value value = ssaEdges.removeSeen();
          for (Phi phi : value.uniquePhiUsers()) {
            visitPhi(phi);
          }
          for (Instruction user : value.uniqueUsers()) {
            BasicBlock userBlock = user.getBlock();
            if (visitedBlocks.get(userBlock.getNumber())) {
              visitInstruction(user);
            }
          }
        }
      }
      return this;
    }

    CodeRewriterResult run() {
      boolean hasChanged = rewriteConstants();
      return CodeRewriterResult.hasChanged(hasChanged);
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean rewriteConstants() {
      AffectedValues affectedValues = new AffectedValues();
      List<BasicBlock> blockToAnalyze = new ArrayList<>();
      BooleanBox hasChanged = new BooleanBox(false);
      mapping.entrySet().stream()
          .filter(entry -> isConstNumber(entry.getKey(), entry.getValue()))
          .sorted(Comparator.comparingInt(entry -> entry.getKey().getNumber()))
          .forEach(
              entry -> {
                Value value = entry.getKey();
                if (!value.hasAnyUsers()) {
                  return;
                }
                long constValue = entry.getValue().asSingleNumberValue().getValue();
                if (value.isDefinedByInstructionSatisfying(Instruction::isConstNumber)) {
                  assert value.getDefinition().asConstNumber().getRawValue() == constValue;
                  return;
                }
                if (value.isPhi()) {
                  // D8 relies on dead code removal to get rid of the dead phi itself.
                  BasicBlock block = value.asPhi().getBlock();
                  blockToAnalyze.add(block);
                  InstructionListIterator iterator = block.listIterator();
                  Instruction inst = iterator.nextUntil(i -> !i.isMoveException());
                  if (!inst.isDebugPosition()) {
                    iterator.previous();
                  }
                  // Create a new constant, because it can be an existing constant that flow
                  // directly into the phi.
                  ConstNumber newConst =
                      ConstNumber.builder()
                          .setFreshOutValue(code, value.getType(), value.getLocalInfo())
                          .setPosition(inst.getPosition())
                          .setValue(constValue)
                          .build();
                  iterator.add(newConst);
                  value.replaceUsers(newConst.outValue(), affectedValues);
                  hasChanged.set();
                } else {
                  Instruction definition = value.getDefinition();
                  BasicBlock block = definition.getBlock();
                  InstructionListIterator iterator = block.listIterator();
                  iterator.nextUntil(i -> i == definition);
                  if (!definition.isArgument()
                      && !definition.instructionMayHaveSideEffects(appView, code.context(), this)) {
                    ConstNumber replacement =
                        ConstNumber.builder().setOutValue(value).setValue(constValue).build();
                    iterator.replaceCurrentInstruction(replacement, affectedValues);
                    hasChanged.set();
                  }
                }
              });
      rewriteAndWithDefiniteBits(affectedValues, hasChanged);
      for (BasicBlock block : blockToAnalyze) {
        block.deduplicatePhis();
      }
      affectedValues.narrowingWithAssumeRemoval(appView, code);
      boolean changed = hasChanged.get();
      if (changed) {
        code.removeAllDeadAndTrivialPhis();
        code.removeRedundantBlocks();
      }
      return changed;
    }

    private void rewriteAndWithDefiniteBits(AffectedValues affectedValues, BooleanBox hasChanged) {
      if (!options.getTestingOptions().enableAndWithDefiniteBitsOptimization) {
        return;
      }
      // Find And instructions that produces an out-value with known bits information.
      Map<And, DefiniteBitsIntNumberValue> andsToProcess =
          new TreeMap<>(Comparator.comparingInt(x -> x.outValue().getNumber()));
      for (Map.Entry<Value, AbstractValue> entry : mapping.entrySet()) {
        AbstractValue abstractValue = entry.getValue();
        if (!abstractValue.isDefiniteBitsIntNumberValue()) {
          continue;
        }
        Value value = entry.getKey();
        if (!value.hasAnyUsers()
            || value.hasLocalInfo()
            || !value.isDefinedByInstructionSatisfying(
                i -> i.isAnd() && i.asAnd().getNumericType().isInt())) {
          continue;
        }
        andsToProcess.put(
            value.getDefinition().asAnd(), abstractValue.asDefiniteBitsIntNumberValue());
      }
      andsToProcess.forEach(
          (and, definiteBits) -> {
            if (internalRewriteAndWithDefiniteBits(and, definiteBits, affectedValues)) {
              hasChanged.set();
            }
          });
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean internalRewriteAndWithDefiniteBits(
        And and, DefiniteBitsIntNumberValue andAbstractValue, AffectedValues affectedValues) {
      AbstractValue leftAbstractValue = getCachedAbstractValue(and.leftValue());
      if (leftAbstractValue.isUnknown() && and.leftValue().knownToBeBoolean()) {
        leftAbstractValue = appView.abstractValueFactory().createDefiniteBitsBooleanNumberValue();
      }

      AbstractValue rightAbstractValue = getCachedAbstractValue(and.rightValue());
      if (rightAbstractValue.isUnknown() && and.rightValue().knownToBeBoolean()) {
        rightAbstractValue = appView.abstractValueFactory().createDefiniteBitsBooleanNumberValue();
      }

      Value nonConstantValue;
      AbstractValue nonConstantAbstractValue;
      Value maskValue;
      if (and.rightValue().isConstNumber()
          && leftAbstractValue.hasDefinitelySetAndUnsetBitsInformation()) {
        nonConstantValue = and.leftValue();
        nonConstantAbstractValue = leftAbstractValue;
        maskValue = and.rightValue();
      } else if (and.leftValue().isConstNumber()
          && rightAbstractValue.hasDefinitelySetAndUnsetBitsInformation()) {
        nonConstantValue = and.rightValue();
        nonConstantAbstractValue = rightAbstractValue;
        maskValue = and.leftValue();
      } else {
        return false;
      }

      if (maskValue.hasLocalInfo()) {
        return false;
      }

      int mask = maskValue.getConstInt();
      int setBits = nonConstantAbstractValue.getDefinitelySetIntBits();
      int unsetBits = nonConstantAbstractValue.getDefinitelyUnsetIntBits();

      // If all bits cleared by the mask (~mask) are already definitely unset in x, then
      // (x & mask) == x.
      if ((~unsetBits & ~mask) == 0) {
        and.outValue().replaceUsers(nonConstantValue, affectedValues);
        return true;
      }

      // Look if the result of the And instruction is only used by an If instruction.
      Value andValue = and.outValue();
      if (andValue.hasSingleUniqueUser()
          && !andValue.hasPhiUsers()
          && andValue.singleUniqueUser().isIf()) {
        If theIf = andValue.singleUniqueUser().asIf();
        if (theIf.getType().isEqualsOrNotEquals()) {
          Value comparisonValue = null;
          int comparison = 0;
          boolean hasConstantComparison = false;
          if (theIf.isZeroTest()) {
            hasConstantComparison = true;
          } else {
            Value other = theIf.lhs() == andValue ? theIf.rhs() : theIf.lhs();
            if (other.isConstInt()) {
              comparisonValue = other;
              comparison = comparisonValue.getConstInt();
              hasConstantComparison = true;
            }
          }
          if (hasConstantComparison && andAbstractValue.maybeContainsInt(comparison)) {
            int knownBits = setBits | unsetBits;
            int unknownBits = ~knownBits;
            if ((unknownBits & ~mask) == 0) {
              // All unknown bits of the `nonConstantValue` are inside `mask`, and all bits outside
              // `mask` are known in `x`. Move the `mask` into the comparison value by rewriting
              // `(x & mask) ==/!= c` to `x ==/!= (c | (setBits & ~mask))`.
              int newComparison = comparison | (setBits & ~mask);
              InstructionListIterator ifIterator = theIf.getBlock().listIterator(theIf);
              If newIf;
              if (newComparison == 0) {
                newIf = new If(theIf.getType(), nonConstantValue);
              } else {
                Value newConstValue =
                    ifIterator.insertConstIntInstruction(code, appView.options(), newComparison);
                newIf = new If(theIf.getType(), ImmutableList.of(nonConstantValue, newConstValue));
              }
              newIf.setPosition(theIf.getPosition(), options);
              ifIterator.next();
              ifIterator.replaceCurrentInstruction(newIf);
              return true;
            }

            // Otherwise, simplify the bitwise operations by stripping known bits of
            // `nonConstantValue` from the mask and the If comparison value.
            int newMask = mask & unknownBits;
            int newComparison = comparison & unknownBits;
            if (shouldReplaceMask(mask, newMask)) {
              InstructionListIterator andIterator = and.getBlock().listIterator(and);
              Value newMaskValue =
                  andIterator.insertConstIntInstruction(code, appView.options(), newMask);
              and.replaceValue(maskValue, newMaskValue);
              if (newComparison != comparison) {
                InstructionListIterator ifIterator = theIf.getBlock().listIterator(theIf);
                if (newComparison == 0) {
                  ifIterator.next();
                  ifIterator.replaceCurrentInstruction(new If(theIf.getType(), andValue));
                } else {
                  Value newConstValue =
                      ifIterator.insertConstIntInstruction(code, appView.options(), newComparison);
                  theIf.replaceValue(comparisonValue, newConstValue);
                }
              }
              return true;
            }
          }
        }
      }

      // Clear bits from the mask that are already known to be unset in the operand, i.e.,
      // rewrite `(nonConstantValue & mask)` to `(nonConstantValue & (mask & ~unsetBits))`.
      int newMask = mask & ~unsetBits;
      if (shouldReplaceMask(mask, newMask)) {
        InstructionListIterator andIterator = and.getBlock().listIterator(and);
        Value newMaskValue =
            andIterator.insertConstIntInstruction(code, appView.options(), newMask);
        and.replaceValue(maskValue, newMaskValue);
        return true;
      }
      return false;
    }

    // Only replace a mask by a simpler mask if the encoding of the new mask is not larger than the
    // encoding of the original mask.
    private boolean shouldReplaceMask(int mask, int newMask) {
      if (newMask == mask) {
        return false;
      }
      if (NumberUtils.is8Bit(mask)) {
        return NumberUtils.is8Bit(newMask);
      }
      if (NumberUtils.is16Bit(mask)) {
        return NumberUtils.is16Bit(newMask);
      }
      return true;
    }

    private AbstractValue getCachedAbstractValue(Value value) {
      return mapping.getOrDefault(value, AbstractValue.bottom());
    }

    private void setAbstractValue(Value value, AbstractValue abstractValue) {
      mapping.put(value, abstractValue);
    }

    private boolean isConstNumber(Value value, AbstractValue abstractValue) {
      return value.getType().isPrimitiveType() && abstractValue.isSingleNumberValue();
    }

    private void visitPhi(Phi phi) {
      BasicBlock phiBlock = phi.getBlock();
      int phiBlockNumber = phiBlock.getNumber();
      AbstractValue phiValue = AbstractValue.bottom();
      List<BasicBlock> predecessors = phiBlock.getPredecessors();
      int size = predecessors.size();
      for (int i = 0; i < size; i++) {
        BasicBlock predecessor = predecessors.get(i);
        if (isExecutableEdge(predecessor.getNumber(), phiBlockNumber)) {
          phiValue =
              joiner.join(phiValue, getCachedAbstractValue(phi.getOperand(i)), phi.getType());
          // Top lattice element can no longer be changed, thus no need to continue.
          if (phiValue.isUnknown()) {
            break;
          }
        }
      }
      AbstractValue previousPhiValue = getCachedAbstractValue(phi);
      assert joiner.lessThanOrEqualTo(previousPhiValue, phiValue, phi.getType());
      if (!phiValue.equals(previousPhiValue)) {
        ssaEdges.addIfNotSeen(phi);
        setAbstractValue(phi, phiValue);
      }
    }

    private void visitInstructions(BasicBlock block) {
      for (Instruction instruction : block.getInstructions()) {
        visitInstruction(instruction);
      }
      visitedBlocks.set(block.getNumber());
    }

    private void visitInstruction(Instruction instruction) {
      if (instruction.hasOutValue() && !instruction.isDebugLocalUninitialized()) {
        AbstractValue value;
        if (instruction.isArgument()) {
          // TODO(b/296996336): Should be able to use instruction.getAbstractValue().
          int index = instruction.asArgument().getIndex();
          CallSiteOptimizationInfo argumentInfos =
              code.context().getOptimizationInfo().getArgumentInfos();
          value = argumentInfos.getAbstractArgumentValue(index);
        } else {
          value = instruction.getAbstractValue(appView, code.context(), this);
        }
        AbstractValue previousValue = getCachedAbstractValue(instruction.outValue());
        assert joiner.lessThanOrEqualTo(previousValue, value, instruction.getOutType());
        if (!value.equals(previousValue)) {
          setAbstractValue(instruction.outValue(), value);
          ssaEdges.addIfNotSeen(instruction.outValue());
        }
      }
      if (instruction.isJumpInstruction()) {
        addFlowEdgesForJumpInstruction(instruction.asJumpInstruction());
      }
    }

    private void addFlowEdgesForJumpInstruction(JumpInstruction jumpInstruction) {
      BasicBlock jumpInstBlock = jumpInstruction.getBlock();
      int jumpInstBlockNumber = jumpInstBlock.getNumber();
      if (jumpInstruction.isIf()) {
        If theIf = jumpInstruction.asIf();
        AbstractValue lhsValue = getCachedAbstractValue(theIf.lhs());
        if (theIf.isZeroTest()) {
          if (isConstNumber(theIf.lhs(), lhsValue)) {
            int intValue = lhsValue.asSingleNumberValue().getIntValue();
            BasicBlock target = theIf.targetFromCondition(Integer.signum(intValue));
            if (!isExecutableEdge(jumpInstBlockNumber, target.getNumber())) {
              setExecutableEdge(jumpInstBlockNumber, target.getNumber());
              flowEdges.addIfNotSeen(target);
            }
            return;
          }
          if (theIf.getType().isEqualsOrNotEquals()
              && lhsValue.hasDefinitelySetAndUnsetBitsInformation()
              && lhsValue.getDefinitelySetIntBits() != 0) {
            BasicBlock target = theIf.targetFromCondition(1);
            if (!isExecutableEdge(jumpInstBlockNumber, target.getNumber())) {
              setExecutableEdge(jumpInstBlockNumber, target.getNumber());
              flowEdges.addIfNotSeen(target);
            }
            return;
          }
        } else {
          AbstractValue rhsValue = getCachedAbstractValue(theIf.rhs());
          if (isConstNumber(theIf.lhs(), lhsValue) && isConstNumber(theIf.rhs(), rhsValue)) {
            long leftValue = lhsValue.asSingleNumberValue().getValue();
            long rightValue = rhsValue.asSingleNumberValue().getValue();
            BasicBlock target = theIf.targetFromCondition(leftValue, rightValue);
            if (!isExecutableEdge(jumpInstBlockNumber, target.getNumber())) {
              setExecutableEdge(jumpInstBlockNumber, target.getNumber());
              flowEdges.addIfNotSeen(target);
            }
            return;
          }
        }
      } else if (jumpInstruction.isIntSwitch()) {
        IntSwitch switchInst = jumpInstruction.asIntSwitch();
        AbstractValue value = getCachedAbstractValue(switchInst.value());
        if (isConstNumber(switchInst.value(), value)) {
          int intValue = value.asSingleNumberValue().getIntValue();
          BasicBlock target =
              intSwitchKeyToTargetMapCache
                  .computeIfAbsent(switchInst, IntSwitch::getKeyToTargetMap)
                  .getOrDefault(intValue, switchInst.fallthroughBlock());
          assert target != null;
          setExecutableEdge(jumpInstBlockNumber, target.getNumber());
          flowEdges.addIfNotSeen(target);
          return;
        }
      } else if (jumpInstruction.isStringSwitch()) {
        StringSwitch switchInst = jumpInstruction.asStringSwitch();
        AbstractValue value = getCachedAbstractValue(switchInst.value());
        BasicBlock target = null;
        if (value.isSingleStringValue()) {
          DexString stringValue = value.asSingleStringValue().getDexString();
          target =
              stringSwitchKeyToTargetMapCache
                  .computeIfAbsent(switchInst, StringSwitch::getKeyToTargetMap)
                  .getOrDefault(stringValue, switchInst.fallthroughBlock());
        } else if (value.isNull()) {
          target = switchInst.fallthroughBlock();
        }
        if (target != null) {
          setExecutableEdge(jumpInstBlockNumber, target.getNumber());
          flowEdges.addIfNotSeen(target);
          return;
        }
      } else {
        assert jumpInstruction.isGoto() || jumpInstruction.isReturn() || jumpInstruction.isThrow();
      }

      for (BasicBlock dst : jumpInstBlock.getSuccessors()) {
        if (!isExecutableEdge(jumpInstBlockNumber, dst.getNumber())) {
          setExecutableEdge(jumpInstBlockNumber, dst.getNumber());
          flowEdges.addIfNotSeen(dst);
        }
      }
    }

    private void setExecutableEdge(int from, int to) {
      BitSet previousExecutable = executableFlowEdges[to];
      if (previousExecutable == null) {
        previousExecutable = new BitSet(executableFlowEdges.length);
        executableFlowEdges[to] = previousExecutable;
      }
      previousExecutable.set(from);
    }

    private boolean isExecutableEdge(int from, int to) {
      BitSet previousExecutable = executableFlowEdges[to];
      if (previousExecutable == null) {
        return false;
      }
      return previousExecutable.get(from);
    }

    @Override
    public AbstractValue getAbstractValue(Value value, AppView<?> appView, ProgramMethod context) {
      return getCachedAbstractValue(value);
    }
  }
}

// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes.intlongarithmetic;

import static com.android.tools.r8.ir.conversion.passes.intlongarithmetic.BinopDescriptor.ADD;
import static com.android.tools.r8.ir.conversion.passes.intlongarithmetic.BinopDescriptor.SUB;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Add;
import com.android.tools.r8.ir.code.And;
import com.android.tools.r8.ir.code.Binop;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.Div;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.LogicalBinop;
import com.android.tools.r8.ir.code.Mul;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.Or;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Rem;
import com.android.tools.r8.ir.code.Shl;
import com.android.tools.r8.ir.code.Shr;
import com.android.tools.r8.ir.code.Sub;
import com.android.tools.r8.ir.code.Ushr;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.Xor;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.CodeRewriterPass;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.utils.internal.collections.WorkList;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.util.Map;

public class IntLongArithmeticRewriter extends CodeRewriterPass<AppInfo> {

  public IntLongArithmeticRewriter(AppView<?> appView) {
    super(appView);
  }

  private final Map<Class<?>, BinopDescriptor> binopDescriptors = createBinopDescriptors();
  private final Map<DexMethod, StaticDescriptor> staticDescriptors = createStaticDescriptors();

  private Map<Class<?>, BinopDescriptor> createBinopDescriptors() {
    ImmutableMap.Builder<Class<?>, BinopDescriptor> builder = ImmutableMap.builder();
    builder.put(Add.class, ADD);
    builder.put(Sub.class, SUB);
    builder.put(Mul.class, BinopDescriptor.MUL);
    builder.put(Div.class, BinopDescriptor.DIV);
    builder.put(Rem.class, BinopDescriptor.REM);
    builder.put(And.class, BinopDescriptor.AND);
    builder.put(Or.class, BinopDescriptor.OR);
    builder.put(Xor.class, BinopDescriptor.XOR);
    builder.put(Shl.class, BinopDescriptor.SHL);
    builder.put(Shr.class, BinopDescriptor.SHR);
    builder.put(Ushr.class, BinopDescriptor.USHR);
    return builder.build();
  }

  private Map<DexMethod, StaticDescriptor> createStaticDescriptors() {
    ImmutableMap.Builder<DexMethod, StaticDescriptor> builder = ImmutableMap.builder();
    builder.put(dexItemFactory.integerMembers.divideUnsigned, StaticDescriptor.DIVIDE_UNSIGNED);
    builder.put(
        dexItemFactory.integerMembers.remainderUnsigned, StaticDescriptor.REMAINDER_UNSIGNED);
    builder.put(dexItemFactory.integerMembers.min, StaticDescriptor.MIN);
    builder.put(dexItemFactory.integerMembers.max, StaticDescriptor.MAX);
    builder.put(dexItemFactory.integerMembers.sum, StaticDescriptor.ADD);
    builder.put(dexItemFactory.longMembers.divideUnsigned, StaticDescriptor.DIVIDE_UNSIGNED);
    builder.put(dexItemFactory.longMembers.remainderUnsigned, StaticDescriptor.REMAINDER_UNSIGNED);
    builder.put(dexItemFactory.longMembers.min, StaticDescriptor.MIN);
    builder.put(dexItemFactory.longMembers.max, StaticDescriptor.MAX);
    builder.put(dexItemFactory.longMembers.sum, StaticDescriptor.ADD);
    builder.put(dexItemFactory.mathMembers.addExactInt, StaticDescriptor.ADD_EXACT);
    builder.put(dexItemFactory.mathMembers.addExactLong, StaticDescriptor.ADD_EXACT);
    builder.put(dexItemFactory.mathMembers.subtractExactInt, StaticDescriptor.SUB_EXACT);
    builder.put(dexItemFactory.mathMembers.subtractExactLong, StaticDescriptor.SUB_EXACT);
    builder.put(dexItemFactory.mathMembers.multiplyExactInt, StaticDescriptor.MUL_EXACT);
    builder.put(dexItemFactory.mathMembers.multiplyExactLong, StaticDescriptor.MUL_EXACT);
    builder.put(dexItemFactory.mathMembers.minInt, StaticDescriptor.MIN);
    builder.put(dexItemFactory.mathMembers.minLong, StaticDescriptor.MIN);
    builder.put(dexItemFactory.mathMembers.maxInt, StaticDescriptor.MAX);
    builder.put(dexItemFactory.mathMembers.maxLong, StaticDescriptor.MAX);
    builder.put(dexItemFactory.mathMembers.floorDivInt, StaticDescriptor.FLOOR_DIV);
    builder.put(dexItemFactory.mathMembers.floorDivLong, StaticDescriptor.FLOOR_DIV);
    builder.put(dexItemFactory.mathMembers.floorModInt, StaticDescriptor.FLOOR_MOD);
    builder.put(dexItemFactory.mathMembers.floorModLong, StaticDescriptor.FLOOR_MOD);
    return builder.build();
  }

  @Override
  protected String getRewriterId() {
    return "BinopRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return options.testing.enableIntLongArithmeticRewriter
        && !isDebugMode(code.context())
        && (code.metadata().mayHaveArithmeticOrLogicalBinop()
            || code.metadata().mayHaveInvokeStatic());
  }

  @Override
  public CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      Instruction next = iterator.next();
      if (next.isBinop() && !next.isCmp()) {
        Binop binop = next.asBinop();
        if (binop.getNumericType() == NumericType.INT
            || binop.getNumericType() == NumericType.LONG) {
          BinopDescriptor binopDescriptor = binopDescriptors.get(binop.getClass());
          assert binopDescriptor != null;
          if (binopSimplification(iterator, binop, binopDescriptor, code)) {
            hasChanged = true;
            continue;
          }
          if (successiveSimplification(iterator, binop, binopDescriptor, code)) {
            hasChanged = true;
            continue;
          }
          // Strength reduction is done at the end to prioritize code size saving optimizations.
          // i.e., x * 3 * 2 => x * 6, not (x * 3) << 1.
          hasChanged |= strengthReduction(iterator, binop, binopDescriptor, code);
        }
      } else if (next.isInvokeStatic()) {
        InvokeStatic invokeStatic = next.asInvokeStatic();
        StaticDescriptor staticDescriptor = staticDescriptors.get(invokeStatic.getInvokedMethod());
        if (staticDescriptor != null) {
          if (staticSimplify(iterator, invokeStatic, staticDescriptor, code)) {
            hasChanged = true;
          }
        }
      }
    }
    if (hasChanged) {
      boolean mayHaveIntroducedUnreachableBlocks = code.unlinkCatchHandlerOnNonThrowableBlocks();
      if (mayHaveIntroducedUnreachableBlocks) {
        code.removeUnreachableBlocks();
      }
      code.removeAllDeadAndTrivialPhis();
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }

  private boolean successiveSimplification(
      InstructionListIterator iterator, Binop binop, BinopDescriptor binopDescriptor, IRCode code) {
    if (binop.outValue().hasDebugUsers()) {
      return false;
    }
    ConstNumber constBLeft = getConstNumber(binop.leftValue());
    ConstNumber constBRight = getConstNumber(binop.rightValue());
    if ((constBLeft != null && constBRight != null)
        || (constBLeft == null && constBRight == null)) {
      return successiveLogicalSimplificationNoConstant(iterator, binop, binopDescriptor, code);
    }
    Value otherValue = constBLeft == null ? binop.leftValue() : binop.rightValue();
    if (otherValue.isPhi() || !otherValue.getDefinition().isBinop()) {
      return false;
    }
    Binop prevBinop = otherValue.getDefinition().asBinop();
    ConstNumber constALeft = getConstNumber(prevBinop.leftValue());
    ConstNumber constARight = getConstNumber(prevBinop.rightValue());
    if ((constALeft != null && constARight != null)
        || (constALeft == null && constARight == null)) {
      return false;
    }
    ConstNumber constB = constBLeft == null ? constBRight : constBLeft;
    ConstNumber constA = constALeft == null ? constARight : constALeft;
    Value input = constALeft == null ? prevBinop.leftValue() : prevBinop.rightValue();
    // We have two successive binops so that a,b constants, x the input and a * x * b.
    if (prevBinop.getClass() == binop.getClass()) {
      if (binopDescriptor.associativeAndCommutative) {
        // a * x * b => x * (a * b) where (a * b) is a constant.
        assert binop.isCommutative();
        rewriteIntoConstThenBinop(
            iterator, binopDescriptor, binopDescriptor, constB, constA, input, true, code);
        return true;
      } else if (binopDescriptor.isShift()) {
        // x shift: a shift: b => x shift: (a + b) where a + b is a constant.
        if (constBRight != null && constARight != null) {
          return rewriteSuccessiveShift(
              iterator, binop, binopDescriptor, constBRight, constARight, input, code);
        }
      } else if (binop.isSub() && constBRight != null) {
        // a - x - b => (a - b) - x where (a - b) is a constant.
        // x - a - b => x - (a + b) where (a + b) is a constant.
        // We ignore b - (x - a) and b - (a - x) with constBRight != null.
        if (constARight == null) {
          rewriteIntoConstThenBinop(iterator, SUB, SUB, constA, constB, input, true, code);
          return true;
        } else {
          rewriteIntoConstThenBinop(iterator, ADD, SUB, constB, constA, input, false, code);
          return true;
        }
      }
    } else {
      if (binop.isSub() && prevBinop.isAdd() && constBRight != null) {
        // x + a - b => x + (a - b) where (a - b) is a constant.
        // a + x - b => x + (a - b) where (a - b) is a constant.
        // We ignore b - (x + a) and b - (a + x) with constBRight != null.
        rewriteIntoConstThenBinop(iterator, SUB, ADD, constA, constB, input, true, code);
        return true;
      } else if (binop.isAdd() && prevBinop.isSub()) {
        // x - a + b => x - (a - b) where (a - b) is a constant.
        // a - x + b => (a + b) - x where (a + b) is a constant.
        if (constALeft == null) {
          rewriteIntoConstThenBinop(iterator, SUB, SUB, constA, constB, input, false, code);
          return true;
        } else {
          rewriteIntoConstThenBinop(iterator, ADD, SUB, constB, constA, input, true, code);
          return true;
        }
      }
    }
    return false;
  }

  private boolean rewriteSuccessiveShift(
      InstructionListIterator iterator,
      Binop binop,
      BinopDescriptor binopDescriptor,
      ConstNumber constBRight,
      ConstNumber constARight,
      Value input,
      IRCode code) {
    assert binop.isShl() || binop.isShr() || binop.isUshr();
    int mask = input.outType().isWide() ? 63 : 31;
    int intA = constARight.getIntValue() & mask;
    int intB = constBRight.getIntValue() & mask;
    if (intA + intB > mask) {
      if (binop.isShr()) {
        return false;
      }
      ConstNumber zero = code.createNumberConstant(0, binop.outValue().getType());
      iterator.replaceCurrentInstruction(zero);
      return true;
    }
    iterator.previous();
    Value newConstantValue =
        iterator.insertConstNumberInstruction(
            code, appView.options(), intA + intB, TypeElement.getInt());
    iterator.next();
    replaceBinop(iterator, code, input, newConstantValue, binopDescriptor);
    return true;
  }

  private boolean successiveLogicalSimplificationNoConstant(
      InstructionListIterator iterator, Binop binop, BinopDescriptor binopDescriptor, IRCode code) {
    if (!(binop.isAnd() || binop.isOr())) {
      return false;
    }
    if (binop.leftValue().isPhi() || binop.rightValue().isPhi()) {
      return false;
    }
    LogicalBinop leftDef = binop.leftValue().getDefinition().asLogicalBinop();
    LogicalBinop rightDef = binop.rightValue().getDefinition().asLogicalBinop();
    if (leftDef == null
        || rightDef == null
        || (leftDef.getClass() != rightDef.getClass())
        || (leftDef.getNumericType() != rightDef.getNumericType())) {
      return false;
    }
    // These optimizations were implemented mostly to deal with Compose specific bit patterns.
    if (leftDef.isAnd() || leftDef.isOr()) {
      return andOrOnCommonInputSimplification(
          iterator, binop, leftDef, rightDef, binopDescriptor, code);
    }
    if (leftDef.isShl() || leftDef.isShr() || leftDef.isUshr()) {
      return shiftOnCommonValueSharing(iterator, binop, leftDef, rightDef, binopDescriptor, code);
    }
    return false;
  }

  private boolean andOrOnCommonInputSimplification(
      InstructionListIterator iterator,
      Instruction binop,
      LogicalBinop leftDef,
      LogicalBinop rightDef,
      BinopDescriptor binopDescriptor,
      IRCode code) {
    // For all permutations of & and |, represented by &| and |&.
    // (x &| a) |& (x &| b) => (a |& b) &| x.
    // a |& b will be simplified into a new constant if both constant.
    Value x, a, b;
    if (leftDef.leftValue() == rightDef.leftValue()) {
      x = leftDef.leftValue();
      a = leftDef.rightValue();
      b = rightDef.rightValue();
    } else if (leftDef.leftValue() == rightDef.rightValue()) {
      x = leftDef.leftValue();
      a = leftDef.rightValue();
      b = rightDef.leftValue();
    } else if (leftDef.rightValue() == rightDef.leftValue()) {
      x = leftDef.rightValue();
      a = leftDef.leftValue();
      b = rightDef.rightValue();
    } else if (leftDef.rightValue() == rightDef.rightValue()) {
      x = leftDef.rightValue();
      a = leftDef.leftValue();
      b = rightDef.leftValue();
    } else {
      return false;
    }

    rewriteIntoTwoSuccessiveBinops(
        iterator,
        binop.getPosition(),
        binopDescriptor,
        binopDescriptors.get(leftDef.getClass()),
        a,
        b,
        x,
        code);
    return true;
  }

  private boolean shiftOnCommonValueSharing(
      InstructionListIterator iterator,
      Instruction binop,
      LogicalBinop leftDef,
      LogicalBinop rightDef,
      BinopDescriptor binopDescriptor,
      IRCode code) {
    // For all permutations of & and |, represented by &|, and any shift operation.
    // (x shift: val) &| (y shift: val) => (x &| y) shift: val.
    // x |& y will be simplified into a new constant if both constant.
    ConstNumber constLeft = getConstNumber(leftDef.rightValue());
    if (constLeft != null) {
      // val is a constant.
      ConstNumber constRight = getConstNumber(rightDef.rightValue());
      if (constRight == null) {
        return false;
      }
      if (constRight.getRawValue() != constLeft.getRawValue()) {
        return false;
      }
    } else {
      // val is not constant.
      if (leftDef.rightValue() != rightDef.rightValue()) {
        return false;
      }
    }

    rewriteIntoTwoSuccessiveBinops(
        iterator,
        binop.getPosition(),
        binopDescriptor,
        binopDescriptors.get(leftDef.getClass()),
        leftDef.leftValue(),
        rightDef.leftValue(),
        leftDef.rightValue(),
        code);
    return true;
  }

  private void rewriteIntoTwoSuccessiveBinops(
      InstructionListIterator iterator,
      Position position,
      BinopDescriptor firstBinop,
      BinopDescriptor secondBinop,
      Value firstLeft,
      Value firstRight,
      Value secondRight,
      IRCode code) {
    // This creates something along the lines of:
    // `(firstLeft firstBinop: firstRight) secondBinop: secondOther`.
    ConstNumber constA = getConstNumber(firstLeft);
    if (constA != null) {
      ConstNumber constB = getConstNumber(firstRight);
      if (constB != null) {
        rewriteIntoConstThenBinop(
            iterator, firstBinop, secondBinop, constA, constB, secondRight, true, code);
        return;
      }
    }
    Binop newFirstBinop = instantiateBinop(code, firstLeft, firstRight, firstBinop);
    newFirstBinop.setPosition(position);
    iterator.previous();
    iterator.add(newFirstBinop);
    iterator.next();
    replaceBinop(iterator, code, newFirstBinop.outValue(), secondRight, secondBinop);
    iterator.previous();
  }

  private void rewriteIntoConstThenBinop(
      InstructionListIterator iterator,
      BinopDescriptor firstBinop,
      BinopDescriptor secondBinop,
      ConstNumber firstLeft,
      ConstNumber firstRight,
      Value secondOther,
      boolean newConstFlowsIntoLeft,
      IRCode code) {
    Value firstOutValue = insertNewConstNumber(code, iterator, firstLeft, firstRight, firstBinop);
    replaceBinop(
        iterator,
        code,
        newConstFlowsIntoLeft ? firstOutValue : secondOther,
        newConstFlowsIntoLeft ? secondOther : firstOutValue,
        secondBinop);
  }

  private void replaceBinop(
      InstructionListIterator iterator,
      IRCode code,
      Value left,
      Value right,
      BinopDescriptor binopDescriptor) {
    Binop newBinop = instantiateBinop(code, left, right, binopDescriptor);
    iterator.replaceCurrentInstruction(newBinop);
    // We need to reset the iterator state to process the new instruction(s).
    iterator.previous();
  }

  private Binop instantiateBinop(IRCode code, Value left, Value right, BinopDescriptor descriptor) {
    TypeElement representative = left.getType().isInt() ? right.getType() : left.getType();
    Value newValue = code.createValue(representative);
    NumericType numericType = representative.isInt() ? NumericType.INT : NumericType.LONG;
    return descriptor.instantiate(numericType, newValue, left, right);
  }

  private Value insertNewConstNumber(
      IRCode code,
      InstructionListIterator iterator,
      ConstNumber left,
      ConstNumber right,
      ArithmeticDescriptor descriptor) {
    TypeElement representative =
        left.outValue().getType().isInt() ? right.outValue().getType() : left.outValue().getType();
    long result =
        representative.isInt()
            ? descriptor.evaluate(left.getIntValue(), right.getIntValue())
            : descriptor.evaluate(left.getLongValue(), right.getLongValue());
    iterator.previous();
    Value value =
        iterator.insertConstNumberInstruction(
            code, appView.options(), result, left.outValue().getType());
    iterator.next();
    return value;
  }

  private boolean staticSimplify(
      InstructionListIterator iterator,
      InvokeStatic invokeStatic,
      StaticDescriptor staticDescriptor,
      IRCode code) {
    if (!invokeStatic.hasOutValue()) {
      // Normally the dead code remover can deal with this, but r8 needs to deal with it here to
      // avoid working with dead instruction and avoid dealing with invoke without out value.
      iterator.removeOrReplaceByDebugLocalRead();
      return true;
    }
    if (staticDescriptor == StaticDescriptor.MIN || staticDescriptor == StaticDescriptor.MAX) {
      if (optimizeMinMax(iterator, invokeStatic, staticDescriptor, code)) {
        return true;
      }
    }
    ConstNumber constNumber = getConstNumber(invokeStatic.getFirstArgument());
    if (constNumber != null) {
      if (simplify(
          invokeStatic,
          iterator,
          constNumber,
          staticDescriptor.leftIdentity(),
          invokeStatic.getSecondArgument(),
          staticDescriptor.leftAbsorbing(),
          invokeStatic.getFirstArgument())) {
        return true;
      }
    }
    constNumber = getConstNumber(invokeStatic.getSecondArgument());
    if (constNumber != null) {
      if (simplify(
          invokeStatic,
          iterator,
          constNumber,
          staticDescriptor.rightIdentity(),
          invokeStatic.getFirstArgument(),
          staticDescriptor.rightAbsorbing(),
          invokeStatic.getSecondArgument())) {
        return true;
      }
      if (staticDescriptor == StaticDescriptor.REMAINDER_UNSIGNED) {
        // remainderUnsigned(x, 1) => 0
        Integer intValue = extractIntValueOrNull(constNumber);
        if (intValue != null) {
          if (intValue.equals(1)) {
            replaceByConstantZero(
                iterator, invokeStatic, code, invokeStatic.getFirstArgument().getType());
            return true;
          }
          int power = extractPowerOfTwo(intValue);
          if (power != -1) {
            // remainderUnsigned(x, 2^k) => x & (2^k - 1)
            replaceByBinopWithRightConstant(
                iterator, invokeStatic.getFirstArgument(), code, intValue - 1, BinopDescriptor.AND);
            return true;
          }
        }
      }
      if (staticDescriptor == StaticDescriptor.FLOOR_MOD) {
        // floorMod(x, 1) => 0
        // floorMod(x, -1) => 0
        Integer intValue = extractIntValueOrNull(constNumber);
        if (intValue != null) {
          if (intValue.equals(-1) || intValue.equals(1)) {
            replaceByConstantZero(
                iterator, invokeStatic, code, invokeStatic.getFirstArgument().getType());
            return true;
          }
          int power = extractPowerOfTwo(intValue);
          if (power != -1) {
            // floorMod(x, 2^k) => x & (2^k - 1)
            replaceByBinopWithRightConstant(
                iterator, invokeStatic.getFirstArgument(), code, intValue - 1, BinopDescriptor.AND);
            return true;
          }
        }
      }
      if (staticDescriptor == StaticDescriptor.FLOOR_DIV) {
        // floorDiv(x, 2^k) => x >> k
        Integer intValue = extractIntValueOrNull(constNumber);
        if (intValue != null) {
          int power = extractPowerOfTwo(intValue);
          if (power != -1) {
            replaceByBinopWithRightConstant(
                iterator, invokeStatic.getFirstArgument(), code, power, BinopDescriptor.SHR);
            return true;
          }
        }
      }
      if (staticDescriptor == StaticDescriptor.DIVIDE_UNSIGNED) {
        // divideUnsigned(x, 2^k) => x >>> k
        Integer intValue = extractIntValueOrNull(constNumber);
        if (intValue != null) {
          int power = extractPowerOfTwo(intValue);
          if (power != -1) {
            replaceByBinopWithRightConstant(
                iterator, invokeStatic.getFirstArgument(), code, power, BinopDescriptor.USHR);
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean optimizeMinMax(
      InstructionListIterator iterator,
      InvokeStatic invokeStatic,
      StaticDescriptor staticDescriptor,
      IRCode code) {
    assert invokeStatic.hasOutValue();
    if (invokeStatic.getFirstArgument() == invokeStatic.getSecondArgument()) {
      // min/max (a, a) => a
      invokeStatic.outValue().replaceUsers(invokeStatic.getFirstArgument());
      iterator.removeOrReplaceByDebugLocalRead();
      return true;
    }
    ConstNumber constLeft = getConstNumber(invokeStatic.getFirstArgument());
    ConstNumber constRight = getConstNumber(invokeStatic.getSecondArgument());
    if (constLeft != null && constRight != null) {
      // min/max between constants can be resolved, it's dealt with in the library method optimizer,
      // but we need to do it again here since we generate multiple of these.
      Value value = insertNewConstNumber(code, iterator, constLeft, constRight, staticDescriptor);
      invokeStatic.outValue().replaceUsers(value);
      iterator.removeOrReplaceByDebugLocalRead();
      return true;
    }
    if ((constLeft == null) != (constRight == null) && !invokeStatic.outValue().hasDebugUsers()) {
      // min (cstA, min (cstB, x)) => min(min(cstA, cstB), x) and min(cstA, cstB) is a constant.
      // same for max.
      Value otherValue =
          constLeft == null ? invokeStatic.getFirstArgument() : invokeStatic.getSecondArgument();
      if (!otherValue.isPhi() && otherValue.getDefinition().isInvokeStatic()) {
        InvokeStatic prevInvoke = otherValue.getDefinition().asInvokeStatic();
        if (staticDescriptors.get(prevInvoke.getInvokedMethod()) == staticDescriptor) {
          ConstNumber constALeft = getConstNumber(prevInvoke.getFirstArgument());
          ConstNumber constARight = getConstNumber(prevInvoke.getSecondArgument());
          if ((constALeft == null) != (constARight == null)) {
            ConstNumber constB = constLeft != null ? constLeft : constRight;
            ConstNumber constA = constALeft != null ? constALeft : constARight;
            Value input =
                constALeft == null ? prevInvoke.getFirstArgument() : prevInvoke.getSecondArgument();
            Value firstOutValue =
                insertNewConstNumber(code, iterator, constA, constB, staticDescriptor);
            Value newValue = code.createValue(invokeStatic.outValue().getType());
            ImmutableList<Value> newArgs =
                constLeft != null
                    ? ImmutableList.of(firstOutValue, input)
                    : ImmutableList.of(input, firstOutValue);
            InvokeStatic newInvoke =
                new InvokeStatic(invokeStatic.getInvokedMethod(), newValue, newArgs);
            iterator.replaceCurrentInstruction(newInvoke);
            iterator.previous();
            return true;
          }
        }
      }
    }
    return false;
  }

  private boolean binopSimplification(
      InstructionListIterator iterator, Binop binop, BinopDescriptor binopDescriptor, IRCode code) {
    ConstNumber constNumber = getConstNumber(binop.leftValue());
    if (constNumber != null) {
      boolean isBooleanValue = binop.outValue().knownToBeBoolean();
      if (simplify(
          binop,
          iterator,
          constNumber,
          binopDescriptor.leftIdentity(isBooleanValue),
          binop.rightValue(),
          binopDescriptor.leftAbsorbing(isBooleanValue),
          binop.leftValue())) {
        return true;
      }
    }
    constNumber = getConstNumber(binop.rightValue());
    if (constNumber != null) {
      boolean isBooleanValue = binop.outValue().knownToBeBoolean();
      if (simplify(
          binop,
          iterator,
          constNumber,
          binopDescriptor.rightIdentity(isBooleanValue),
          binop.leftValue(),
          binopDescriptor.rightAbsorbing(isBooleanValue),
          binop.rightValue())) {
        return true;
      }
      if (binop.isRem()) {
        // x % 1 ==> 0, x % -1 ==> 0.
        Integer intValue = extractIntValueOrNull(constNumber);
        if (intValue != null && (intValue.equals(-1) || intValue.equals(1))) {
          replaceByConstantZero(iterator, binop, code, binop.outValue().getType());
          return true;
        }
      }
    }
    if (binop.leftValue() == binop.rightValue()) {
      if (binop.isXor() || binop.isSub()) {
        // a ^ a => 0, a - a => 0
        ConstNumber zero = code.createNumberConstant(0, binop.outValue().getType());
        iterator.replaceCurrentInstruction(zero);
        return true;
      } else if (binop.isAnd() || binop.isOr()) {
        // a & a => a, a | a => a.
        binop.outValue().replaceUsers(binop.leftValue());
        iterator.removeOrReplaceByDebugLocalRead();
        return true;
      }
    }
    return false;
  }

  private boolean strengthReduction(
      InstructionListIterator iterator, Binop binop, BinopDescriptor binopDescriptor, IRCode code) {
    ConstNumber constNumber = getConstNumber(binop.leftValue());
    if (constNumber != null) {
      if (binop.isMul()) {
        // 2^k * x => x << k
        if (reduceMul(constNumber, iterator, binop.rightValue(), code)) {
          return true;
        }
      }
    }
    constNumber = getConstNumber(binop.rightValue());
    if (constNumber != null) {
      if (binop.isMul()) {
        // x * 2^k => x << k
        if (reduceMul(constNumber, iterator, binop.leftValue(), code)) {
          return true;
        }
      }
      if (binop.isDiv()) {
        // x / -1 => x * -1
        Integer intValue = extractIntValueOrNull(constNumber);
        if (intValue != null && intValue.equals(-1)) {
          replaceBinop(iterator, code, binop.leftValue(), binop.rightValue(), BinopDescriptor.MUL);
          return true;
        }
      }
    }
    if (binop.leftValue() == binop.rightValue()) {
      if (binop.isAdd()) {
        // a + a => a << 1
        Value value = binop.leftValue();
        replaceByBinopWithRightConstant(iterator, value, code, 1, BinopDescriptor.SHL);
        return true;
      }
    }
    return false;
  }

  private boolean reduceMul(
      ConstNumber constNumber, InstructionListIterator iterator, Value binop, IRCode code) {
    Integer intValue = extractIntValueOrNull(constNumber);
    if (intValue != null) {
      int power = extractPowerOfTwo(intValue);
      if (power != -1) {
        replaceByBinopWithRightConstant(iterator, binop, code, power, BinopDescriptor.SHL);
        return true;
      }
    }
    return false;
  }

  private void replaceByBinopWithRightConstant(
      InstructionListIterator iterator,
      Value value,
      IRCode code,
      long constant,
      BinopDescriptor binopDescriptor) {
    iterator.previous();
    TypeElement type = binopDescriptor.isShift() ? TypeElement.getInt() : value.getType();
    Value cst = iterator.insertConstNumberInstruction(code, appView.options(), constant, type);
    iterator.next();
    replaceBinop(iterator, code, value, cst, binopDescriptor);
  }

  private void replaceByConstantZero(
      InstructionListIterator iterator, Instruction instruction, IRCode code, TypeElement outType) {
    replaceByConstant(iterator, instruction, code, outType, 0L);
  }

  private void replaceByConstant(
      InstructionListIterator iterator,
      Instruction instruction,
      IRCode code,
      TypeElement outType,
      long constantValue) {
    iterator.previous();
    Value value =
        iterator.insertConstNumberInstruction(code, appView.options(), constantValue, outType);
    iterator.next();
    instruction.outValue().replaceUsers(value);
    iterator.removeOrReplaceByDebugLocalRead();
  }

  @SuppressWarnings("ReferenceEquality")
  private ConstNumber getConstNumber(Value val) {
    ConstNumber constNumber = getConstNumberIfConstant(val);
    if (constNumber != null) {
      return constNumber;
    }
    // phi(v1(0), v2(0)) is equivalent to ConstNumber(0) for the simplification.
    if (val.isPhi() && getConstNumberIfConstant(val.asPhi().getOperands().get(0)) != null) {
      ConstNumber phiConstNumber = null;
      WorkList<Phi> phiWorkList = WorkList.newIdentityWorkList(val.asPhi());
      while (phiWorkList.hasNext()) {
        Phi next = phiWorkList.next();
        for (Value operand : next.getOperands()) {
          ConstNumber operandConstNumber = getConstNumberIfConstant(operand);
          if (operandConstNumber != null) {
            if (phiConstNumber == null) {
              phiConstNumber = operandConstNumber;
            } else if (operandConstNumber.getRawValue() == phiConstNumber.getRawValue()) {
              assert operandConstNumber.getOutType() == phiConstNumber.getOutType();
            } else {
              // Different const numbers, cannot conclude a value from the phi.
              return null;
            }
          } else if (operand.isPhi()) {
            phiWorkList.addIfNotSeen(operand.asPhi());
          } else {
            return null;
          }
        }
      }
      return phiConstNumber;
    }
    return null;
  }

  private static ConstNumber getConstNumberIfConstant(Value val) {
    if (val.isConstant() && val.getConstInstruction().isConstNumber()) {
      return val.getConstInstruction().asConstNumber();
    }
    return null;
  }

  private boolean simplify(
      Instruction instruction,
      InstructionListIterator iterator,
      ConstNumber constNumber,
      Integer identityElement,
      Value identityReplacement,
      Integer absorbingElement,
      Value absorbingReplacement) {
    Integer intValue = extractIntValueOrNull(constNumber);
    assert instruction.hasOutValue();
    if (identityElement != null && identityElement.equals(intValue)) {
      instruction.outValue().replaceUsers(identityReplacement);
      iterator.removeOrReplaceByDebugLocalRead();
      return true;
    }
    if (absorbingElement != null && absorbingElement.equals(intValue)) {
      instruction.outValue().replaceUsers(absorbingReplacement);
      iterator.removeOrReplaceByDebugLocalRead();
      return true;
    }
    return false;
  }

  private static Integer extractIntValueOrNull(ConstNumber constNumber) {
    Integer intValue;
    if (constNumber.outValue().getType().isInt()) {
      intValue = constNumber.getIntValue();
    } else {
      assert constNumber.outValue().getType().isLong();
      long longValue = constNumber.getLongValue();
      intValue = (int) longValue;
      if ((long) intValue != longValue) {
        intValue = null;
      }
    }
    return intValue;
  }

  private int extractPowerOfTwo(int i) {
    if (Integer.bitCount(i) == 1 && i != 1 && i > 0) {
      return Integer.numberOfTrailingZeros(i);
    } else {
      return -1;
    }
  }
}

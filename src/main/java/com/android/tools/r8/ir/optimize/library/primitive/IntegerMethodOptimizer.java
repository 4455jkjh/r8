// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory.IntegerMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.Set;

public class IntegerMethodOptimizer extends PrimitiveMethodOptimizer {

  private final IntegerMembers integerMembers;

  IntegerMethodOptimizer(AppView<?> appView) {
    super(appView);
    this.integerMembers = factory.integerMembers;
  }

  @Override
  DexMethod getBoxMethod() {
    return integerMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return integerMembers.intValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedInteger();
  }

  @Override
  public DexType getType() {
    return factory.boxedIntType;
  }

  @Override
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    DexMethod singleTargetReference = singleTarget.getReference();
    switch (singleTargetReference.getName().getFirstByteAsChar()) {
      case 'b':
        if (singleTargetReference.isIdenticalTo(integerMembers.bitCount)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Integer::bitCount);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.byteValue)) {
          optimizeBoxedIntToIntFunction(code, instructionIterator, invoke, Integer::byteValue);
        }
        break;
      case 'c':
        if (singleTargetReference.isIdenticalTo(integerMembers.compare)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Integer::compare);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.compareTo)) {
          optimizeBoxedIntBoxedIntToIntFunction(
              code, instructionIterator, invoke, Integer::compareTo);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.compareUnsigned)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Integer::compareUnsigned);
        }
        break;
      case 'd':
        if (singleTargetReference.isIdenticalTo(integerMembers.decode)) {
          optimizeDecode(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.divideUnsigned)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Integer::divideUnsigned);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.doubleValue)) {
          optimizeBoxedIntToDoubleFunction(code, instructionIterator, invoke, Integer::doubleValue);
        }
        break;
      case 'e':
        if (singleTargetReference.isIdenticalTo(integerMembers.equals)) {
          optimizeBoxedIntBoxedIntToBooleanFunction(
              code, instructionIterator, invoke, Integer::equals);
        }
        break;
      case 'f':
        if (singleTargetReference.isIdenticalTo(integerMembers.floatValue)) {
          optimizeBoxedIntToFloatFunction(code, instructionIterator, invoke, Integer::floatValue);
        }
        break;
      case 'h':
        if (singleTargetReference.isIdenticalTo(integerMembers.hashCode)) {
          optimizeBoxedIntToIntFunction(code, instructionIterator, invoke, Object::hashCode);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.staticHashCode)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Integer::hashCode);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.highestOneBit)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Integer::highestOneBit);
        }
        break;
      case 'i':
        if (singleTargetReference.isIdenticalTo(integerMembers.intValue)) {
          optimizeUnboxMethod(code, instructionIterator, invoke);
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(integerMembers.longValue)) {
          optimizeBoxedIntToLongFunction(code, instructionIterator, invoke, Integer::longValue);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.lowestOneBit)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Integer::lowestOneBit);
        }
        break;
      case 'm':
        if (singleTargetReference.isIdenticalTo(integerMembers.max)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Integer::max);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.min)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Integer::min);
        }
        break;
      case 'n':
        if (singleTargetReference.isIdenticalTo(integerMembers.numberOfLeadingZeros)) {
          optimizeIntToIntFunction(
              code, instructionIterator, invoke, Integer::numberOfLeadingZeros);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.numberOfTrailingZeros)) {
          optimizeIntToIntFunction(
              code, instructionIterator, invoke, Integer::numberOfTrailingZeros);
        }
        break;
      case 'p':
        if (singleTargetReference.isIdenticalTo(integerMembers.parseInt)) {
          optimizeStringToIntFunction(
              code, instructionIterator, invoke, s -> Integer.parseInt(s.toString()));
        } else if (singleTargetReference.isIdenticalTo(integerMembers.parseIntWithRadix)) {
          optimizeStringIntToIntFunction(
              code, instructionIterator, invoke, (s, r) -> Integer.parseInt(s.toString(), r));
        } else if (singleTargetReference.isIdenticalTo(integerMembers.parseUnsignedInt)) {
          optimizeStringToIntFunction(
              code, instructionIterator, invoke, s -> Integer.parseUnsignedInt(s.toString()));
        } else if (singleTargetReference.isIdenticalTo(integerMembers.parseUnsignedIntWithRadix)) {
          optimizeStringIntToIntFunction(
              code,
              instructionIterator,
              invoke,
              (s, r) -> Integer.parseUnsignedInt(s.toString(), r));
        }
        break;
      case 'r':
        if (singleTargetReference.isIdenticalTo(integerMembers.remainderUnsigned)) {
          optimizeIntIntToIntFunction(
              code, instructionIterator, invoke, Integer::remainderUnsigned);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.reverse)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Integer::reverse);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.reverseBytes)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Integer::reverseBytes);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.rotateLeft)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Integer::rotateLeft);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.rotateRight)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Integer::rotateRight);
        }
        break;
      case 's':
        if (singleTargetReference.isIdenticalTo(integerMembers.shortValue)) {
          optimizeBoxedIntToIntFunction(code, instructionIterator, invoke, Integer::shortValue);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.signum)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Integer::signum);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.sum)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Integer::sum);
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(integerMembers.toBinaryString)) {
          optimizeIntToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              i -> factory.createString(Integer.toBinaryString(i)));
        } else if (singleTargetReference.isIdenticalTo(integerMembers.toHexString)) {
          optimizeIntToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              i -> factory.createString(Integer.toHexString(i)));
        } else if (singleTargetReference.isIdenticalTo(integerMembers.toOctalString)) {
          optimizeIntToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              i -> factory.createString(Integer.toOctalString(i)));
        } else if (singleTargetReference.isIdenticalTo(integerMembers.toString)) {
          optimizeBoxedIntToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              i -> factory.createString(i.toString()));
        } else if (singleTargetReference.isIdenticalTo(integerMembers.staticToString)) {
          optimizeIntToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              i -> factory.createString(Integer.toString(i)));
        } else if (singleTargetReference.isIdenticalTo(integerMembers.toStringWithRadix)) {
          optimizeIntIntToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              (i, r) -> factory.createString(Integer.toString(i, r)));
        } else if (singleTargetReference.isIdenticalTo(integerMembers.toUnsignedLong)) {
          optimizeIntToLongFunction(code, instructionIterator, invoke, Integer::toUnsignedLong);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.toUnsignedString)) {
          optimizeIntToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              i -> factory.createString(Integer.toUnsignedString(i)));
        } else if (singleTargetReference.isIdenticalTo(integerMembers.toUnsignedStringWithRadix)) {
          optimizeIntIntToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              (i, r) -> factory.createString(Integer.toUnsignedString(i, r)));
        }
        break;
      case 'v':
        if (singleTargetReference.isIdenticalTo(integerMembers.valueOf)) {
          optimizeBoxMethod(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.valueOfString)) {
          optimizeValueOfString(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(integerMembers.valueOfStringWithRadix)) {
          optimizeValueOfStringWithRadix(code, instructionIterator, invoke, affectedValues);
        }
        break;
      default:
        break;
    }
    return instructionIterator;
  }

  private void optimizeValueOfResult(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      int result) {
    if (invoke.hasUnusedOutValue()) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else {
      Value constInt =
          instructionIterator.insertConstNumberInstruction(
              code, appView.options(), result, TypeElement.getInt());
      InvokeStatic invokeStatic =
          InvokeStatic.builder()
              .setMethod(integerMembers.valueOf)
              .setSingleArgument(constInt)
              .setOutValue(invoke.outValue())
              .setPosition(invoke)
              .build();
      instructionIterator.replaceCurrentInstruction(invokeStatic, affectedValues);
    }
  }

  private void optimizeDecode(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(appView, code);
    if (s != null) {
      int result;
      try {
        result = Integer.decode(s.toString());
      } catch (NumberFormatException ignored) {
        // Leave as is if format is invalid.
        return;
      }
      optimizeValueOfResult(code, instructionIterator, invoke, affectedValues, result);
    }
  }

  private void optimizeValueOfString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(appView, code);
    if (s != null) {
      try {
        int result = Integer.parseInt(s.toString());
        optimizeValueOfResult(code, instructionIterator, invoke, affectedValues, result);
      } catch (NumberFormatException ignored) {
        // Leave as is if format is invalid.
      }
    }
  }

  private void optimizeValueOfStringWithRadix(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(appView, code);
    if (s == null) {
      return;
    }
    Integer radix = invoke.getSecondArgument().getConstIntOrNull(appView, code);
    if (radix == null) {
      return;
    }
    try {
      int result = Integer.parseInt(s.toString(), radix);
      optimizeValueOfResult(code, instructionIterator, invoke, affectedValues, result);
    } catch (NumberFormatException ignored) {
      // Leave as is if format is invalid.
    }
  }
}

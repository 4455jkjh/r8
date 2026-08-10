// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory.LongMembers;
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

public class LongMethodOptimizer extends PrimitiveMethodOptimizer {

  private final LongMembers longMembers;

  LongMethodOptimizer(AppView<?> appView) {
    super(appView);
    this.longMembers = factory.longMembers;
  }

  @Override
  DexMethod getBoxMethod() {
    return longMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return longMembers.longValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedLong();
  }

  @Override
  public DexType getType() {
    return factory.boxedLongType;
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
        if (singleTargetReference.isIdenticalTo(longMembers.bitCount)) {
          optimizeLongToIntFunction(code, instructionIterator, invoke, Long::bitCount);
        } else if (singleTargetReference.isIdenticalTo(longMembers.byteValue)) {
          optimizeBoxedLongToIntFunction(code, instructionIterator, invoke, Long::byteValue);
        }
        break;
      case 'c':
        if (singleTargetReference.isIdenticalTo(longMembers.compare)) {
          optimizeLongLongToIntFunction(code, instructionIterator, invoke, Long::compare);
        } else if (singleTargetReference.isIdenticalTo(longMembers.compareTo)) {
          optimizeBoxedLongBoxedLongToIntFunction(
              code, instructionIterator, invoke, Long::compareTo);
        } else if (singleTargetReference.isIdenticalTo(longMembers.compareUnsigned)) {
          optimizeLongLongToIntFunction(code, instructionIterator, invoke, Long::compareUnsigned);
        }
        break;
      case 'd':
        if (singleTargetReference.isIdenticalTo(longMembers.decode)) {
          optimizeDecode(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(longMembers.divideUnsigned)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Long::divideUnsigned);
        } else if (singleTargetReference.isIdenticalTo(longMembers.doubleValue)) {
          optimizeBoxedLongToDoubleFunction(code, instructionIterator, invoke, Long::doubleValue);
        }
        break;
      case 'e':
        if (singleTargetReference.isIdenticalTo(longMembers.equals)) {
          optimizeBoxedLongBoxedLongToBooleanFunction(
              code, instructionIterator, invoke, Long::equals);
        }
        break;
      case 'f':
        if (singleTargetReference.isIdenticalTo(longMembers.floatValue)) {
          optimizeBoxedLongToFloatFunction(code, instructionIterator, invoke, Long::floatValue);
        }
        break;
      case 'h':
        if (singleTargetReference.isIdenticalTo(longMembers.hashCode)) {
          optimizeBoxedLongToIntFunction(code, instructionIterator, invoke, Object::hashCode);
        } else if (singleTargetReference.isIdenticalTo(longMembers.staticHashCode)) {
          optimizeLongToIntFunction(code, instructionIterator, invoke, Long::hashCode);
        } else if (singleTargetReference.isIdenticalTo(longMembers.highestOneBit)) {
          optimizeLongToLongFunction(code, instructionIterator, invoke, Long::highestOneBit);
        }
        break;
      case 'i':
        if (singleTargetReference.isIdenticalTo(longMembers.intValue)) {
          optimizeBoxedLongToIntFunction(code, instructionIterator, invoke, Long::intValue);
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(longMembers.longValue)) {
          optimizeUnboxMethod(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(longMembers.lowestOneBit)) {
          optimizeLongToLongFunction(code, instructionIterator, invoke, Long::lowestOneBit);
        }
        break;
      case 'm':
        if (singleTargetReference.isIdenticalTo(longMembers.max)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Long::max);
        } else if (singleTargetReference.isIdenticalTo(longMembers.min)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Long::min);
        }
        break;
      case 'n':
        if (singleTargetReference.isIdenticalTo(longMembers.numberOfLeadingZeros)) {
          optimizeLongToIntFunction(code, instructionIterator, invoke, Long::numberOfLeadingZeros);
        } else if (singleTargetReference.isIdenticalTo(longMembers.numberOfTrailingZeros)) {
          optimizeLongToIntFunction(code, instructionIterator, invoke, Long::numberOfTrailingZeros);
        }
        break;
      case 'p':
        if (singleTargetReference.isIdenticalTo(longMembers.parseLong)) {
          optimizeStringToLongFunction(
              code, instructionIterator, invoke, s -> Long.parseLong(s.toString()));
        } else if (singleTargetReference.isIdenticalTo(longMembers.parseLongWithRadix)) {
          optimizeStringIntToLongFunction(
              code, instructionIterator, invoke, (s, r) -> Long.parseLong(s.toString(), r));
        } else if (singleTargetReference.isIdenticalTo(longMembers.parseUnsignedLong)) {
          optimizeStringToLongFunction(
              code, instructionIterator, invoke, s -> Long.parseUnsignedLong(s.toString()));
        } else if (singleTargetReference.isIdenticalTo(longMembers.parseUnsignedLongWithRadix)) {
          optimizeStringIntToLongFunction(
              code, instructionIterator, invoke, (s, r) -> Long.parseUnsignedLong(s.toString(), r));
        }
        break;
      case 'r':
        if (singleTargetReference.isIdenticalTo(longMembers.remainderUnsigned)) {
          optimizeLongLongToLongFunction(
              code, instructionIterator, invoke, Long::remainderUnsigned);
        } else if (singleTargetReference.isIdenticalTo(longMembers.reverse)) {
          optimizeLongToLongFunction(code, instructionIterator, invoke, Long::reverse);
        } else if (singleTargetReference.isIdenticalTo(longMembers.reverseBytes)) {
          optimizeLongToLongFunction(code, instructionIterator, invoke, Long::reverseBytes);
        } else if (singleTargetReference.isIdenticalTo(longMembers.rotateLeft)) {
          optimizeLongIntToLongFunction(code, instructionIterator, invoke, Long::rotateLeft);
        } else if (singleTargetReference.isIdenticalTo(longMembers.rotateRight)) {
          optimizeLongIntToLongFunction(code, instructionIterator, invoke, Long::rotateRight);
        }
        break;
      case 's':
        if (singleTargetReference.isIdenticalTo(longMembers.shortValue)) {
          optimizeBoxedLongToIntFunction(code, instructionIterator, invoke, Long::shortValue);
        } else if (singleTargetReference.isIdenticalTo(longMembers.signum)) {
          optimizeLongToIntFunction(code, instructionIterator, invoke, Long::signum);
        } else if (singleTargetReference.isIdenticalTo(longMembers.sum)) {
          optimizeLongLongToLongFunction(code, instructionIterator, invoke, Long::sum);
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(longMembers.toBinaryString)) {
          optimizeLongToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              l -> factory.createString(Long.toBinaryString(l)));
        } else if (singleTargetReference.isIdenticalTo(longMembers.toHexString)) {
          optimizeLongToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              l -> factory.createString(Long.toHexString(l)));
        } else if (singleTargetReference.isIdenticalTo(longMembers.toOctalString)) {
          optimizeLongToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              l -> factory.createString(Long.toOctalString(l)));
        } else if (singleTargetReference.isIdenticalTo(longMembers.toString)) {
          optimizeBoxedLongToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              l -> factory.createString(l.toString()));
        } else if (singleTargetReference.isIdenticalTo(longMembers.staticToString)) {
          optimizeLongToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              l -> factory.createString(Long.toString(l)));
        } else if (singleTargetReference.isIdenticalTo(longMembers.toStringWithRadix)) {
          optimizeLongIntToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              (l, r) -> factory.createString(Long.toString(l, r)));
        } else if (singleTargetReference.isIdenticalTo(longMembers.toUnsignedString)) {
          optimizeLongToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              l -> factory.createString(Long.toUnsignedString(l)));
        } else if (singleTargetReference.isIdenticalTo(longMembers.toUnsignedStringWithRadix)) {
          optimizeLongIntToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              (l, r) -> factory.createString(Long.toUnsignedString(l, r)));
        }
        break;
      case 'v':
        if (singleTargetReference.isIdenticalTo(longMembers.valueOf)) {
          optimizeBoxMethod(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(longMembers.valueOfString)) {
          optimizeValueOfString(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(longMembers.valueOfStringWithRadix)) {
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
      long result) {
    if (invoke.hasUnusedOutValue()) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else {
      Value constLong =
          instructionIterator.insertConstNumberInstruction(
              code, appView.options(), result, TypeElement.getLong());
      InvokeStatic invokeStatic =
          InvokeStatic.builder()
              .setMethod(longMembers.valueOf)
              .setSingleArgument(constLong)
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
      long result;
      try {
        result = Long.decode(s.toString());
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
        long result = Long.parseLong(s.toString());
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
      long result = Long.parseLong(s.toString(), radix);
      optimizeValueOfResult(code, instructionIterator, invoke, affectedValues, result);
    } catch (NumberFormatException ignored) {
      // Leave as is if format is invalid.
    }
  }
}

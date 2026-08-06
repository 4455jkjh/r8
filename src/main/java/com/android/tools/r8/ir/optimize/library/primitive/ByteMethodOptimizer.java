// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory.ByteMembers;
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

public class ByteMethodOptimizer extends PrimitiveMethodOptimizer {

  private final ByteMembers byteMembers;

  ByteMethodOptimizer(AppView<?> appView) {
    super(appView);
    this.byteMembers = factory.byteMembers;
  }

  @Override
  DexMethod getBoxMethod() {
    return byteMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return byteMembers.byteValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedByte();
  }

  @Override
  public DexType getType() {
    return factory.boxedByteType;
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
        if (singleTargetReference.isIdenticalTo(byteMembers.byteValue)) {
          optimizeUnboxMethod(code, instructionIterator, invoke);
        }
        break;
      case 'c':
        if (singleTargetReference.isIdenticalTo(byteMembers.compare)) {
          optimizeByteByteToIntFunction(code, instructionIterator, invoke, Byte::compare);
        } else if (singleTargetReference.isIdenticalTo(byteMembers.compareTo)) {
          optimizeBoxedByteBoxedByteToIntFunction(
              code, instructionIterator, invoke, Byte::compareTo);
        }
        break;
      case 'd':
        if (singleTargetReference.isIdenticalTo(byteMembers.decode)) {
          optimizeDecode(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(byteMembers.doubleValue)) {
          optimizeBoxedByteToDoubleFunction(code, instructionIterator, invoke, Byte::doubleValue);
        }
        break;
      case 'e':
        if (singleTargetReference.isIdenticalTo(byteMembers.equals)) {
          optimizeBoxedByteBoxedByteToBooleanFunction(
              code, instructionIterator, invoke, Byte::equals);
        }
        break;
      case 'f':
        if (singleTargetReference.isIdenticalTo(byteMembers.floatValue)) {
          optimizeBoxedByteToFloatFunction(code, instructionIterator, invoke, Byte::floatValue);
        }
        break;
      case 'h':
        if (singleTargetReference.isIdenticalTo(byteMembers.hashCode)) {
          optimizeBoxedByteToIntFunction(code, instructionIterator, invoke, Object::hashCode);
        } else if (singleTargetReference.isIdenticalTo(byteMembers.staticHashCode)) {
          optimizeByteToIntFunction(code, instructionIterator, invoke, Byte::hashCode);
        }
        break;
      case 'i':
        if (singleTargetReference.isIdenticalTo(byteMembers.intValue)) {
          optimizeBoxedByteToIntFunction(code, instructionIterator, invoke, Byte::intValue);
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(byteMembers.longValue)) {
          optimizeBoxedByteToLongFunction(code, instructionIterator, invoke, Byte::longValue);
        }
        break;
      case 'p':
        if (singleTargetReference.isIdenticalTo(byteMembers.parseByte)) {
          optimizeStringToIntFunction(
              code, instructionIterator, invoke, s -> Byte.parseByte(s.toString()));
        } else if (singleTargetReference.isIdenticalTo(byteMembers.parseByteWithRadix)) {
          optimizeStringIntToIntFunction(
              code, instructionIterator, invoke, (s, r) -> Byte.parseByte(s.toString(), r));
        }
        break;
      case 's':
        if (singleTargetReference.isIdenticalTo(byteMembers.shortValue)) {
          optimizeBoxedByteToIntFunction(code, instructionIterator, invoke, Byte::shortValue);
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(byteMembers.toString)) {
          optimizeBoxedByteToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              b -> factory.createString(b.toString()));
        } else if (singleTargetReference.isIdenticalTo(byteMembers.staticToString)) {
          optimizeByteToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              b -> factory.createString(Byte.toString(b)));
        } else if (singleTargetReference.isIdenticalTo(byteMembers.toUnsignedInt)) {
          optimizeByteToIntFunction(code, instructionIterator, invoke, Byte::toUnsignedInt);
        } else if (singleTargetReference.isIdenticalTo(byteMembers.toUnsignedLong)) {
          optimizeByteToLongFunction(code, instructionIterator, invoke, Byte::toUnsignedLong);
        }
        break;
      case 'v':
        if (singleTargetReference.isIdenticalTo(byteMembers.valueOf)) {
          optimizeBoxMethod(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(byteMembers.valueOfString)) {
          optimizeValueOfString(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(byteMembers.valueOfStringWithRadix)) {
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
      byte result) {
    if (invoke.hasUnusedOutValue()) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else {
      Value constInt =
          instructionIterator.insertConstNumberInstruction(
              code, appView.options(), result, TypeElement.getInt());
      InvokeStatic invokeStatic =
          InvokeStatic.builder()
              .setMethod(byteMembers.valueOf)
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
      byte result;
      try {
        result = Byte.decode(s.toString());
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
        byte result = Byte.parseByte(s.toString());
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
      byte result = Byte.parseByte(s.toString(), radix);
      optimizeValueOfResult(code, instructionIterator, invoke, affectedValues, result);
    } catch (NumberFormatException ignored) {
      // Leave as is if format is invalid.
    }
  }
}

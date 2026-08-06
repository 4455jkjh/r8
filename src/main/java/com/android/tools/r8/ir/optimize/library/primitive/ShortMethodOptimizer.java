// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory.ShortMembers;
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

public class ShortMethodOptimizer extends PrimitiveMethodOptimizer {

  private final ShortMembers shortMembers;

  ShortMethodOptimizer(AppView<?> appView) {
    super(appView);
    this.shortMembers = factory.shortMembers;
  }

  @Override
  DexMethod getBoxMethod() {
    return shortMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return shortMembers.shortValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedShort();
  }

  @Override
  public DexType getType() {
    return factory.boxedShortType;
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
        if (singleTargetReference.isIdenticalTo(shortMembers.byteValue)) {
          optimizeBoxedShortToIntFunction(code, instructionIterator, invoke, Short::byteValue);
        }
        break;
      case 'c':
        if (singleTargetReference.isIdenticalTo(shortMembers.compare)) {
          optimizeShortShortToIntFunction(code, instructionIterator, invoke, Short::compare);
        } else if (singleTargetReference.isIdenticalTo(shortMembers.compareTo)) {
          optimizeBoxedShortBoxedShortToIntFunction(
              code, instructionIterator, invoke, Short::compareTo);
        }
        break;
      case 'd':
        if (singleTargetReference.isIdenticalTo(shortMembers.decode)) {
          optimizeDecode(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(shortMembers.doubleValue)) {
          optimizeBoxedShortToDoubleFunction(code, instructionIterator, invoke, Short::doubleValue);
        }
        break;
      case 'e':
        if (singleTargetReference.isIdenticalTo(shortMembers.equals)) {
          optimizeBoxedShortBoxedShortToBooleanFunction(
              code, instructionIterator, invoke, Short::equals);
        }
        break;
      case 'f':
        if (singleTargetReference.isIdenticalTo(shortMembers.floatValue)) {
          optimizeBoxedShortToFloatFunction(code, instructionIterator, invoke, Short::floatValue);
        }
        break;
      case 'h':
        if (singleTargetReference.isIdenticalTo(shortMembers.hashCode)) {
          optimizeBoxedShortToIntFunction(code, instructionIterator, invoke, Object::hashCode);
        } else if (singleTargetReference.isIdenticalTo(shortMembers.staticHashCode)) {
          optimizeShortToIntFunction(code, instructionIterator, invoke, Short::hashCode);
        }
        break;
      case 'i':
        if (singleTargetReference.isIdenticalTo(shortMembers.intValue)) {
          optimizeBoxedShortToIntFunction(code, instructionIterator, invoke, Short::intValue);
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(shortMembers.longValue)) {
          optimizeBoxedShortToLongFunction(code, instructionIterator, invoke, Short::longValue);
        }
        break;
      case 'p':
        if (singleTargetReference.isIdenticalTo(shortMembers.parseShort)) {
          optimizeStringToIntFunction(
              code, instructionIterator, invoke, s -> Short.parseShort(s.toString()));
        } else if (singleTargetReference.isIdenticalTo(shortMembers.parseShortWithRadix)) {
          optimizeStringIntToIntFunction(
              code, instructionIterator, invoke, (s, r) -> Short.parseShort(s.toString(), r));
        }
        break;
      case 'r':
        if (singleTargetReference.isIdenticalTo(shortMembers.reverseBytes)) {
          optimizeShortToIntFunction(code, instructionIterator, invoke, Short::reverseBytes);
        }
        break;
      case 's':
        if (singleTargetReference.isIdenticalTo(shortMembers.shortValue)) {
          optimizeUnboxMethod(code, instructionIterator, invoke);
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(shortMembers.toString)) {
          optimizeBoxedShortToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              s -> factory.createString(s.toString()));
        } else if (singleTargetReference.isIdenticalTo(shortMembers.staticToString)) {
          optimizeShortToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              s -> factory.createString(Short.toString(s)));
        } else if (singleTargetReference.isIdenticalTo(shortMembers.toUnsignedInt)) {
          optimizeShortToIntFunction(code, instructionIterator, invoke, Short::toUnsignedInt);
        } else if (singleTargetReference.isIdenticalTo(shortMembers.toUnsignedLong)) {
          optimizeShortToLongFunction(code, instructionIterator, invoke, Short::toUnsignedLong);
        }
        break;
      case 'v':
        if (singleTargetReference.isIdenticalTo(shortMembers.valueOf)) {
          optimizeBoxMethod(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(shortMembers.valueOfString)) {
          optimizeValueOfString(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(shortMembers.valueOfStringWithRadix)) {
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
      short result) {
    if (invoke.hasUnusedOutValue()) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else {
      Value constInt =
          instructionIterator.insertConstNumberInstruction(
              code, appView.options(), result, TypeElement.getInt());
      InvokeStatic invokeStatic =
          InvokeStatic.builder()
              .setMethod(shortMembers.valueOf)
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
      try {
        short result = Short.decode(s.toString());
        optimizeValueOfResult(code, instructionIterator, invoke, affectedValues, result);
      } catch (NumberFormatException ignored) {
        // Leave as is if format is invalid.
      }
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
        short result = Short.parseShort(s.toString());
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
      short result = Short.parseShort(s.toString(), radix);
      optimizeValueOfResult(code, instructionIterator, invoke, affectedValues, result);
    } catch (NumberFormatException ignored) {
      // Leave as is if format is invalid.
    }
  }
}

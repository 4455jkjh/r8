// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory.FloatMembers;
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
import com.android.tools.r8.utils.internal.LongUtils;
import java.util.Set;

public class FloatMethodOptimizer extends PrimitiveMethodOptimizer {

  private final FloatMembers floatMembers;

  FloatMethodOptimizer(AppView<?> appView) {
    super(appView);
    this.floatMembers = factory.floatMembers;
  }

  @Override
  DexMethod getBoxMethod() {
    return floatMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return floatMembers.floatValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedFloat();
  }

  @Override
  public DexType getType() {
    return factory.boxedFloatType;
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
        if (singleTargetReference.isIdenticalTo(floatMembers.byteValue)) {
          optimizeBoxedFloatToIntFunction(code, instructionIterator, invoke, Float::byteValue);
        }
        break;
      case 'c':
        if (singleTargetReference.isIdenticalTo(floatMembers.compare)) {
          optimizeFloatFloatToIntFunction(code, instructionIterator, invoke, Float::compare);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.compareTo)) {
          optimizeBoxedFloatBoxedFloatToIntFunction(
              code, instructionIterator, invoke, Float::compareTo);
        }
        break;
      case 'd':
        if (singleTargetReference.isIdenticalTo(floatMembers.doubleValue)) {
          optimizeBoxedFloatToDoubleFunction(code, instructionIterator, invoke, Float::doubleValue);
        }
        break;
      case 'f':
        if (singleTargetReference.isIdenticalTo(floatMembers.floatToIntBits)) {
          optimizeFloatToIntFunction(code, instructionIterator, invoke, Float::floatToIntBits);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.floatToRawIntBits)) {
          optimizeFloatToIntFunction(code, instructionIterator, invoke, Float::floatToRawIntBits);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.floatValue)) {
          optimizeUnboxMethod(code, instructionIterator, invoke);
        }
        break;
      case 'h':
        if (singleTargetReference.isIdenticalTo(floatMembers.hashCode)) {
          optimizeBoxedFloatToIntFunction(code, instructionIterator, invoke, Object::hashCode);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.staticHashCode)) {
          optimizeFloatToIntFunction(code, instructionIterator, invoke, Float::hashCode);
        }
        break;
      case 'i':
        if (singleTargetReference.isIdenticalTo(floatMembers.intBitsToFloat)) {
          optimizeIntToFloatFunction(code, instructionIterator, invoke, Float::intBitsToFloat);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.intValue)) {
          optimizeBoxedFloatToIntFunction(code, instructionIterator, invoke, Float::intValue);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.isFinite)) {
          optimizeFloatToBooleanFunction(code, instructionIterator, invoke, Float::isFinite);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.isInfinite)) {
          optimizeBoxedFloatToBooleanFunction(
              code, instructionIterator, invoke, f -> f.isInfinite());
        } else if (singleTargetReference.isIdenticalTo(floatMembers.staticIsInfinite)) {
          optimizeFloatToBooleanFunction(code, instructionIterator, invoke, Float::isInfinite);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.isNaN)) {
          optimizeBoxedFloatToBooleanFunction(code, instructionIterator, invoke, f -> f.isNaN());
        } else if (singleTargetReference.isIdenticalTo(floatMembers.staticIsNaN)) {
          optimizeFloatToBooleanFunction(code, instructionIterator, invoke, Float::isNaN);
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(floatMembers.longValue)) {
          optimizeBoxedFloatToLongFunction(code, instructionIterator, invoke, Float::longValue);
        }
        break;
      case 'm':
        if (singleTargetReference.isIdenticalTo(floatMembers.max)) {
          optimizeFloatFloatToFloatFunction(code, instructionIterator, invoke, Float::max);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.min)) {
          optimizeFloatFloatToFloatFunction(code, instructionIterator, invoke, Float::min);
        }
        break;
      case 'p':
        if (singleTargetReference.isIdenticalTo(floatMembers.parseFloat)) {
          optimizeStringToFloatFunction(
              code, instructionIterator, invoke, s -> Float.parseFloat(s.toString()));
        }
        break;
      case 's':
        if (singleTargetReference.isIdenticalTo(floatMembers.shortValue)) {
          optimizeBoxedFloatToIntFunction(code, instructionIterator, invoke, Float::shortValue);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.sum)) {
          optimizeFloatFloatToFloatFunction(code, instructionIterator, invoke, Float::sum);
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(floatMembers.toHexString)) {
          optimizeFloatToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              f -> factory.createString(Float.toHexString(f)));
        } else if (singleTargetReference.isIdenticalTo(floatMembers.toString)) {
          optimizeBoxedFloatToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              f -> factory.createString(f.toString()));
        } else if (singleTargetReference.isIdenticalTo(floatMembers.staticToString)) {
          optimizeFloatToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              f -> factory.createString(Float.toString(f)));
        }
        break;
      case 'v':
        if (singleTargetReference.isIdenticalTo(floatMembers.valueOf)) {
          optimizeBoxMethod(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.valueOfString)) {
          optimizeValueOfString(code, instructionIterator, invoke, affectedValues);
        }
        break;
      default:
        break;
    }
    return instructionIterator;
  }

  private void optimizeValueOfString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    DexString str = invoke.getFirstArgument().getConstStringOrNull(appView, code);
    if (str != null) {
      float result;
      try {
        result = Float.parseFloat(str.toString());
      } catch (NumberFormatException ignored) {
        return;
      }
      if (invoke.hasUnusedOutValue()) {
        instructionIterator.removeOrReplaceByDebugLocalRead();
      } else {
        Value constFloat =
            instructionIterator.insertConstNumberInstruction(
                code, appView.options(), LongUtils.encodeFloat(result), TypeElement.getFloat());
        InvokeStatic invokeStatic =
            InvokeStatic.builder()
                .setMethod(floatMembers.valueOf)
                .setSingleArgument(constFloat)
                .setOutValue(invoke.outValue())
                .setPosition(invoke)
                .build();
        instructionIterator.replaceCurrentInstruction(invokeStatic, affectedValues);
      }
    }
  }
}

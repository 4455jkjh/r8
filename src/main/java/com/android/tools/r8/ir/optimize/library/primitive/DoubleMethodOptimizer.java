// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory.DoubleMembers;
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

public class DoubleMethodOptimizer extends PrimitiveMethodOptimizer {

  private final DoubleMembers doubleMembers;

  DoubleMethodOptimizer(AppView<?> appView) {
    super(appView);
    this.doubleMembers = factory.doubleMembers;
  }

  @Override
  DexMethod getBoxMethod() {
    return doubleMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return doubleMembers.doubleValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedDouble();
  }

  @Override
  public DexType getType() {
    return factory.boxedDoubleType;
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
        if (singleTargetReference.isIdenticalTo(doubleMembers.byteValue)) {
          optimizeBoxedDoubleToIntFunction(code, instructionIterator, invoke, Double::byteValue);
        }
        break;
      case 'c':
        if (singleTargetReference.isIdenticalTo(doubleMembers.compare)) {
          optimizeDoubleDoubleToIntFunction(code, instructionIterator, invoke, Double::compare);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.compareTo)) {
          optimizeBoxedDoubleBoxedDoubleToIntFunction(
              code, instructionIterator, invoke, Double::compareTo);
        }
        break;
      case 'd':
        if (singleTargetReference.isIdenticalTo(doubleMembers.doubleToLongBits)) {
          optimizeDoubleToLongFunction(code, instructionIterator, invoke, Double::doubleToLongBits);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.doubleToRawLongBits)) {
          optimizeDoubleToLongFunction(
              code, instructionIterator, invoke, Double::doubleToRawLongBits);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.doubleValue)) {
          optimizeUnboxMethod(code, instructionIterator, invoke);
        }
        break;
      case 'e':
        if (singleTargetReference.isIdenticalTo(doubleMembers.equals)) {
          optimizeBoxedDoubleBoxedDoubleToBooleanFunction(
              code, instructionIterator, invoke, Double::equals);
        }
        break;
      case 'f':
        if (singleTargetReference.isIdenticalTo(doubleMembers.floatValue)) {
          optimizeBoxedDoubleToFloatFunction(code, instructionIterator, invoke, Double::floatValue);
        }
        break;
      case 'h':
        if (singleTargetReference.isIdenticalTo(doubleMembers.hashCode)) {
          optimizeBoxedDoubleToIntFunction(code, instructionIterator, invoke, Object::hashCode);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.staticHashCode)) {
          optimizeDoubleToIntFunction(code, instructionIterator, invoke, Double::hashCode);
        }
        break;
      case 'i':
        if (singleTargetReference.isIdenticalTo(doubleMembers.intValue)) {
          optimizeBoxedDoubleToIntFunction(code, instructionIterator, invoke, Double::intValue);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.isFinite)) {
          optimizeDoubleToBooleanFunction(code, instructionIterator, invoke, Double::isFinite);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.isInfinite)) {
          optimizeBoxedDoubleToBooleanFunction(
              code, instructionIterator, invoke, d -> d.isInfinite());
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.staticIsInfinite)) {
          optimizeDoubleToBooleanFunction(code, instructionIterator, invoke, Double::isInfinite);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.isNaN)) {
          optimizeBoxedDoubleToBooleanFunction(code, instructionIterator, invoke, d -> d.isNaN());
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.staticIsNaN)) {
          optimizeDoubleToBooleanFunction(code, instructionIterator, invoke, Double::isNaN);
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(doubleMembers.longBitsToDouble)) {
          optimizeLongToDoubleFunction(code, instructionIterator, invoke, Double::longBitsToDouble);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.longValue)) {
          optimizeBoxedDoubleToLongFunction(code, instructionIterator, invoke, Double::longValue);
        }
        break;
      case 'm':
        if (singleTargetReference.isIdenticalTo(doubleMembers.max)) {
          optimizeDoubleDoubleToDoubleFunction(code, instructionIterator, invoke, Double::max);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.min)) {
          optimizeDoubleDoubleToDoubleFunction(code, instructionIterator, invoke, Double::min);
        }
        break;
      case 'p':
        if (singleTargetReference.isIdenticalTo(doubleMembers.parseDouble)) {
          optimizeStringToDoubleFunction(
              code, instructionIterator, invoke, s -> Double.parseDouble(s.toString()));
        }
        break;
      case 's':
        if (singleTargetReference.isIdenticalTo(doubleMembers.shortValue)) {
          optimizeBoxedDoubleToIntFunction(code, instructionIterator, invoke, Double::shortValue);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.sum)) {
          optimizeDoubleDoubleToDoubleFunction(code, instructionIterator, invoke, Double::sum);
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(doubleMembers.toHexString)) {
          optimizeDoubleToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              d -> factory.createString(Double.toHexString(d)));
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.toString)) {
          optimizeBoxedDoubleToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              d -> factory.createString(d.toString()));
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.staticToString)) {
          optimizeDoubleToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              d -> factory.createString(Double.toString(d)));
        }
        break;
      case 'v':
        if (singleTargetReference.isIdenticalTo(doubleMembers.valueOf)) {
          optimizeBoxMethod(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(doubleMembers.valueOfString)) {
          optimizeValueOfString(code, instructionIterator, invoke, affectedValues);
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
      double result) {
    if (invoke.hasUnusedOutValue()) {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else {
      Value constDouble =
          instructionIterator.insertConstNumberInstruction(
              code, appView.options(), Double.doubleToRawLongBits(result), TypeElement.getDouble());
      InvokeStatic invokeStatic =
          InvokeStatic.builder()
              .setMethod(doubleMembers.valueOf)
              .setSingleArgument(constDouble)
              .setOutValue(invoke.outValue())
              .setPosition(invoke)
              .build();
      instructionIterator.replaceCurrentInstruction(invokeStatic, affectedValues);
    }
  }

  private void optimizeValueOfString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    DexString str = invoke.getFirstArgument().getConstStringOrNull(appView, code);
    if (str != null) {
      double result;
      try {
        result = Double.parseDouble(str.toString());
      } catch (NumberFormatException ignored) {
        return;
      }
      optimizeValueOfResult(code, instructionIterator, invoke, affectedValues, result);
    }
  }
}

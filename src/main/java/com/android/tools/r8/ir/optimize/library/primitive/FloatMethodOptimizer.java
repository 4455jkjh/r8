// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.internal.LongUtils;
import java.util.Set;

public class FloatMethodOptimizer extends PrimitiveMethodOptimizer {

  FloatMethodOptimizer(AppView<?> appView) {
    super(appView);
  }

  @Override
  DexMethod getBoxMethod() {
    return dexItemFactory.floatMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return dexItemFactory.floatMembers.floatValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedFloat();
  }

  @Override
  public DexType getType() {
    return dexItemFactory.boxedFloatType;
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
    var floatMembers = dexItemFactory.floatMembers;
    switch (singleTargetReference.getName().getFirstByteAsChar()) {
      case 'b':
        if (singleTargetReference.isIdenticalTo(floatMembers.byteValue)) {
          optimizeByteValue(code, instructionIterator, invoke);
        }
        break;
      case 'c':
        if (singleTargetReference.isIdenticalTo(floatMembers.compare)) {
          optimizeCompare(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.compareTo)) {
          optimizeCompareTo(code, instructionIterator, invoke);
        }
        break;
      case 'd':
        if (singleTargetReference.isIdenticalTo(floatMembers.doubleValue)) {
          optimizeDoubleValue(code, instructionIterator, invoke, affectedValues);
        }
        break;
      case 'f':
        if (singleTargetReference.isIdenticalTo(floatMembers.floatToIntBits)) {
          optimizeFloatToIntBits(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.floatToRawIntBits)) {
          optimizeFloatToRawIntBits(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.floatValue)) {
          optimizeUnboxMethod(code, instructionIterator, invoke);
        }
        break;
      case 'h':
        if (singleTargetReference.isIdenticalTo(floatMembers.hashCode)) {
          optimizeHashCode(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.staticHashCode)) {
          optimizeStaticHashCode(code, instructionIterator, invoke);
        }
        break;
      case 'i':
        if (singleTargetReference.isIdenticalTo(floatMembers.intBitsToFloat)) {
          optimizeIntBitsToFloat(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.intValue)) {
          optimizeIntValue(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.isFinite)) {
          optimizeIsFinite(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.isInfinite)) {
          optimizeIsInfinite(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.staticIsInfinite)) {
          optimizeStaticIsInfinite(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.isNaN)) {
          optimizeIsNaN(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.staticIsNaN)) {
          optimizeStaticIsNaN(code, instructionIterator, invoke);
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(floatMembers.longValue)) {
          optimizeLongValue(code, instructionIterator, invoke, affectedValues);
        }
        break;
      case 'm':
        if (singleTargetReference.isIdenticalTo(floatMembers.max)) {
          optimizeMax(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.min)) {
          optimizeMin(code, instructionIterator, invoke, affectedValues);
        }
        break;
      case 'p':
        if (singleTargetReference.isIdenticalTo(floatMembers.parseFloat)) {
          optimizeParseFloat(code, instructionIterator, invoke, affectedValues);
        }
        break;
      case 's':
        if (singleTargetReference.isIdenticalTo(floatMembers.shortValue)) {
          optimizeShortValue(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.sum)) {
          optimizeSum(code, instructionIterator, invoke, affectedValues);
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(floatMembers.toHexString)) {
          optimizeToHexString(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.toString)) {
          optimizeToString(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(floatMembers.staticToString)) {
          optimizeStaticToString(code, instructionIterator, invoke, affectedValues);
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

  private Float getConstFloatOrNull(Value value, IRCode code) {
    Value aliased = value.getAliasedValue();
    if (aliased.isConstFloat()) {
      return aliased.getConstFloat();
    }
    AbstractValue abstractValue = aliased.getAbstractValue(appView, code.context());
    if (abstractValue.isSingleNumberValue()) {
      return abstractValue.asSingleNumberValue().getFloatValue();
    }
    return null;
  }

  private Float getConstBoxedFloatOrNull(Value value, IRCode code) {
    Value aliased = value.getAliasedValue();
    if (aliased.isDefinedByInstructionSatisfying(Instruction::isInvokeStatic)) {
      InvokeStatic invoke = aliased.getDefinition().asInvokeStatic();
      if (invoke.getInvokedMethod().isIdenticalTo(dexItemFactory.floatMembers.valueOf)) {
        return getConstFloatOrNull(invoke.getFirstArgument(), code);
      }
      if (invoke.getInvokedMethod().isIdenticalTo(dexItemFactory.floatMembers.valueOfString)) {
        DexString str = getConstStringOrNull(invoke.getFirstArgument(), code);
        if (str != null) {
          try {
            return Float.parseFloat(str.toString());
          } catch (NumberFormatException ignored) {
            return null;
          }
        }
      }
    }
    AbstractValue abstractValue = aliased.getAbstractValue(appView, code.context());
    if (abstractValue.isSingleBoxedFloat()) {
      return abstractValue.asSingleBoxedFloat().getFloatValue();
    }
    return null;
  }

  private Integer getConstIntOrNull(Value value, IRCode code) {
    Value aliased = value.getAliasedValue();
    if (aliased.isConstNumber()) {
      return aliased.getConstInt();
    }
    AbstractValue abstractValue = aliased.getAbstractValue(appView, code.context());
    if (abstractValue.isSingleNumberValue()) {
      return abstractValue.asSingleNumberValue().getIntValue();
    }
    return null;
  }

  private DexString getConstStringOrNull(Value value, IRCode code) {
    Value aliased = value.getAliasedValue();
    if (aliased.isConstString()) {
      return aliased.getConstString();
    }
    AbstractValue abstractValue = aliased.getAbstractValue(appView, code.context());
    if (abstractValue.isSingleStringValue()) {
      return abstractValue.asSingleStringValue().getDexString();
    }
    return null;
  }

  private void replaceCurrentInstructionWithConstFloat(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      float result) {
    if (invoke.hasOutValue()) {
      ConstNumber constNumber = code.createFloatConstant(result, invoke.getLocalInfo());
      instructionIterator.replaceCurrentInstruction(constNumber, affectedValues);
    } else {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private void replaceCurrentInstructionWithConstDouble(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      double result) {
    if (invoke.hasOutValue()) {
      ConstNumber constNumber = code.createDoubleConstant(result, invoke.getLocalInfo());
      instructionIterator.replaceCurrentInstruction(constNumber, affectedValues);
    } else {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private void replaceCurrentInstructionWithConstLong(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      long result) {
    if (invoke.hasOutValue()) {
      ConstNumber constNumber = code.createLongConstant(result, invoke.getLocalInfo());
      instructionIterator.replaceCurrentInstruction(constNumber, affectedValues);
    } else {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private void replaceCurrentInstructionWithConstInt(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke, int result) {
    if (invoke.hasOutValue()) {
      ConstNumber constNumber = code.createIntConstant(result, invoke.getLocalInfo());
      instructionIterator.replaceCurrentInstruction(constNumber);
    } else {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private void replaceCurrentInstructionWithConstBoolean(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      boolean result) {
    if (invoke.hasOutValue()) {
      ConstNumber constNumber = code.createBooleanConstant(result, invoke.getLocalInfo());
      instructionIterator.replaceCurrentInstruction(constNumber);
    } else {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private void replaceCurrentInstructionWithConstString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      String result) {
    if (invoke.hasOutValue()) {
      DexString dexString = dexItemFactory.createString(result);
      instructionIterator.replaceCurrentInstructionWithConstString(
          appView, code, dexString, affectedValues);
    } else {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private void optimizeByteValue(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstBoxedFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstInt(code, instructionIterator, invoke, f.byteValue());
    }
  }

  private void optimizeCompare(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f1 = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = getConstFloatOrNull(invoke.getSecondArgument(), code);
    if (f2 == null) {
      return;
    }
    replaceCurrentInstructionWithConstInt(code, instructionIterator, invoke, Float.compare(f1, f2));
  }

  private void optimizeCompareTo(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f1 = getConstBoxedFloatOrNull(invoke.getFirstArgument(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = getConstBoxedFloatOrNull(invoke.getSecondArgument(), code);
    if (f2 == null) {
      return;
    }
    replaceCurrentInstructionWithConstInt(code, instructionIterator, invoke, f1.compareTo(f2));
  }

  private void optimizeDoubleValue(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    Float f = getConstBoxedFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstDouble(
          code, instructionIterator, invoke, affectedValues, f.doubleValue());
    }
  }

  private void optimizeFloatToIntBits(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstInt(
          code, instructionIterator, invoke, Float.floatToIntBits(f));
    }
  }

  private void optimizeFloatToRawIntBits(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstInt(
          code, instructionIterator, invoke, Float.floatToRawIntBits(f));
    }
  }

  private void optimizeHashCode(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstBoxedFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstInt(code, instructionIterator, invoke, f.hashCode());
    }
  }

  private void optimizeStaticHashCode(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstInt(code, instructionIterator, invoke, Float.hashCode(f));
    }
  }

  private void optimizeIntBitsToFloat(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    Integer bits = getConstIntOrNull(invoke.getFirstArgument(), code);
    if (bits != null) {
      replaceCurrentInstructionWithConstFloat(
          code, instructionIterator, invoke, affectedValues, Float.intBitsToFloat(bits));
    }
  }

  private void optimizeIntValue(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstBoxedFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstInt(code, instructionIterator, invoke, f.intValue());
    }
  }

  private void optimizeIsFinite(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstBoolean(
          code, instructionIterator, invoke, Float.isFinite(f));
    }
  }

  private void optimizeIsInfinite(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstBoxedFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstBoolean(code, instructionIterator, invoke, f.isInfinite());
    }
  }

  private void optimizeStaticIsInfinite(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstBoolean(
          code, instructionIterator, invoke, Float.isInfinite(f));
    }
  }

  private void optimizeIsNaN(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstBoxedFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstBoolean(code, instructionIterator, invoke, f.isNaN());
    }
  }

  private void optimizeStaticIsNaN(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstBoolean(code, instructionIterator, invoke, Float.isNaN(f));
    }
  }

  private void optimizeLongValue(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    Float f = getConstBoxedFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstLong(
          code, instructionIterator, invoke, affectedValues, f.longValue());
    }
  }

  private void optimizeMax(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    Float a = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (a == null) {
      return;
    }
    Float b = getConstFloatOrNull(invoke.getSecondArgument(), code);
    if (b == null) {
      return;
    }
    replaceCurrentInstructionWithConstFloat(
        code, instructionIterator, invoke, affectedValues, Float.max(a, b));
  }

  private void optimizeMin(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    Float a = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (a == null) {
      return;
    }
    Float b = getConstFloatOrNull(invoke.getSecondArgument(), code);
    if (b == null) {
      return;
    }
    replaceCurrentInstructionWithConstFloat(
        code, instructionIterator, invoke, affectedValues, Float.min(a, b));
  }

  private void optimizeParseFloat(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    DexString str = getConstStringOrNull(invoke.getFirstArgument(), code);
    if (str != null) {
      try {
        float result = Float.parseFloat(str.toString());
        replaceCurrentInstructionWithConstFloat(
            code, instructionIterator, invoke, affectedValues, result);
      } catch (NumberFormatException ignored) {
        // Leave as is if format is invalid.
      }
    }
  }

  private void optimizeShortValue(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Float f = getConstBoxedFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstInt(code, instructionIterator, invoke, f.shortValue());
    }
  }

  private void optimizeSum(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    Float a = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (a == null) {
      return;
    }
    Float b = getConstFloatOrNull(invoke.getSecondArgument(), code);
    if (b == null) {
      return;
    }
    replaceCurrentInstructionWithConstFloat(
        code, instructionIterator, invoke, affectedValues, Float.sum(a, b));
  }

  private void optimizeToHexString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    Float f = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstString(
          code, instructionIterator, invoke, affectedValues, Float.toHexString(f));
    }
  }

  private void optimizeToString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    Float f = getConstBoxedFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstString(
          code, instructionIterator, invoke, affectedValues, f.toString());
    }
  }

  private void optimizeStaticToString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    Float f = getConstFloatOrNull(invoke.getFirstArgument(), code);
    if (f != null) {
      replaceCurrentInstructionWithConstString(
          code, instructionIterator, invoke, affectedValues, Float.toString(f));
    }
  }

  private void optimizeValueOfString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    DexString str = getConstStringOrNull(invoke.getFirstArgument(), code);
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
                .setMethod(dexItemFactory.floatMembers.valueOf)
                .setSingleArgument(constFloat)
                .setOutValue(invoke.outValue())
                .setPosition(invoke)
                .build();
        instructionIterator.replaceCurrentInstruction(invokeStatic, affectedValues);
      }
    }
  }
}

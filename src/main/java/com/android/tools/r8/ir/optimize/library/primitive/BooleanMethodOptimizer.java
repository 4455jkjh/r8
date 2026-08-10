// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory.BooleanMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.Set;

public class BooleanMethodOptimizer extends PrimitiveMethodOptimizer {

  private final BooleanMembers booleanMembers;

  BooleanMethodOptimizer(AppView<?> appView) {
    super(appView);
    this.booleanMembers = factory.booleanMembers;
  }

  @Override
  DexMethod getBoxMethod() {
    return booleanMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return booleanMembers.booleanValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedBoolean();
  }

  @Override
  public DexType getType() {
    return factory.boxedBooleanType;
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
        if (singleTargetReference.isIdenticalTo(booleanMembers.booleanValue)) {
          optimizeUnboxMethod(code, instructionIterator, invoke);
        }
        break;
      case 'c':
        if (singleTargetReference.isIdenticalTo(booleanMembers.compare)) {
          optimizeBooleanBooleanToIntFunction(code, instructionIterator, invoke, Boolean::compare);
        } else if (singleTargetReference.isIdenticalTo(booleanMembers.compareTo)) {
          optimizeBoxedBooleanBoxedBooleanToIntFunction(
              code, instructionIterator, invoke, Boolean::compareTo);
        }
        break;
      case 'e':
        if (singleTargetReference.isIdenticalTo(booleanMembers.equals)) {
          optimizeBoxedBooleanBoxedBooleanToBooleanFunction(
              code, instructionIterator, invoke, Boolean::equals);
        }
        break;
      case 'h':
        if (singleTargetReference.isIdenticalTo(booleanMembers.hashCode)) {
          optimizeBoxedBooleanToIntFunction(code, instructionIterator, invoke, Object::hashCode);
        } else if (singleTargetReference.isIdenticalTo(booleanMembers.staticHashCode)) {
          optimizeBooleanToIntFunction(code, instructionIterator, invoke, Boolean::hashCode);
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(booleanMembers.logicalAnd)) {
          optimizeBooleanBooleanToBooleanFunction(
              code, instructionIterator, invoke, Boolean::logicalAnd);
        } else if (singleTargetReference.isIdenticalTo(booleanMembers.logicalOr)) {
          optimizeBooleanBooleanToBooleanFunction(
              code, instructionIterator, invoke, Boolean::logicalOr);
        } else if (singleTargetReference.isIdenticalTo(booleanMembers.logicalXor)) {
          optimizeBooleanBooleanToBooleanFunction(
              code, instructionIterator, invoke, Boolean::logicalXor);
        }
        break;
      case 'p':
        if (singleTargetReference.isIdenticalTo(booleanMembers.parseBoolean)) {
          optimizeStringToBooleanFunction(
              code, instructionIterator, invoke, s -> Boolean.parseBoolean(s.toString()));
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(booleanMembers.toString)) {
          optimizeBoxedBooleanToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              b -> factory.createString(b.toString()));
        } else if (singleTargetReference.isIdenticalTo(booleanMembers.staticToString)) {
          optimizeBooleanToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              b -> factory.createString(Boolean.toString(b)));
        }
        break;
      case 'v':
        if (singleTargetReference.isIdenticalTo(booleanMembers.valueOf)) {
          optimizeBoxMethod(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(booleanMembers.valueOfString)) {
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
    DexString s = invoke.getFirstArgument().getConstStringOrNull(appView, code);
    if (s != null) {
      boolean result = Boolean.parseBoolean(s.toString());
      if (invoke.hasUnusedOutValue()) {
        instructionIterator.removeOrReplaceByDebugLocalRead();
      } else {
        instructionIterator.replaceCurrentInstructionWithStaticGet(
            appView, code, result ? booleanMembers.TRUE : booleanMembers.FALSE, affectedValues);
      }
    }
  }

  @Override
  void optimizeBoxMethod(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod boxInvoke,
      AffectedValues affectedValues) {
    // Optimize Boolean.valueOf(b) into Boolean.FALSE or Boolean.TRUE.
    Boolean b = boxInvoke.getFirstArgument().getConstBooleanOrNull(appView, code);
    if (b != null) {
      instructionIterator.replaceCurrentInstructionWithStaticGet(
          appView, code, b ? booleanMembers.TRUE : booleanMembers.FALSE, affectedValues);
      return;
    }
    super.optimizeBoxMethod(code, instructionIterator, boxInvoke, affectedValues);
  }
}

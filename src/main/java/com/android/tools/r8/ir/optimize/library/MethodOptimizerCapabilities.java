// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public interface MethodOptimizerCapabilities {

  AppView<?> getAppView();

  default void optimizeStringToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      Predicate<DexString> fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    if (firstArg != null) {
      boolean replacement = fn.test(firstArg);
      instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
    }
  }

  interface StringToIntFunction {

    int apply(DexString s);
  }

  default void optimizeStringToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      StringToIntFunction fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    if (firstArg != null) {
      int replacement = fn.apply(firstArg);
      instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
    }
  }

  default void optimizeStringToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      UnaryOperator<DexString> fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    if (firstArg != null) {
      DexString replacement = fn.apply(firstArg);
      replaceCurrentInstructionWithConstString(
          code, instructionIterator, invoke, affectedValues, replacement);
    }
  }

  interface StringIntToIntFunction {

    int apply(DexString s, int i);
  }

  default void optimizeStringIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      StringIntToIntFunction fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    if (firstArg != null && invoke.getSecondArgument().isConstInt()) {
      int replacement = fn.apply(firstArg, invoke.getSecondArgument().getConstInt());
      instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
    }
  }

  interface StringIntToStringFunction {

    DexString apply(DexString s, int i);
  }

  default void optimizeStringIntToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      StringIntToStringFunction fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    if (firstArg != null && invoke.getSecondArgument().isConstInt()) {
      DexString replacement = fn.apply(firstArg, invoke.getSecondArgument().getConstInt());
      replaceCurrentInstructionWithConstString(
          code, instructionIterator, invoke, affectedValues, replacement);
    }
  }

  interface StringIntIntToIntFunction {

    int apply(DexString s, int i, int j);
  }

  default void optimizeStringIntIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      StringIntIntToIntFunction fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    if (firstArg != null
        && invoke.getSecondArgument().isConstInt()
        && invoke.getThirdArgument().isConstInt()) {
      int replacement =
          fn.apply(
              firstArg,
              invoke.getSecondArgument().getConstInt(),
              invoke.getThirdArgument().getConstInt());
      instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
    }
  }

  interface StringIntIntToStringFunction {

    DexString apply(DexString s, int i, int j);
  }

  default void optimizeStringIntIntToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      StringIntIntToStringFunction fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    if (firstArg != null
        && invoke.getSecondArgument().isConstInt()
        && invoke.getThirdArgument().isConstInt()) {
      DexString replacement =
          fn.apply(
              firstArg,
              invoke.getSecondArgument().getConstInt(),
              invoke.getThirdArgument().getConstInt());
      replaceCurrentInstructionWithConstString(
          code, instructionIterator, invoke, affectedValues, replacement);
    }
  }

  default void optimizeStringStringToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BiPredicate<DexString, DexString> fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    DexString secondArg = invoke.getSecondArgument().getConstStringOrNull();
    if (firstArg != null && secondArg != null) {
      boolean replacement = fn.test(firstArg, secondArg);
      instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
    }
  }

  interface StringStringToIntFunction {

    int apply(DexString s, DexString t);
  }

  default void optimizeStringStringToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      StringStringToIntFunction fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    DexString secondArg = invoke.getSecondArgument().getConstStringOrNull();
    if (firstArg != null && secondArg != null) {
      int replacement = fn.apply(firstArg, secondArg);
      instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
    }
  }

  interface StringStringToStringFunction {

    DexString apply(DexString s1, DexString s2);
  }

  default void optimizeStringStringToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      StringStringToStringFunction fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    DexString secondArg = invoke.getSecondArgument().getConstStringOrNull();
    if (firstArg != null && secondArg != null) {
      DexString replacement = fn.apply(firstArg, secondArg);
      replaceCurrentInstructionWithConstString(
          code, instructionIterator, invoke, affectedValues, replacement);
    }
  }

  interface StringStringIntToIntFunction {

    int apply(DexString s, DexString t, int i);
  }

  default void optimizeStringStringIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      StringStringIntToIntFunction fn) {
    DexString firstArg = invoke.getFirstArgument().getConstStringOrNull();
    DexString secondArg = invoke.getSecondArgument().getConstStringOrNull();
    if (firstArg != null && secondArg != null && invoke.getThirdArgument().isConstInt()) {
      int replacement = fn.apply(firstArg, secondArg, invoke.getThirdArgument().getConstInt());
      instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
    }
  }

  private void replaceCurrentInstructionWithConstString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      DexString replacement) {
    assert invoke.getFirstArgument().isConstString();
    if (replacement == null) {
      return;
    }
    if (replacement.isIdenticalTo(invoke.getFirstArgument().getConstStringOrNull())) {
      if (invoke.hasOutValue()) {
        invoke.outValue().replaceUsers(invoke.getFirstArgument(), affectedValues);
        invoke
            .getFirstArgument()
            .uniquePhiUsers()
            .forEach(phi -> phi.removeTrivialPhi(null, affectedValues));
      }
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else {
      instructionIterator.replaceCurrentInstructionWithConstString(
          getAppView(), code, replacement, affectedValues);
    }
  }
}

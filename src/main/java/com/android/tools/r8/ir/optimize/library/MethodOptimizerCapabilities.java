// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.internal.BooleanBooleanToBooleanFunction;
import com.android.tools.r8.utils.internal.BooleanBooleanToIntFunction;
import com.android.tools.r8.utils.internal.BooleanToIntFunction;
import com.android.tools.r8.utils.internal.BooleanToObjFunction;
import com.android.tools.r8.utils.internal.ByteByteToIntFunction;
import com.android.tools.r8.utils.internal.ByteToIntFunction;
import com.android.tools.r8.utils.internal.ByteToLongFunction;
import com.android.tools.r8.utils.internal.ByteToObjFunction;
import com.android.tools.r8.utils.internal.CharCharToBooleanFunction;
import com.android.tools.r8.utils.internal.CharCharToIntFunction;
import com.android.tools.r8.utils.internal.CharToBooleanFunction;
import com.android.tools.r8.utils.internal.CharToIntFunction;
import com.android.tools.r8.utils.internal.CharToObjFunction;
import com.android.tools.r8.utils.internal.DoubleDoubleToBooleanFunction;
import com.android.tools.r8.utils.internal.DoubleDoubleToDoubleFunction;
import com.android.tools.r8.utils.internal.DoubleDoubleToIntFunction;
import com.android.tools.r8.utils.internal.DoubleIntToDoubleFunction;
import com.android.tools.r8.utils.internal.DoubleToBooleanFunction;
import com.android.tools.r8.utils.internal.DoubleToDoubleFunction;
import com.android.tools.r8.utils.internal.DoubleToIntFunction;
import com.android.tools.r8.utils.internal.DoubleToLongFunction;
import com.android.tools.r8.utils.internal.DoubleToObjFunction;
import com.android.tools.r8.utils.internal.FloatDoubleToFloatFunction;
import com.android.tools.r8.utils.internal.FloatFloatFloatFloatToIntFunction;
import com.android.tools.r8.utils.internal.FloatFloatFloatFloatToLongFunction;
import com.android.tools.r8.utils.internal.FloatFloatFloatToIntFunction;
import com.android.tools.r8.utils.internal.FloatFloatFloatToLongFunction;
import com.android.tools.r8.utils.internal.FloatFloatToBooleanFunction;
import com.android.tools.r8.utils.internal.FloatFloatToFloatFunction;
import com.android.tools.r8.utils.internal.FloatFloatToIntFunction;
import com.android.tools.r8.utils.internal.FloatIntToFloatFunction;
import com.android.tools.r8.utils.internal.FloatToBooleanFunction;
import com.android.tools.r8.utils.internal.FloatToFloatFunction;
import com.android.tools.r8.utils.internal.FloatToIntFunction;
import com.android.tools.r8.utils.internal.FloatToObjFunction;
import com.android.tools.r8.utils.internal.IntIntIntIntToIntFunction;
import com.android.tools.r8.utils.internal.IntIntIntToIntFunction;
import com.android.tools.r8.utils.internal.IntIntToBooleanFunction;
import com.android.tools.r8.utils.internal.IntIntToIntFunction;
import com.android.tools.r8.utils.internal.IntIntToObjFunction;
import com.android.tools.r8.utils.internal.IntToBooleanFunction;
import com.android.tools.r8.utils.internal.IntToFloatFunction;
import com.android.tools.r8.utils.internal.IntToIntFunction;
import com.android.tools.r8.utils.internal.IntToLongFunction;
import com.android.tools.r8.utils.internal.IntToObjFunction;
import com.android.tools.r8.utils.internal.LongIntToLongFunction;
import com.android.tools.r8.utils.internal.LongIntToObjFunction;
import com.android.tools.r8.utils.internal.LongLongToIntFunction;
import com.android.tools.r8.utils.internal.LongLongToLongFunction;
import com.android.tools.r8.utils.internal.LongToBooleanFunction;
import com.android.tools.r8.utils.internal.LongToDoubleFunction;
import com.android.tools.r8.utils.internal.LongToFloatFunction;
import com.android.tools.r8.utils.internal.LongToIntFunction;
import com.android.tools.r8.utils.internal.LongToLongFunction;
import com.android.tools.r8.utils.internal.LongToObjFunction;
import com.android.tools.r8.utils.internal.ObjIntIntToIntFunction;
import com.android.tools.r8.utils.internal.ObjIntIntToObjFunction;
import com.android.tools.r8.utils.internal.ObjIntToIntFunction;
import com.android.tools.r8.utils.internal.ObjIntToLongFunction;
import com.android.tools.r8.utils.internal.ObjIntToObjFunction;
import com.android.tools.r8.utils.internal.ObjObjIntToIntFunction;
import com.android.tools.r8.utils.internal.ObjObjToIntFunction;
import com.android.tools.r8.utils.internal.ObjToBooleanFunction;
import com.android.tools.r8.utils.internal.ObjToDoubleFunction;
import com.android.tools.r8.utils.internal.ObjToFloatFunction;
import com.android.tools.r8.utils.internal.ObjToIntFunction;
import com.android.tools.r8.utils.internal.ObjToLongFunction;
import com.android.tools.r8.utils.internal.ShortShortToIntFunction;
import com.android.tools.r8.utils.internal.ShortToIntFunction;
import com.android.tools.r8.utils.internal.ShortToLongFunction;
import com.android.tools.r8.utils.internal.ShortToObjFunction;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;

public interface MethodOptimizerCapabilities {

  AppView<?> getAppView();

  // Boolean & BoxedBoolean

  default void optimizeBooleanBooleanToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BooleanBooleanToBooleanFunction fn) {
    Boolean b1 = invoke.getFirstArgument().getConstBooleanOrNull(getAppView(), code);
    if (b1 == null) {
      return;
    }
    Boolean b2 = invoke.getSecondArgument().getConstBooleanOrNull(getAppView(), code);
    if (b2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(b1, b2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeBooleanToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BooleanToIntFunction fn) {
    Boolean b = invoke.getFirstArgument().getConstBooleanOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBooleanBooleanToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BooleanBooleanToIntFunction fn) {
    Boolean b1 = invoke.getFirstArgument().getConstBooleanOrNull(getAppView(), code);
    if (b1 == null) {
      return;
    }
    Boolean b2 = invoke.getSecondArgument().getConstBooleanOrNull(getAppView(), code);
    if (b2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(b1, b2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBooleanToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      BooleanToObjFunction<DexString> fn) {
    Boolean b = invoke.getFirstArgument().getConstBooleanOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeBoxedBooleanBoxedBooleanToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjObjToIntFunction<Boolean, Boolean> fn) {
    Boolean b1 = invoke.getFirstArgument().getConstBoxedBooleanOrNull(getAppView(), code);
    if (b1 == null) {
      return;
    }
    Boolean b2 = invoke.getSecondArgument().getConstBoxedBooleanOrNull(getAppView(), code);
    if (b2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(b1, b2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedBooleanBoxedBooleanToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BiPredicate<Boolean, Boolean> fn) {
    Boolean b1 = invoke.getFirstArgument().getConstBoxedBooleanOrNull(getAppView(), code);
    if (b1 == null) {
      return;
    }
    Boolean b2 = invoke.getSecondArgument().getConstBoxedBooleanOrNull(getAppView(), code);
    if (b2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.test(b1, b2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeBoxedBooleanToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToIntFunction<Boolean> fn) {
    Boolean b = invoke.getFirstArgument().getConstBoxedBooleanOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedBooleanToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      Function<Boolean, DexString> fn) {
    Boolean b = invoke.getFirstArgument().getConstBoxedBooleanOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeByteToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ByteToIntFunction fn) {
    Byte b = invoke.getFirstArgument().getConstByteOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeByteToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ByteToLongFunction fn) {
    Byte b = invoke.getFirstArgument().getConstByteOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeByteToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      ByteToObjFunction<DexString> fn) {
    Byte b = invoke.getFirstArgument().getConstByteOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeByteByteToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ByteByteToIntFunction fn) {
    Byte b1 = invoke.getFirstArgument().getConstByteOrNull(getAppView(), code);
    if (b1 == null) {
      return;
    }
    Byte b2 = invoke.getSecondArgument().getConstByteOrNull(getAppView(), code);
    if (b2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(b1, b2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedByteToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToIntFunction<Byte> fn) {
    Byte b = invoke.getFirstArgument().getConstBoxedByteOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedByteToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToLongFunction<Byte> fn) {
    Byte b = invoke.getFirstArgument().getConstBoxedByteOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeBoxedByteToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToFloatFunction<Byte> fn) {
    Byte b = invoke.getFirstArgument().getConstBoxedByteOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeBoxedByteToDoubleFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToDoubleFunction<Byte> fn) {
    Byte b = invoke.getFirstArgument().getConstBoxedByteOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    double replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstDouble(code, replacement);
  }

  default void optimizeBoxedByteToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      Function<Byte, DexString> fn) {
    Byte b = invoke.getFirstArgument().getConstBoxedByteOrNull(getAppView(), code);
    if (b == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(b);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeBoxedByteBoxedByteToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjObjToIntFunction<Byte, Byte> fn) {
    Byte b1 = invoke.getFirstArgument().getConstBoxedByteOrNull(getAppView(), code);
    if (b1 == null) {
      return;
    }
    Byte b2 = invoke.getSecondArgument().getConstBoxedByteOrNull(getAppView(), code);
    if (b2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(b1, b2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedByteBoxedByteToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BiPredicate<Byte, Byte> fn) {
    Byte b1 = invoke.getFirstArgument().getConstBoxedByteOrNull(getAppView(), code);
    if (b1 == null) {
      return;
    }
    Byte b2 = invoke.getSecondArgument().getConstBoxedByteOrNull(getAppView(), code);
    if (b2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.test(b1, b2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeCharToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      CharToBooleanFunction fn) {
    Character c = invoke.getFirstArgument().getConstCharOrNull(getAppView(), code);
    if (c == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(c);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeCharToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      CharToIntFunction fn) {
    Character c = invoke.getFirstArgument().getConstCharOrNull(getAppView(), code);
    if (c == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(c);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeCharToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      CharToObjFunction<DexString> fn) {
    Character c = invoke.getFirstArgument().getConstCharOrNull(getAppView(), code);
    if (c == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(c);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeCharCharToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      CharCharToBooleanFunction fn) {
    Character c1 = invoke.getFirstArgument().getConstCharOrNull(getAppView(), code);
    if (c1 == null) {
      return;
    }
    Character c2 = invoke.getSecondArgument().getConstCharOrNull(getAppView(), code);
    if (c2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(c1, c2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeCharCharToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      CharCharToIntFunction fn) {
    Character c1 = invoke.getFirstArgument().getConstCharOrNull(getAppView(), code);
    if (c1 == null) {
      return;
    }
    Character c2 = invoke.getSecondArgument().getConstCharOrNull(getAppView(), code);
    if (c2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(c1, c2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedCharToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToIntFunction<Character> fn) {
    Character c = invoke.getFirstArgument().getConstBoxedCharOrNull(getAppView(), code);
    if (c == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(c);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedCharToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      Function<Character, DexString> fn) {
    Character c = invoke.getFirstArgument().getConstBoxedCharOrNull(getAppView(), code);
    if (c == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(c);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeBoxedCharBoxedCharToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjObjToIntFunction<Character, Character> fn) {
    Character c1 = invoke.getFirstArgument().getConstBoxedCharOrNull(getAppView(), code);
    if (c1 == null) {
      return;
    }
    Character c2 = invoke.getSecondArgument().getConstBoxedCharOrNull(getAppView(), code);
    if (c2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(c1, c2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedCharBoxedCharToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BiPredicate<Character, Character> fn) {
    Character c1 = invoke.getFirstArgument().getConstBoxedCharOrNull(getAppView(), code);
    if (c1 == null) {
      return;
    }
    Character c2 = invoke.getSecondArgument().getConstBoxedCharOrNull(getAppView(), code);
    if (c2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.test(c1, c2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeIntToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      IntToBooleanFunction fn) {
    Integer i = invoke.getFirstArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      IntToIntFunction fn) {
    Integer i = invoke.getFirstArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeIntToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      IntToLongFunction fn) {
    Integer i = invoke.getFirstArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeIntToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      IntToFloatFunction fn) {
    Integer i = invoke.getFirstArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeIntToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      IntToObjFunction<DexString> fn) {
    Integer i = invoke.getFirstArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(i);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeIntIntToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      IntIntToBooleanFunction fn) {
    Integer i1 = invoke.getFirstArgument().getConstIntOrNull(getAppView(), code);
    if (i1 == null) {
      return;
    }
    Integer i2 = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(i1, i2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeIntIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      IntIntToIntFunction fn) {
    Integer i1 = invoke.getFirstArgument().getConstIntOrNull(getAppView(), code);
    if (i1 == null) {
      return;
    }
    Integer i2 = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(i1, i2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeIntIntIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      IntIntIntToIntFunction fn) {
    Integer i1 = invoke.getArgument(0).getConstIntOrNull(getAppView(), code);
    if (i1 == null) {
      return;
    }
    Integer i2 = invoke.getArgument(1).getConstIntOrNull(getAppView(), code);
    if (i2 == null) {
      return;
    }
    Integer i3 = invoke.getArgument(2).getConstIntOrNull(getAppView(), code);
    if (i3 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(i1, i2, i3);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeIntIntIntIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      IntIntIntIntToIntFunction fn) {
    Integer i1 = invoke.getArgument(0).getConstIntOrNull(getAppView(), code);
    if (i1 == null) {
      return;
    }
    Integer i2 = invoke.getArgument(1).getConstIntOrNull(getAppView(), code);
    if (i2 == null) {
      return;
    }
    Integer i3 = invoke.getArgument(2).getConstIntOrNull(getAppView(), code);
    if (i3 == null) {
      return;
    }
    Integer i4 = invoke.getArgument(3).getConstIntOrNull(getAppView(), code);
    if (i4 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(i1, i2, i3, i4);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeIntIntToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      IntIntToObjFunction<DexString> fn) {
    Integer i1 = invoke.getFirstArgument().getConstIntOrNull(getAppView(), code);
    if (i1 == null) {
      return;
    }
    Integer i2 = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i2 == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(i1, i2);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeBoxedIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToIntFunction<Integer> fn) {
    Integer i = invoke.getFirstArgument().getConstBoxedIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedIntToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToLongFunction<Integer> fn) {
    Integer i = invoke.getFirstArgument().getConstBoxedIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeBoxedIntToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToFloatFunction<Integer> fn) {
    Integer i = invoke.getFirstArgument().getConstBoxedIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeBoxedIntToDoubleFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToDoubleFunction<Integer> fn) {
    Integer i = invoke.getFirstArgument().getConstBoxedIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    double replacement;
    try {
      replacement = fn.apply(i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstDouble(code, replacement);
  }

  default void optimizeBoxedIntToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      Function<Integer, DexString> fn) {
    Integer i = invoke.getFirstArgument().getConstBoxedIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(i);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeBoxedIntBoxedIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjObjToIntFunction<Integer, Integer> fn) {
    Integer i1 = invoke.getFirstArgument().getConstBoxedIntOrNull(getAppView(), code);
    if (i1 == null) {
      return;
    }
    Integer i2 = invoke.getSecondArgument().getConstBoxedIntOrNull(getAppView(), code);
    if (i2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(i1, i2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedIntBoxedIntToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BiPredicate<Integer, Integer> fn) {
    Integer i1 = invoke.getFirstArgument().getConstBoxedIntOrNull(getAppView(), code);
    if (i1 == null) {
      return;
    }
    Integer i2 = invoke.getSecondArgument().getConstBoxedIntOrNull(getAppView(), code);
    if (i2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.test(i1, i2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeLongToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      LongToIntFunction fn) {
    Long l = invoke.getFirstArgument().getConstLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(l);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeLongToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      LongToLongFunction fn) {
    Long l = invoke.getFirstArgument().getConstLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(l);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeLongToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      LongToBooleanFunction fn) {
    Long l = invoke.getFirstArgument().getConstLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(l);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeLongToDoubleFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      LongToDoubleFunction fn) {
    Long l = invoke.getFirstArgument().getConstLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    double replacement;
    try {
      replacement = fn.apply(l);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstDouble(code, replacement);
  }

  default void optimizeLongToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      LongToFloatFunction fn) {
    Long l = invoke.getFirstArgument().getConstLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(l);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeLongToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      LongToObjFunction<DexString> fn) {
    Long l = invoke.getFirstArgument().getConstLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(l);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeLongLongToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      LongLongToIntFunction fn) {
    Long l1 = invoke.getFirstArgument().getConstLongOrNull(getAppView(), code);
    if (l1 == null) {
      return;
    }
    Long l2 = invoke.getSecondArgument().getConstLongOrNull(getAppView(), code);
    if (l2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(l1, l2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeLongLongToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      LongLongToLongFunction fn) {
    Long l1 = invoke.getFirstArgument().getConstLongOrNull(getAppView(), code);
    if (l1 == null) {
      return;
    }
    Long l2 = invoke.getSecondArgument().getConstLongOrNull(getAppView(), code);
    if (l2 == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(l1, l2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeLongIntToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      LongIntToLongFunction fn) {
    Long l = invoke.getFirstArgument().getConstLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    Integer i = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(l, i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeLongIntToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      LongIntToObjFunction<DexString> fn) {
    Long l = invoke.getFirstArgument().getConstLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    Integer i = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(l, i);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeBoxedLongToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToIntFunction<Long> fn) {
    Long l = invoke.getFirstArgument().getConstBoxedLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(l);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedLongToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToFloatFunction<Long> fn) {
    Long l = invoke.getFirstArgument().getConstBoxedLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(l);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeBoxedLongToDoubleFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToDoubleFunction<Long> fn) {
    Long l = invoke.getFirstArgument().getConstBoxedLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    double replacement;
    try {
      replacement = fn.apply(l);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstDouble(code, replacement);
  }

  default void optimizeBoxedLongToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      Function<Long, DexString> fn) {
    Long l = invoke.getFirstArgument().getConstBoxedLongOrNull(getAppView(), code);
    if (l == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(l);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeBoxedLongBoxedLongToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjObjToIntFunction<Long, Long> fn) {
    Long l1 = invoke.getFirstArgument().getConstBoxedLongOrNull(getAppView(), code);
    if (l1 == null) {
      return;
    }
    Long l2 = invoke.getSecondArgument().getConstBoxedLongOrNull(getAppView(), code);
    if (l2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(l1, l2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedLongBoxedLongToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BiPredicate<Long, Long> fn) {
    Long l1 = invoke.getFirstArgument().getConstBoxedLongOrNull(getAppView(), code);
    if (l1 == null) {
      return;
    }
    Long l2 = invoke.getSecondArgument().getConstBoxedLongOrNull(getAppView(), code);
    if (l2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.test(l1, l2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeFloatToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatToBooleanFunction fn) {
    Float f = invoke.getFirstArgument().getConstFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(f);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeFloatToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatToIntFunction fn) {
    Float f = invoke.getFirstArgument().getConstFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(f);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeFloatToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      FloatToObjFunction<DexString> fn) {
    Float f = invoke.getFirstArgument().getConstFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(f);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeFloatFloatToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatFloatToBooleanFunction fn) {
    Float f1 = invoke.getFirstArgument().getConstFloatOrNull(getAppView(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = invoke.getSecondArgument().getConstFloatOrNull(getAppView(), code);
    if (f2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(f1, f2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeFloatFloatToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatFloatToIntFunction fn) {
    Float f1 = invoke.getFirstArgument().getConstFloatOrNull(getAppView(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = invoke.getSecondArgument().getConstFloatOrNull(getAppView(), code);
    if (f2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(f1, f2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeFloatFloatFloatToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatFloatFloatToIntFunction fn) {
    Float f1 = invoke.getArgument(0).getConstFloatOrNull(getAppView(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = invoke.getArgument(1).getConstFloatOrNull(getAppView(), code);
    if (f2 == null) {
      return;
    }
    Float f3 = invoke.getArgument(2).getConstFloatOrNull(getAppView(), code);
    if (f3 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(f1, f2, f3);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeFloatFloatFloatFloatToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatFloatFloatFloatToIntFunction fn) {
    Float f1 = invoke.getArgument(0).getConstFloatOrNull(getAppView(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = invoke.getArgument(1).getConstFloatOrNull(getAppView(), code);
    if (f2 == null) {
      return;
    }
    Float f3 = invoke.getArgument(2).getConstFloatOrNull(getAppView(), code);
    if (f3 == null) {
      return;
    }
    Float f4 = invoke.getArgument(3).getConstFloatOrNull(getAppView(), code);
    if (f4 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(f1, f2, f3, f4);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeFloatFloatFloatFloatToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatFloatFloatFloatToLongFunction fn) {
    Float f1 = invoke.getArgument(0).getConstFloatOrNull(getAppView(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = invoke.getArgument(1).getConstFloatOrNull(getAppView(), code);
    if (f2 == null) {
      return;
    }
    Float f3 = invoke.getArgument(2).getConstFloatOrNull(getAppView(), code);
    if (f3 == null) {
      return;
    }
    Float f4 = invoke.getArgument(3).getConstFloatOrNull(getAppView(), code);
    if (f4 == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(f1, f2, f3, f4);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeFloatFloatFloatToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatFloatFloatToLongFunction fn) {
    Float f1 = invoke.getArgument(0).getConstFloatOrNull(getAppView(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = invoke.getArgument(1).getConstFloatOrNull(getAppView(), code);
    if (f2 == null) {
      return;
    }
    Float f3 = invoke.getArgument(2).getConstFloatOrNull(getAppView(), code);
    if (f3 == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(f1, f2, f3);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeFloatFloatToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatFloatToFloatFunction fn) {
    Float f1 = invoke.getFirstArgument().getConstFloatOrNull(getAppView(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = invoke.getSecondArgument().getConstFloatOrNull(getAppView(), code);
    if (f2 == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(f1, f2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeBoxedFloatToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToBooleanFunction<Float> fn) {
    Float f = invoke.getFirstArgument().getConstBoxedFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(f);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeBoxedFloatToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToIntFunction<Float> fn) {
    Float f = invoke.getFirstArgument().getConstBoxedFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(f);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedFloatToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToLongFunction<Float> fn) {
    Float f = invoke.getFirstArgument().getConstBoxedFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(f);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeBoxedFloatToDoubleFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToDoubleFunction<Float> fn) {
    Float f = invoke.getFirstArgument().getConstBoxedFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    double replacement;
    try {
      replacement = fn.apply(f);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstDouble(code, replacement);
  }

  default void optimizeBoxedFloatToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      Function<Float, DexString> fn) {
    Float f = invoke.getFirstArgument().getConstBoxedFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(f);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeBoxedFloatBoxedFloatToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjObjToIntFunction<Float, Float> fn) {
    Float f1 = invoke.getFirstArgument().getConstBoxedFloatOrNull(getAppView(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = invoke.getSecondArgument().getConstBoxedFloatOrNull(getAppView(), code);
    if (f2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(f1, f2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedFloatBoxedFloatToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BiPredicate<Float, Float> fn) {
    Float f1 = invoke.getFirstArgument().getConstBoxedFloatOrNull(getAppView(), code);
    if (f1 == null) {
      return;
    }
    Float f2 = invoke.getSecondArgument().getConstBoxedFloatOrNull(getAppView(), code);
    if (f2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.test(f1, f2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeDoubleToDoubleFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DoubleToDoubleFunction fn) {
    Double d = invoke.getFirstArgument().getConstDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    double replacement;
    try {
      replacement = fn.apply(d);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstDouble(code, replacement);
  }

  default void optimizeFloatToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatToFloatFunction fn) {
    Float f = invoke.getFirstArgument().getConstFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(f);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeDoubleIntToDoubleFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DoubleIntToDoubleFunction fn) {
    Double d = invoke.getFirstArgument().getConstDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    Integer i = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    double replacement;
    try {
      replacement = fn.apply(d, i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstDouble(code, replacement);
  }

  default void optimizeFloatIntToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatIntToFloatFunction fn) {
    Float f = invoke.getFirstArgument().getConstFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    Integer i = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(f, i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeFloatDoubleToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      FloatDoubleToFloatFunction fn) {
    Float f = invoke.getFirstArgument().getConstFloatOrNull(getAppView(), code);
    if (f == null) {
      return;
    }
    Double d = invoke.getSecondArgument().getConstDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(f, d);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeDoubleToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DoubleToBooleanFunction fn) {
    Double d = invoke.getFirstArgument().getConstDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(d);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeDoubleToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DoubleToIntFunction fn) {
    Double d = invoke.getFirstArgument().getConstDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(d);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeDoubleToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DoubleToLongFunction fn) {
    Double d = invoke.getFirstArgument().getConstDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(d);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeDoubleToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      DoubleToObjFunction<DexString> fn) {
    Double d = invoke.getFirstArgument().getConstDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(d);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeDoubleDoubleToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DoubleDoubleToBooleanFunction fn) {
    Double d1 = invoke.getFirstArgument().getConstDoubleOrNull(getAppView(), code);
    if (d1 == null) {
      return;
    }
    Double d2 = invoke.getSecondArgument().getConstDoubleOrNull(getAppView(), code);
    if (d2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(d1, d2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeDoubleDoubleToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DoubleDoubleToIntFunction fn) {
    Double d1 = invoke.getFirstArgument().getConstDoubleOrNull(getAppView(), code);
    if (d1 == null) {
      return;
    }
    Double d2 = invoke.getSecondArgument().getConstDoubleOrNull(getAppView(), code);
    if (d2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(d1, d2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeDoubleDoubleToDoubleFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DoubleDoubleToDoubleFunction fn) {
    Double d1 = invoke.getFirstArgument().getConstDoubleOrNull(getAppView(), code);
    if (d1 == null) {
      return;
    }
    Double d2 = invoke.getSecondArgument().getConstDoubleOrNull(getAppView(), code);
    if (d2 == null) {
      return;
    }
    double replacement;
    try {
      replacement = fn.apply(d1, d2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstDouble(code, replacement);
  }

  default void optimizeBoxedDoubleToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToBooleanFunction<Double> fn) {
    Double d = invoke.getFirstArgument().getConstBoxedDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.apply(d);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeBoxedDoubleToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToIntFunction<Double> fn) {
    Double d = invoke.getFirstArgument().getConstBoxedDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(d);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedDoubleToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToLongFunction<Double> fn) {
    Double d = invoke.getFirstArgument().getConstBoxedDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(d);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeBoxedDoubleToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToFloatFunction<Double> fn) {
    Double d = invoke.getFirstArgument().getConstBoxedDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(d);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeBoxedDoubleToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      Function<Double, DexString> fn) {
    Double d = invoke.getFirstArgument().getConstBoxedDoubleOrNull(getAppView(), code);
    if (d == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(d);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeBoxedDoubleBoxedDoubleToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjObjToIntFunction<Double, Double> fn) {
    Double d1 = invoke.getFirstArgument().getConstBoxedDoubleOrNull(getAppView(), code);
    if (d1 == null) {
      return;
    }
    Double d2 = invoke.getSecondArgument().getConstBoxedDoubleOrNull(getAppView(), code);
    if (d2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(d1, d2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedDoubleBoxedDoubleToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BiPredicate<Double, Double> fn) {
    Double d1 = invoke.getFirstArgument().getConstBoxedDoubleOrNull(getAppView(), code);
    if (d1 == null) {
      return;
    }
    Double d2 = invoke.getSecondArgument().getConstBoxedDoubleOrNull(getAppView(), code);
    if (d2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.test(d1, d2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeShortToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ShortToIntFunction fn) {
    Short s = invoke.getFirstArgument().getConstShortOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeShortToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ShortToLongFunction fn) {
    Short s = invoke.getFirstArgument().getConstShortOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeShortToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      ShortToObjFunction<DexString> fn) {
    Short s = invoke.getFirstArgument().getConstShortOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeShortShortToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ShortShortToIntFunction fn) {
    Short s1 = invoke.getFirstArgument().getConstShortOrNull(getAppView(), code);
    if (s1 == null) {
      return;
    }
    Short s2 = invoke.getSecondArgument().getConstShortOrNull(getAppView(), code);
    if (s2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(s1, s2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedShortToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToIntFunction<Short> fn) {
    Short s = invoke.getFirstArgument().getConstBoxedShortOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedShortToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToLongFunction<Short> fn) {
    Short s = invoke.getFirstArgument().getConstBoxedShortOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeBoxedShortToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToFloatFunction<Short> fn) {
    Short s = invoke.getFirstArgument().getConstBoxedShortOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeBoxedShortToDoubleFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToDoubleFunction<Short> fn) {
    Short s = invoke.getFirstArgument().getConstBoxedShortOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    double replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstDouble(code, replacement);
  }

  default void optimizeBoxedShortToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      Function<Short, DexString> fn) {
    Short s = invoke.getFirstArgument().getConstBoxedShortOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeBoxedShortBoxedShortToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjObjToIntFunction<Short, Short> fn) {
    Short s1 = invoke.getFirstArgument().getConstBoxedShortOrNull(getAppView(), code);
    if (s1 == null) {
      return;
    }
    Short s2 = invoke.getSecondArgument().getConstBoxedShortOrNull(getAppView(), code);
    if (s2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(s1, s2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeBoxedShortBoxedShortToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BiPredicate<Short, Short> fn) {
    Short s1 = invoke.getFirstArgument().getConstBoxedShortOrNull(getAppView(), code);
    if (s1 == null) {
      return;
    }
    Short s2 = invoke.getSecondArgument().getConstBoxedShortOrNull(getAppView(), code);
    if (s2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.test(s1, s2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeStringToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      Predicate<DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.test(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeStringToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToIntFunction<DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeStringToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToLongFunction<DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeStringToFloatFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToFloatFunction<DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    float replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstFloat(code, replacement);
  }

  default void optimizeStringToDoubleFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjToDoubleFunction<DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    double replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstDouble(code, replacement);
  }

  default void optimizeStringToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      UnaryOperator<DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(s);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeStringIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjIntToIntFunction<DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    Integer i = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(s, i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeStringIntToLongFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjIntToLongFunction<DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    Integer i = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    long replacement;
    try {
      replacement = fn.apply(s, i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstLong(code, replacement);
  }

  default void optimizeStringIntToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      ObjIntToObjFunction<DexString, DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    Integer i = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(s, i);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeStringIntIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjIntIntToIntFunction<DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    Integer i1 = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i1 == null) {
      return;
    }
    Integer i2 = invoke.getThirdArgument().getConstIntOrNull(getAppView(), code);
    if (i2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(s, i1, i2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeStringIntIntToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      ObjIntIntToObjFunction<DexString, DexString> fn) {
    DexString s = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s == null) {
      return;
    }
    Integer i1 = invoke.getSecondArgument().getConstIntOrNull(getAppView(), code);
    if (i1 == null) {
      return;
    }
    Integer i2 = invoke.getThirdArgument().getConstIntOrNull(getAppView(), code);
    if (i2 == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(s, i1, i2);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeStringStringToBooleanFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      BiPredicate<DexString, DexString> fn) {
    DexString s1 = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s1 == null) {
      return;
    }
    DexString s2 = invoke.getSecondArgument().getConstStringOrNull(getAppView(), code);
    if (s2 == null) {
      return;
    }
    boolean replacement;
    try {
      replacement = fn.test(s1, s2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstBoolean(code, replacement);
  }

  default void optimizeStringStringToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjObjToIntFunction<DexString, DexString> fn) {
    DexString s1 = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s1 == null) {
      return;
    }
    DexString s2 = invoke.getSecondArgument().getConstStringOrNull(getAppView(), code);
    if (s2 == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(s1, s2);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void optimizeStringStringToStringFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      BiFunction<DexString, DexString, DexString> fn) {
    DexString s1 = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s1 == null) {
      return;
    }
    DexString s2 = invoke.getSecondArgument().getConstStringOrNull(getAppView(), code);
    if (s2 == null) {
      return;
    }
    DexString replacement;
    try {
      replacement = fn.apply(s1, s2);
    } catch (RuntimeException e) {
      return;
    }
    replaceCurrentInstructionWithConstString(
        code, instructionIterator, invoke, affectedValues, replacement);
  }

  default void optimizeStringStringIntToIntFunction(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      ObjObjIntToIntFunction<DexString, DexString> fn) {
    DexString s1 = invoke.getFirstArgument().getConstStringOrNull(getAppView(), code);
    if (s1 == null) {
      return;
    }
    DexString s2 = invoke.getSecondArgument().getConstStringOrNull(getAppView(), code);
    if (s2 == null) {
      return;
    }
    Integer i = invoke.getThirdArgument().getConstIntOrNull(getAppView(), code);
    if (i == null) {
      return;
    }
    int replacement;
    try {
      replacement = fn.apply(s1, s2, i);
    } catch (RuntimeException e) {
      return;
    }
    instructionIterator.replaceCurrentInstructionWithConstInt(code, replacement);
  }

  default void replaceCurrentInstructionWithConstString(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues,
      DexString replacement) {
    if (replacement == null) {
      return;
    }
    Value firstArg = invoke.getFirstArgument();
    if (firstArg.isConstString() && replacement.isIdenticalTo(firstArg.getConstString())) {
      if (invoke.hasOutValue()) {
        invoke.outValue().replaceUsers(firstArg, affectedValues);
        firstArg.uniquePhiUsers().forEach(phi -> phi.removeTrivialPhi(null, affectedValues));
      }
      instructionIterator.removeOrReplaceByDebugLocalRead();
    } else {
      instructionIterator.replaceCurrentInstructionWithConstString(
          getAppView(), code, replacement, affectedValues);
    }
  }
}

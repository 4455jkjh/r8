// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library.primitive;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory.CharMembers;
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
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.Set;

@SuppressWarnings({"deprecation", "CharacterGetNumericValue"})
public class CharacterMethodOptimizer extends PrimitiveMethodOptimizer {

  private final CharMembers charMembers;

  CharacterMethodOptimizer(AppView<?> appView) {
    super(appView);
    this.charMembers = factory.charMembers;
  }

  @Override
  DexMethod getBoxMethod() {
    return charMembers.valueOf;
  }

  @Override
  DexMethod getUnboxMethod() {
    return charMembers.charValue;
  }

  @Override
  boolean isMatchingSingleBoxedPrimitive(AbstractValue abstractValue) {
    return abstractValue.isSingleBoxedChar();
  }

  @Override
  public DexType getType() {
    return factory.boxedCharType;
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
      case 'c':
        if (singleTargetReference.isIdenticalTo(charMembers.charCount)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Character::charCount);
        } else if (singleTargetReference.isIdenticalTo(charMembers.charValue)) {
          optimizeUnboxMethod(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(charMembers.codePointAt)) {
          optimizeCodePointAt(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(charMembers.codePointBefore)) {
          optimizeCodePointBefore(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(charMembers.codePointCount)) {
          optimizeCodePointCount(code, instructionIterator, invoke);
        } else if (singleTargetReference.isIdenticalTo(charMembers.compare)) {
          optimizeCharCharToIntFunction(code, instructionIterator, invoke, Character::compare);
        } else if (singleTargetReference.isIdenticalTo(charMembers.compareTo)) {
          optimizeBoxedCharBoxedCharToIntFunction(
              code, instructionIterator, invoke, Character::compareTo);
        }
        break;
      case 'd':
        if (singleTargetReference.isIdenticalTo(charMembers.digitWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.digitWithInt)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Character::digit);
        }
        break;
      case 'e':
        if (singleTargetReference.isIdenticalTo(charMembers.equals)) {
          optimizeBoxedCharBoxedCharToBooleanFunction(
              code, instructionIterator, invoke, Character::equals);
        }
        break;
      case 'f':
        if (singleTargetReference.isIdenticalTo(charMembers.forDigit)) {
          optimizeIntIntToIntFunction(code, instructionIterator, invoke, Character::forDigit);
        }
        break;
      case 'g':
        if (singleTargetReference.isIdenticalTo(charMembers.getDirectionalityWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.getDirectionalityWithInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Character::getDirectionality);
        } else if (singleTargetReference.isIdenticalTo(charMembers.getName)) {
          optimizeGetName(code, instructionIterator, invoke, affectedValues);
        } else if (singleTargetReference.isIdenticalTo(charMembers.getNumericValueWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.getNumericValueWithInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Character::getNumericValue);
        } else if (singleTargetReference.isIdenticalTo(charMembers.getTypeWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.getTypeWithInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Character::getType);
        }
        break;
      case 'h':
        if (singleTargetReference.isIdenticalTo(charMembers.hashCode)) {
          optimizeBoxedCharToIntFunction(code, instructionIterator, invoke, Object::hashCode);
        } else if (singleTargetReference.isIdenticalTo(charMembers.staticHashCode)) {
          optimizeCharToIntFunction(code, instructionIterator, invoke, Character::hashCode);
        } else if (singleTargetReference.isIdenticalTo(charMembers.highSurrogate)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Character::highSurrogate);
        }
        break;
      case 'i':
        if (singleTargetReference.isIdenticalTo(charMembers.isAlphabetic)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isAlphabetic);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isBmpCodePoint)) {
          optimizeIntToBooleanFunction(
              code, instructionIterator, invoke, Character::isBmpCodePoint);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isDefinedWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isDefinedWithInt)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isDefined);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isDigitWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isDigitWithInt)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isDigit);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isHighSurrogate)) {
          optimizeCharToBooleanFunction(
              code, instructionIterator, invoke, Character::isHighSurrogate);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isIdentifierIgnorableWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isIdentifierIgnorableWithInt)) {
          optimizeIntToBooleanFunction(
              code, instructionIterator, invoke, Character::isIdentifierIgnorable);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isIdeographic)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isIdeographic);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isISOControlWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isISOControlWithInt)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isISOControl);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isJavaIdentifierPartWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isJavaIdentifierPartWithInt)) {
          optimizeIntToBooleanFunction(
              code, instructionIterator, invoke, Character::isJavaIdentifierPart);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isJavaIdentifierStartWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isJavaIdentifierStartWithInt)) {
          optimizeIntToBooleanFunction(
              code, instructionIterator, invoke, Character::isJavaIdentifierStart);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isJavaLetter)) {
          optimizeCharToBooleanFunction(code, instructionIterator, invoke, Character::isJavaLetter);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isJavaLetterOrDigit)) {
          optimizeCharToBooleanFunction(
              code, instructionIterator, invoke, Character::isJavaLetterOrDigit);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isLetterWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isLetterWithInt)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isLetter);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isLetterOrDigitWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isLetterOrDigitWithInt)) {
          optimizeIntToBooleanFunction(
              code, instructionIterator, invoke, Character::isLetterOrDigit);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isLowerCaseWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isLowerCaseWithInt)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isLowerCase);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isLowSurrogate)) {
          optimizeCharToBooleanFunction(
              code, instructionIterator, invoke, Character::isLowSurrogate);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isMirroredWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isMirroredWithInt)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isMirrored);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isSpace)) {
          optimizeCharToBooleanFunction(code, instructionIterator, invoke, Character::isSpace);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isSpaceCharWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isSpaceCharWithInt)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isSpaceChar);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isSupplementaryCodePoint)) {
          optimizeIntToBooleanFunction(
              code, instructionIterator, invoke, Character::isSupplementaryCodePoint);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isSurrogate)) {
          optimizeCharToBooleanFunction(code, instructionIterator, invoke, Character::isSurrogate);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isSurrogatePair)) {
          optimizeCharCharToBooleanFunction(
              code, instructionIterator, invoke, Character::isSurrogatePair);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isTitleCaseWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isTitleCaseWithInt)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isTitleCase);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isUnicodeIdentifierPartWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isUnicodeIdentifierPartWithInt)) {
          optimizeIntToBooleanFunction(
              code, instructionIterator, invoke, Character::isUnicodeIdentifierPart);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isUnicodeIdentifierStartWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isUnicodeIdentifierStartWithInt)) {
          optimizeIntToBooleanFunction(
              code, instructionIterator, invoke, Character::isUnicodeIdentifierStart);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isUpperCaseWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isUpperCaseWithInt)) {
          optimizeIntToBooleanFunction(code, instructionIterator, invoke, Character::isUpperCase);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isValidCodePoint)) {
          optimizeIntToBooleanFunction(
              code, instructionIterator, invoke, Character::isValidCodePoint);
        } else if (singleTargetReference.isIdenticalTo(charMembers.isWhitespaceWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.isWhitespaceWithInt)) {
          // Android <=4.0.4 does not recognize all as whitespace. Just ensure local consistency.
          if (options.isGeneratingDex()
              && options.getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L)) {
            optimizeIntToBooleanFunction(
                code, instructionIterator, invoke, Character::isWhitespace);
          }
        }
        break;
      case 'l':
        if (singleTargetReference.isIdenticalTo(charMembers.lowSurrogate)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Character::lowSurrogate);
        }
        break;
      case 'o':
        if (singleTargetReference.isIdenticalTo(charMembers.offsetByCodePoints)) {
          optimizeOffsetByCodePoints(code, instructionIterator, invoke);
        }
        break;
      case 'r':
        if (singleTargetReference.isIdenticalTo(charMembers.reverseBytes)) {
          optimizeCharToIntFunction(code, instructionIterator, invoke, Character::reverseBytes);
        }
        break;
      case 't':
        if (singleTargetReference.isIdenticalTo(charMembers.toCodePoint)) {
          optimizeCharCharToIntFunction(code, instructionIterator, invoke, Character::toCodePoint);
        } else if (singleTargetReference.isIdenticalTo(charMembers.toLowerCaseWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.toLowerCaseWithInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Character::toLowerCase);
        } else if (singleTargetReference.isIdenticalTo(charMembers.toString)) {
          optimizeBoxedCharToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              c -> factory.createString(c.toString()));
        } else if (singleTargetReference.isIdenticalTo(charMembers.staticToString)) {
          optimizeCharToStringFunction(
              code,
              instructionIterator,
              invoke,
              affectedValues,
              c -> factory.createString(Character.toString(c)));
        } else if (singleTargetReference.isIdenticalTo(charMembers.toTitleCaseWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.toTitleCaseWithInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Character::toTitleCase);
        } else if (singleTargetReference.isIdenticalTo(charMembers.toUpperCaseWithChar)
            || singleTargetReference.isIdenticalTo(charMembers.toUpperCaseWithInt)) {
          optimizeIntToIntFunction(code, instructionIterator, invoke, Character::toUpperCase);
        }
        break;
      case 'v':
        if (singleTargetReference.isIdenticalTo(charMembers.valueOf)) {
          optimizeBoxMethod(code, instructionIterator, invoke, affectedValues);
        }
        break;
      default:
        break;
    }
    return instructionIterator;
  }

  private void optimizeCodePointAt(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    DexString seq = invoke.getFirstArgument().getConstStringOrNull(appView, code);
    if (seq == null) {
      return;
    }
    Integer index = invoke.getSecondArgument().getConstIntOrNull(appView, code);
    if (index == null) {
      return;
    }
    String s = seq.toString();
    if (index >= 0 && index < s.length()) {
      int result = Character.codePointAt(s, index);
      instructionIterator.replaceCurrentInstructionWithConstInt(code, result);
    }
  }

  private void optimizeCodePointBefore(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    DexString seq = invoke.getFirstArgument().getConstStringOrNull(appView, code);
    if (seq == null) {
      return;
    }
    Integer index = invoke.getSecondArgument().getConstIntOrNull(appView, code);
    if (index == null) {
      return;
    }
    String s = seq.toString();
    if (index > 0 && index <= s.length()) {
      int result = Character.codePointBefore(s, index);
      instructionIterator.replaceCurrentInstructionWithConstInt(code, result);
    }
  }

  private void optimizeCodePointCount(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    DexString seq = invoke.getFirstArgument().getConstStringOrNull(appView, code);
    if (seq == null) {
      return;
    }
    Integer beginIndex = invoke.getSecondArgument().getConstIntOrNull(appView, code);
    if (beginIndex == null) {
      return;
    }
    Integer endIndex = invoke.getThirdArgument().getConstIntOrNull(appView, code);
    if (endIndex == null) {
      return;
    }
    String s = seq.toString();
    if (beginIndex >= 0 && endIndex >= beginIndex && endIndex <= s.length()) {
      int result = Character.codePointCount(s, beginIndex, endIndex);
      instructionIterator.replaceCurrentInstructionWithConstInt(code, result);
    }
  }

  private void optimizeGetName(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      AffectedValues affectedValues) {
    Integer codePoint = invoke.getFirstArgument().getConstIntOrNull(appView, code);
    if (codePoint == null) {
      return;
    }
    String name = Character.getName(codePoint);
    if (name != null) {
      instructionIterator.replaceCurrentInstructionWithConstString(
          appView, code, name, affectedValues);
    } else {
      instructionIterator.replaceCurrentInstructionWithConstNull(code);
    }
  }

  private void optimizeOffsetByCodePoints(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    DexString seq = invoke.getFirstArgument().getConstStringOrNull(appView, code);
    if (seq == null) {
      return;
    }
    Integer index = invoke.getSecondArgument().getConstIntOrNull(appView, code);
    if (index == null) {
      return;
    }
    Integer offset = invoke.getThirdArgument().getConstIntOrNull(appView, code);
    if (offset == null) {
      return;
    }
    try {
      int result = Character.offsetByCodePoints(seq.toString(), index, offset);
      instructionIterator.replaceCurrentInstructionWithConstInt(code, result);
    } catch (IndexOutOfBoundsException ignored) {
      // Leave as is if index/offset out of bounds.
    }
  }
}

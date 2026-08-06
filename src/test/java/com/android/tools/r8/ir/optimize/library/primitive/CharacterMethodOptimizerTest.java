// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.library.primitive;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolder;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CharacterMethodOptimizerTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "2", // charCount
          "a", // charValue
          "98", // codePointAt
          "98", // codePointBefore
          "3", // codePointCount
          "-1", // compare
          "-1", // compareTo
          "10", // digit char
          "10", // digit int
          "true", // equals
          "false", // equals
          "a", // forDigit
          "0", // getDirectionality char
          "0", // getDirectionality int
          "LATIN CAPITAL LETTER A", // getName
          "10", // getNumericValue char
          "10", // getNumericValue int
          "2", // getType char
          "2", // getType int
          "97", // hashCode
          "97", // static hashCode
          "55296", // highSurrogate
          "true", // isAlphabetic
          "true", // isBmpCodePoint
          "true", // isDefined char
          "true", // isDefined int
          "true", // isDigit char
          "true", // isDigit int
          "true", // isHighSurrogate
          "true", // isIdentifierIgnorable char
          "true", // isIdentifierIgnorable int
          "true", // isIdeographic
          "true", // isISOControl char
          "true", // isISOControl int
          "true", // isJavaIdentifierPart char
          "true", // isJavaIdentifierPart int
          "true", // isJavaIdentifierStart char
          "true", // isJavaIdentifierStart int
          "true", // isJavaLetter
          "true", // isJavaLetterOrDigit
          "true", // isLetter char
          "true", // isLetter int
          "true", // isLetterOrDigit char
          "true", // isLetterOrDigit int
          "true", // isLowerCase char
          "true", // isLowerCase int
          "true", // isLowSurrogate
          "true", // isMirrored char
          "true", // isMirrored int
          "true", // isSpace
          "true", // isSpaceChar char
          "true", // isSpaceChar int
          "true", // isSupplementaryCodePoint
          "true", // isSurrogate
          "true", // isSurrogatePair
          "false", // isTitleCase char
          "false", // isTitleCase int
          "true", // isUnicodeIdentifierPart char
          "true", // isUnicodeIdentifierPart int
          "true", // isUnicodeIdentifierStart char
          "true", // isUnicodeIdentifierStart int
          "true", // isUpperCase char
          "true", // isUpperCase int
          "true", // isValidCodePoint
          "true", // isWhitespace char
          "true", // isWhitespace int
          "56320", // lowSurrogate
          "2", // offsetByCodePoints
          "513", // reverseBytes
          "65536", // toCodePoint
          "a", // toLowerCase char
          "97", // toLowerCase int
          "a", // toString
          "a", // static toString
          "A", // toTitleCase char
          "65", // toTitleCase int
          "A", // toUpperCase char
          "65", // toUpperCase int
          "a"); // valueOf

  @Test
  public void testD8Release() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addOptionsModification(options -> options.apiModelingOptions().disableOutlining())
        .release()
        .compile()
        .inspect(inspector -> inspect(inspector, false))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addOptionsModification(options -> options.apiModelingOptions().disableOutlining())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(inspector -> inspect(inspector, true))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void inspect(CodeInspector inspector, boolean isR8) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    if (isR8) {
      verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testCharValue"));
    }
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testCharCount"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testCodePointAt"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testCodePointBefore"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testCodePointCount"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testCompare"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testCompareTo"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testEquals"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testHashCode"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testStaticHashCode"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsAlphabetic"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsBmpCodePoint"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsIdeographic"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsSurrogate"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testLowSurrogate"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testGetName"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testDigit"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testForDigit"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testGetDirectionality"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testGetNumericValue"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testGetType"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testHighSurrogate"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsDefined"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsDigit"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsHighSurrogate"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsIdentifierIgnorable"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsISOControl"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsJavaIdentifierPart"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsJavaIdentifierStart"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsJavaLetter"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsJavaLetterOrDigit"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsLetter"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsLetterOrDigit"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsLowerCase"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsMirrored"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsSpace"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsSpaceChar"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsSupplementaryCodePoint"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsSurrogatePair"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsTitleCase"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsUnicodeIdentifierPart"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsUnicodeIdentifierStart"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsUpperCase"));
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testIsValidCodePoint"));
    if (parameters.isDexRuntime()
        && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.L)) {
      verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testIsWhitespace"));
    }
    verifyMethodHasNoCharacterInvokes(
        mainClass.uniqueMethodWithOriginalName("testOffsetByCodePoints"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testReverseBytes"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testToCodePoint"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testToLowerCase"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testToString"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testStaticToString"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testToTitleCase"));
    verifyMethodHasNoCharacterInvokes(mainClass.uniqueMethodWithOriginalName("testToUpperCase"));
  }

  private void verifyMethodHasNoCharacterInvokes(MethodSubject method) {
    assertThat(method, not(invokesMethodWithHolder(Character.class)));
  }

  @SuppressWarnings("deprecation")
  static class Main {

    @NeverInline
    static void testCharCount() {
      System.out.println(Character.charCount(0x10000));
    }

    @NeverInline
    static void testCharValue() {
      System.out.println(Character.valueOf('a').charValue());
    }

    @NeverInline
    static void testCodePointAt() {
      System.out.println(Character.codePointAt("abc", 1));
    }

    @NeverInline
    static void testCodePointBefore() {
      System.out.println(Character.codePointBefore("abc", 2));
    }

    @NeverInline
    static void testCodePointCount() {
      System.out.println(Character.codePointCount("abc", 0, 3));
    }

    @NeverInline
    static void testCompare() {
      System.out.println(Character.compare('a', 'b'));
    }

    @NeverInline
    static void testCompareTo() {
      System.out.println(Character.valueOf('a').compareTo(Character.valueOf('b')));
    }

    @NeverInline
    static void testDigit() {
      System.out.println(Character.digit('a', 16));
      System.out.println(Character.digit((int) 'a', 16));
    }

    @NeverInline
    static void testEquals() {
      System.out.println(Character.valueOf('a').equals(Character.valueOf('a')));
      System.out.println(Character.valueOf('a').equals(Character.valueOf('b')));
    }

    @NeverInline
    static void testForDigit() {
      System.out.println(Character.forDigit(10, 16));
    }

    @NeverInline
    static void testGetDirectionality() {
      System.out.println(Character.getDirectionality('a'));
      System.out.println(Character.getDirectionality((int) 'a'));
    }

    @NeverInline
    static void testGetName() {
      System.out.println(Character.getName((int) 'A'));
    }

    @NeverInline
    static void testGetNumericValue() {
      System.out.println(Character.getNumericValue('a'));
      System.out.println(Character.getNumericValue((int) 'a'));
    }

    @NeverInline
    static void testGetType() {
      System.out.println(Character.getType('a'));
      System.out.println(Character.getType((int) 'a'));
    }

    @NeverInline
    static void testHashCode() {
      System.out.println(Character.valueOf('a').hashCode());
    }

    @NeverInline
    static void testStaticHashCode() {
      System.out.println(Character.hashCode('a'));
    }

    @NeverInline
    static void testHighSurrogate() {
      System.out.println((int) Character.highSurrogate(0x10000));
    }

    @NeverInline
    static void testIsAlphabetic() {
      System.out.println(Character.isAlphabetic((int) 'a'));
    }

    @NeverInline
    static void testIsBmpCodePoint() {
      System.out.println(Character.isBmpCodePoint((int) 'a'));
    }

    @NeverInline
    static void testIsDefined() {
      System.out.println(Character.isDefined('a'));
      System.out.println(Character.isDefined((int) 'a'));
    }

    @NeverInline
    static void testIsDigit() {
      System.out.println(Character.isDigit('1'));
      System.out.println(Character.isDigit((int) '1'));
    }

    @NeverInline
    static void testIsHighSurrogate() {
      System.out.println(Character.isHighSurrogate('\uD800'));
    }

    @NeverInline
    static void testIsIdentifierIgnorable() {
      System.out.println(Character.isIdentifierIgnorable('\u0000'));
      System.out.println(Character.isIdentifierIgnorable(0));
    }

    @NeverInline
    static void testIsIdeographic() {
      System.out.println(Character.isIdeographic(0x4E00));
    }

    @NeverInline
    static void testIsISOControl() {
      System.out.println(Character.isISOControl('\r'));
      System.out.println(Character.isISOControl((int) '\r'));
    }

    @NeverInline
    static void testIsJavaIdentifierPart() {
      System.out.println(Character.isJavaIdentifierPart('a'));
      System.out.println(Character.isJavaIdentifierPart((int) 'a'));
    }

    @NeverInline
    static void testIsJavaIdentifierStart() {
      System.out.println(Character.isJavaIdentifierStart('a'));
      System.out.println(Character.isJavaIdentifierStart((int) 'a'));
    }

    @NeverInline
    static void testIsJavaLetter() {
      System.out.println(Character.isJavaLetter('a'));
    }

    @NeverInline
    static void testIsJavaLetterOrDigit() {
      System.out.println(Character.isJavaLetterOrDigit('a'));
    }

    @NeverInline
    static void testIsLetter() {
      System.out.println(Character.isLetter('a'));
      System.out.println(Character.isLetter((int) 'a'));
    }

    @NeverInline
    static void testIsLetterOrDigit() {
      System.out.println(Character.isLetterOrDigit('a'));
      System.out.println(Character.isLetterOrDigit((int) 'a'));
    }

    @NeverInline
    static void testIsLowerCase() {
      System.out.println(Character.isLowerCase('a'));
      System.out.println(Character.isLowerCase((int) 'a'));
    }

    @NeverInline
    static void testIsLowSurrogate() {
      System.out.println(Character.isLowSurrogate('\uDC00'));
    }

    @NeverInline
    static void testIsMirrored() {
      System.out.println(Character.isMirrored('('));
      System.out.println(Character.isMirrored((int) '('));
    }

    @NeverInline
    static void testIsSpace() {
      System.out.println(Character.isSpace(' '));
    }

    @NeverInline
    static void testIsSpaceChar() {
      System.out.println(Character.isSpaceChar(' '));
      System.out.println(Character.isSpaceChar((int) ' '));
    }

    @NeverInline
    static void testIsSupplementaryCodePoint() {
      System.out.println(Character.isSupplementaryCodePoint(0x10000));
    }

    @NeverInline
    static void testIsSurrogate() {
      System.out.println(Character.isSurrogate('\uD800'));
    }

    @NeverInline
    static void testIsSurrogatePair() {
      System.out.println(Character.isSurrogatePair('\uD800', '\uDC00'));
    }

    @NeverInline
    static void testIsTitleCase() {
      System.out.println(Character.isTitleCase('a'));
      System.out.println(Character.isTitleCase((int) 'a'));
    }

    @NeverInline
    static void testIsUnicodeIdentifierPart() {
      System.out.println(Character.isUnicodeIdentifierPart('a'));
      System.out.println(Character.isUnicodeIdentifierPart((int) 'a'));
    }

    @NeverInline
    static void testIsUnicodeIdentifierStart() {
      System.out.println(Character.isUnicodeIdentifierStart('a'));
      System.out.println(Character.isUnicodeIdentifierStart((int) 'a'));
    }

    @NeverInline
    static void testIsUpperCase() {
      System.out.println(Character.isUpperCase('A'));
      System.out.println(Character.isUpperCase((int) 'A'));
    }

    @NeverInline
    static void testIsValidCodePoint() {
      System.out.println(Character.isValidCodePoint(0x10000));
    }

    @NeverInline
    static void testIsWhitespace() {
      System.out.println(Character.isWhitespace(' '));
      System.out.println(Character.isWhitespace((int) ' '));
    }

    @NeverInline
    static void testLowSurrogate() {
      System.out.println((int) Character.lowSurrogate(0x10000));
    }

    @NeverInline
    static void testOffsetByCodePoints() {
      System.out.println(Character.offsetByCodePoints("abc", 0, 2));
    }

    @NeverInline
    static void testReverseBytes() {
      System.out.println((int) Character.reverseBytes('\u0102'));
    }

    @NeverInline
    static void testToCodePoint() {
      System.out.println(Character.toCodePoint('\uD800', '\uDC00'));
    }

    @NeverInline
    static void testToLowerCase() {
      System.out.println(Character.toLowerCase('A'));
      System.out.println(Character.toLowerCase((int) 'A'));
    }

    @NeverInline
    static void testToString() {
      System.out.println(Character.valueOf('a').toString());
    }

    @NeverInline
    static void testStaticToString() {
      System.out.println(Character.toString('a'));
    }

    @NeverInline
    static void testToTitleCase() {
      System.out.println(Character.toTitleCase('a'));
      System.out.println(Character.toTitleCase((int) 'a'));
    }

    @NeverInline
    static void testToUpperCase() {
      System.out.println(Character.toUpperCase('a'));
      System.out.println(Character.toUpperCase((int) 'a'));
    }

    @NeverInline
    static void testValueOfChar() {
      System.out.println(Character.valueOf('a'));
    }

    public static void main(String[] args) {
      testCharCount();
      testCharValue();
      testCodePointAt();
      testCodePointBefore();
      testCodePointCount();
      testCompare();
      testCompareTo();
      testDigit();
      testEquals();
      testForDigit();
      testGetDirectionality();
      testGetName();
      testGetNumericValue();
      testGetType();
      testHashCode();
      testStaticHashCode();
      testHighSurrogate();
      testIsAlphabetic();
      testIsBmpCodePoint();
      testIsDefined();
      testIsDigit();
      testIsHighSurrogate();
      testIsIdentifierIgnorable();
      testIsIdeographic();
      testIsISOControl();
      testIsJavaIdentifierPart();
      testIsJavaIdentifierStart();
      testIsJavaLetter();
      testIsJavaLetterOrDigit();
      testIsLetter();
      testIsLetterOrDigit();
      testIsLowerCase();
      testIsLowSurrogate();
      testIsMirrored();
      testIsSpace();
      testIsSpaceChar();
      testIsSupplementaryCodePoint();
      testIsSurrogate();
      testIsSurrogatePair();
      testIsTitleCase();
      testIsUnicodeIdentifierPart();
      testIsUnicodeIdentifierStart();
      testIsUpperCase();
      testIsValidCodePoint();
      testIsWhitespace();
      testLowSurrogate();
      testOffsetByCodePoints();
      testReverseBytes();
      testToCodePoint();
      testToLowerCase();
      testToString();
      testStaticToString();
      testToTitleCase();
      testToUpperCase();
      testValueOfChar();
    }
  }
}

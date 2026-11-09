// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.dex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexStringTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DexStringTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testEncodingLength() {
    DexItemFactory factory = new DexItemFactory();
    checkEncodedLength(factory.createString("\u0000"), 2);
    checkEncodedLength(factory.createString("\u0001"), 1);
    checkEncodedLength(factory.createString("\u007f"), 1);
    checkEncodedLength(factory.createString("\u0080"), 2);
    checkEncodedLength(factory.createString("\u07ff"), 2);
    checkEncodedLength(factory.createString("\u0800"), 3);
    checkEncodedLength(factory.createString("\uffff"), 3);
    checkEncodedLength(factory.createString("\ud800\udc00"), 6);
    checkEncodedLength(factory.createString("\udbff\udfff"), 6);
  }

  @Test
  public void testCompare() {
    DexItemFactory factory = new DexItemFactory();

    // Test strings in lexicographic order.
    DexString[] strings = {
      factory.createString(""),
      factory.createString("\u0000"),
      factory.createString("\u0001"),
      factory.createString("\u0060a"), // 'a' is 0x61.
      factory.createString("a"),
      factory.createString("a\u0000a"),
      factory.createString("a\u0001a"),
      factory.createString("a\u0060a"), // 'a' is 0x61.
      factory.createString("aa"),
      factory.createString("aaa"),
      factory.createString("a\u007f"), // U+007f is the last code point with one UTF-8 bytes.
      factory.createString("a\u007fa"),
      factory.createString("a\u0080"), // U+0080 is the first code point with two UTF-8 bytes.
      factory.createString("a\u0080a"),
      factory.createString("a\u07ff"), // U+07ff is the last code point with two UTF-8 bytes.
      factory.createString("a\u07ffa"),
      factory.createString("a\u0800"), // U+0800 is the first code point with three UTF-8 bytes.
      factory.createString("a\u0800a"),
      factory.createString("a\u0801"),
      factory.createString("a\u0801a"),
      factory.createString("a\ud800\udc00a"), // Surrogate pair for U+010000. Sorts per UTF-16.
      factory.createString("a\udbff\udfffa"), // Surrogate pair for U+10ffff. Sorts per UTF-16.
      factory.createString("a\uffffa"),
      factory.createString("\u007f"), // U+007f is the last code point with one UTF-8 bytes.
      factory.createString("\u0080"), // U+0080 is the first code point with two UTF-8 bytes.
      factory.createString("\u07ff"), // U+07ff is the last code point with two UTF-8 bytes.
      factory.createString("\u0800"), // U+0800 is the first code point with three UTF-8 bytes.
      factory.createString("\u0801"), // U+0800 is the first code point with three UTF-8 bytes.
      factory.createString("\ud800\udc00"), // Surrogate pair for U+010000. Sorts per UTF-16.
      factory.createString("\udbff\udfff"), // Surrogate pair for U+10ffff. Sorts per UTF-16.
      factory.createString("\uffff"),
    };

    for (int i = 0; i < strings.length; i++) {
      for (int j = 0; j < strings.length; j++) {
        int expected = Integer.signum(i - j);
        check(expected, strings[i], strings[j]);
        check(-expected, strings[j], strings[i]);
      }
    }
  }

  private void check(int expected, DexString s1, DexString s2) {
    assertEquals(
        s1.dump() + " " + s2.dump(),
        expected,
        Integer.signum(s1.toString().compareTo(s2.toString())));
    assertEquals(s1.dump() + " " + s2.dump(), expected, Integer.signum(s1.compareTo(s2)));
  }

  private void checkEncodedLength(DexString s, int encodedLength) {
    // The terminating zero is not part of the encoding,
    int length = s.content.length;
    assertEquals(0, s.content[length - 1]);
    assertEquals(encodedLength, length - 1);
  }

  @Test
  public void testToASCIIString() {
    DexItemFactory factory = new DexItemFactory();
    assertEquals("\\u0000", factory.createString("\u0000").toASCIIString());
    assertEquals("\\u0001", factory.createString("\u0001").toASCIIString());
    assertEquals("\\u001f", factory.createString("\u001f").toASCIIString());
    assertEquals(" ", factory.createString("\u0020").toASCIIString());
    assertEquals("~", factory.createString("\u007e").toASCIIString());
    assertEquals("\\u007f", factory.createString("\u007f").toASCIIString());
    assertEquals("\\u0080", factory.createString("\u0080").toASCIIString());
    assertEquals("\\u07ff", factory.createString("\u07ff").toASCIIString());
    assertEquals("\\u0800", factory.createString("\u0800").toASCIIString());
    assertEquals("\\uffff", factory.createString("\uffff").toASCIIString());
    assertEquals("\\ud800\\udc00", factory.createString("\ud800\udc00").toASCIIString());
    assertEquals("\\udbff\\udfff", factory.createString("\udbff\udfff").toASCIIString());
  }

  @Test
  public void testIsValidMethodName() {
    DexItemFactory factory = new DexItemFactory();

    // Empty strings.
    checkValidMethodName(factory, "", false);
    assertFalse(factory.createString(0, new byte[0]).isValidMethodName());

    // Fast-path valid ASCII identifiers and boundary characters.
    checkValidMethodName(factory, "a", true);
    checkValidMethodName(factory, "z", true);
    checkValidMethodName(factory, "A", true);
    checkValidMethodName(factory, "Z", true);
    checkValidMethodName(factory, "0", true);
    checkValidMethodName(factory, "9", true);
    checkValidMethodName(factory, "$", true);
    checkValidMethodName(factory, "-", true);
    checkValidMethodName(factory, "_", true);
    checkValidMethodName(factory, " ", true);
    checkValidMethodName(factory, "foo", true);
    checkValidMethodName(factory, "foo bar", true);
    checkValidMethodName(factory, "aAzZ09$-_ ", true);

    // Initializers and custom/malformed <...> names.
    checkValidMethodName(factory, "<init>", true);
    checkValidMethodName(factory, "<clinit>", true);
    checkValidMethodName(factory, "<>", false);
    checkValidMethodName(factory, "<", false);
    checkValidMethodName(factory, ">", false);
    checkValidMethodName(factory, "<custom>", false);
    checkValidMethodName(factory, "<fooo>", false);
    checkValidMethodName(factory, "<inix>", false);
    checkValidMethodName(factory, "<foobar>", false);
    checkValidMethodName(factory, "<clinix>", false);
    checkValidMethodName(factory, "<init", false);
    checkValidMethodName(factory, "init>", false);
    checkValidMethodName(factory, "<init>a", false);
    checkValidMethodName(factory, "<clinit", false);
    checkValidMethodName(factory, "clinit>", false);
    checkValidMethodName(factory, "<clinit>a", false);
    checkValidMethodName(factory, "a<init>", false);
    checkValidMethodName(factory, "foo<bar>", false);

    // Invalid ASCII boundary characters around valid ranges.
    checkValidMethodName(factory, "`", false); // 'a' - 1
    checkValidMethodName(factory, "{", false); // 'z' + 1
    checkValidMethodName(factory, "@", false); // 'A' - 1
    checkValidMethodName(factory, "[", false); // 'Z' + 1
    checkValidMethodName(factory, "/", false); // '0' - 1
    checkValidMethodName(factory, ":", false); // '9' + 1
    checkValidMethodName(factory, ".", false);
    checkValidMethodName(factory, ";", false);
    checkValidMethodName(factory, "\t", false);
    checkValidMethodName(factory, "\n", false);
    checkValidMethodName(factory, "\u001f", false);
    checkValidMethodName(factory, "\u007f", false);
    checkValidMethodName(factory, "foo/bar", false);
    checkValidMethodName(factory, "foo.bar", false);
    checkValidMethodName(factory, "foo;bar", false);

    // Non-ASCII fallbacks (valid).
    checkValidMethodName(factory, "\u00a0", true);
    checkValidMethodName(factory, "foo\u00a0bar", true);
    checkValidMethodName(factory, "\u00a1", true);
    checkValidMethodName(factory, "f\u00e9", true);
    checkValidMethodName(factory, "\u1fff", true);
    checkValidMethodName(factory, "\u2000", true);
    checkValidMethodName(factory, "\u2010", true);
    checkValidMethodName(factory, "\u2027", true);
    checkValidMethodName(factory, "\u2030", true);
    checkValidMethodName(factory, "\u3000", true);
    checkValidMethodName(factory, "\u4e2d\u6587", true);
    checkValidMethodName(factory, "\ud7ff", true);
    checkValidMethodName(factory, "\ue000", true);
    checkValidMethodName(factory, "\ufefe", true);
    checkValidMethodName(factory, "\uff00", true);
    checkValidMethodName(factory, "\uffef", true);
    checkValidMethodName(factory, "\ud800\udc00", true);
    checkValidMethodName(factory, "a\ud800\udc00b", true);
    checkValidMethodName(factory, "\udbff\udfff", true);

    // Non-ASCII fallbacks (invalid).
    checkValidMethodName(factory, "\u0000", false);
    checkValidMethodName(factory, "a\u0000b", false);
    checkValidMethodName(factory, "\u0080", false);
    checkValidMethodName(factory, "\u009f", false);
    checkValidMethodName(factory, "a\u0080b", false);
    checkValidMethodName(factory, "\u200b", false);
    checkValidMethodName(factory, "\u2028", false);
    checkValidMethodName(factory, "\ufeff", false);
    checkValidMethodName(factory, "\ufff0", false);
    checkValidMethodName(factory, "\uffff", false);
    checkValidMethodName(factory, "<f\u00e9>", false);
    checkValidMethodName(factory, "f\u00e9/bar", false);
    assertFalse(factory.createString(2, new byte[] {'a', (byte) 0x80, 0}).isValidMethodName());
  }

  @Test
  public void testIsValidFieldName() {
    DexItemFactory factory = new DexItemFactory();

    // Empty strings.
    checkValidFieldName(factory, "", false);
    assertFalse(factory.createString(0, new byte[0]).isValidFieldName());

    // Fast-path valid ASCII identifiers and boundary characters.
    checkValidFieldName(factory, "a", true);
    checkValidFieldName(factory, "z", true);
    checkValidFieldName(factory, "A", true);
    checkValidFieldName(factory, "Z", true);
    checkValidFieldName(factory, "0", true);
    checkValidFieldName(factory, "9", true);
    checkValidFieldName(factory, "$", true);
    checkValidFieldName(factory, "-", true);
    checkValidFieldName(factory, "_", true);
    checkValidFieldName(factory, " ", true);
    checkValidFieldName(factory, "foo", true);
    checkValidFieldName(factory, "foo bar", true);
    checkValidFieldName(factory, "aAzZ09$-_ ", true);

    // <init>, <clinit>, and custom <...> field names (which fall back to DescriptorUtils).
    checkValidFieldName(factory, "<init>", true);
    checkValidFieldName(factory, "<clinit>", true);
    checkValidFieldName(factory, "<custom>", true);
    checkValidFieldName(factory, "<foo bar>", true);
    checkValidFieldName(factory, "<f\u00e9>", true);
    checkValidFieldName(factory, "<init", false);
    checkValidFieldName(factory, "init>", false);
    checkValidFieldName(factory, "<", false);
    checkValidFieldName(factory, ">", false);
    checkValidFieldName(factory, "<custom>a", false);
    checkValidFieldName(factory, "a<custom>", false);
    checkValidFieldName(factory, "<<custom>>", false);
    checkValidFieldName(factory, "<foo/bar>", false);
    checkValidFieldName(factory, "<foo.bar>", false);

    // Invalid ASCII boundary characters around valid ranges.
    checkValidFieldName(factory, "`", false); // 'a' - 1
    checkValidFieldName(factory, "{", false); // 'z' + 1
    checkValidFieldName(factory, "@", false); // 'A' - 1
    checkValidFieldName(factory, "[", false); // 'Z' + 1
    checkValidFieldName(factory, "/", false); // '0' - 1
    checkValidFieldName(factory, ":", false); // '9' + 1
    checkValidFieldName(factory, ".", false);
    checkValidFieldName(factory, ";", false);
    checkValidFieldName(factory, "\t", false);
    checkValidFieldName(factory, "\n", false);
    checkValidFieldName(factory, "\u001f", false);
    checkValidFieldName(factory, "\u007f", false);
    checkValidFieldName(factory, "foo/bar", false);
    checkValidFieldName(factory, "foo.bar", false);
    checkValidFieldName(factory, "foo;bar", false);

    // Non-ASCII fallbacks (valid).
    checkValidFieldName(factory, "\u00a0", true);
    checkValidFieldName(factory, "foo\u00a0bar", true);
    checkValidFieldName(factory, "\u00a1", true);
    checkValidFieldName(factory, "f\u00e9", true);
    checkValidFieldName(factory, "\u1fff", true);
    checkValidFieldName(factory, "\u2000", true);
    checkValidFieldName(factory, "\u2010", true);
    checkValidFieldName(factory, "\u2027", true);
    checkValidFieldName(factory, "\u2030", true);
    checkValidFieldName(factory, "\u3000", true);
    checkValidFieldName(factory, "\u4e2d\u6587", true);
    checkValidFieldName(factory, "\ud7ff", true);
    checkValidFieldName(factory, "\ue000", true);
    checkValidFieldName(factory, "\ufefe", true);
    checkValidFieldName(factory, "\uff00", true);
    checkValidFieldName(factory, "\uffef", true);
    checkValidFieldName(factory, "\ud800\udc00", true);
    checkValidFieldName(factory, "a\ud800\udc00b", true);
    checkValidFieldName(factory, "\udbff\udfff", true);

    // Non-ASCII fallbacks (invalid).
    checkValidFieldName(factory, "\u0000", false);
    checkValidFieldName(factory, "a\u0000b", false);
    checkValidFieldName(factory, "\u0080", false);
    checkValidFieldName(factory, "\u009f", false);
    checkValidFieldName(factory, "a\u0080b", false);
    checkValidFieldName(factory, "\u200b", false);
    checkValidFieldName(factory, "\u2028", false);
    checkValidFieldName(factory, "\ufeff", false);
    checkValidFieldName(factory, "\ufff0", false);
    checkValidFieldName(factory, "\uffff", false);
    checkValidFieldName(factory, "f\u00e9/bar", false);
    assertFalse(factory.createString(2, new byte[] {'a', (byte) 0x80, 0}).isValidFieldName());
  }

  @Test
  public void testIsValidClassDescriptor() {
    DexItemFactory factory = new DexItemFactory();

    // Empty and short strings (< 3 chars), or missing 'L' / ';'.
    checkValidClassDescriptor(factory, "", false);
    checkValidClassDescriptor(factory, "L", false);
    checkValidClassDescriptor(factory, "I", false);
    checkValidClassDescriptor(factory, "L;", false);
    checkValidClassDescriptor(factory, "ab", false);
    checkValidClassDescriptor(factory, "L\u00e9", false);
    checkValidClassDescriptor(factory, "\u4e2d", false);
    checkValidClassDescriptor(factory, "java/lang/Object;", false);
    checkValidClassDescriptor(factory, "Ljava/lang/Object", false);
    checkValidClassDescriptor(factory, "[Ljava/lang/Object;", false);
    assertFalse(factory.createString(0, new byte[0]).isValidClassDescriptor());

    // Fast-path valid ASCII class descriptors and boundary characters.
    checkValidClassDescriptor(factory, "La;", true);
    checkValidClassDescriptor(factory, "Lz;", true);
    checkValidClassDescriptor(factory, "LA;", true);
    checkValidClassDescriptor(factory, "LZ;", true);
    checkValidClassDescriptor(factory, "L0;", true);
    checkValidClassDescriptor(factory, "L9;", true);
    checkValidClassDescriptor(factory, "L$;", true);
    checkValidClassDescriptor(factory, "L-;", true);
    checkValidClassDescriptor(factory, "L_;", true);
    checkValidClassDescriptor(factory, "L ;", true);
    checkValidClassDescriptor(factory, "Ljava/lang/Object;", true);
    checkValidClassDescriptor(factory, "La/b/C$D-E_F 09;", true);
    checkValidClassDescriptor(factory, "LaAzZ09$-_ /b;", true);

    // Leading/trailing slashes and consecutive slashes '//'.
    checkValidClassDescriptor(factory, "L/;", false);
    checkValidClassDescriptor(factory, "L/a;", false);
    checkValidClassDescriptor(factory, "La/;", false);
    checkValidClassDescriptor(factory, "La//b;", false);
    checkValidClassDescriptor(factory, "La///b;", false);
    checkValidClassDescriptor(factory, "La/b//c;", false);
    checkValidClassDescriptor(factory, "L\u00e9//b;", false);
    checkValidClassDescriptor(factory, "La//\u00e9;", false);
    checkValidClassDescriptor(factory, "L/\u00e9;", false);
    checkValidClassDescriptor(factory, "L\u00e9/;", false);

    // Invalid ASCII boundary characters around valid ranges.
    checkValidClassDescriptor(factory, "L`;", false); // 'a' - 1
    checkValidClassDescriptor(factory, "L{;", false); // 'z' + 1
    checkValidClassDescriptor(factory, "L@;", false); // 'A' - 1
    checkValidClassDescriptor(factory, "L[;", false); // 'Z' + 1
    checkValidClassDescriptor(factory, "L:;", false); // '9' + 1
    checkValidClassDescriptor(factory, "La.b;", false);
    checkValidClassDescriptor(factory, "La;b;", false);
    checkValidClassDescriptor(factory, "L<init>;", false);
    checkValidClassDescriptor(factory, "La<b>;", false);
    checkValidClassDescriptor(factory, "L\t;", false);
    checkValidClassDescriptor(factory, "L\n;", false);
    checkValidClassDescriptor(factory, "L\u001f;", false);
    checkValidClassDescriptor(factory, "L\u007f;", false);

    // Non-ASCII fallbacks (valid).
    checkValidClassDescriptor(factory, "L\u00a0;", true);
    checkValidClassDescriptor(factory, "La/b\u00a0c;", true);
    checkValidClassDescriptor(factory, "L\u00a1;", true);
    checkValidClassDescriptor(factory, "Lf\u00e9/B\u00e4r;", true);
    checkValidClassDescriptor(factory, "L\u2000;", true);
    checkValidClassDescriptor(factory, "L\u3000;", true);
    checkValidClassDescriptor(factory, "L\u4e2d/\u6587;", true);
    checkValidClassDescriptor(factory, "L\ufefe;", true);
    checkValidClassDescriptor(factory, "L\uffef;", true);
    checkValidClassDescriptor(factory, "L\ud800\udc00;", true);
    checkValidClassDescriptor(factory, "L\udbff\udfff;", true);

    // Non-ASCII fallbacks (invalid).
    checkValidClassDescriptor(factory, "L\u0000;", false);
    checkValidClassDescriptor(factory, "La\u0000b;", false);
    checkValidClassDescriptor(factory, "L\u0080;", false);
    checkValidClassDescriptor(factory, "L\u009f;", false);
    checkValidClassDescriptor(factory, "L\u200b;", false);
    checkValidClassDescriptor(factory, "L\u2028;", false);
    checkValidClassDescriptor(factory, "L\ufeff;", false);
    checkValidClassDescriptor(factory, "L\ufff0;", false);
    checkValidClassDescriptor(factory, "L\uffff;", false);
    checkValidClassDescriptor(factory, "Lf\u00e9.Bar;", false);
    assertFalse(
        factory
            .createString(4, new byte[] {'L', 'a', (byte) 0x80, ';', 0})
            .isValidClassDescriptor());
  }

  @Test
  public void testIsValidSimpleName() {
    DexItemFactory factory = new DexItemFactory();

    // Empty strings.
    checkValidSimpleName(factory, AndroidApiLevel.Q, "", false);
    checkValidSimpleName(factory, AndroidApiLevel.R, "", false);
    assertFalse(factory.createString(0, new byte[0]).isValidSimpleName(AndroidApiLevel.Q));
    assertFalse(factory.createString(0, new byte[0]).isValidSimpleName(AndroidApiLevel.R));

    // Fast-path ASCII without spaces.
    checkValidSimpleName(factory, AndroidApiLevel.Q, "a", true);
    checkValidSimpleName(factory, AndroidApiLevel.R, "a", true);
    checkValidSimpleName(factory, AndroidApiLevel.Q, "foo", true);
    checkValidSimpleName(factory, AndroidApiLevel.R, "foo", true);
    checkValidSimpleName(factory, AndroidApiLevel.Q, "aAzZ09$-_", true);
    checkValidSimpleName(factory, AndroidApiLevel.R, "aAzZ09$-_", true);

    // ASCII spaces (disallowed prior to Android R, allowed from Android R).
    checkValidSimpleName(factory, AndroidApiLevel.Q, " ", false);
    checkValidSimpleName(factory, AndroidApiLevel.R, " ", true);
    checkValidSimpleName(factory, AndroidApiLevel.Q, "foo bar", false);
    checkValidSimpleName(factory, AndroidApiLevel.R, "foo bar", true);

    // Non-ASCII Unicode spaces (disallowed prior to Android R, allowed from Android R).
    String[] unicodeSpaces = {
      "\u00a0", "foo\u00a0bar", "\u1680", "\u2000", "\u200a", "\u202f", "\u205f", "\u3000"
    };
    for (String unicodeSpace : unicodeSpaces) {
      checkValidSimpleName(factory, AndroidApiLevel.Q, unicodeSpace, false);
      checkValidSimpleName(factory, AndroidApiLevel.R, unicodeSpace, true);
    }

    // Non-ASCII non-space characters.
    String[] nonSpaces = {"\u00a1", "f\u00e9", "\u200b", "\u2010", "\u4e2d", "\ud800\udc00"};
    for (String nonSpace : nonSpaces) {
      checkValidSimpleName(factory, AndroidApiLevel.Q, nonSpace, true);
      checkValidSimpleName(factory, AndroidApiLevel.R, nonSpace, true);
    }

    // Malformed MUTF-8 fallback.
    assertFalse(
        factory
            .createString(2, new byte[] {'a', (byte) 0x80, 0})
            .isValidSimpleName(AndroidApiLevel.Q));
    assertTrue(
        factory
            .createString(2, new byte[] {'a', (byte) 0x80, 0})
            .isValidSimpleName(AndroidApiLevel.R));
  }

  private void checkValidMethodName(DexItemFactory factory, String string, boolean expected) {
    assertEquals(string, expected, DescriptorUtils.isValidMethodName(string));
    assertEquals(string, expected, factory.createString(string).isValidMethodName());
  }

  private void checkValidFieldName(DexItemFactory factory, String string, boolean expected) {
    assertEquals(string, expected, DescriptorUtils.isValidFieldName(string));
    assertEquals(string, expected, factory.createString(string).isValidFieldName());
  }

  private void checkValidClassDescriptor(DexItemFactory factory, String string, boolean expected) {
    assertEquals(string, expected, DescriptorUtils.isValidClassDescriptor(string));
    assertEquals(string, expected, factory.createString(string).isValidClassDescriptor());
  }

  private void checkValidSimpleName(
      DexItemFactory factory, AndroidApiLevel apiLevel, String string, boolean expected) {
    assertEquals(string, expected, DexString.isValidSimpleName(apiLevel, string));
    assertEquals(string, expected, factory.createString(string).isValidSimpleName(apiLevel));
  }
}

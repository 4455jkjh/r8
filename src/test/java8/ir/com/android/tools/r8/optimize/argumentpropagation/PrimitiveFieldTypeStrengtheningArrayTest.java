// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class PrimitiveFieldTypeStrengtheningArrayTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addInnerClasses(getClass())
        .release()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "[1]",
            "[\u0001, \u007F]",
            "[1, 127]",
            "[1, 127, 32767, 65535]",
            "[1, 127, 32767, 65535, 2147483647]");
  }

  @Test
  public void testR8() throws Exception {
    boolean optimize = !parameters.canHaveDalvikIntUsedAsNonIntPrimitiveTypeBug();
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keepclassmembers class * { static *** return*(); }")
        .addKeepClassAndMembersRules(Sinks.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());

              // Byte field.
              FieldSubject byteToBooleanField =
                  mainClass.uniqueFieldWithOriginalName("byteToBooleanField");
              assertThat(byteToBooleanField, isPresent());
              assertEquals(
                  optimize ? "boolean" : "byte",
                  byteToBooleanField.getField().getType().getTypeName());

              // Char fields.
              FieldSubject charToBooleanField =
                  mainClass.uniqueFieldWithOriginalName("charToBooleanField");
              assertThat(charToBooleanField, isPresent());
              assertEquals("char", charToBooleanField.getField().getType().getTypeName());

              FieldSubject charToByteField =
                  mainClass.uniqueFieldWithOriginalName("charToByteField");
              assertThat(charToByteField, isPresent());
              assertEquals("char", charToByteField.getField().getType().getTypeName());

              // Short fields.
              FieldSubject shortToBooleanField =
                  mainClass.uniqueFieldWithOriginalName("shortToBooleanField");
              assertThat(shortToBooleanField, isPresent());
              assertEquals(
                  optimize ? "boolean" : "short",
                  shortToBooleanField.getField().getType().getTypeName());

              FieldSubject shortToByteField =
                  mainClass.uniqueFieldWithOriginalName("shortToByteField");
              assertThat(shortToByteField, isPresent());
              assertEquals(
                  optimize ? "byte" : "short", shortToByteField.getField().getType().getTypeName());

              // Int fields.
              FieldSubject intToBooleanField =
                  mainClass.uniqueFieldWithOriginalName("intToBooleanField");
              assertThat(intToBooleanField, isPresent());
              assertEquals(
                  optimize ? "boolean" : "int",
                  intToBooleanField.getField().getType().getTypeName());

              FieldSubject intToByteField = mainClass.uniqueFieldWithOriginalName("intToByteField");
              assertThat(intToByteField, isPresent());
              assertEquals(
                  optimize ? "byte" : "int", intToByteField.getField().getType().getTypeName());

              FieldSubject intToShortField =
                  mainClass.uniqueFieldWithOriginalName("intToShortField");
              assertThat(intToShortField, isPresent());
              assertEquals(
                  optimize ? "short" : "int", intToShortField.getField().getType().getTypeName());

              FieldSubject intToCharField = mainClass.uniqueFieldWithOriginalName("intToCharField");
              assertThat(intToCharField, isPresent());
              assertEquals(
                  optimize ? "char" : "int", intToCharField.getField().getType().getTypeName());

              // Long fields.
              FieldSubject longToBooleanField =
                  mainClass.uniqueFieldWithOriginalName("longToBooleanField");
              assertThat(longToBooleanField, isPresent());
              assertEquals(
                  optimize ? "boolean" : "int",
                  longToBooleanField.getField().getType().getTypeName());

              FieldSubject longToByteField =
                  mainClass.uniqueFieldWithOriginalName("longToByteField");
              assertThat(longToByteField, isPresent());
              assertEquals(
                  optimize ? "byte" : "int", longToByteField.getField().getType().getTypeName());

              FieldSubject longToShortField =
                  mainClass.uniqueFieldWithOriginalName("longToShortField");
              assertThat(longToShortField, isPresent());
              assertEquals(
                  optimize ? "short" : "int", longToShortField.getField().getType().getTypeName());

              FieldSubject longToCharField =
                  mainClass.uniqueFieldWithOriginalName("longToCharField");
              assertThat(longToCharField, isPresent());
              assertEquals(
                  optimize ? "char" : "int", longToCharField.getField().getType().getTypeName());

              FieldSubject longToIntField = mainClass.uniqueFieldWithOriginalName("longToIntField");
              assertThat(longToIntField, isPresent());
              assertEquals("int", longToIntField.getField().getType().getTypeName());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "[1]",
            "[\u0001, \u007F]",
            "[1, 127]",
            "[1, 127, 32767, 65535]",
            "[1, 127, 32767, 65535, 2147483647]");
  }

  static class Main {

    // Byte field.
    static byte byteToBooleanField;

    // Char fields.
    static char charToBooleanField;
    static char charToByteField;

    // Short fields.
    static short shortToBooleanField;
    static short shortToByteField;

    // Int fields.
    static int intToBooleanField;
    static int intToByteField;
    static int intToShortField;
    static int intToCharField;

    // Long fields.
    static long longToBooleanField;
    static long longToByteField;
    static long longToShortField;
    static long longToCharField;
    static long longToIntField;

    public static void main(String[] args) {
      // Byte field.
      byteToBooleanField = 1;

      // Char fields.
      charToBooleanField = 1;
      charToByteField = Byte.MAX_VALUE;

      // Short fields.
      shortToBooleanField = 1;
      shortToByteField = Byte.MAX_VALUE;

      // Int fields.
      intToBooleanField = 1;
      intToByteField = Byte.MAX_VALUE;
      intToShortField = Short.MAX_VALUE;
      intToCharField = Character.MAX_VALUE;

      // Long fields.
      longToBooleanField = 1;
      longToByteField = Byte.MAX_VALUE;
      longToShortField = Short.MAX_VALUE;
      longToCharField = Character.MAX_VALUE;
      longToIntField = Integer.MAX_VALUE;

      // Use fields.
      readFieldsIntoArrays();
      readFieldsIntoInvokes();
      readFieldsIntoReturns();
    }

    @NeverInline
    static void readFieldsIntoArrays() {
      // Byte fields.
      System.out.println(Arrays.toString(new byte[] {byteToBooleanField}));

      // Char fields.
      System.out.println(Arrays.toString(new char[] {charToBooleanField, charToByteField}));

      // Short fields.
      System.out.println(Arrays.toString(new short[] {shortToBooleanField, shortToByteField}));

      // Int fields.
      System.out.println(
          Arrays.toString(
              new int[] {intToBooleanField, intToByteField, intToShortField, intToCharField}));

      // Long fields.
      System.out.println(
          Arrays.toString(
              new long[] {
                longToBooleanField,
                longToByteField,
                longToShortField,
                longToCharField,
                longToIntField
              }));
    }

    @NeverInline
    static void readFieldsIntoInvokes() {
      Sinks.byteSink(byteToBooleanField);
      Sinks.charSink(charToBooleanField);
      Sinks.charSink(charToByteField);
      Sinks.shortSink(shortToBooleanField);
      Sinks.shortSink(shortToByteField);
      Sinks.intSink(intToBooleanField);
      Sinks.intSink(intToByteField);
      Sinks.intSink(intToShortField);
      Sinks.intSink(intToCharField);
      Sinks.longSink(longToBooleanField);
      Sinks.longSink(longToByteField);
      Sinks.longSink(longToShortField);
      Sinks.longSink(longToCharField);
      Sinks.longSink(longToIntField);
    }

    @NeverInline
    static void readFieldsIntoReturns() {
      Sinks.byteSink(returnByteToBooleanField());
      Sinks.charSink(returnCharToBooleanField());
      Sinks.charSink(returnCharToByteField());
      Sinks.shortSink(returnShortToBooleanField());
      Sinks.shortSink(returnShortToByteField());
      Sinks.intSink(returnIntToBooleanField());
      Sinks.intSink(returnIntToByteField());
      Sinks.intSink(returnIntToShortField());
      Sinks.intSink(returnIntToCharField());
      Sinks.longSink(returnLongToBooleanField());
      Sinks.longSink(returnLongToByteField());
      Sinks.longSink(returnLongToShortField());
      Sinks.longSink(returnLongToCharField());
      Sinks.longSink(returnLongToIntField());
    }

    static byte returnByteToBooleanField() {
      return byteToBooleanField;
    }

    static char returnCharToBooleanField() {
      return charToBooleanField;
    }

    static char returnCharToByteField() {
      return charToByteField;
    }

    static short returnShortToBooleanField() {
      return shortToBooleanField;
    }

    static short returnShortToByteField() {
      return shortToByteField;
    }

    static int returnIntToBooleanField() {
      return intToBooleanField;
    }

    static int returnIntToByteField() {
      return intToByteField;
    }

    static int returnIntToShortField() {
      return intToShortField;
    }

    static int returnIntToCharField() {
      return intToCharField;
    }

    static long returnLongToBooleanField() {
      return longToBooleanField;
    }

    static long returnLongToByteField() {
      return longToByteField;
    }

    static long returnLongToShortField() {
      return longToShortField;
    }

    static long returnLongToCharField() {
      return longToCharField;
    }

    static long returnLongToIntField() {
      return longToIntField;
    }
  }

  static class Sinks {

    static void byteSink(byte b) {}

    static void charSink(char c) {}

    static void shortSink(short s) {}

    static void intSink(int i) {}

    static void longSink(long l) {}
  }
}

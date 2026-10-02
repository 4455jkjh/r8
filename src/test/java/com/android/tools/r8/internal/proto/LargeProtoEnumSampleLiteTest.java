// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This large enum is a copy of a sample presented with the design doc for proto lite.
@RunWith(Parameterized.class)
public class LargeProtoEnumSampleLiteTest extends LargeProtoEnumTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String[] EXPECTED_OUTPUT =
      new String[] {
        "values: 77",
        "valueOf(VALUE_0): VALUE_0",
        "forNumber(50): VALUE_1",
        "internalMap(1058): VALUE_42",
        "internalGetVerifier(50): true",
        "internalGetVerifier(99999): false",
        "VALUE_0.getNumber: 0",
        "VALUE_0.index: 0",
        "VALUE_0.value: 0",
        "VALUE_0.name: VALUE_0",
        "VALUE_0.getName: VALUE_0",
        "VALUE_0.ordinal: 0",
        "VALUE_0.toString: VALUE_0",
        "VALUE_40.getNumber: 516"
      };

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClassFileData(
            getProgramClassFileDataForProtoLite(LargeProtoEnumSampleLiteTest.class))
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            getProgramClassFileDataForProtoLite(LargeProtoEnumSampleLiteTest.class))
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class " + LogType.class.getTypeName() + " { *; }",
            "-keep class com.google.protobuf.** { *; }")
        .enableProtoShrinking()
        .setMinApi(parameters)
        .allowDiagnosticWarningMessages()
        .compile()
        .inspect(this::inspectDex)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8WithoutProtoShrinking() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            getProgramClassFileDataForProtoLite(LargeProtoEnumSampleLiteTest.class))
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class " + LogType.class.getTypeName() + " { *; }",
            "-keep class " + LogType0.class.getTypeName() + " { *; }",
            "-keep class " + LogType1.class.getTypeName() + " { *; }",
            "-keep class com.google.protobuf.** { *; }")
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspectD8(CodeInspector inspector) {
    ClassSubject enumSubject = inspector.clazz(LogType.class);
    assertThat(enumSubject, isPresent());
    assertFalse(enumSubject.getDexProgramClass().isEnum());
    assertEquals(
        Object.class.getTypeName(),
        enumSubject.getDexProgramClass().getSuperType().toSourceString());
    assertThat(inspector.clazz(LogType0.class), isPresent());
    assertThat(inspector.clazz(LogType1.class), isPresent());
  }

  private void inspectDex(CodeInspector inspector) {
    ClassSubject enumSubject = inspector.clazz(LogType.class);
    assertThat(enumSubject, isPresent());
    assertTrue(enumSubject.getDexProgramClass().isEnum());
    assertEquals(
        Enum.class.getTypeName(), enumSubject.getDexProgramClass().getSuperType().toSourceString());
    assertThat(inspector.clazz(LogType0.class), isAbsent());
    assertThat(inspector.clazz(LogType1.class), isAbsent());
  }

  private void inspectCf(CodeInspector inspector) {
    ClassSubject enumSubject = inspector.clazz(LogType.class);
    assertThat(enumSubject, isPresent());
    assertFalse(enumSubject.getDexProgramClass().isEnum());
    assertEquals(
        Object.class.getTypeName(),
        enumSubject.getDexProgramClass().getSuperType().toSourceString());
    assertThat(inspector.clazz(LogType0.class), isPresent());
    assertThat(inspector.clazz(LogType1.class), isPresent());
  }

  public interface LogType0 {
    LogType VALUE_0 = new LogType(0, 0, "VALUE_0");
    int VALUE_0_VALUE = 0;
    LogType VALUE_1 = new LogType(50, 1, "VALUE_1");
    int VALUE_1_VALUE = 50;
    LogType VALUE_2 = new LogType(383, 2, "VALUE_2");
    int VALUE_2_VALUE = 383;
    LogType VALUE_3 = new LogType(1307, 3, "VALUE_3");
    int VALUE_3_VALUE = 1307;
    LogType VALUE_4 = new LogType(11, 4, "VALUE_4");
    int VALUE_4_VALUE = 11;
    LogType VALUE_5 = new LogType(298, 5, "VALUE_5");
    int VALUE_5_VALUE = 298;
    LogType VALUE_6 = new LogType(174, 6, "VALUE_6");
    int VALUE_6_VALUE = 174;
    LogType VALUE_7 = new LogType(3, 7, "VALUE_7");
    int VALUE_7_VALUE = 3;
    LogType VALUE_8 = new LogType(30, 8, "VALUE_8");
    int VALUE_8_VALUE = 30;
    LogType VALUE_9 = new LogType(98, 9, "VALUE_9");
    int VALUE_9_VALUE = 98;
    LogType VALUE_10 = new LogType(99, 10, "VALUE_10");
    int VALUE_10_VALUE = 99;
    LogType VALUE_11 = new LogType(1800, 11, "VALUE_11");
    int VALUE_11_VALUE = 1800;
    LogType VALUE_12 = new LogType(1, 12, "VALUE_12");
    int VALUE_12_VALUE = 1;
    LogType VALUE_13 = new LogType(440, 13, "VALUE_13");
    int VALUE_13_VALUE = 440;
    LogType VALUE_14 = new LogType(402, 14, "VALUE_14");
    int VALUE_14_VALUE = 402;
    LogType VALUE_15 = new LogType(287, 15, "VALUE_15");
    int VALUE_15_VALUE = 287;
    LogType VALUE_16 = new LogType(5, 16, "VALUE_16");
    int VALUE_16_VALUE = 5;
    LogType VALUE_17 = new LogType(12, 17, "VALUE_17");
    int VALUE_17_VALUE = 12;
    LogType VALUE_18 = new LogType(8, 18, "VALUE_18");
    int VALUE_18_VALUE = 8;
    LogType VALUE_19 = new LogType(10, 19, "VALUE_19");
    int VALUE_19_VALUE = 10;
    LogType VALUE_20 = new LogType(299, 20, "VALUE_20");
    int VALUE_20_VALUE = 299;
    LogType VALUE_21 = new LogType(274, 21, "VALUE_21");
    int VALUE_21_VALUE = 274;
    LogType VALUE_22 = new LogType(1698, 22, "VALUE_22");
    int VALUE_22_VALUE = 1698;
    LogType VALUE_23 = new LogType(9, 23, "VALUE_23");
    int VALUE_23_VALUE = 9;
    LogType VALUE_24 = new LogType(1288, 24, "VALUE_24");
    int VALUE_24_VALUE = 1288;
    LogType VALUE_25 = new LogType(1662, 25, "VALUE_25");
    int VALUE_25_VALUE = 1662;
    LogType VALUE_26 = new LogType(790, 26, "VALUE_26");
    int VALUE_26_VALUE = 790;
    LogType VALUE_27 = new LogType(4, 27, "VALUE_27");
    int VALUE_27_VALUE = 4;
    LogType VALUE_28 = new LogType(116, 28, "VALUE_28");
    int VALUE_28_VALUE = 116;
    LogType VALUE_29 = new LogType(948, 29, "VALUE_29");
    int VALUE_29_VALUE = 948;
    LogType VALUE_30 = new LogType(487, 30, "VALUE_30");
    int VALUE_30_VALUE = 487;
    LogType VALUE_31 = new LogType(32, 31, "VALUE_31");
    int VALUE_31_VALUE = 32;
    LogType VALUE_32 = new LogType(31, 32, "VALUE_32");
    int VALUE_32_VALUE = 31;
    LogType VALUE_33 = new LogType(1028, 33, "VALUE_33");
    int VALUE_33_VALUE = 1028;
    LogType VALUE_34 = new LogType(2, 34, "VALUE_34");
    int VALUE_34_VALUE = 2;
    LogType VALUE_35 = new LogType(326, 35, "VALUE_35");
    int VALUE_35_VALUE = 326;
    LogType VALUE_36 = new LogType(612, 36, "VALUE_36");
    int VALUE_36_VALUE = 612;
    LogType VALUE_37 = new LogType(1413, 37, "VALUE_37");
    int VALUE_37_VALUE = 1413;
    LogType VALUE_38 = new LogType(496, 38, "VALUE_38");
    int VALUE_38_VALUE = 496;
    LogType VALUE_39 = new LogType(6, 39, "VALUE_39");
    int VALUE_39_VALUE = 6;

    static LogType valueOf0(String name) {
      switch (name) {
        case "VALUE_0":
          return VALUE_0;
        case "VALUE_1":
          return VALUE_1;
        case "VALUE_2":
          return VALUE_2;
        case "VALUE_3":
          return VALUE_3;
        case "VALUE_4":
          return VALUE_4;
        case "VALUE_5":
          return VALUE_5;
        case "VALUE_6":
          return VALUE_6;
        case "VALUE_7":
          return VALUE_7;
        case "VALUE_8":
          return VALUE_8;
        case "VALUE_9":
          return VALUE_9;
        case "VALUE_10":
          return VALUE_10;
        case "VALUE_11":
          return VALUE_11;
        case "VALUE_12":
          return VALUE_12;
        case "VALUE_13":
          return VALUE_13;
        case "VALUE_14":
          return VALUE_14;
        case "VALUE_15":
          return VALUE_15;
        case "VALUE_16":
          return VALUE_16;
        case "VALUE_17":
          return VALUE_17;
        case "VALUE_18":
          return VALUE_18;
        case "VALUE_19":
          return VALUE_19;
        case "VALUE_20":
          return VALUE_20;
        case "VALUE_21":
          return VALUE_21;
        case "VALUE_22":
          return VALUE_22;
        case "VALUE_23":
          return VALUE_23;
        case "VALUE_24":
          return VALUE_24;
        case "VALUE_25":
          return VALUE_25;
        case "VALUE_26":
          return VALUE_26;
        case "VALUE_27":
          return VALUE_27;
        case "VALUE_28":
          return VALUE_28;
        case "VALUE_29":
          return VALUE_29;
        case "VALUE_30":
          return VALUE_30;
        case "VALUE_31":
          return VALUE_31;
        case "VALUE_32":
          return VALUE_32;
        case "VALUE_33":
          return VALUE_33;
        case "VALUE_34":
          return VALUE_34;
        case "VALUE_35":
          return VALUE_35;
        case "VALUE_36":
          return VALUE_36;
        case "VALUE_37":
          return VALUE_37;
        case "VALUE_38":
          return VALUE_38;
        case "VALUE_39":
          return VALUE_39;
        default:
          return null;
      }
    }

    static LogType forNumber0(int value) {
      switch (value) {
        case 0:
          return VALUE_0;
        case 50:
          return VALUE_1;
        case 383:
          return VALUE_2;
        case 1307:
          return VALUE_3;
        case 11:
          return VALUE_4;
        case 298:
          return VALUE_5;
        case 174:
          return VALUE_6;
        case 3:
          return VALUE_7;
        case 30:
          return VALUE_8;
        case 98:
          return VALUE_9;
        case 99:
          return VALUE_10;
        case 1800:
          return VALUE_11;
        case 1:
          return VALUE_12;
        case 440:
          return VALUE_13;
        case 402:
          return VALUE_14;
        case 287:
          return VALUE_15;
        case 5:
          return VALUE_16;
        case 12:
          return VALUE_17;
        case 8:
          return VALUE_18;
        case 10:
          return VALUE_19;
        case 299:
          return VALUE_20;
        case 274:
          return VALUE_21;
        case 1698:
          return VALUE_22;
        case 9:
          return VALUE_23;
        case 1288:
          return VALUE_24;
        case 1662:
          return VALUE_25;
        case 790:
          return VALUE_26;
        case 4:
          return VALUE_27;
        case 116:
          return VALUE_28;
        case 948:
          return VALUE_29;
        case 487:
          return VALUE_30;
        case 32:
          return VALUE_31;
        case 31:
          return VALUE_32;
        case 1028:
          return VALUE_33;
        case 2:
          return VALUE_34;
        case 326:
          return VALUE_35;
        case 612:
          return VALUE_36;
        case 1413:
          return VALUE_37;
        case 496:
          return VALUE_38;
        case 6:
          return VALUE_39;
        default:
          return null;
      }
    }

    static LogType[] values0() {
      return new LogType[] {
        VALUE_0, VALUE_1, VALUE_2, VALUE_3, VALUE_4, VALUE_5, VALUE_6, VALUE_7, VALUE_8, VALUE_9,
        VALUE_10, VALUE_11, VALUE_12, VALUE_13, VALUE_14, VALUE_15, VALUE_16, VALUE_17, VALUE_18,
            VALUE_19,
        VALUE_20, VALUE_21, VALUE_22, VALUE_23, VALUE_24, VALUE_25, VALUE_26, VALUE_27, VALUE_28,
            VALUE_29,
        VALUE_30, VALUE_31, VALUE_32, VALUE_33, VALUE_34, VALUE_35, VALUE_36, VALUE_37, VALUE_38,
            VALUE_39
      };
    }
  }

  public interface LogType1 {
    LogType VALUE_40 = new LogType(516, 40, "VALUE_40");
    int VALUE_40_VALUE = 516;
    LogType VALUE_41 = new LogType(60, 41, "VALUE_41");
    int VALUE_41_VALUE = 60;
    LogType VALUE_42 = new LogType(1058, 42, "VALUE_42");
    int VALUE_42_VALUE = 1058;
    LogType VALUE_43 = new LogType(1848, 43, "VALUE_43");
    int VALUE_43_VALUE = 1848;
    LogType VALUE_44 = new LogType(375, 44, "VALUE_44");
    int VALUE_44_VALUE = 375;
    LogType VALUE_45 = new LogType(17, 45, "VALUE_45");
    int VALUE_45_VALUE = 17;
    LogType VALUE_46 = new LogType(166, 46, "VALUE_46");
    int VALUE_46_VALUE = 166;
    LogType VALUE_47 = new LogType(7, 47, "VALUE_47");
    int VALUE_47_VALUE = 7;
    LogType VALUE_48 = new LogType(512, 48, "VALUE_48");
    int VALUE_48_VALUE = 512;
    LogType VALUE_49 = new LogType(407, 49, "VALUE_49");
    int VALUE_49_VALUE = 407;
    LogType VALUE_50 = new LogType(242, 50, "VALUE_50");
    int VALUE_50_VALUE = 242;
    LogType VALUE_51 = new LogType(431, 51, "VALUE_51");
    int VALUE_51_VALUE = 431;
    LogType VALUE_52 = new LogType(706, 52, "VALUE_52");
    int VALUE_52_VALUE = 706;
    LogType VALUE_53 = new LogType(13, 53, "VALUE_53");
    int VALUE_53_VALUE = 13;
    LogType VALUE_54 = new LogType(37, 54, "VALUE_54");
    int VALUE_54_VALUE = 37;
    LogType VALUE_55 = new LogType(301, 55, "VALUE_55");
    int VALUE_55_VALUE = 301;
    LogType VALUE_56 = new LogType(310, 56, "VALUE_56");
    int VALUE_56_VALUE = 310;
    LogType VALUE_57 = new LogType(1089, 57, "VALUE_57");
    int VALUE_57_VALUE = 1089;
    LogType VALUE_58 = new LogType(1423, 58, "VALUE_58");
    int VALUE_58_VALUE = 1423;
    LogType VALUE_59 = new LogType(1717, 59, "VALUE_59");
    int VALUE_59_VALUE = 1717;
    LogType VALUE_60 = new LogType(2026, 60, "VALUE_60");
    int VALUE_60_VALUE = 2026;
    LogType VALUE_61 = new LogType(61, 61, "VALUE_61");
    int VALUE_61_VALUE = 61;
    LogType VALUE_62 = new LogType(40, 62, "VALUE_62");
    int VALUE_62_VALUE = 40;
    LogType VALUE_63 = new LogType(1939, 63, "VALUE_63");
    int VALUE_63_VALUE = 1939;
    LogType VALUE_64 = new LogType(296, 64, "VALUE_64");
    int VALUE_64_VALUE = 296;
    LogType VALUE_65 = new LogType(14, 65, "VALUE_65");
    int VALUE_65_VALUE = 14;
    LogType VALUE_66 = new LogType(49, 66, "VALUE_66");
    int VALUE_66_VALUE = 49;
    LogType VALUE_67 = new LogType(269, 67, "VALUE_67");
    int VALUE_67_VALUE = 269;
    LogType VALUE_68 = new LogType(212, 68, "VALUE_68");
    int VALUE_68_VALUE = 212;
    LogType VALUE_69 = new LogType(319, 69, "VALUE_69");
    int VALUE_69_VALUE = 319;
    LogType VALUE_70 = new LogType(1632, 70, "VALUE_70");
    int VALUE_70_VALUE = 1632;
    LogType VALUE_71 = new LogType(90, 71, "VALUE_71");
    int VALUE_71_VALUE = 90;
    LogType VALUE_72 = new LogType(527, 72, "VALUE_72");
    int VALUE_72_VALUE = 527;
    LogType VALUE_73 = new LogType(277, 73, "VALUE_73");
    int VALUE_73_VALUE = 277;
    LogType VALUE_74 = new LogType(95, 74, "VALUE_74");
    int VALUE_74_VALUE = 95;
    LogType VALUE_75 = new LogType(96, 75, "VALUE_75");
    int VALUE_75_VALUE = 96;

    static LogType valueOf1(String name) {
      switch (name) {
        case "VALUE_40":
          return VALUE_40;
        case "VALUE_41":
          return VALUE_41;
        case "VALUE_42":
          return VALUE_42;
        case "VALUE_43":
          return VALUE_43;
        case "VALUE_44":
          return VALUE_44;
        case "VALUE_45":
          return VALUE_45;
        case "VALUE_46":
          return VALUE_46;
        case "VALUE_47":
          return VALUE_47;
        case "VALUE_48":
          return VALUE_48;
        case "VALUE_49":
          return VALUE_49;
        case "VALUE_50":
          return VALUE_50;
        case "VALUE_51":
          return VALUE_51;
        case "VALUE_52":
          return VALUE_52;
        case "VALUE_53":
          return VALUE_53;
        case "VALUE_54":
          return VALUE_54;
        case "VALUE_55":
          return VALUE_55;
        case "VALUE_56":
          return VALUE_56;
        case "VALUE_57":
          return VALUE_57;
        case "VALUE_58":
          return VALUE_58;
        case "VALUE_59":
          return VALUE_59;
        case "VALUE_60":
          return VALUE_60;
        case "VALUE_61":
          return VALUE_61;
        case "VALUE_62":
          return VALUE_62;
        case "VALUE_63":
          return VALUE_63;
        case "VALUE_64":
          return VALUE_64;
        case "VALUE_65":
          return VALUE_65;
        case "VALUE_66":
          return VALUE_66;
        case "VALUE_67":
          return VALUE_67;
        case "VALUE_68":
          return VALUE_68;
        case "VALUE_69":
          return VALUE_69;
        case "VALUE_70":
          return VALUE_70;
        case "VALUE_71":
          return VALUE_71;
        case "VALUE_72":
          return VALUE_72;
        case "VALUE_73":
          return VALUE_73;
        case "VALUE_74":
          return VALUE_74;
        case "VALUE_75":
          return VALUE_75;
        default:
          return null;
      }
    }

    static LogType forNumber1(int value) {
      switch (value) {
        case 516:
          return VALUE_40;
        case 60:
          return VALUE_41;
        case 1058:
          return VALUE_42;
        case 1848:
          return VALUE_43;
        case 375:
          return VALUE_44;
        case 17:
          return VALUE_45;
        case 166:
          return VALUE_46;
        case 7:
          return VALUE_47;
        case 512:
          return VALUE_48;
        case 407:
          return VALUE_49;
        case 242:
          return VALUE_50;
        case 431:
          return VALUE_51;
        case 706:
          return VALUE_52;
        case 13:
          return VALUE_53;
        case 37:
          return VALUE_54;
        case 301:
          return VALUE_55;
        case 310:
          return VALUE_56;
        case 1089:
          return VALUE_57;
        case 1423:
          return VALUE_58;
        case 1717:
          return VALUE_59;
        case 2026:
          return VALUE_60;
        case 61:
          return VALUE_61;
        case 40:
          return VALUE_62;
        case 1939:
          return VALUE_63;
        case 296:
          return VALUE_64;
        case 14:
          return VALUE_65;
        case 49:
          return VALUE_66;
        case 269:
          return VALUE_67;
        case 212:
          return VALUE_68;
        case 319:
          return VALUE_69;
        case 1632:
          return VALUE_70;
        case 90:
          return VALUE_71;
        case 527:
          return VALUE_72;
        case 277:
          return VALUE_73;
        case 95:
          return VALUE_74;
        case 96:
          return VALUE_75;
        default:
          return null;
      }
    }

    static LogType[] values1() {
      return new LogType[] {
        VALUE_40, VALUE_41, VALUE_42, VALUE_43, VALUE_44, VALUE_45, VALUE_46, VALUE_47, VALUE_48,
            VALUE_49,
        VALUE_50, VALUE_51, VALUE_52, VALUE_53, VALUE_54, VALUE_55, VALUE_56, VALUE_57, VALUE_58,
            VALUE_59,
        VALUE_60, VALUE_61, VALUE_62, VALUE_63, VALUE_64, VALUE_65, VALUE_66, VALUE_67, VALUE_68,
            VALUE_69,
        VALUE_70, VALUE_71, VALUE_72, VALUE_73, VALUE_74, VALUE_75
      };
    }
  }

  public static class LogType
      implements Internal.EnumLite, java.io.Serializable, LogType0, LogType1 {
    public static final LogType UNRECOGNIZED = new LogType(-1, 76, "UNRECOGNIZED");

    public final int getNumber() {
      if (this == UNRECOGNIZED) {
        throw new IllegalArgumentException("Can't get the number of an unknown enum value.");
      }
      return value;
    }

    public static LogType forNumber(int value) {
      if (LogType0.forNumber0(value) != null) {
        return LogType0.forNumber0(value);
      }
      if (LogType1.forNumber1(value) != null) {
        return LogType1.forNumber1(value);
      }
      return null;
    }

    public static LogType valueOf(String name) {
      if (LogType0.valueOf0(name) != null) {
        return LogType0.valueOf0(name);
      }
      if (LogType1.valueOf1(name) != null) {
        return LogType1.valueOf1(name);
      }
      throw new IllegalArgumentException("No enum constant ." + name);
    }

    public static LogType[] values() {
      int ordinal = 0;
      LogType[] values = new LogType[77];
      LogType[] values0 = LogType0.values0();
      System.arraycopy(values0, 0, values, ordinal, values0.length);
      ordinal += values0.length;
      LogType[] values1 = LogType1.values1();
      System.arraycopy(values1, 0, values, ordinal, values1.length);
      ordinal += values1.length;
      values[76] = UNRECOGNIZED;
      return values;
    }

    public static Internal.EnumLiteMap<LogType> internalGetValueMap() {
      return internalValueMap;
    }

    private static final Internal.EnumLiteMap<LogType> internalValueMap =
        new Internal.EnumLiteMap<LogType>() {
          public LogType findValueByNumber(int number) {
            return LogType.forNumber(number);
          }
        };

    public static Internal.EnumVerifier internalGetVerifier() {
      return LogTypeVerifier.INSTANCE;
    }

    private static final class LogTypeVerifier implements Internal.EnumVerifier {
      static final Internal.EnumVerifier INSTANCE = new LogTypeVerifier();

      @Override
      public boolean isInRange(int number) {
        return LogType.forNumber(number) != null;
      }
    }

    private final int value;
    private final String name;
    private final int ordinal;

    LogType(int v, int o, String n) {
      this.value = v;
      this.ordinal = o;
      this.name = n;
    }

    public int index() {
      return ordinal;
    }

    public int ordinal() {
      return ordinal;
    }

    public int value() {
      return value;
    }

    public String name() {
      return name;
    }

    public String getName() {
      return name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public static class Main {
    public static void main(String[] args) {
      System.out.println("values: " + LogType.values().length);
      System.out.println("valueOf(VALUE_0): " + LogType.valueOf("VALUE_0").name());
      System.out.println("forNumber(50): " + LogType.forNumber(50).name());
      System.out.println(
          "internalMap(1058): " + LogType.internalGetValueMap().findValueByNumber(1058).name());
      System.out.println("internalGetVerifier(50): " + LogType.internalGetVerifier().isInRange(50));
      System.out.println(
          "internalGetVerifier(99999): " + LogType.internalGetVerifier().isInRange(99999));
      System.out.println("VALUE_0.getNumber: " + LogType.VALUE_0.getNumber());
      System.out.println("VALUE_0.index: " + LogType.VALUE_0.index());
      System.out.println("VALUE_0.value: " + LogType.VALUE_0.value());
      System.out.println("VALUE_0.name: " + LogType.VALUE_0.name());
      System.out.println("VALUE_0.getName: " + LogType.VALUE_0.getName());
      System.out.println("VALUE_0.ordinal: " + LogType.VALUE_0.ordinal());
      System.out.println("VALUE_0.toString: " + LogType.VALUE_0);
      System.out.println("VALUE_40.getNumber: " + LogType.VALUE_40.getNumber());
    }
  }
}

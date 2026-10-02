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

@RunWith(Parameterized.class)
public class LargeProtoEnumAliasLiteTest extends LargeProtoEnumTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String[] EXPECTED_OUTPUT =
      new String[] {
        "values: 4",
        "valueOf(FOO): FOO",
        "valueOf(BAR): BAR",
        "valueOf(QUX): QUX",
        "BAZ == BAR: true",
        "QUUX == QUX: true",
        "BAZ.name: BAR",
        "QUUX.name: QUX",
        "forNumber(1): FOO",
        "forNumber(2): BAR",
        "forNumber(3): QUX",
        "internalMap(2): BAR",
        "internalGetVerifier(1): true",
        "internalGetVerifier(99): false",
        "FOO.getNumber: 1",
        "FOO.index: 0",
        "FOO.value: 1",
        "FOO.name: FOO",
        "FOO.getName: FOO",
        "FOO.ordinal: 0",
        "FOO.toString: FOO",
        "BAZ.getNumber: 2",
        "QUUX.getNumber: 3",
        "BAZ_VALUE: 2",
        "QUUX_VALUE: 3"
      };

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClassFileData(
            getProgramClassFileDataForProtoLite(LargeProtoEnumAliasLiteTest.class))
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
            getProgramClassFileDataForProtoLite(LargeProtoEnumAliasLiteTest.class))
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class " + MyEnum.class.getTypeName() + " { *; }",
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
            getProgramClassFileDataForProtoLite(LargeProtoEnumAliasLiteTest.class))
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class " + MyEnum.class.getTypeName() + " { *; }",
            "-keep class " + MyEnum0.class.getTypeName() + " { *; }",
            "-keep class " + MyEnum1.class.getTypeName() + " { *; }",
            "-keep class com.google.protobuf.** { *; }")
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspectD8(CodeInspector inspector) {
    ClassSubject enumSubject = inspector.clazz(MyEnum.class);
    assertThat(enumSubject, isPresent());
    assertFalse(enumSubject.getDexProgramClass().isEnum());
    assertEquals(
        Object.class.getTypeName(),
        enumSubject.getDexProgramClass().getSuperType().toSourceString());
    assertThat(inspector.clazz(MyEnum0.class), isPresent());
    assertThat(inspector.clazz(MyEnum1.class), isPresent());
  }

  private void inspectDex(CodeInspector inspector) {
    ClassSubject enumSubject = inspector.clazz(MyEnum.class);
    assertThat(enumSubject, isPresent());
    assertTrue(enumSubject.getDexProgramClass().isEnum());
    assertEquals(
        Enum.class.getTypeName(), enumSubject.getDexProgramClass().getSuperType().toSourceString());
    assertThat(inspector.clazz(MyEnum0.class), isAbsent());
    assertThat(inspector.clazz(MyEnum1.class), isAbsent());
  }

  // Large enum simulated structure generated by protoc for proto-lite with aliases:
  public interface MyEnum0 {
    MyEnum FOO = new MyEnum(1, 0, "FOO");
    int FOO_VALUE = 1;
    MyEnum BAR = new MyEnum(2, 1, "BAR");
    int BAR_VALUE = 2;
    // Alias for BAR (same wire value 2):
    MyEnum BAZ = BAR;
    int BAZ_VALUE = 2;

    static MyEnum forNumber0(int value) {
      switch (value) {
        case 1:
          return FOO;
        case 2:
          return BAR;
        default:
          return null;
      }
    }

    static MyEnum valueOf0(String name) {
      switch (name) {
        case "FOO":
          return FOO;
        case "BAR":
          return BAR;
        default:
          return null;
      }
    }

    static MyEnum[] values0() {
      return new MyEnum[] {FOO, BAR};
    }
  }

  public interface MyEnum1 {
    MyEnum QUX = new MyEnum(3, 2, "QUX");
    int QUX_VALUE = 3;
    // Alias for QUX (same wire value 3):
    MyEnum QUUX = QUX;
    int QUUX_VALUE = 3;

    static MyEnum forNumber1(int value) {
      switch (value) {
        case 3:
          return QUX;
        default:
          return null;
      }
    }

    static MyEnum valueOf1(String name) {
      switch (name) {
        case "QUX":
          return QUX;
        default:
          return null;
      }
    }

    static MyEnum[] values1() {
      return new MyEnum[] {QUX};
    }
  }

  public static final class MyEnum
      implements Internal.EnumLite, java.io.Serializable, MyEnum0, MyEnum1 {
    public static final MyEnum UNRECOGNIZED = new MyEnum(-1, 3, "UNRECOGNIZED");

    public final int getNumber() {
      if (this == UNRECOGNIZED) {
        throw new IllegalArgumentException("Can't get the number of an unknown enum value.");
      }
      return value;
    }

    public static MyEnum forNumber(int value) {
      MyEnum found = MyEnum0.forNumber0(value);
      if (found != null) {
        return found;
      }
      found = MyEnum1.forNumber1(value);
      if (found != null) {
        return found;
      }
      return null;
    }

    public static MyEnum valueOf(String name) {
      MyEnum found = MyEnum0.valueOf0(name);
      if (found != null) {
        return found;
      }
      found = MyEnum1.valueOf1(name);
      if (found != null) {
        return found;
      }
      throw new IllegalArgumentException("No enum constant MyEnum." + name);
    }

    public static MyEnum[] values() {
      int ordinal = 0;
      MyEnum[] values = new MyEnum[4];
      MyEnum[] values0 = MyEnum0.values0();
      System.arraycopy(values0, 0, values, ordinal, values0.length);
      ordinal += values0.length;
      MyEnum[] values1 = MyEnum1.values1();
      System.arraycopy(values1, 0, values, ordinal, values1.length);
      ordinal += values1.length;
      values[3] = UNRECOGNIZED;
      return values;
    }

    private static final Internal.EnumLiteMap<MyEnum> internalValueMap =
        new Internal.EnumLiteMap<MyEnum>() {
          public MyEnum findValueByNumber(int number) {
            return MyEnum.forNumber(number);
          }
        };

    public static Internal.EnumLiteMap<MyEnum> internalGetValueMap() {
      return internalValueMap;
    }

    public static Internal.EnumVerifier internalGetVerifier() {
      return MyEnumVerifier.INSTANCE;
    }

    private static final class MyEnumVerifier implements Internal.EnumVerifier {
      static final Internal.EnumVerifier INSTANCE = new MyEnumVerifier();

      @Override
      public boolean isInRange(int number) {
        return MyEnum.forNumber(number) != null;
      }
    }

    private final int value;
    private final String name;
    private final int index;

    MyEnum(int v, int i, String n) {
      this.value = v;
      this.index = i;
      this.name = n;
    }

    public int index() {
      return index;
    }

    public int ordinal() {
      return index;
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
      System.out.println("values: " + MyEnum.values().length);
      System.out.println("valueOf(FOO): " + MyEnum.valueOf("FOO").name());
      System.out.println("valueOf(BAR): " + MyEnum.valueOf("BAR").name());
      System.out.println("valueOf(QUX): " + MyEnum.valueOf("QUX").name());
      System.out.println("BAZ == BAR: " + (MyEnum0.BAZ == MyEnum0.BAR));
      System.out.println("QUUX == QUX: " + (MyEnum1.QUUX == MyEnum1.QUX));
      System.out.println("BAZ.name: " + MyEnum0.BAZ.name());
      System.out.println("QUUX.name: " + MyEnum1.QUUX.name());
      System.out.println("forNumber(1): " + MyEnum.forNumber(1).name());
      System.out.println("forNumber(2): " + MyEnum.forNumber(2).name());
      System.out.println("forNumber(3): " + MyEnum.forNumber(3).name());
      System.out.println(
          "internalMap(2): " + MyEnum.internalGetValueMap().findValueByNumber(2).name());
      System.out.println("internalGetVerifier(1): " + MyEnum.internalGetVerifier().isInRange(1));
      System.out.println("internalGetVerifier(99): " + MyEnum.internalGetVerifier().isInRange(99));
      System.out.println("FOO.getNumber: " + MyEnum.FOO.getNumber());
      System.out.println("FOO.index: " + MyEnum.FOO.index());
      System.out.println("FOO.value: " + MyEnum.FOO.value());
      System.out.println("FOO.name: " + MyEnum.FOO.name());
      System.out.println("FOO.getName: " + MyEnum.FOO.getName());
      System.out.println("FOO.ordinal: " + MyEnum.FOO.ordinal());
      System.out.println("FOO.toString: " + MyEnum.FOO.toString());
      System.out.println("BAZ.getNumber: " + MyEnum0.BAZ.getNumber());
      System.out.println("QUUX.getNumber: " + MyEnum1.QUUX.getNumber());
      System.out.println("BAZ_VALUE: " + MyEnum.BAZ_VALUE);
      System.out.println("QUUX_VALUE: " + MyEnum.QUUX_VALUE);
    }
  }
}

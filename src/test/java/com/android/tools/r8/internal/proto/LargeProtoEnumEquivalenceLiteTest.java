// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LargeProtoEnumEquivalenceLiteTest extends LargeProtoEnumTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private static final String[] EXPECTED_OUTPUT =
      new String[] {
        "Large:",
        "values: 4",
        "valueOf(FOO): FOO",
        "forNumber(1): FOO",
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
        "BAZ_VALUE: 3",
        "Normal:",
        "values: 4",
        "valueOf(FOO): FOO",
        "forNumber(1): FOO",
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
        "BAZ_VALUE: 3"
      };

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters.getBackend())
        .addProgramClassFileData(
            getProgramClassFileDataForProtoLite(LargeProtoEnumEquivalenceLiteTest.class))
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
            getProgramClassFileDataForProtoLite(LargeProtoEnumEquivalenceLiteTest.class))
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class " + LargeEnum.class.getTypeName() + " { *; }",
            "-keep class " + NormalEnum.class.getTypeName() + " { *; }",
            "-keep class com.google.protobuf.** { *; }",
            "-dontobfuscate")
        .enableProtoShrinking()
        .setMinApi(parameters)
        .allowDiagnosticWarningMessages()
        .compile()
        .inspect(this::inspectEquivalence)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8WithoutProtoShrinking() throws Exception {
    parameters.assumeDexRuntime();
    testForR8(parameters.getBackend())
        .addProgramClassFileData(
            getProgramClassFileDataForProtoLite(LargeProtoEnumEquivalenceLiteTest.class))
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keep class " + LargeEnum.class.getTypeName() + " { *; }",
            "-keep class " + NormalEnum.class.getTypeName() + " { *; }",
            "-keep class com.google.protobuf.** { *; }",
            "-dontobfuscate")
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspectD8)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private void inspectD8(CodeInspector inspector) {
    assertThat(inspector.clazz(LargeEnum0.class), isPresent());
    assertThat(inspector.clazz(LargeEnum1.class), isPresent());
    ClassSubject largeSubject = inspector.clazz(LargeEnum.class);
    assertThat(largeSubject, isPresent());
    assertFalse(largeSubject.getDexProgramClass().isEnum());
  }

  private void inspectEquivalence(CodeInspector inspector) {
    assertThat(inspector.clazz(LargeEnum0.class), isAbsent());
    assertThat(inspector.clazz(LargeEnum1.class), isAbsent());

    ClassSubject largeSubject = inspector.clazz(LargeEnum.class);
    ClassSubject normalSubject = inspector.clazz(NormalEnum.class);

    assertThat(largeSubject, isPresent());
    assertThat(normalSubject, isPresent());

    DexProgramClass largeClass = largeSubject.getDexProgramClass();
    DexProgramClass normalClass = normalSubject.getDexProgramClass();

    // 1. Class-level properties
    assertTrue(largeClass.isEnum());
    assertTrue(normalClass.isEnum());
    assertEquals(normalClass.getSuperType(), largeClass.getSuperType());
    assertEquals(normalClass.getInterfaces().size(), largeClass.getInterfaces().size());

    // 2. Field comparison (excluding _VALUE constants which are in helper interfaces for large
    // enum)
    List<DexEncodedField> largeFields = new ArrayList<>();
    for (DexEncodedField field : largeClass.fields()) {
      if (!field.getReference().name.toString().endsWith("_VALUE")) {
        largeFields.add(field);
      }
    }
    largeFields.sort(Comparator.comparing(f -> f.getReference().name.toString()));

    List<DexEncodedField> normalFields = new ArrayList<>();
    for (DexEncodedField field : normalClass.fields()) {
      if (!field.getReference().name.toString().endsWith("_VALUE")) {
        normalFields.add(field);
      }
    }
    normalFields.sort(Comparator.comparing(f -> f.getReference().name.toString()));

    assertEquals(normalFields.size(), largeFields.size());
    for (int i = 0; i < normalFields.size(); i++) {
      DexEncodedField normalField = normalFields.get(i);
      DexEncodedField largeField = largeFields.get(i);
      assertEquals(normalField.getReference().name, largeField.getReference().name);
      assertEquals(normalField.isStatic(), largeField.isStatic());
    }

    // 3. Method comparison
    List<DexEncodedMethod> largeMethods = new ArrayList<>();
    largeClass.methods().forEach(largeMethods::add);
    largeMethods.sort(Comparator.comparing(m -> m.getReference().name.toString()));

    List<DexEncodedMethod> normalMethods = new ArrayList<>();
    normalClass.methods().forEach(normalMethods::add);
    normalMethods.sort(Comparator.comparing(m -> m.getReference().name.toString()));

    for (DexEncodedMethod normalMethod : normalMethods) {
      String methodName = normalMethod.getReference().name.toString();
      DexEncodedMethod largeMethod =
          largeClass.lookupMethod(
              m -> {
                if (!m.getReference().name.toString().equals(methodName)) {
                  return false;
                }
                if (m.getParameters().size() != normalMethod.getParameters().size()) {
                  return false;
                }
                for (int p = 0; p < m.getParameters().size(); p++) {
                  String normalParam = normalMethod.getParameters().get(p).toSourceString();
                  String largeParam = m.getParameters().get(p).toSourceString();
                  normalParam = normalParam.replace("NormalEnum", "<ENUM>");
                  largeParam = largeParam.replace("LargeEnum", "<ENUM>");
                  if (!normalParam.equals(largeParam)) {
                    return false;
                  }
                }
                return true;
              });
      assertNotNull("Missing method in LargeEnum: " + methodName, largeMethod);
      assertEquals(normalMethod.isStatic(), largeMethod.isStatic());
      assertEquals(normalMethod.getParameters().size(), largeMethod.getParameters().size());

      // Compare instructions
      if (normalMethod.hasCode() && normalMethod.getCode().isDexCode()) {
        assertTrue(largeMethod.hasCode());
        assertTrue(largeMethod.getCode().isDexCode());
        DexCode normalDexCode = normalMethod.getCode().asDexCode();
        DexCode largeDexCode = largeMethod.getCode().asDexCode();

        List<String> normalInsts = normalizeInstructions(normalDexCode, NormalEnum.class);
        List<String> largeInsts = normalizeInstructions(largeDexCode, LargeEnum.class);
        assertEquals("Instructions mismatch for method " + methodName, normalInsts, largeInsts);
      }
    }
  }

  private List<String> normalizeInstructions(DexCode dexCode, Class<?> enumClass) {
    List<String> result = new ArrayList<>();
    String enumTypeStr = descriptor(enumClass);
    for (DexInstruction inst : dexCode.instructions) {
      String s = inst.toString();
      s = s.replace(enumTypeStr, "L<ENUM>;");
      s = s.replace("LargeEnum", "<ENUM>").replace("NormalEnum", "<ENUM>");
      s = s.replaceAll("INSTANCE\\$\\d+", "INSTANCE");
      s = s.replaceAll("\\b[vp][0-9]+\\b", "v?");
      result.add(s);
    }
    for (int i = 0; i < result.size(); i++) {
      String curr = result.get(i);
      if (i + 1 < result.size() && result.get(i + 1).contains("<init>(B)V")) {
        result.set(
            i,
            curr.replaceAll(
                "Const4\\s+v\\?,\\s+0x[0-9a-fA-F]+\\s+\\(-?[0-9]+\\)", "Const4 v?, 0x?"));
      }
    }
    return result;
  }

  // ---------------- Large Enum definition ----------------
  public interface LargeEnum0 {
    LargeEnum FOO = new LargeEnum(1, 0, "FOO");
    int FOO_VALUE = 1;
    LargeEnum BAR = new LargeEnum(2, 1, "BAR");
    int BAR_VALUE = 2;

    static LargeEnum forNumber0(int value) {
      switch (value) {
        case 1:
          return FOO;
        case 2:
          return BAR;
        default:
          return null;
      }
    }

    static LargeEnum valueOf0(String name) {
      switch (name) {
        case "FOO":
          return FOO;
        case "BAR":
          return BAR;
        default:
          return null;
      }
    }

    static LargeEnum[] values0() {
      return new LargeEnum[] {FOO, BAR};
    }
  }

  public interface LargeEnum1 {
    LargeEnum BAZ = new LargeEnum(3, 2, "BAZ");
    int BAZ_VALUE = 3;

    static LargeEnum forNumber1(int value) {
      switch (value) {
        case 3:
          return BAZ;
        default:
          return null;
      }
    }

    static LargeEnum valueOf1(String name) {
      switch (name) {
        case "BAZ":
          return BAZ;
        default:
          return null;
      }
    }

    static LargeEnum[] values1() {
      return new LargeEnum[] {BAZ};
    }
  }

  public static final class LargeEnum
      implements Internal.EnumLite, java.io.Serializable, LargeEnum0, LargeEnum1 {
    public static final LargeEnum UNRECOGNIZED = new LargeEnum(-1, 3, "UNRECOGNIZED");

    public final int getNumber() {
      if (this == UNRECOGNIZED) {
        throw new IllegalArgumentException("Can't get the number of an unknown enum value.");
      }
      return value;
    }

    public static LargeEnum forNumber(int value) {
      LargeEnum found = LargeEnum0.forNumber0(value);
      if (found != null) {
        return found;
      }
      found = LargeEnum1.forNumber1(value);
      if (found != null) {
        return found;
      }
      return null;
    }

    public static LargeEnum valueOf(String name) {
      LargeEnum found = LargeEnum0.valueOf0(name);
      if (found != null) {
        return found;
      }
      found = LargeEnum1.valueOf1(name);
      if (found != null) {
        return found;
      }
      throw new IllegalArgumentException("No enum constant LargeEnum." + name);
    }

    public static LargeEnum[] values() {
      int ordinal = 0;
      LargeEnum[] values = new LargeEnum[4];
      LargeEnum[] values0 = LargeEnum0.values0();
      System.arraycopy(values0, 0, values, ordinal, values0.length);
      ordinal += values0.length;
      LargeEnum[] values1 = LargeEnum1.values1();
      System.arraycopy(values1, 0, values, ordinal, values1.length);
      ordinal += values1.length;
      values[3] = UNRECOGNIZED;
      return values;
    }

    private static final Internal.EnumLiteMap<LargeEnum> internalValueMap =
        new Internal.EnumLiteMap<LargeEnum>() {
          public LargeEnum findValueByNumber(int number) {
            return LargeEnum.forNumber(number);
          }
        };

    public static Internal.EnumLiteMap<LargeEnum> internalGetValueMap() {
      return internalValueMap;
    }

    public static Internal.EnumVerifier internalGetVerifier() {
      return LargeEnumVerifier.INSTANCE;
    }

    private static final class LargeEnumVerifier implements Internal.EnumVerifier {
      static final Internal.EnumVerifier INSTANCE = new LargeEnumVerifier();

      @Override
      public boolean isInRange(int number) {
        return LargeEnum.forNumber(number) != null;
      }
    }

    private final int value;
    private final String name;
    private final int index;

    LargeEnum(int v, int i, String n) {
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

  // ---------------- Normal Enum definition ----------------
  public enum NormalEnum implements Internal.EnumLite, java.io.Serializable {
    FOO(1),
    BAR(2),
    BAZ(3),
    UNRECOGNIZED(-1);

    public static final int FOO_VALUE = 1;
    public static final int BAR_VALUE = 2;
    public static final int BAZ_VALUE = 3;

    public final int getNumber() {
      if (this == UNRECOGNIZED) {
        throw new IllegalArgumentException("Can't get the number of an unknown enum value.");
      }
      return value;
    }

    public static NormalEnum forNumber(int value) {
      switch (value) {
        case 1:
          return FOO;
        case 2:
          return BAR;
        case 3:
          return BAZ;
        default:
          return null;
      }
    }

    private static final Internal.EnumLiteMap<NormalEnum> internalValueMap =
        new Internal.EnumLiteMap<NormalEnum>() {
          public NormalEnum findValueByNumber(int number) {
            return NormalEnum.forNumber(number);
          }
        };

    public static Internal.EnumLiteMap<NormalEnum> internalGetValueMap() {
      return internalValueMap;
    }

    public static Internal.EnumVerifier internalGetVerifier() {
      return NormalEnumVerifier.INSTANCE;
    }

    private static final class NormalEnumVerifier implements Internal.EnumVerifier {
      static final Internal.EnumVerifier INSTANCE = new NormalEnumVerifier();

      @Override
      public boolean isInRange(int number) {
        return NormalEnum.forNumber(number) != null;
      }
    }

    public int index() {
      return ordinal();
    }

    public int value() {
      return value;
    }

    public String getName() {
      return name();
    }

    private final int value;

    NormalEnum(int value) {
      this.value = value;
    }
  }

  public static class Main {
    public static void main(String[] args) {
      System.out.println("Large:");
      testLarge();
      System.out.println("Normal:");
      testNormal();
    }

    private static void testLarge() {
      System.out.println("values: " + LargeEnum.values().length);
      System.out.println("valueOf(FOO): " + LargeEnum.valueOf("FOO").name());
      System.out.println("forNumber(1): " + LargeEnum.forNumber(1).name());
      System.out.println(
          "internalMap(2): " + LargeEnum.internalGetValueMap().findValueByNumber(2).name());
      System.out.println("internalGetVerifier(1): " + LargeEnum.internalGetVerifier().isInRange(1));
      System.out.println(
          "internalGetVerifier(99): " + LargeEnum.internalGetVerifier().isInRange(99));
      System.out.println("FOO.getNumber: " + LargeEnum.FOO.getNumber());
      System.out.println("FOO.index: " + LargeEnum.FOO.index());
      System.out.println("FOO.value: " + LargeEnum.FOO.value());
      System.out.println("FOO.name: " + LargeEnum.FOO.name());
      System.out.println("FOO.getName: " + LargeEnum.FOO.getName());
      System.out.println("FOO.ordinal: " + LargeEnum.FOO.ordinal());
      System.out.println("FOO.toString: " + LargeEnum.FOO);
      System.out.println("BAZ_VALUE: " + LargeEnum.BAZ_VALUE);
    }

    private static void testNormal() {
      System.out.println("values: " + NormalEnum.values().length);
      System.out.println("valueOf(FOO): " + NormalEnum.valueOf("FOO").name());
      System.out.println("forNumber(1): " + NormalEnum.forNumber(1).name());
      System.out.println(
          "internalMap(2): " + NormalEnum.internalGetValueMap().findValueByNumber(2).name());
      System.out.println(
          "internalGetVerifier(1): " + NormalEnum.internalGetVerifier().isInRange(1));
      System.out.println(
          "internalGetVerifier(99): " + NormalEnum.internalGetVerifier().isInRange(99));
      System.out.println("FOO.getNumber: " + NormalEnum.FOO.getNumber());
      System.out.println("FOO.index: " + NormalEnum.FOO.index());
      System.out.println("FOO.value: " + NormalEnum.FOO.value());
      System.out.println("FOO.name: " + NormalEnum.FOO.name());
      System.out.println("FOO.getName: " + NormalEnum.FOO.getName());
      System.out.println("FOO.ordinal: " + NormalEnum.FOO.ordinal());
      System.out.println("FOO.toString: " + NormalEnum.FOO);
      System.out.println("BAZ_VALUE: " + NormalEnum.BAZ_VALUE);
    }
  }
}

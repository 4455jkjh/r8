// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LargeProtoEnumNestMemberPruningTest extends LargeProtoEnumTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters)
                .addProgramClassFileData(getProgramClassFileData())
                .addKeepMainRule(Main.class)
                .addKeepClassAndMembersRules(MyEnum.class)
                .addKeepRules("-keep class com.google.protobuf.** { *; }")
                .enableProtoShrinking()
                .allowDiagnosticWarningMessages()
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics.assertErrorsMatch(diagnosticException(AssertionError.class))));
  }

  void inspect(CodeInspector inspector) {
    assertTrue(inspector.clazz(MyEnum.class).getAccessFlags().isEnum());
    assertThat(inspector.clazz(MyEnum0.class), isAbsent());
    assertThat(inspector.clazz(MyEnum1.class), isAbsent());
  }

  private List<byte[]> getProgramClassFileData() throws IOException {
    List<byte[]> classFileData = new ArrayList<>(getProtoLiteClassFileData());
    for (Class<?> nestClass :
        ImmutableList.of(Host.class, MyEnum.class, MyEnum0.class, MyEnum1.class)) {
      classFileData.add(
          remapProtoClasses(
              transformer(nestClass)
                  .setNest(Host.class, MyEnum.class, MyEnum0.class, MyEnum1.class)
                  .transform()));
    }
    classFileData.add(remapProtoClasses(MyEnumVerifier.class));
    classFileData.add(remapProtoClasses(Main.class));
    return classFileData;
  }

  public static class Host {}

  public interface MyEnum0 {
    MyEnum FOO = new MyEnum(1, 0, "FOO");
    int FOO_VALUE = 1;
    MyEnum BAR = new MyEnum(2, 1, "BAR");
    int BAR_VALUE = 2;

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
    MyEnum BAZ = new MyEnum(3, 2, "BAZ");
    int BAZ_VALUE = 3;

    static MyEnum forNumber1(int value) {
      switch (value) {
        case 3:
          return BAZ;
        default:
          return null;
      }
    }

    static MyEnum valueOf1(String name) {
      switch (name) {
        case "BAZ":
          return BAZ;
        default:
          return null;
      }
    }

    static MyEnum[] values1() {
      return new MyEnum[] {BAZ};
    }
  }

  public static final class MyEnumVerifier implements Internal.EnumVerifier {
    static final Internal.EnumVerifier INSTANCE = new MyEnumVerifier();

    @Override
    public boolean isInRange(int number) {
      return MyEnum.forNumber(number) != null;
    }
  }

  public static final class MyEnum implements Internal.EnumLite, Serializable, MyEnum0, MyEnum1 {
    public static final MyEnum UNRECOGNIZED = new MyEnum(-1, 3, "UNRECOGNIZED");

    @Override
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

    public static Internal.EnumVerifier internalGetVerifier() {
      return MyEnumVerifier.INSTANCE;
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

    public String name() {
      return name;
    }

    @Override
    public String toString() {
      return name;
    }
  }

  public static class Main {
    public static void main(String[] args) {
      System.out.println(MyEnum.forNumber(1));
      System.out.println(MyEnum.valueOf("BAZ").getNumber());
    }
  }
}

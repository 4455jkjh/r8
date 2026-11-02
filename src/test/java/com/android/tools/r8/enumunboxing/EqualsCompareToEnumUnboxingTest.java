// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EqualsCompareToEnumUnboxingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EqualsCompareToEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test(expected = CompilationFailedException.class)
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters)
        .addInnerClasses(EqualsCompareToEnumUnboxingTest.class)
        .addKeepMainRule(EnumEqualscompareTo.class)
        .addEnumUnboxingInspector(
            inspector ->
                inspector.assertUnboxed(
                    EnumEqualscompareTo.MyEnum.class,
                    EnumEqualscompareTo.MyNamedEnum.class,
                    EnumEqualscompareTo.OtherNamedEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertErrorsMatch(diagnosticException(AssertionError.class)));
  }

  static class EnumEqualscompareTo {

    interface NamedEnum {
      int ordinal();

      String name();
    }

    @NeverClassInline
    enum MyEnum {
      A,
      B
    }

    @NeverClassInline
    enum MyNamedEnum implements NamedEnum {
      X,
      Y
    }

    @NeverClassInline
    enum OtherNamedEnum implements NamedEnum {
      Z
    }

    public static void main(String[] args) {
      equalsTest();
      objectsEqualsTest();
      compareToTest();
      ordinalAndNameInterfaceTest();
    }

    @NeverInline
    @SuppressWarnings({"ConstantConditions", "EqualsWithItself", "ResultOfMethodCallIgnored"})
    private static void objectsEqualsTest() {
      System.out.println(performObjectsEquals(MyEnum.A, MyEnum.B));
      System.out.println(false);
      System.out.println(performObjectsEquals(MyEnum.A, MyEnum.A));
      System.out.println(true);
      System.out.println(performObjectsEquals(MyEnum.A, null));
      System.out.println(false);
      System.out.println(performObjectsEquals(null, MyEnum.A));
      System.out.println(false);
      System.out.println(performObjectsEquals(null, null));
      System.out.println(true);
    }

    @NeverInline
    private static boolean performObjectsEquals(MyEnum a, MyEnum b) {
      return Objects.equals(a, b);
    }

    @NeverInline
    @SuppressWarnings({"ConstantConditions", "EqualsWithItself", "ResultOfMethodCallIgnored"})
    private static void equalsTest() {
      System.out.println(MyEnum.A.equals(MyEnum.B));
      System.out.println(false);
      System.out.println(MyEnum.A.equals(MyEnum.A));
      System.out.println(true);
      System.out.println(MyEnum.A.equals(null));
      System.out.println(false);
      try {
        ((MyEnum) null).equals(null);
      } catch (NullPointerException npe) {
        System.out.println("npe " + npe.getMessage());
        System.out.println("npe " + npe.getMessage());
      }
    }

    @NeverInline
    @SuppressWarnings({"ConstantConditions", "EqualsWithItself", "ResultOfMethodCallIgnored"})
    private static void compareToTest() {
      System.out.println(MyEnum.B.compareTo(MyEnum.A) > 0);
      System.out.println(true);
      System.out.println(MyEnum.A.compareTo(MyEnum.B) < 0);
      System.out.println(true);
      System.out.println(MyEnum.A.compareTo(MyEnum.A) == 0);
      System.out.println(true);
      System.out.println(new ComparableBox<>(MyEnum.B).compareTo(MyEnum.A) > 0);
      System.out.println(true);
      System.out.println(new ComparableBox<>(MyEnum.A).compareTo(MyEnum.B) < 0);
      System.out.println(true);
      System.out.println(new ComparableBox<>(MyEnum.A).compareTo(MyEnum.A) == 0);
      System.out.println(true);
      try {
        ((MyEnum) null).equals(null);
      } catch (NullPointerException npe) {
        System.out.println("npe " + npe.getMessage());
        System.out.println("npe " + npe.getMessage());
      }
      try {
        MyEnum.A.compareTo(null);
      } catch (NullPointerException npe) {
        System.out.println("npe " + npe.getMessage());
        System.out.println("npe " + npe.getMessage());
      }
      try {
        new ComparableBox<>(MyEnum.A).compareTo(null);
        System.out.println(false);
      } catch (NullPointerException npe) {
        System.out.println("npe " + npe.getMessage());
        System.out.println("npe " + npe.getMessage());
      }
    }

    @NeverInline
    private static void ordinalAndNameInterfaceTest() {
      System.out.println(new NamedEnumBox(MyNamedEnum.X).ordinal());
      System.out.println(0);
      System.out.println(new NamedEnumBox(MyNamedEnum.Y).ordinal());
      System.out.println(1);
      System.out.println(new NamedEnumBox(OtherNamedEnum.Z).ordinal());
      System.out.println(0);
      System.out.println(new NamedEnumBox(MyNamedEnum.X).name());
      System.out.println("X");
      System.out.println(new NamedEnumBox(MyNamedEnum.Y).name());
      System.out.println("Y");
      System.out.println(new NamedEnumBox(OtherNamedEnum.Z).name());
      System.out.println("Z");
    }

    // Exceeds the simple-inlining limit with multiple callers so ClassInliner inlines after
    // Devirtualizer, leaving invoke-interface on the unboxed enum receiver.
    static class ComparableBox<T extends Comparable<? super T>> {
      final T value;

      ComparableBox(T value) {
        this.value = value;
      }

      int compareTo(T other) {
        int first = value.compareTo(other);
        int second = value.compareTo(other);
        return first == second ? first : 0;
      }
    }

    static class NamedEnumBox {
      final NamedEnum value;

      NamedEnumBox(NamedEnum value) {
        this.value = value;
      }

      int ordinal() {
        int first = value.ordinal();
        int second = value.ordinal();
        return first == second ? first : -1;
      }

      String name() {
        String first = value.name();
        String second = value.name();
        return first.equals(second) ? first : "";
      }
    }
  }
}

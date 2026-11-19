// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumUnboxingNullableReceiverVirtualCallTest extends EnumUnboxingTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enumValueOptimization;

  @Parameter(2)
  public EnumKeepRules enumKeepRules;

  @Parameters(name = "{0}, value opt.: {1}, keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  private static final List<String> EXPECTED =
      ImmutableList.of(
          "Validation successful.",
          "Executing request.",
          "Executing request.",
          "NPE",
          "Simple",
          "NPE_Simple",
          "custom",
          "NPE_ToString",
          "Ordinal",
          "0",
          "Ordinal",
          "1",
          "NPE_Ordinal",
          "DefaultToString",
          "A",
          "DefaultToString",
          "B",
          "NPE_DefaultToString",
          "a",
          "b",
          "NPE_CustomToString");

  private static final List<String> UNEXPECTED =
      ImmutableList.of(
          "Validation successful.",
          "Executing request.",
          "Executing request.",
          "Executing request.", // <-- Additional.
          "ERROR_NO_NPE", // <-- Difference.
          "Simple",
          "NPE_Simple",
          "custom",
          "NPE_ToString",
          "Ordinal",
          "0",
          "Ordinal",
          "1",
          "Ordinal", // <-- Additional.
          "UNEXPECTED_NPE_Ordinal", // <-- Additional.
          "ERROR_NO_NPE_Ordinal", // <-- Difference.
          "DefaultToString",
          "A",
          "DefaultToString",
          "B",
          "DefaultToString", // <-- Additional.
          "UNEXPECTED_NPE_DefaultToString", // <-- Additional.
          "ERROR_NO_NPE_DefaultToString", // <-- Difference.
          "a",
          "b",
          "b", // <-- Additional.
          "ERROR_NO_NPE_CustomToString"); // <-- Difference.

  @Test
  public void testRuntime() throws Exception {
    assumeTrue(enumValueOptimization && enumKeepRules.isNone());
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(
            inspector ->
                inspector.assertUnboxed(
                    Handler.class,
                    SimpleEnum.class,
                    ToStringEnum.class,
                    OrdinalEnum.class,
                    DefaultToStringEnum.class,
                    CustomToStringEnum.class))
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(options -> enableEnumOptions(options, enumValueOptimization))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(UNEXPECTED);
  }

  @NeverClassInline
  public enum Handler {
    SANDBOXED {
      @Override
      @NeverInline
      public void handle(Req r) {
        r.validate();
        r.exec();
      }
    },
    TRUSTED {
      @Override
      @NeverInline
      public void handle(Req r) {
        r.exec();
      }
    };

    public abstract void handle(Req r);
  }

  @NeverClassInline
  public enum SimpleEnum {
    A,
    B;

    @NeverInline
    public void action() {
      System.out.println("Simple");
    }
  }

  @NeverClassInline
  public enum ToStringEnum {
    X,
    Y;

    @Override
    @NeverInline
    public String toString() {
      return "custom";
    }
  }

  @NeverClassInline
  public enum OrdinalEnum {
    A,
    B;

    // References 'this' so ArgumentPropagator does not strip the receiver before enum unboxing.
    @NeverInline
    public void action() {
      System.out.println("Ordinal");
      try {
        System.out.println(ordinal());
      } catch (NullPointerException e) {
        System.out.println("UNEXPECTED_NPE_Ordinal");
      }
    }
  }

  @NeverClassInline
  public enum DefaultToStringEnum {
    A,
    B;

    // References 'this' so ArgumentPropagator does not strip the receiver before enum unboxing.
    @NeverInline
    public void action() {
      System.out.println("DefaultToString");
      try {
        System.out.println(toString());
      } catch (NullPointerException e) {
        System.out.println("UNEXPECTED_NPE_DefaultToString");
      }
    }
  }

  @NeverClassInline
  public enum CustomToStringEnum {
    A,
    B;

    // References 'this' so ArgumentPropagator does not strip the receiver before enum unboxing.
    @Override
    @NeverInline
    public String toString() {
      return this == A ? "a" : "b";
    }
  }

  public static class Req {
    @NeverInline
    public void validate() {
      System.out.println("Validation successful.");
    }

    @NeverInline
    public void exec() {
      System.out.println("Executing request.");
    }
  }

  public static class Main {

    @NeverInline
    static Handler lookupHandler(String src) {
      return "internal".equals(src)
          ? Handler.TRUSTED
          : "web".equals(src) ? Handler.SANDBOXED : null;
    }

    @NeverInline
    static SimpleEnum lookupSimple(String src) {
      return "a".equals(src) ? SimpleEnum.A : "b".equals(src) ? SimpleEnum.B : null;
    }

    @NeverInline
    static ToStringEnum lookupToString(String src) {
      return "x".equals(src) ? ToStringEnum.X : "y".equals(src) ? ToStringEnum.Y : null;
    }

    @NeverInline
    static OrdinalEnum lookupOrdinal(String src) {
      return "a".equals(src) ? OrdinalEnum.A : "b".equals(src) ? OrdinalEnum.B : null;
    }

    @NeverInline
    static DefaultToStringEnum lookupDefaultToString(String src) {
      return "a".equals(src)
          ? DefaultToStringEnum.A
          : "b".equals(src) ? DefaultToStringEnum.B : null;
    }

    @NeverInline
    static CustomToStringEnum lookupCustomToString(String src) {
      return "a".equals(src) ? CustomToStringEnum.A : "b".equals(src) ? CustomToStringEnum.B : null;
    }

    public static void main(String[] args) {
      Req req = new Req();
      lookupHandler("web").handle(req);
      lookupHandler("internal").handle(req);
      try {
        lookupHandler("evil").handle(req);
        System.out.println("ERROR_NO_NPE");
      } catch (NullPointerException e) {
        System.out.println("NPE");
      }

      lookupSimple("a").action();
      try {
        lookupSimple("evil").action();
        System.out.println("ERROR_NO_NPE_SIMPLE");
      } catch (NullPointerException e) {
        System.out.println("NPE_Simple");
      }

      System.out.println(lookupToString("x").toString());
      try {
        System.out.println(lookupToString("evil").toString());
      } catch (NullPointerException e) {
        System.out.println("NPE_ToString");
      }

      lookupOrdinal("a").action();
      lookupOrdinal("b").action();
      try {
        lookupOrdinal("evil").action();
        System.out.println("ERROR_NO_NPE_Ordinal");
      } catch (NullPointerException e) {
        System.out.println("NPE_Ordinal");
      }

      lookupDefaultToString("a").action();
      lookupDefaultToString("b").action();
      try {
        lookupDefaultToString("evil").action();
        System.out.println("ERROR_NO_NPE_DefaultToString");
      } catch (NullPointerException e) {
        System.out.println("NPE_DefaultToString");
      }

      System.out.println(lookupCustomToString("a").toString());
      System.out.println(lookupCustomToString("b").toString());
      try {
        System.out.println(lookupCustomToString("evil").toString());
        System.out.println("ERROR_NO_NPE_CustomToString");
      } catch (NullPointerException e) {
        System.out.println("NPE_CustomToString");
      }
    }
  }
}

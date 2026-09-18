// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

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
    return enumUnboxingTestParameters();
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
          "NPE_ToString");

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
          "NPE_ToString");

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(EnumUnboxingNullableReceiverVirtualCallTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addEnumUnboxingInspector(
            inspector ->
                inspector.assertUnboxed(Handler.class, SimpleEnum.class, ToStringEnum.class))
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
    }
  }
}

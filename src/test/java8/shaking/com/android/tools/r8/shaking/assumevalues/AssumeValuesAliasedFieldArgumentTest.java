// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.assumevalues;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AssumeValuesAliasedFieldArgumentTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    String featuresTypeName = Features.class.getTypeName();
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumevalues class " + Main.class.getTypeName() + " {",
            "  static java.lang.String getFeature(* = \"b\") return \"b\";",
            "  static java.lang.String getFeature(* = \"c\") return \"c\";",
            "  static java.lang.String hasFeature(* = "
                + featuresTypeName
                + ".FEATURE_A) return \"a\";",
            "  static java.lang.String hasFeature(* = "
                + featuresTypeName
                + ".FEATURE_B) return \"b\";",
            "  static java.lang.String hasFeature(* = "
                + featuresTypeName
                + ".FEATURE_C) return \"c\";",
            "}")
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b", "c", "unmatched");
  }

  static class Features {

    static String FEATURE_A = "a";
    static String FEATURE_B = "b";
    static final String FEATURE_C = "c";
  }

  static class Main {

    public static void main(String[] args) {
      String keyB = getKeyB();
      String keyC = getKeyC();
      if (args.length > 0) {
        Features.FEATURE_A = args[0];
        System.out.println(Features.FEATURE_B);
      }
      String a = Features.FEATURE_A;
      if (a != null) {
        System.out.println(hasFeature(a));
      }
      String b = getFeature(keyB);
      if (b != null) {
        System.out.println(hasFeature(b));
      }
      String c = getFeature(keyC);
      if (c != null) {
        System.out.println(hasFeature(c));
      }
      String d = args.length > 0 ? "x" : "y";
      if (d != null) {
        System.out.println(hasFeature(d));
      }
    }

    @NeverPropagateValue
    static String getKeyB() {
      int x = 1;
      return x > 0 ? "b" : "other";
    }

    @NeverPropagateValue
    static String getKeyC() {
      int x = 1;
      return x > 0 ? "c" : "other";
    }

    @NeverInline
    static String getFeature(String s) {
      return System.currentTimeMillis() > 0 ? s : null;
    }

    @NeverInline
    static String hasFeature(String unused) {
      return "unmatched";
    }
  }
}

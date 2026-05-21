// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class EnumWithSubclassOverrideTest extends EnumUnboxingTestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean enumValueOptimization;

  @Parameter(2)
  public EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addInnerClasses(getClass())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Sub B side effect", "Base copy A", "Base copy B");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addEnumUnboxingInspector(inspector -> inspector.assertUnboxed(MyEnum.class))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Sub B side effect", "Base copy A", "Base copy B");
  }

  static class Main {
    public static void main(String[] args) {
      MyEnum e1 = args.length > 0 ? MyEnum.A : MyEnum.B;
      MyEnum e2 = args.length > 0 ? MyEnum.B : MyEnum.A;
      System.out.println(e1.copy("A"));
      System.out.println(e2.copy("B"));
    }
  }

  enum MyEnum {
    A {
      @Override
      String create(String val) {
        return "Base copy " + val;
      }
    },
    B {
      @Override
      String create(String val) {
        return "Base copy " + val;
      }

      @Override
      @NeverInline
      String copy(String val) {
        String res = super.copy(val);
        System.out.println("Sub B side effect");
        return res;
      }
    };

    abstract String create(String val);

    String copy(String val) {
      return create(val);
    }
  }
}

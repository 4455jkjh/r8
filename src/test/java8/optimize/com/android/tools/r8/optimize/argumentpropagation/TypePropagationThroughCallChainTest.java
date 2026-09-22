// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TypePropagationThroughCallChainTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertEquals(
                  "java.lang.String",
                  mainClass.uniqueMethodWithOriginalName("a").getParameter(0).getTypeName());
              assertEquals(
                  "java.lang.String",
                  mainClass.uniqueMethodWithOriginalName("b").getParameter(0).getTypeName());
              assertEquals(
                  "java.lang.String",
                  mainClass.uniqueMethodWithOriginalName("c").getParameter(0).getTypeName());
            });
  }

  static class Main {

    public static void main(String[] args) {
      a(args[0]);
    }

    @NeverInline
    static void a(Object o) {
      b(o);
    }

    @NeverInline
    static void b(Object o) {
      c(o);
    }

    @NeverInline
    static void c(Object o) {
      System.out.println(o);
    }
  }
}

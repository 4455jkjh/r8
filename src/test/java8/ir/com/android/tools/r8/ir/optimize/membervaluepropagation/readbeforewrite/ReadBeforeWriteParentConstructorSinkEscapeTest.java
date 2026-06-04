// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.membervaluepropagation.readbeforewrite;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
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
public class ReadBeforeWriteParentConstructorSinkEscapeTest extends TestBase {

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
        .addKeepClassAndMembersRules(Main.class)
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject bClass = inspector.clazz(B.class);
              assertThat(bClass, isPresent());
              // Since A.<init> leaks `this` we cannot prove that the field B.f is not read before
              // written without going beyond the constructor chain.
              assertEquals(1, bClass.allFields().size());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello, world!");
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(new B());
    }

    static void sink(Object o) {}
  }

  @NoVerticalClassMerging
  static class A {

    @NeverInline
    A() {
      Main.sink(this);
    }
  }

  static class B extends A {

    String f;

    @NeverInline
    B() {
      f = "Hello, world!";
    }

    @Override
    public String toString() {
      return f;
    }
  }
}

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
public class ReadBeforeWriteParentConstructorPhiEscapeTest extends TestBase {

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
              // Since A.<init> leaks `this` to the heap via a phi node,
              // we cannot prove that the field B.f is not read before written.
              assertEquals(1, bClass.allFields().size());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Default value observed!", "Hello, world!");
  }

  static class Box {
    B field;
  }

  static class Main {

    public static void main(String[] args) {
      Box box = new Box();
      System.out.println(new B(box));
    }
  }

  @NoVerticalClassMerging
  static class A {

    @SuppressWarnings({"UnnecessaryLocalVariable", "CastCanBeRemovedNarrowingVariableType"})
    @NeverInline
    A(Box box, boolean condition) {
      B alias = (B) this;
      Object val;
      if (condition) {
        val = alias;
      } else {
        val = null;
      }
      B castVal = (B) val;
      box.field = castVal;
    }
  }

  static class B extends A {

    String f;

    @NeverInline
    B(Box box) {
      super(box, System.currentTimeMillis() > 0);
      if (box.field != null && box.field.f == null) {
        System.out.println("Default value observed!");
      }
      f = "Hello, world!";
    }

    @Override
    public String toString() {
      return f;
    }
  }
}

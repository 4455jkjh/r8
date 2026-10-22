// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

// Regression test for merging the instance fields `String field` and `Integer field` into the
// instance field `Object field`, when the target class has a static field `Object field`.
public class MergedInstanceFieldCollidesWithStaticFieldTest extends HorizontalClassMergingTestBase {

  public MergedInstanceFieldCollidesWithStaticFieldTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(
            transformer(A.class).renameAndRemapField("staticField", "field").transform())
        .addProgramClasses(B.class, Main.class)
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A: a, static", "B: 42");
  }

  @NeverClassInline
  public static class A {

    // Renamed to `field` by the test.
    @NeverPropagateValue public static Object staticField;

    @NeverPropagateValue public String field;

    public A(String field) {
      this.field = field;
    }

    @NeverInline
    public static void setStaticField(Object value) {
      staticField = value;
    }

    @NeverInline
    public void print() {
      System.out.println("A: " + field + ", " + staticField);
    }
  }

  @NeverClassInline
  public static class B {

    @NeverPropagateValue public Integer field;

    public B(Integer field) {
      this.field = field;
    }

    @NeverInline
    public void print() {
      System.out.println("B: " + field);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.setStaticField("static");
      new A("a").print();
      new B(42).print();
    }
  }
}

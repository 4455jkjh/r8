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
// instance field `Object field`, when the target class also has an instance field `Object field`.
public class MergedInstanceFieldCollidesWithInstanceFieldTest
    extends HorizontalClassMergingTestBase {

  public MergedInstanceFieldCollidesWithInstanceFieldTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClassFileData(
            transformer(A.class).renameAndRemapField("objectField", "field").transform(),
            transformer(B.class).renameAndRemapField("objectField", "field").transform())
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A: a, 1", "B: 42, 2");
  }

  @NeverClassInline
  public static class A {

    @NeverPropagateValue public String field;

    // Renamed to `field` by the test.
    @NeverPropagateValue public Object objectField;

    public A(String field, Object objectField) {
      this.field = field;
      this.objectField = objectField;
    }

    @NeverInline
    public void print() {
      System.out.println("A: " + field + ", " + objectField);
    }
  }

  @NeverClassInline
  public static class B {

    @NeverPropagateValue public Integer field;

    // Renamed to `field` by the test.
    @NeverPropagateValue public Object objectField;

    public B(Integer field, Object objectField) {
      this.field = field;
      this.objectField = objectField;
    }

    @NeverInline
    public void print() {
      System.out.println("B: " + field + ", " + objectField);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A("a", 1).print();
      new B(42, 2).print();
    }
  }
}

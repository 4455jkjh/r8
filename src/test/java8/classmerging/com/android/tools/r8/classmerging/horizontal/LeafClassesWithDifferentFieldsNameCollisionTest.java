// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

// Tests that the fields B.x and B.y, which have no matching field in A, are renamed when moved to
// A, since A already has an instance field x and a static field y.
public class LeafClassesWithDifferentFieldsNameCollisionTest
    extends HorizontalClassMergingTestBase {

  public LeafClassesWithDifferentFieldsNameCollisionTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertMergedInto(B.class, A.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A: 1, 2, 5", "B: 3, 4");
  }

  @NeverClassInline
  public static class A {

    @NeverPropagateValue static byte y;

    @NeverPropagateValue private byte x;

    @NeverPropagateValue private short z;

    public A(int x, int z) {
      this.x = (byte) x;
      this.z = (short) z;
    }

    @NeverInline
    public static void setY(int value) {
      y = (byte) value;
    }

    @NeverInline
    public void print() {
      System.out.println("A: " + x + ", " + y + ", " + z);
    }
  }

  @NeverClassInline
  public static class B {

    @NeverPropagateValue public byte x;

    @NeverPropagateValue byte y;

    public B(int x, int y) {
      this.x = (byte) x;
      this.y = (byte) y;
    }

    @NeverInline
    public void print() {
      System.out.println("B: " + x + ", " + y);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.setY(args.length + 2);
      new A(args.length + 1, args.length + 5).print();
      new B(args.length + 3, args.length + 4).print();
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import java.lang.reflect.Field;
import org.junit.Test;
import org.junit.runners.Parameterized.Parameters;

public class LeafClassesWithDifferentFieldsTest extends HorizontalClassMergingTestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().withoutCollapsedDexRuntimes().build();
  }

  public LeafClassesWithDifferentFieldsTest(TestParameters parameters) {
    super(parameters);
  }

  private boolean isArt8OrAbove() {
    return parameters.isDexRuntime()
        && parameters.getDexRuntimeVersion().isNewerThan(Version.V7_0_0);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        // A, B, C and G have different fields, and all classes except E have an instance size of
        // 16 bytes in ART.
        // - Merging A, B and G (4 + 2 + 1 + 1 ($r8$classId) = 8 bytes of fields) does not increase
        //   the instance size.
        // - Merging C with A, B or G would increase the instance size to 24 bytes.
        // - D has a subclass, and is therefore only merged with F, which has the same fields.
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(A.class, B.class, G.class)
                    .assertIsCompleteMergeGroup(D.class, F.class)
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class, Boolean.toString(isArt8OrAbove()))
        .assertSuccessWithOutputLines("A: 2", "B: 3", "C: 4", "D: 5", "D: 6", "F: 7", "G: 8");
  }

  @Test
  public void testR8NoHorizontalClassMergingWithDifferentFields() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.testing.enableHorizontalClassMergingWithDifferentFields = false)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(D.class, F.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .run(parameters.getRuntime(), Main.class, Boolean.toString(isArt8OrAbove()))
        .assertSuccessWithOutputLines("A: 2", "B: 3", "C: 4", "D: 5", "D: 6", "F: 7", "G: 8");
  }

  @NeverClassInline
  public static class A {

    private final int i;

    public A(int i) {
      this.i = i;
    }

    @NeverInline
    public void print() {
      System.out.println("A: " + i);
    }
  }

  @NeverClassInline
  public static class B {

    private final short s;

    public B(int i) {
      this.s = (short) i;
    }

    @NeverInline
    public void print() {
      System.out.println("B: " + s);
    }
  }

  @NeverClassInline
  public static class C {

    private final long l;

    public C(long l) {
      this.l = l;
    }

    @NeverInline
    public void print() {
      System.out.println("C: " + l);
    }
  }

  @NeverClassInline
  @NoVerticalClassMerging
  public static class D {

    private final char c;

    public D(char c) {
      this.c = c;
    }

    @NeverInline
    public void print() {
      System.out.println("D: " + c);
    }
  }

  @NeverClassInline
  public static class E extends D {

    public E(char c) {
      super(c);
    }
  }

  @NeverClassInline
  public static class F {

    private final char c;

    @NeverInline
    public F(char c) {
      this.c = c;
    }

    @NeverInline
    public void print() {
      System.out.println("F: " + c);
    }
  }

  @NeverClassInline
  public static class G {

    private final byte b;

    public G(int b) {
      this.b = (byte) b;
    }

    @NeverInline
    public void print() {
      System.out.println("G: " + b);
    }
  }

  public static class Main {

    @NeverInline
    private static int getObjectSize(Object o) throws Exception {
      Field objectSizeField = Class.class.getDeclaredField("objectSizeAllocFastPath");
      objectSizeField.setAccessible(true);
      return objectSizeField.getInt(o.getClass());
    }

    @NeverInline
    private static void checkObjectSize(Object o, int expectedObjectSize) throws Exception {
      int objectSize = getObjectSize(o);
      if (objectSize != expectedObjectSize) {
        throw new AssertionError(
            "Expected objectSize " + expectedObjectSize + ", got " + objectSize);
      }
    }

    public static void main(String[] args) throws Exception {
      A a = new A(args.length + 1);
      a.print();
      B b = new B(args.length + 2);
      b.print();
      C c = new C(args.length + 3);
      c.print();
      D d = new D((char) (args.length + '4'));
      d.print();
      E e = new E((char) (args.length + '5'));
      e.print();
      F f = new F((char) (args.length + '6'));
      f.print();
      G g = new G(args.length + 7);
      g.print();

      if (Boolean.parseBoolean(args[0])) {
        checkObjectSize(a, 16);
        checkObjectSize(b, 16);
        checkObjectSize(c, 16);
        checkObjectSize(d, 16);
        checkObjectSize(e, 16);
        checkObjectSize(f, 16);
        checkObjectSize(g, 16);
      }
    }
  }
}

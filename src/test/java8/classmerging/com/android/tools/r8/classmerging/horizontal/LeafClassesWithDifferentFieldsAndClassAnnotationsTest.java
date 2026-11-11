// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;

public class LeafClassesWithDifferentFieldsAndClassAnnotationsTest
    extends HorizontalClassMergingTestBase {

  public LeafClassesWithDifferentFieldsAndClassAnnotationsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8Compat(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(TypeAnnotation.class)
        .addKeepRuntimeVisibleAnnotations()
        // All classes have an instance size of 16 bytes in ART. A and C have different annotations
        // and are therefore not merged. D and E have the same fields and are merged, although
        // NoClassAnnotationCollisions would distribute them across the groups of A and C if they
        // were grouped with A and C, which have different fields.
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(D.class, E.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a0", "c1.0", "d2", "e3");
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface TypeAnnotation {
    int value();
  }

  @NeverClassInline
  @TypeAnnotation(1)
  public static class A {

    int f;

    A(int f) {
      this.f = f;
    }

    @NeverInline
    public void print() {
      System.out.println("a" + f);
    }
  }

  @NeverClassInline
  @TypeAnnotation(2)
  public static class C {

    float f;

    C(float f) {
      this.f = f;
    }

    @NeverInline
    public void print() {
      System.out.println("c" + f);
    }
  }

  @NeverClassInline
  public static class D {

    long f;

    D(long f) {
      this.f = f;
    }

    @NeverInline
    public void print() {
      System.out.println("d" + f);
    }
  }

  @NeverClassInline
  public static class E {

    long f;

    E(long f) {
      this.f = f;
    }

    @NeverInline
    public void print() {
      System.out.println("e" + f);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A(args.length).print();
      new C(args.length + 1).print();
      new D(args.length + 2).print();
      new E(args.length + 3).print();
    }
  }
}

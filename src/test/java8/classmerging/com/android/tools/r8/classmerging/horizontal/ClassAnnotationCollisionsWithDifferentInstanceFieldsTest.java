// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;

public class ClassAnnotationCollisionsWithDifferentInstanceFieldsTest
    extends HorizontalClassMergingTestBase {

  public ClassAnnotationCollisionsWithDifferentInstanceFieldsTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8Compat(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(TypeAnnotation.class)
        .addKeepRuntimeVisibleAnnotations()
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(A.class, B.class)
                    .assertIsCompleteMergeGroup(D.class, E.class)
                    .assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class).annotation(TypeAnnotation.class), isPresent());
              assertThat(inspector.clazz(B.class), isAbsent());
              assertThat(inspector.clazz(C.class).annotation(TypeAnnotation.class), isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "b", "c", "d0", "e1");
  }

  @Retention(RetentionPolicy.RUNTIME)
  @Target({ElementType.TYPE})
  public @interface TypeAnnotation {
    int value();
  }

  @NeverClassInline
  @TypeAnnotation(1)
  public static class A {

    @NeverInline
    public void print() {
      System.out.println("a");
    }
  }

  @NeverClassInline
  @TypeAnnotation(1)
  public static class B {

    @NeverInline
    public void print() {
      System.out.println("b");
    }
  }

  @NeverClassInline
  @TypeAnnotation(2)
  public static class C {

    @NeverInline
    public void print() {
      System.out.println("c");
    }
  }

  @NeverClassInline
  public static class D {

    int f;

    D(int f) {
      this.f = f;
    }

    @NeverInline
    public void print() {
      System.out.println("d" + f);
    }
  }

  @NeverClassInline
  public static class E {

    int f;

    E(int f) {
      this.f = f;
    }

    @NeverInline
    public void print() {
      System.out.println("e" + f);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A().print();
      new B().print();
      new C().print();
      new D(args.length).print();
      new E(args.length + 1).print();
    }
  }
}

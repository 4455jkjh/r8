// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import org.junit.Test;

public class ClassesDistinguishedByDirectCheckCastTest extends HorizontalClassMergingTestBase {

  public ClassesDistinguishedByDirectCheckCastTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "fail", "fail", "bar");
  }

  @NeverClassInline
  public static class A {
    @NeverInline
    public void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  public static class B {
    @NeverInline
    public void bar() {
      System.out.println("bar");
    }
  }

  public static class Main {
    @NeverInline
    public static void checkObject1(Object o) {
      A a = (A) o;
      a.foo();
    }

    @NeverInline
    public static void checkObject2(Object o) {
      B b = (B) o;
      b.bar();
    }

    public static void main(String[] args) {
      A a = new A();
      B b = new B();
      try {
        checkObject1(a);
      } catch (ClassCastException ex) {
        System.out.println("fail");
      }
      try {
        checkObject1(b);
      } catch (ClassCastException ex) {
        System.out.println("fail");
      }
      try {
        checkObject2(a);
      } catch (ClassCastException ex) {
        System.out.println("fail");
      }
      try {
        checkObject2(b);
      } catch (ClassCastException ex) {
        System.out.println("fail");
      }
    }
  }
}

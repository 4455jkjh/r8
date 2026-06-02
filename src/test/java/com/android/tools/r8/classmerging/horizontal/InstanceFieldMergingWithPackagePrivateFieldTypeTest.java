// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import org.junit.Test;

public class InstanceFieldMergingWithPackagePrivateFieldTypeTest
    extends HorizontalClassMergingTestBase {

  public InstanceFieldMergingWithPackagePrivateFieldTypeTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(PublicClass.class, PackagePrivateClass.class)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .addRepackagingInspector(
            inspector -> inspector.assertIsRepackaged(Reference.classFromClass(Consumer.class)))
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IllegalAccessError.class);
  }

  static class Main {

    public static void main(String[] args) {
      A a = new A(new PackagePrivateClass());
      B b = new B(new Object());
      Consumer.accept(a, b);
      A.disableRepackaging();
      B.disableRepackaging();
    }
  }

  // Repackaged to another package.
  @NoHorizontalClassMerging
  public static class Consumer {

    @NeverInline
    public static void accept(A a, B b) {
      PublicClass publicClass = a.f;
      publicClass.doSomething();
      System.out.println(b.f);
    }
  }

  @NeverClassInline
  public static class A {

    public PackagePrivateClass f;

    public A(PackagePrivateClass f) {
      this.f = f;
    }

    @NeverInline
    public static void disableRepackaging() {
      System.out.println(Main.class); // Access package-private class.
    }
  }

  @NeverClassInline
  public static class B {

    public Object f;

    public B(Object f) {
      this.f = f;
    }

    @NeverInline
    public static void disableRepackaging() {
      System.out.println(Main.class); // Access package-private class.
    }
  }

  // Kept.
  public static class PublicClass {

    @NeverInline
    public void doSomething() {
      System.out.println("PublicClass!");
    }
  }

  // Kept.
  static class PackagePrivateClass extends PublicClass {}
}

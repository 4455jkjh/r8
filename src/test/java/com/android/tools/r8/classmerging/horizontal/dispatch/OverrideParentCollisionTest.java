// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal.dispatch;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.HorizontalClassMergingTestBase;
import org.junit.Test;

public class OverrideParentCollisionTest extends HorizontalClassMergingTestBase {

  public OverrideParentCollisionTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(this.getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .apply(this::checkMappingOutput)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo", "bar", "foo", "parent")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(B.class), not(isPresent()));
            });
  }

  private void checkMappingOutput(R8TestCompileResult result) {
    // Merging of Parent, A and B should still emit a stack trace mapping back to the Parent frame.
    // That frame will be encoded as a qualified method mapping.
    assertThat(result.getProguardMap(), containsString(Parent.class.getTypeName() + ".<init>()"));
  }

  @NeverClassInline
  public static class Parent {
    @NeverInline
    public void foo() {
      System.out.println("parent");
    }
  }

  @NeverClassInline
  public static class A extends Parent {
    @NeverInline
    @Override
    public void foo() {
      System.out.println("foo");
    }
  }

  @NeverClassInline
  public static class B extends Parent {
    // TODO(b/164924717): remove non overlapping constructor requirement
    public B(String s) {}

    @NeverInline
    public void bar() {
      System.out.println("bar");
    }
  }

  public static class Main {
    @NeverInline
    static void callFoo(Parent p) {
      p.foo();
    }

    public static void main(String[] args) {
      A a = new A();
      a.foo();
      B b = new B("");
      b.bar();
      callFoo(a);
      callFoo(b);
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.verticalclassmerging.ClassMergerMode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureVerticalMergeAfterHorizontalMergeTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMaximumApiLevel().build();
  }

  public GenericSignatureVerticalMergeAfterHorizontalMergeTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // TODO(b/571344481): This should not fail. Currently fails when assertions are enabled.
    CompilationFailedException exception =
        assertThrows(
            CompilationFailedException.class,
            () ->
                testForR8Compat(parameters.getBackend())
                    .addInnerClasses(getClass())
                    .setMinApi(parameters)
                    .addKeepMainRule(Main.class)
                    .addKeepAttributeSignature()
                    .addOptionsModification(
                        options ->
                            // Only allow the final round of vertical class merging, which runs
                            // after horizontal class merging, as in the NowInAndroid compilation.
                            options
                                .getVerticalClassMergerOptions()
                                .setModePredicate(mode -> mode == ClassMergerMode.FINAL))
                    .enableInliningAnnotations()
                    .enableNeverClassInliningAnnotations()
                    .enableNoHorizontalClassMergingAnnotations()
                    .addHorizontallyMergedClassesInspector(
                        inspector ->
                            inspector
                                .assertMergedInto(B.class, A.class)
                                .assertNoOtherClassesMerged())
                    .compile());
    assertTrue(hasSuperTypeInconsistencyAssertion(exception));
  }

  private static boolean hasSuperTypeInconsistencyAssertion(Throwable throwable) {
    for (Throwable t = throwable; t != null; t = t.getCause()) {
      if (t instanceof AssertionError
          && t.getMessage() != null
          && t.getMessage().contains("Super type inconsistency in generic signature")) {
        return true;
      }
    }
    return false;
  }

  @NeverClassInline
  public static class A<E> {

    @NeverInline
    public void a() {
      System.out.println("A");
    }
  }

  @NeverClassInline
  public static class B<E> {

    @NeverInline
    public void b() {
      System.out.println("B");
    }
  }

  @NoHorizontalClassMerging
  public abstract static class Middle<E> extends B<E> {

    @NeverInline
    public void middle() {
      System.out.println("Middle");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class Sub<E> extends Middle<Object> {

    @NeverInline
    public void sub() {
      System.out.println("Sub");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new A<String>().a();
      new B<String>().b();
      Sub<String> sub = new Sub<>();
      sub.middle();
      sub.sub();
    }
  }
}

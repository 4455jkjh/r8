// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelHorizontalMergeFieldTypeTest extends TestBase {

  private static final AndroidApiLevel mockLevel = AndroidApiLevel.S;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private boolean addToBootClasspath() {
    return parameters.isCfRuntime()
        || parameters.getRuntime().maxSupportedApiLevel().isGreaterThanOrEqualTo(mockLevel);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8(parameters)
        .addProgramClasses(Main.class, A.class, B.class, Helper.class)
        .addLibraryClasses(LibraryClassPresentSince31.class)
        .addDefaultRuntimeLibrary(parameters)
        .apply(setMockApiLevelForClass(LibraryClassPresentSince31.class, mockLevel))
        .compile()
        .applyIf(
            addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClassPresentSince31.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("null", "hello");
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .addProgramClasses(Main.class, A.class, B.class, Helper.class)
        .addLibraryClasses(LibraryClassPresentSince31.class)
        .addDefaultRuntimeLibrary(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .enableNeverClassInliningAnnotations()
        .apply(setMockApiLevelForClass(LibraryClassPresentSince31.class, mockLevel))
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertClassesNotMerged)
        .compile()
        .applyIf(
            addToBootClasspath(), b -> b.addBootClasspathClasses(LibraryClassPresentSince31.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("null", "hello");
  }

  public static class LibraryClassPresentSince31 {}

  public static class Main {

    public static void main(String[] args) {
      LibraryClassPresentSince31 lib = System.currentTimeMillis() > 0 ? null : Helper.getNull();
      new A(lib).foo();
      new B("hello").bar();
    }
  }

  @NeverClassInline
  public static class A {

    @NeverPropagateValue private final LibraryClassPresentSince31 field;

    public A(LibraryClassPresentSince31 field) {
      this.field = field;
    }

    @NeverInline
    public void foo() {
      Helper.accept(field);
    }
  }

  @NeverClassInline
  public static class B {

    @NeverPropagateValue private final String field;

    public B(String field) {
      this.field = field;
    }

    @NeverInline
    public void bar() {
      System.out.println(field);
    }
  }

  public static class Helper {

    @NeverInline
    public static LibraryClassPresentSince31 getNull() {
      return System.currentTimeMillis() > 0 ? null : new LibraryClassPresentSince31();
    }

    @NeverInline
    public static void accept(LibraryClassPresentSince31 item) {
      System.out.println(item == null ? "null" : "present");
    }
  }
}

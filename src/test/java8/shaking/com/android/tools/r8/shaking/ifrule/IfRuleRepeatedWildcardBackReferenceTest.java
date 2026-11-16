// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleRepeatedWildcardBackReferenceTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testRepeatedTypeWildcards() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        // <1> refers to the class name and not to the field type, which uses the same wildcard.
        .addKeepRules("-if class ** { ** f; } -keepclassmembers class <1> { void kept(); }")
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("B")
        .inspect(
            inspector -> {
              assertThat(
                  inspector.clazz(A.class).uniqueMethodWithOriginalName("kept"), isPresent());
              assertThat(inspector.clazz(B.class).uniqueMethodWithOriginalName("kept"), isAbsent());
            });
  }

  @Test
  public void testRepeatedMemberNameWildcards() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        // <2> refers to the field name (*) and not to the method name (*).
        .addKeepRules(
            "-if class ** { "
                + B.class.getTypeName()
                + " *; void *(); } -keepclassmembers class <1> { void <2>(); }")
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("B")
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("f"), isPresent());
              assertThat(inspector.clazz(A.class).uniqueMethodWithOriginalName("kept"), isAbsent());
            });
  }

  @Test
  public void testGroupedClassAnnotationWildcards() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(TestClass.class)
        .addKeepClassRules(Tag.class)
        .addKeepRuntimeVisibleAnnotations()
        // Both -if rules have equivalent "@** class **" class parts and reference <1>, so
        // whichever rule becomes the group key, the other requires registerClassCapture.
        .addKeepRules(
            "-if @** class ** { "
                + B.class.getTypeName()
                + " f; } -keepclassmembers @interface <1> { java.lang.String value1(); }",
            "-if @** class ** { void trigger(); }"
                + " -keepclassmembers @interface <1> { java.lang.String value2(); }")
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("B")
        .inspect(
            inspector -> {
              assertThat(
                  inspector.clazz(Tag.class).uniqueMethodWithOriginalName("value1"), isPresent());
              assertThat(
                  inspector.clazz(Tag.class).uniqueMethodWithOriginalName("value2"), isPresent());
              assertThat(
                  inspector.clazz(Tag.class).uniqueMethodWithOriginalName("unused"), isAbsent());
            });
  }

  @Retention(RetentionPolicy.RUNTIME)
  @interface Tag {
    String value1() default "";

    String value2() default "";

    String unused() default "";
  }

  @Tag
  static class A {
    B f = new B();

    @NeverInline
    void trigger() {
      System.out.println(f);
    }

    void f() {}

    void kept() {}
  }

  static class B {
    void kept() {}

    @Override
    public String toString() {
      return "B";
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new A().trigger();
    }
  }
}

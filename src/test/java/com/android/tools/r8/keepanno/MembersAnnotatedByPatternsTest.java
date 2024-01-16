// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.keepanno.annotations.ClassNamePattern;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MembersAnnotatedByPatternsTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("b", "bar", "a", "foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build();
  }

  public MembersAnnotatedByPatternsTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getInputClasses())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .enableExperimentalKeepAnnotations()
        .addProgramClasses(getInputClasses())
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkOutput);
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(
        TestClass.class,
        Reflector.class,
        A.class,
        B.class,
        C.class,
        OnMembers.class,
        OnFields.class,
        OnMethods.class);
  }

  private void checkOutput(CodeInspector inspector) {
    // The class constant use will ensure the annotation remains.
    assertThat(inspector.clazz(A.class), isPresentAndRenamed());

    ClassSubject onMembers = inspector.clazz(OnMembers.class);
    assertThat(onMembers.uniqueFieldWithOriginalName("a"), isAbsent());
    assertThat(onMembers.uniqueFieldWithOriginalName("b"), isPresentAndNotRenamed());
    assertThat(onMembers.uniqueMethodWithOriginalName("foo"), isAbsent());
    assertThat(onMembers.uniqueMethodWithOriginalName("bar"), isPresentAndNotRenamed());

    ClassSubject onFields = inspector.clazz(OnFields.class);
    assertThat(onFields.uniqueFieldWithOriginalName("a"), isPresentAndNotRenamed());
    assertThat(onFields.uniqueFieldWithOriginalName("b"), isAbsent());
    assertThat(onFields.uniqueMethodWithOriginalName("foo"), isAbsent());
    assertThat(onFields.uniqueMethodWithOriginalName("bar"), isAbsent());

    ClassSubject onMethods = inspector.clazz(OnMethods.class);
    assertThat(onMethods.uniqueFieldWithOriginalName("a"), isAbsent());
    assertThat(onMethods.uniqueFieldWithOriginalName("b"), isAbsent());
    assertThat(onMethods.uniqueMethodWithOriginalName("foo"), isPresentAndNotRenamed());
    assertThat(onMethods.uniqueMethodWithOriginalName("bar"), isAbsent());
  }

  @Target({ElementType.FIELD, ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @interface A {}

  @Target({ElementType.FIELD})
  @Retention(RetentionPolicy.RUNTIME)
  @interface B {}

  @Target({ElementType.METHOD})
  @Retention(RetentionPolicy.RUNTIME)
  @interface C {}

  static class Reflector {

    @UsesReflection({
      @KeepTarget(
          classConstant = OnMembers.class,
          kind = KeepItemKind.CLASS_AND_MEMBERS,
          memberAnnotatedByClassConstant = A.class,
          constraintAdditions = {KeepConstraint.ANNOTATIONS}),
      @KeepTarget(
          classConstant = OnFields.class,
          kind = KeepItemKind.CLASS_AND_FIELDS,
          fieldAnnotatedByClassName =
              "com.android.tools.r8.keepanno.MembersAnnotatedByPatternsTest$B",
          constraintAdditions = {KeepConstraint.ANNOTATIONS}),
      @KeepTarget(
          classConstant = OnMethods.class,
          kind = KeepItemKind.CLASS_AND_METHODS,
          methodAnnotatedByClassNamePattern =
              @ClassNamePattern(simpleName = "MembersAnnotatedByPatternsTest$C"),
          constraintAdditions = {KeepConstraint.ANNOTATIONS})
    })
    public void foo(Class<?> clazz) throws Exception {
      for (Field field : clazz.getDeclaredFields()) {
        if (field.isAnnotationPresent(A.class) || field.isAnnotationPresent(B.class)) {
          System.out.println(field.getName());
        }
      }
      for (Method method : clazz.getDeclaredMethods()) {
        if (method.isAnnotationPresent(A.class) || method.isAnnotationPresent(C.class)) {
          System.out.println(method.getName());
        }
      }
    }
  }

  static class OnMembers {
    int a;

    @A int b;

    void foo() {}

    @A
    void bar() {}
  }

  static class OnFields {
    @B int a;

    int b;

    void foo() {}

    void bar() {}
  }

  static class OnMethods {
    int a;

    int b;

    @C
    void foo() {}

    void bar() {}
  }

  static class TestClass {

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new Reflector().foo(OnMembers.class);
      new Reflector().foo(OnFields.class);
      new Reflector().foo(OnMethods.class);
    }
  }
}

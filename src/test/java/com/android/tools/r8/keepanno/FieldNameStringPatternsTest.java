// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.KeepTarget;
import com.android.tools.r8.keepanno.annotations.StringPattern;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.keepanno.annotations.UsesReflection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;

@RunWith(Parameterized.class)
public class FieldNameStringPatternsTest extends KeepAnnoTestBase {

  static final String EXPECTED = StringUtils.lines("1", "2");

  @Parameter public KeepAnnoParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static List<KeepAnnoParameters> data() {
    return createParameters(
        getTestParameters().withDefaultRuntimes().withApiLevel(AndroidApiLevel.B).build());
  }

  @Test
  public void test() throws Exception {
    testForKeepAnno(parameters)
        .skipEdgeExtraction()
        .addProgramClasses(getInputClasses())
        .setExcludedOuterClass(getClass())
        .run(TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .applyIf(parameters.isShrinker(), r -> r.inspect(this::checkOutput));
  }

  public List<Class<?>> getInputClasses() {
    return ImmutableList.of(TestClass.class, A.class, B.class);
  }

  private void checkOutput(CodeInspector inspector) {
    assertThat(inspector.clazz(B.class), isPresentAndRenamed());
    assertThat(inspector.clazz(B.class).field("int", "bar"), isAbsent());
    assertThat(inspector.clazz(B.class).field("int", "hiddenOneI"), isPresent());
    assertThat(inspector.clazz(B.class).field("int", "hiddenTwoI"), isPresent());
    assertThat(inspector.clazz(B.class).field("java.lang.String", "hiddenSomeS"), isAbsent());
  }

  static class A {

    @UsesReflection(
        @KeepTarget(
            classConstant = B.class,
            fieldNamePattern = @StringPattern(startsWith = "hidden", endsWith = "I")))
    public void foo() throws Exception {
      int counter = 1;
      for (Field field : B.class.getDeclaredFields()) {
        String name = field.getName();
        if (name.startsWith("hidden")) {
          if (name.endsWith("I")) {
            field.set(null, counter++);
          }
        }
      }
      for (Field field : B.class.getDeclaredFields()) {
        String name = field.getName();
        if (name.startsWith("hidden")) {
          if (name.endsWith("I")) {
            System.out.println(field.get(null));
          }
        }
      }
    }
  }

  static class B {
    static int hiddenOneI = 9;
    static int hiddenTwoI = 18;
    static String hiddenSomeS = "foo";
    static int bar = 7;
  }

  static class TestClass {

    @UsedByReflection(kind = KeepItemKind.CLASS_AND_METHODS)
    public static void main(String[] args) throws Exception {
      new A().foo();
    }
  }
}

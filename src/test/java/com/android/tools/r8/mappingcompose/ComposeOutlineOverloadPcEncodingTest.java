// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.mappingcompose;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ComposeOutlineOverloadPcEncodingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withMaximumApiLevel().build();
  }

  @Test
  public void test() throws Throwable {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-keepclassmembers,allowshrinking class "
                + Main.class.getTypeName()
                + " { static void caller(...); }")
        .addKeepAttributeLineNumberTable()
        .addKeepAttributeSourceFile()
        .addOptionsModification(
            options -> {
              options.outline.threshold = 2;
              options.outline.minSize = 2;
            })
        .collectSyntheticItems()
        .enableInliningAnnotations()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              ClassSubject outlineClassSubject =
                  inspector.clazz(syntheticItems.syntheticOutlineClass(Main.class, 0));
              assertThat(outlineClassSubject, isPresent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .inspectStackTrace(
            stackTrace -> {
              assertThat(
                  stackTrace,
                  StackTrace.isSame(
                      StackTrace.builder()
                          .add(
                              StackTraceLine.builder()
                                  .setClassName(Main.class.getTypeName())
                                  .setMethodName("caller")
                                  .setFileName("ComposeOutlineOverloadPcEncodingTest.java")
                                  .setLineNumber(104)
                                  .build())
                          .add(
                              StackTraceLine.builder()
                                  .setClassName(Main.class.getTypeName())
                                  .setMethodName("main")
                                  .setFileName("ComposeOutlineOverloadPcEncodingTest.java")
                                  .setLineNumber(89)
                                  .build())
                          .build()));
            });
  }

  static class Main {

    public static void main(String[] args) {
      caller(args.length);
      Other.other(args.length);
      caller((Object) args);
    }

    @NeverInline
    static void caller(int a) {
      for (int i = 0; i < a; i++) {
        System.out.println(i);
      }
      System.out.println("caller:" + a);
      Greeter.step1();
      Greeter.step2();
    }

    @NeverInline
    static void caller(Object o) {
      throw new RuntimeException(String.valueOf(o));
    }
  }

  static class Other {

    @NeverInline
    static void other(int a) {
      System.out.println("other:" + a);
      Greeter.step1();
      Greeter.step2();
    }
  }

  static class Greeter {

    @NeverInline
    static void step1() {
      System.out.println("step1");
    }

    @NeverInline
    static void step2() {
      System.out.println("step2");
    }
  }
}

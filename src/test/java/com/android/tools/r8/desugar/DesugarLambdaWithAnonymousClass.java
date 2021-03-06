// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarLambdaWithAnonymousClass extends TestBase {

  private List<String> EXPECTED_JAVAC_RESULT =
      ImmutableList.of("Hello from inside lambda$test$0", "Hello from inside lambda$testStatic$1");

  private List<String> EXPECTED_DESUGARED_RESULT =
      ImmutableList.of(
          "Hello from inside lambda$test$0$DesugarLambdaWithAnonymousClass$TestClass",
          "Hello from inside lambda$testStatic$1");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private final TestParameters parameters;

  public DesugarLambdaWithAnonymousClass(TestParameters parameters) {
    this.parameters = parameters;
  }

  static class Counter {
    private int count = 0;

    void increment() {
      count++;
    }

    int getCount() {
      return count;
    }
  }

  private void checkEnclosingMethod(CodeInspector inspector) {
    Counter counter = new Counter();
    inspector.forAllClasses(
        clazz -> {
          if (clazz.getFinalName().endsWith("$TestClass$1")
              || clazz.getFinalName().endsWith("$TestClass$2")) {
            counter.increment();
            assertTrue(clazz.isAnonymousClass());
            DexMethod enclosingMethod = clazz.getFinalEnclosingMethod();
            ClassSubject testClassSubject =
                inspector.clazz(DesugarLambdaWithAnonymousClass.TestClass.class);
            assertEquals(
                testClassSubject, inspector.clazz(enclosingMethod.holder.toSourceString()));
            assertThat(
                testClassSubject.uniqueMethodWithName(enclosingMethod.name.toString()),
                isPresent());
          }
        });
    assertEquals(2, counter.getCount());
  }

  @BeforeClass
  public static void checkExpectedJavacNames() throws Exception {
    CodeInspector inspector =
        new CodeInspector(
            ToolHelper.getClassFilesForInnerClasses(DesugarLambdaWithAnonymousClass.class));
    String outer = DesugarLambdaWithAnonymousClass.class.getTypeName();
    ClassSubject testClass = inspector.clazz(outer + "$TestClass");
    assertThat(testClass, isPresent());
    assertThat(testClass.uniqueMethodWithName("lambda$test$0"), isPresent());
    assertThat(testClass.uniqueMethodWithName("lambda$testStatic$1"), isPresent());
    assertThat(inspector.clazz(outer + "$TestClass$1"), isPresent());
    assertThat(inspector.clazz(outer + "$TestClass$2"), isPresent());
  }

  @Test
  public void testDesugar() throws Exception {
    testForDesugaring(parameters)
        .addInnerClasses(DesugarLambdaWithAnonymousClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::checkEnclosingMethod)
        .applyIf(
            DesugarTestConfiguration::isNotDesugared,
            r -> r.assertSuccessWithOutputLines(EXPECTED_JAVAC_RESULT))
        .applyIf(
            DesugarTestConfiguration::isDesugared,
            r -> r.assertSuccessWithOutputLines(EXPECTED_DESUGARED_RESULT));
  }

  @Test
  public void testR8() throws Exception {
    try {
      testForR8(parameters.getBackend())
          // TODO(b/158752316, b/157700141): Disable inlining to keep the synthetic lambda methods.
          .addOptionsModification(options -> options.enableInlining = false)
          .addInnerClasses(DesugarLambdaWithAnonymousClass.class)
          .setMinApi(parameters.getApiLevel())
          .addKeepRules("-keep class * { *; }")
          // Keeping the synthetic lambda methods is currently not supported - they are
          // forcefully unpinned. The following rule has no effect. See b/b/157700141.
          .addKeepRules("-keep class **.*$TestClass { synthetic *; }")
          .addKeepAttributes("InnerClasses", "EnclosingMethod")
          .compile()
          .inspect(this::checkEnclosingMethod)
          .run(parameters.getRuntime(), TestClass.class)
          .assertSuccessWithOutputLines(
              parameters.isCfRuntime() ? EXPECTED_JAVAC_RESULT : EXPECTED_DESUGARED_RESULT);
      assertFalse(parameters.isDexRuntime());
    } catch (AssertionError e) {
      assertTrue(parameters.isDexRuntime());
    }
  }

  public interface MyConsumer<T> {
    void accept(T s);
  }

  public static class StringList extends ArrayList<String> {
    public void forEachString(MyConsumer<String> consumer) {
      for (String s : this) {
        consumer.accept(s);
      }
    }
  }

  public static class TestClass {

    public void test() {
      StringList list = new StringList();

      list.add("Hello ");
      list.add("from ");
      list.add("inside ");

      list.forEachString(
          s -> {
            new MyConsumer<String>() {
              public void accept(String s) {
                System.out.print(s);
                if (s.startsWith("inside")) {
                  if (getClass().getEnclosingMethod() == null) {
                    System.out.println("<null>");
                  } else {
                    System.out.println(getClass().getEnclosingMethod().getName());
                  }
                }
              }
            }.accept(s);
          });
    }

    public static void testStatic() {
      StringList list = new StringList();

      list.add("Hello ");
      list.add("from ");
      list.add("inside ");

      list.forEachString(
          s -> {
            new MyConsumer<String>() {
              public void accept(String s) {
                System.out.print(s);
                if (s.startsWith("inside")) {
                  if (getClass().getEnclosingMethod() == null) {
                    System.out.println("<null>");
                  } else {
                    System.out.println(getClass().getEnclosingMethod().getName());
                  }
                }
              }
            }.accept(s);
          });
    }

    public static void main(String[] args) {
      new TestClass().test();
      TestClass.testStatic();
    }
  }
}

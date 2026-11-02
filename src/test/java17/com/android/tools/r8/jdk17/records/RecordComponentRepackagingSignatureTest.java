// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.jdk17.records;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.RecordComponentInfo;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RecordComponentRepackagingSignatureTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDefaultDexRuntime()
        .withAllApiLevels()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .build();
  }

  @Test
  public void test() throws Exception {
    R8TestBuilder<?, ?, ?> testBuilder =
        testForR8(parameters)
            .addInnerClassesAndStrippedOuter(getClass())
            .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
            .addKeepMainRule(Main.class)
            .addKeepClassAndMembersRules(Person.class)
            .addKeepAttributeSignature()
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .enableNoHorizontalClassMergingAnnotations();
    if (parameters.isDexRuntime()) {
      assertThrows(
          CompilationFailedException.class,
          () ->
              testBuilder.compileWithExpectedDiagnostics(
                  diagnostics ->
                      diagnostics.assertErrorsMatch(diagnosticException(AssertionError.class))));
      return;
    }
    testBuilder
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Jane Doe", "Inner")
        .inspect(this::inspect);
  }

  void inspect(CodeInspector inspector) {
    ClassSubject personClass = inspector.clazz(Person.class);
    assertThat(personClass, isPresent());
    ClassSubject boxClass = inspector.clazz(Box.class);
    assertThat(boxClass, isPresent());
    ClassSubject innerClass = inspector.clazz(Outer.Inner.class);
    assertThat(innerClass, isPresent());
    if (parameters.isCfRuntime()) {
      List<RecordComponentInfo> recordComponents =
          personClass.getDexProgramClass().getRecordComponents();
      assertEquals(2, recordComponents.size());
      assertEquals(
          "L" + boxClass.getFinalBinaryName() + "<Ljava/lang/String;>;",
          recordComponents.get(0).getSignature().toString());
      assertTrue(recordComponents.get(1).getSignature().hasNoSignature());
    } else {
      assertTrue(personClass.getDexProgramClass().getRecordComponents().isEmpty());
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class Box<T> {

    private final T value;

    @NeverInline
    public Box(T value) {
      this.value = value;
    }

    @NeverInline
    public T get() {
      return value;
    }
  }

  @NeverClassInline
  public static class Outer<T> {

    private final T value;

    @NeverInline
    public Outer(T value) {
      this.value = value;
    }

    @NeverClassInline
    public class Inner {

      @NeverInline
      public T get() {
        return value;
      }
    }
  }

  public record Person(Box<String> name, Outer<String>.Inner inner) {}

  public static class Main {

    public static void main(String[] args) {
      Person person = new Person(new Box<>("Jane Doe"), new Outer<>("Inner").new Inner());
      System.out.println(person.name().get());
      System.out.println(person.inner().get());
    }
  }
}

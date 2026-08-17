// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DataResourceConsumerForTesting;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RepackageResourcesWithVerticalMergingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    DataResourceConsumerForTesting dataResourceConsumer = new DataResourceConsumerForTesting();

    String aResourceName = A.class.getTypeName().replace('.', '/') + ".txt";
    String bResourceName = B.class.getTypeName().replace('.', '/') + ".txt";

    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addOptionsModification(options -> options.dataResourceConsumer = dataResourceConsumer)
            .enableInliningAnnotations()
            .enableNeverClassInliningAnnotations()
            .addDataResources(
                DataEntryResource.fromString(aResourceName, Origin.unknown(), "A"),
                DataEntryResource.fromString(bResourceName, Origin.unknown(), "B"))
            .addKeepRules("-adaptresourcefilenames")
            .allowDiagnosticWarningMessages()
            .setMinApi(parameters)
            .addVerticallyMergedClassesInspector(
                inspector ->
                    inspector.assertMergedIntoSubtype(A.class).assertNoOtherClassesMerged())
            .addRepackagingInspector(
                inspector -> inspector.assertIsRepackaged(Reference.classFromClass(B.class)))
            .compile();

    CodeInspector codeInspector = compileResult.inspector();

    ClassSubject aClassSubject = codeInspector.clazz(A.class);
    assertThat(aClassSubject, isAbsent());

    ClassSubject bClassSubject = codeInspector.clazz(B.class);
    assertThat(bClassSubject, isPresent());

    // Both A.txt and B.txt adapt to target class B's final name, causing a resource collision.
    String expectedAdaptedResourceName = bClassSubject.getFinalName().replace('.', '/') + ".txt";
    compileResult.inspectDiagnosticMessages(
        diagnosticMessages ->
            diagnosticMessages.assertWarningsMatch(
                diagnosticMessage(
                    containsString("Resource '" + bResourceName + "' already exists."))));

    assertEquals(
        ImmutableList.of(expectedAdaptedResourceName),
        ImmutableList.copyOf(dataResourceConsumer.getAll().keySet()));

    compileResult.run(parameters.getRuntime(), Main.class).assertSuccessWithOutputLines("a", "b");
  }

  @NeverClassInline
  public static class A {

    @NeverInline
    public A() {
      System.out.println("a");
    }
  }

  @NeverClassInline
  public static class B extends A {

    @NeverInline
    public B() {
      System.out.println("b");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B();
    }
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.apimodel;

import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForClass;
import static com.android.tools.r8.apimodel.ApiModelingTestHelper.setMockApiLevelForMethod;
import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethod;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.LibraryDesugaringTestConfiguration;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ApiModelOutlineFromD8R8SynthesizedMethodTest extends TestBase {

  private static final AndroidApiLevel API_LEVEL = AndroidApiLevel.N;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    MethodReference apiMethod =
        Reference.methodFromMethod(LibraryClass.class.getDeclaredMethod("apiMethod"));
    testForR8(parameters)
        .addProgramClasses(Main.class)
        .addLibraryClasses(LibraryClass.class)
        .addLibraryFiles(ToolHelper.getLatestAndroidJar())
        .addKeepMainRule(Main.class)
        .apply(setMockApiLevelForClass(LibraryClass.class, API_LEVEL))
        .apply(setMockApiLevelForMethod(apiMethod, API_LEVEL))
        // Enable library desugaring so that API outlining is run lir-to-lir in R8.
        .enableCoreLibraryDesugaring(
            LibraryDesugaringTestConfiguration.forSpecification(
                LibraryDesugaringSpecification.JDK11.getSpecification()))
        .enableInliningAnnotations()
        .collectSyntheticItems()
        .compile()
        .inspectWithSyntheticItems(
            (inspector, syntheticItems) -> {
              ClassSubject mainClassSubject = inspector.clazz(Main.class);
              assertThat(mainClassSubject, isPresent());

              // Verify that argument propagation removed the unused parameter from test(int), which
              // rewrites it via toTypeSubstitutedMethodAsInlining and marks it isD8R8Synthesized().
              MethodSubject testMethodSubject =
                  mainClassSubject.uniqueMethodWithOriginalName("test");
              assertThat(testMethodSubject, isPresent());
              assertTrue(testMethodSubject.getParameters().isEmpty());

              // TODO(b/587855603): LIR-to-LIR API outlining currently skips methods that satisfy
              //  isD8R8Synthesized(). Fix ApiInvokeOutlinerDesugaring so that
              //  LibraryClass.apiMethod() is outlined when
              //  parameters.getApiLevel().isLessThan(API_LEVEL).
              assertThat(
                  inspector.syntheticClass(syntheticItems.syntheticApiOutlineClass(Main.class, 0)),
                  isAbsent());
              assertEquals(1, inspector.allClasses().size());
              assertThat(testMethodSubject, invokesMethod(apiMethod));
            })
        .addBootClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("LibraryClass::apiMethod");
  }

  // Only present from API level 23.
  public static class LibraryClass {

    public static void apiMethod() {
      System.out.println("LibraryClass::apiMethod");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      test(42);
    }

    @NeverInline
    static void test(int unused) {
      // Should be outlined when compiling to < N.
      LibraryClass.apiMethod();
    }
  }
}

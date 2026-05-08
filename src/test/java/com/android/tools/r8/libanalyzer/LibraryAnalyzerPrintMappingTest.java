// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringDiagnostic;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryAnalyzerPrintMappingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testEmptyOutput() throws Exception {
    testForLibraryAnalyzer()
        .addProgramClasses(Main.class)
        .addDefaultLibrary()
        .addKeepRules("-printmapping out.txt")
        .setAar()
        .setMinApi(AndroidApiLevel.getDefault())
        .compileWithExpectedDiagnostics(this::inspectDiagnostics)
        .inspectValidateConsumerKeepRulesResult(
            result -> result.assertContainsBlockedKeepRule("-printmapping"));
  }

  @Test
  public void testNonEmptyOutput() throws Exception {
    testForLibraryAnalyzer()
        .addProgramClasses(Main.class)
        .addDefaultLibrary()
        .addKeepRules(
            "-keep class " + Main.class.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}",
            "-printmapping out.txt")
        .setAar()
        .setMinApi(AndroidApiLevel.getDefault())
        .compileWithExpectedDiagnostics(this::inspectDiagnostics)
        .inspectValidateConsumerKeepRulesResult(
            result -> result.assertContainsBlockedKeepRule("-printmapping"));
  }

  private void inspectDiagnostics(TestDiagnosticMessages diagnostics) {
    diagnostics.assertOnlyWarnings().assertWarningsMatch(getExpectedDiagnosticMatcher());
  }

  private static Matcher<Diagnostic> getExpectedDiagnosticMatcher() {
    return allOf(
        diagnosticType(StringDiagnostic.class),
        diagnosticMessage(containsString("Options with file names are not supported")));
  }

  static class Main {

    public static void main(String[] args) {}
  }
}

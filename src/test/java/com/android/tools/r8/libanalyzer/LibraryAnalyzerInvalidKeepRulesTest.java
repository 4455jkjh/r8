// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.ProguardRuleParserErrorDiagnostic;
import com.android.tools.r8.libanalyzer.LibraryAnalyzerTestBuilder.AarOrJar;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.google.common.collect.Iterables;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryAnalyzerInvalidKeepRulesTest extends TestBase {

  @Parameter(0)
  public AarOrJar aarOrJar;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, {0}")
  public static List<Object[]> data() {
    return buildParameters(AarOrJar.values(), getTestParameters().withNoneRuntime().build());
  }

  @Test
  public void test() throws Exception {
    testForLibraryAnalyzer()
        .addProgramClasses(Main.class)
        .addDefaultLibrary()
        .addKeepRules("-classobfuscationdictionary dictionary.txt")
        .apply(
            b -> {
              for (int i = 0; i < 16; i++) {
                b.addSecondaryAarOrJar("-keep class " + Main.class.getTypeName());
              }
            })
        .setAarOrJar(aarOrJar)
        .setMinApi(AndroidApiLevel.getDefault())
        .compileWithExpectedDiagnostics(this::inspectDiagnostics)
        .inspectD8CompileResult(D8CompileResultInspector::assertPresent)
        .inspectR8CompileResult(R8CompileResultInspector::assertAbsent)
        .inspectValidateConsumerKeepRulesResult(
            ValidateConsumerKeepRulesResultInspector::assertAbsent);
  }

  private void inspectDiagnostics(TestDiagnosticMessages diagnostics) {
    diagnostics
        .assertNoInfos()
        .assertWarningsMatch(getExpectedWarningMatcher(), getExpectedWarningMatcher())
        .assertErrorsMatch(getExpectedErrorMatcher(), getExpectedErrorMatcher());

    int maxBytes = 0;
    for (Diagnostic diagnostic :
        Iterables.concat(diagnostics.getErrors(), diagnostics.getWarnings())) {
      maxBytes = Math.max(maxBytes, diagnostic.getDiagnosticMessage().getBytes().length);
    }
    assertTrue(Integer.toString(maxBytes), maxBytes < 10000);
  }

  private static Matcher<Diagnostic> getExpectedWarningMatcher() {
    return allOf(
        diagnosticType(ExceptionDiagnostic.class),
        diagnosticException(CompilationFailedException.class),
        diagnosticMessage(not(containsString("ExceptionDiagnostic"))));
  }

  private static Matcher<Diagnostic> getExpectedErrorMatcher() {
    return allOf(
        diagnosticType(ProguardRuleParserErrorDiagnostic.class),
        diagnosticMessage(containsString("Options with file names are not supported")));
  }

  static class Main {

    public static void main(String[] args) {}
  }
}

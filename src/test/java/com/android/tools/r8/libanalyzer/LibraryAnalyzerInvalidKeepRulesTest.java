// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.libanalyzer.LibraryAnalyzerTestBuilder.AarOrJar;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.BooleanBox;
import com.google.common.collect.Iterables;
import java.util.List;
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
    BooleanBox oom = new BooleanBox();
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
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              try {
                for (Diagnostic diagnostic :
                    Iterables.concat(diagnostics.getErrors(), diagnostics.getWarnings())) {
                  diagnostic.getDiagnosticMessage();
                }
              } catch (OutOfMemoryError e) {
                oom.set();
              }
            })
        .inspectD8CompileResult(D8CompileResultInspector::assertPresent)
        .inspectR8CompileResult(R8CompileResultInspector::assertAbsent)
        .inspectValidateConsumerKeepRulesResult(
            ValidateConsumerKeepRulesResultInspector::assertAbsent);
    assertTrue(oom.get());
  }

  static class Main {

    public static void main(String[] args) {}
  }
}

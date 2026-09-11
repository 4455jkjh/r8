// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container.conststring20;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.UnsupportedAndroidApiLevelDiagnostic;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexVersion;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.ListUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DexConstString20R8PartialTest extends DexConstString20TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    assertEquals(38, AndroidApiLevel.MAIN.getMajor());
    return getTestParameters().withDefaultDexRuntime().withPartialCompilation().build();
  }

  @Test
  public void test() throws Exception {
    // TODO(b/556098237): Replace AndroidApiLevel.MAIN by AndroidApiLevel.D.
    assertEquals(38, AndroidApiLevel.MAIN.getMajor());

    parameters.getPartialCompilationTestParameters().assumeIsSome();
    Path output =
        testForR8(parameters)
            .addInnerClasses(getClass())
            .addDontObfuscate()
            .addDontOptimize()
            .addDontShrink()
            .addProgramClassFileData(get32KClassesWith64KStrings())
            .addR8PartialOptionsModification(
                options -> {
                  options.testing.enableDexConstString20 = true;
                  options.testing.forceDexContainerFormat = true;
                })
            .addKeepAttributeSourceFile()
            .allowDiagnosticWarningMessages()
            .release()
            .setMinApi(AndroidApiLevel.MAIN)
            .compileWithExpectedDiagnostics(
                diagnostics ->
                    diagnostics
                        .assertOnlyWarnings()
                        .assertWarningsMatch(
                            diagnosticType(UnsupportedAndroidApiLevelDiagnostic.class),
                            diagnosticType(UnsupportedAndroidApiLevelDiagnostic.class),
                            diagnosticType(UnsupportedAndroidApiLevelDiagnostic.class)))
            .inspect(
                inspector -> {
                  MethodSubject mainMethod = inspector.clazz(Main.class).mainMethod();
                  assertTrue(
                      mainMethod
                          .streamInstructions()
                          .anyMatch(InstructionSubject::isConstString20));
                })
            .writeSingleDexOutputToFile();

    // Check dex version.
    byte[] dex = Files.readAllBytes(output);
    validate(dex, DexVersion.V42);

    // Check string index.
    List<DexString> strings = parseStringPool(dex);
    int zIndex = ListUtils.firstIndexMatching(strings, string -> string.isEqualTo("z"));
    assertTrue(zIndex > Constants.U16BIT_MAX);
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println("z");
    }
  }
}

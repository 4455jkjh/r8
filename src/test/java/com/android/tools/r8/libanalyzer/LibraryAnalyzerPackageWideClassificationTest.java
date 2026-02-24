// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.libanalyzer.proto.KeepRuleBlastRadiusSummary;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LibraryAnalyzerPackageWideClassificationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void test() throws Exception {
    Set<String> packageWideKeepRules =
        Sets.newHashSet(
            "-keep class com.example.* { *; }",
            "-keep class com.example.** { *; }",
            "-keep class com.example.**, com.other.Class { *; }",
            "-keep class !com.example.utils.**, com.example.** { *; }");
    testForLibraryAnalyzer()
        .addProgramClasses(Main.class)
        .addDefaultLibrary()
        .addKeepRules(packageWideKeepRules)
        .setAar()
        .setMinApi(AndroidApiLevel.getDefault())
        .compile()
        .inspectD8CompileResult(D8CompileResultInspector::assertPresent)
        .inspectR8CompileResult(
            inspector -> {
              inspector.assertPresent();
              Set<String> unusedPackageWideKeepRules =
                  inspector
                      .getResult()
                      .getConfiguration()
                      .getUnusedPackageWideKeepRulesList()
                      .stream()
                      .map(KeepRuleBlastRadiusSummary::getSource)
                      .collect(Collectors.toSet());
              assertEquals(packageWideKeepRules, unusedPackageWideKeepRules);
            })
        .inspectValidateConsumerKeepRulesResult(i -> i.assertPresent().assertNoBlockedKeepRules());
  }

  static class Main {

    public static void main(String[] args) {}
  }
}

// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessages;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClankDepsTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ClankDepsTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private static final Path DIR = Paths.get("third_party", "chrome", "clank_google3_prebuilt");
  private static final Path CLASSES = DIR.resolve("classes.jar");
  private static final Path CONFIG = DIR.resolve("proguard.txt");

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addProgramFiles(CLASSES)
        .addKeepRuleFiles(CONFIG)
        .addDontWarn("androidx.**")
        .addDontWarnJavax()
        .addDontWarn("dalvik.system.VMStack")
        .addDontWarn("zzz.com.facebook.litho.R$id")
        .addDontWarn("com.google.android.libraries.elements.R$id")
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules()
        .allowUnnecessaryDontWarnWildcards()
        .setMinApi(AndroidApiLevel.N)
        .allowDiagnosticInfoMessages()
        .compileWithExpectedDiagnostics(TestDiagnosticMessages::assertOnlyInfos)
        .apply(TestBase::verifyHasInfoFromGenericSignatureTypeParameterValidation);
  }
}

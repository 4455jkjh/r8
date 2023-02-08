// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.internal.opensourceapps;

import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TiviTest extends TestBase {

  private static Path outDirectory;

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @BeforeClass
  public static void setup() throws IOException {
    assumeTrue(ToolHelper.isLocalDevelopment());
    outDirectory = getStaticTemp().newFolder().toPath();
    ZipUtils.unzip(Paths.get("third_party/opensource-apps/tivi/dump_app.zip"), outDirectory);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(Backend.DEX)
        .addProgramFiles(outDirectory.resolve("program.jar"))
        .addOptionsModification(options -> options.enableEnumUnboxing = false)
        .addOptionsModification(options -> options.outline.enabled = false)
        .addOptionsModification(
            options -> options.apiModelingOptions().enableOutliningOfMethods = false)
        .addOptionsModification(
            options -> options.apiModelingOptions().enableStubbingOfClasses = false)
        .addOptionsModification(options -> options.callSiteOptimizationOptions().setEnabled(false))
        .addOptionsModification(
            options -> options.getArtProfileOptions().setEnableCompletenessCheckForTesting(true))
        .apply(this::configure)
        .compile();
  }

  @Test
  public void testR8Recompilation() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(Backend.CF)
            .addProgramFiles(outDirectory.resolve("program.jar"))
            .apply(this::configure)
            .apply(this::configureCf)
            .compile();
    testForR8(Backend.DEX)
        .addProgramFiles(compileResult.writeToZip())
        .apply(this::configure)
        .compile();
  }

  @Test
  public void testR8Compat() throws Exception {
    testForR8Compat(Backend.DEX)
        .addProgramFiles(outDirectory.resolve("program.jar"))
        .apply(this::configure)
        .compile();
  }

  @Test
  public void testR8CompatRecompilation() throws Exception {
    R8TestCompileResult compileResult =
        testForR8Compat(Backend.CF)
            .addProgramFiles(outDirectory.resolve("program.jar"))
            .apply(this::configure)
            .apply(this::configureCf)
            .compile();
    testForR8Compat(Backend.DEX)
        .addProgramFiles(compileResult.writeToZip())
        .apply(this::configure)
        .compile();
  }

  private void configure(R8TestBuilder<?> testBuilder) {
    testBuilder
        .addClasspathFiles(outDirectory.resolve("classpath.jar"))
        .addLibraryFiles(outDirectory.resolve("library.jar"))
        .addKeepRuleFiles(outDirectory.resolve("proguard.config"))
        .addOptionsModification(
            options -> options.getOpenClosedInterfacesOptions().suppressAllOpenInterfaces())
        .setMinApi(AndroidApiLevel.M)
        .allowDiagnosticMessages()
        .allowUnnecessaryDontWarnWildcards()
        .allowUnusedDontWarnPatterns()
        .allowUnusedProguardConfigurationRules();
  }

  private void configureCf(R8TestBuilder<?> testBuilder) {
    testBuilder.addOptionsModification(options -> options.horizontalClassMergerOptions().disable());
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.checkdiscarded;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.AssertUtils.assertFailsCompilation;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.KotlinMetadataDiscardedDiagnostic;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.BooleanUtils;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class CheckKotlinMetadataDiscardedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public KotlinTestParameters kotlinParameters;

  @Parameter(2)
  public boolean enableShrinking;

  @Parameters(name = "{0}, {1}, shrink: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withNoneRuntime().build(),
        getKotlinTestParameters().withAllCompilers().build(),
        BooleanUtils.values());
  }

  @Test
  public void testDiscardedSucceedsWithoutKeepKotlinMetadata() throws Exception {
    testForR8(Backend.DEX)
        .addProgramFiles(
            kotlinParameters.getCompiler().getKotlinStdlibJar(),
            kotlinParameters.getCompiler().getKotlinAnnotationJar())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepRuntimeVisibleAnnotations()
        .addKeepRules("-checkkotlinmetadatadiscarded")
        // Explicitly keeping kotlin.Unit should not cause its kotlin.Metadata to be kept when
        // kotlin.Metadata is not itself kept.
        .addKeepRules("-keep class kotlin.Unit")
        .applyIf(!enableShrinking, TestShrinkerBuilder::addDontShrink)
        .setMinApi(AndroidApiLevel.B)
        .compile();
  }

  @Test
  public void testDiscardedSucceedsForKotlinUnitWithKeepKotlinMetadata() throws Exception {
    testForR8(Backend.DEX)
        .addProgramFiles(
            kotlinParameters.getCompiler().getKotlinStdlibJar(),
            kotlinParameters.getCompiler().getKotlinAnnotationJar())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepKotlinMetadata()
        .addKeepRuntimeVisibleAnnotations()
        // The kotlin.Unit class should not have any kotlin.Metadata when it is not explicitly kept
        // despite kotlin.Metadata being explicitly kept.
        .addKeepRules("-checkkotlinmetadatadiscarded class kotlin.Unit")
        .applyIf(!enableShrinking, TestShrinkerBuilder::addDontShrink)
        .setMinApi(AndroidApiLevel.B)
        .compile();
  }

  @Test
  public void testNotDiscardedFails() throws Exception {
    assertFailsCompilation(
        () ->
            testForR8(Backend.DEX)
                .addProgramFiles(
                    kotlinParameters.getCompiler().getKotlinStdlibJar(),
                    kotlinParameters.getCompiler().getKotlinAnnotationJar())
                .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
                .addKeepKotlinMetadata()
                .addKeepRuntimeVisibleAnnotations()
                .addKeepRules("-checkkotlinmetadatadiscarded")
                .applyIf(!enableShrinking, TestShrinkerBuilder::addDontShrink)
                .setMinApi(AndroidApiLevel.B)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics
                            .assertOnlyErrors()
                            .assertErrorsMatch(
                                allOf(
                                    diagnosticType(KotlinMetadataDiscardedDiagnostic.class),
                                    diagnosticMessage(
                                        containsString(
                                            "Kotlin metadata for kotlin.Metadata was not"
                                                + " discarded."))))));
  }

  @Test
  public void testNotDiscardedFailsForKotlinMetadata() throws Exception {
    assertFailsCompilation(
        () ->
            testForR8(Backend.DEX)
                .addProgramFiles(
                    kotlinParameters.getCompiler().getKotlinStdlibJar(),
                    kotlinParameters.getCompiler().getKotlinAnnotationJar())
                .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
                .addKeepKotlinMetadata()
                .addKeepRuntimeVisibleAnnotations()
                .addKeepRules("-checkkotlinmetadatadiscarded class kotlin.Metadata")
                .applyIf(!enableShrinking, TestShrinkerBuilder::addDontShrink)
                .setMinApi(AndroidApiLevel.B)
                .compileWithExpectedDiagnostics(
                    diagnostics ->
                        diagnostics
                            .assertOnlyErrors()
                            .assertErrorsMatch(
                                allOf(
                                    diagnosticType(KotlinMetadataDiscardedDiagnostic.class),
                                    diagnosticMessage(
                                        containsString(
                                            "Kotlin metadata for kotlin.Metadata was not"
                                                + " discarded."))))));
  }
}

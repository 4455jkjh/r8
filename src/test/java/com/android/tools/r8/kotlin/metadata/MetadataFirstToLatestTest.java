// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.ToolHelper.getKotlinAnnotationJar;
import static com.android.tools.r8.ToolHelper.getKotlinStdlibJar;
import static org.hamcrest.CoreMatchers.equalTo;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * Kotlin has limited support for metadata of older versions. In particular, kotlinc 1.5 has
 * deprecated byte-code version which is expected by kotlinc 1.3. The expectation is that if we
 * compile with kotlinc 1.3 and then compile with R8 with a new version of the kolin-metadata-jvm
 * library, the kotlin library is no longer usable in kotlinc 1.3. However, it should be usable in
 * kotlinc 1.5.
 */
@RunWith(Parameterized.class)
public class MetadataFirstToLatestTest extends KotlinMetadataTestBase {

  private final String EXPECTED = StringUtils.lines("foo");
  private static final String PKG_LIB = PKG + ".crossinline_anon_lib";
  private static final String PKG_APP = PKG + ".crossinline_anon_app";
  private final TestParameters parameters;

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(
          getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"));

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withCompiler(KotlinCompilerVersion.KOTLINC_1_3_72)
            .withAllTargetVersions()
            .build());
  }

  public MetadataFirstToLatestTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void smokeTest() throws Exception {
    runTest(
        KotlinCompilerVersion.KOTLINC_1_5_0,
        libJars.getForConfiguration(kotlinc, targetVersion),
        getKotlinStdlibJar(kotlinc));
  }

  @Test
  public void testOnFirst() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .addClasspathFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
            .addKeepAllClassesRule()
            .addKeepAllAttributes()
            .compile()
            .writeToZip();
    Path stdLibJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(
                ToolHelper.getKotlinStdlibJar(kotlinc), ToolHelper.getKotlinAnnotationJar(kotlinc))
            .addKeepAllClassesRule()
            .addKeepAllAttributes()
            .allowDiagnosticWarningMessages()
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .writeToZip();
    // TODO(b/187781614): This is expected to fail when upgrading.
    runTest(KotlinCompilerVersion.KOTLINC_1_3_72, libJar, stdLibJar);
  }

  @Test
  public void testOnLatest() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .addClasspathFiles(getKotlinStdlibJar(kotlinc), getKotlinAnnotationJar(kotlinc))
            .addKeepAllClassesRule()
            .addKeepAllAttributes()
            .compile()
            .writeToZip();
    Path stdLibJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(
                ToolHelper.getKotlinStdlibJar(kotlinc), ToolHelper.getKotlinAnnotationJar(kotlinc))
            .addKeepAllClassesRule()
            .addKeepAllAttributes()
            .allowDiagnosticWarningMessages()
            .compile()
            .assertAllWarningMessagesMatch(
                equalTo("Resource 'META-INF/MANIFEST.MF' already exists."))
            .writeToZip();
    runTest(KotlinCompilerVersion.KOTLINC_1_5_0, libJar, stdLibJar);
  }

  private void runTest(KotlinCompilerVersion kotlinCompilerVersion, Path libJar, Path stdLibJar)
      throws Exception {
    Path output =
        kotlinc(
                parameters.getRuntime().asCf(),
                new KotlinCompiler(kotlinCompilerVersion),
                targetVersion)
            .addClasspathFiles(libJar, stdLibJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .noStdLib()
            .compile();
    testForJvm()
        .addRunClasspathFiles(stdLibJar, libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }
}

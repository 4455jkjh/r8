// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInnerClassTest extends KotlinMetadataTestBase {

  private static final String EXPECTED =
      StringUtils.lines(
          "fun <init>(kotlin.Int):"
              + " com.android.tools.r8.kotlin.metadata.nested_reflect.Outer.Nested",
          "fun com.android.tools.r8.kotlin.metadata.nested_reflect.Outer.Inner.<init>(kotlin.Int):"
              + " com.android.tools.r8.kotlin.metadata.nested_reflect.Outer.Inner");
  private static final String EXPECTED_OUTER_RENAMED =
      StringUtils.lines(
          "fun <init>(kotlin.Int): com.android.tools.r8.kotlin.metadata.nested_reflect.Nested",
          "fun <init>(kotlin.Int): com.android.tools.r8.kotlin.metadata.nested_reflect.Inner");
  private static final String PKG_NESTED_REFLECT = PKG + ".nested_reflect";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataRewriteInnerClassTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer jarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/nested_reflect", "main"));

  @Test
  public void smokeTest() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    Path libJar = jarMap.getForConfiguration(kotlinc, targetVersion);
    testForRuntime(parameters)
        .addProgramFiles(
            ToolHelper.getKotlinStdlibJar(kotlinc), ToolHelper.getKotlinReflectJar(kotlinc), libJar)
        .run(parameters.getRuntime(), PKG_NESTED_REFLECT + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataOuterRenamed() throws Exception {
    Path mainJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
            .addClasspathFiles(ToolHelper.getKotlinReflectJar(kotlinc))
            .addClasspathFiles(ToolHelper.getKotlinAnnotationJar(kotlinc))
            .addProgramFiles(jarMap.getForConfiguration(kotlinc, targetVersion))
            .addKeepRules("-keep public class " + PKG_NESTED_REFLECT + ".Outer$Nested { *; }")
            .addKeepRules("-keep public class " + PKG_NESTED_REFLECT + ".Outer$Inner { *; }")
            .addKeepMainRule(PKG_NESTED_REFLECT + ".MainKt")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(inspector -> inspectPruned(inspector, true))
            .writeToZip();

    runD8(mainJar, EXPECTED_OUTER_RENAMED);
  }

  @Test
  public void testMetadataOuterNotRenamed() throws Exception {
    Path mainJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
            .addClasspathFiles(ToolHelper.getKotlinReflectJar(kotlinc))
            .addClasspathFiles(ToolHelper.getKotlinAnnotationJar(kotlinc))
            .addProgramFiles(jarMap.getForConfiguration(kotlinc, targetVersion))
            .addKeepAttributeInnerClassesAndEnclosingMethod()
            .addKeepRules("-keep public class " + PKG_NESTED_REFLECT + ".Outer { *; }")
            .addKeepRules("-keep public class " + PKG_NESTED_REFLECT + ".Outer$Nested { *; }")
            .addKeepRules("-keep public class " + PKG_NESTED_REFLECT + ".Outer$Inner { *; }")
            .addKeepMainRule(PKG_NESTED_REFLECT + ".MainKt")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .inspect(inspector -> inspectPruned(inspector, false))
            .writeToZip();

    runD8(mainJar, EXPECTED);
  }

  private void runD8(Path jar, String expected) throws Exception {
    Path output = temp.newFile("output.zip").toPath();
    ProgramConsumer programConsumer =
        parameters.isCfRuntime()
            ? new ClassFileConsumer.ArchiveConsumer(output, true)
            : new ArchiveConsumer(output, true);
    testForD8(parameters.getBackend())
        .addProgramFiles(
            ToolHelper.getKotlinStdlibJar(kotlinc), ToolHelper.getKotlinReflectJar(kotlinc), jar)
        .setMinApi(parameters.getApiLevel())
        .setProgramConsumer(programConsumer)
        .addOptionsModification(
            options -> {
              // Needed for passing kotlin_builtin files to output.
              options.testing.enableD8ResourcesPassThrough = true;
              options.dataResourceConsumer = options.programConsumer.getDataResourceConsumer();
            })
        .run(parameters.getRuntime(), PKG_NESTED_REFLECT + ".MainKt")
        .assertSuccessWithOutput(expected);
  }

  private void inspectPruned(CodeInspector inspector, boolean outerRenamed) {
    assertThat(
        inspector.clazz(PKG_NESTED_REFLECT + ".Outer"),
        outerRenamed ? isPresentAndRenamed() : isPresent());
    assertThat(inspector.clazz(PKG_NESTED_REFLECT + ".Outer$Nested"), isPresent());
    assertThat(inspector.clazz(PKG_NESTED_REFLECT + ".Outer$Inner"), isPresent());
  }
}

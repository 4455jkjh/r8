// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.ToolHelper.getKotlinCompilers;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinCompilerTool.KotlinCompiler;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInReturnTypeTest extends KotlinMetadataTestBase {
  private static final String EXPECTED = StringUtils.lines("Impl::foo", "Program::foo", "true");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, target: {1}, kotlinc: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        KotlinTargetVersion.values(),
        getKotlinCompilers());
  }

  public MetadataRewriteInReturnTypeTest(
      TestParameters parameters, KotlinTargetVersion targetVersion, KotlinCompiler kotlinc) {
    super(targetVersion, kotlinc);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer returnTypeLibJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/returntype_lib", "lib"));

  @Test
  public void smokeTest() throws Exception {
    Path libJar = returnTypeLibJarMap.getForConfiguration(kotlinc, targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/returntype_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(kotlinc), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".returntype_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInReturnType_renamed() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar(kotlinc))
            .addProgramFiles(returnTypeLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep non-private members of Impl
            .addKeepRules("-keep public class **.Impl { !private *; }")
            // Keep Itf, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.Itf")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addDontWarnJetBrainsNotNullAnnotation()
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/returntype_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(kotlinc), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".returntype_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    String itfClassName = PKG + ".returntype_lib.Itf";
    String implClassName = PKG + ".returntype_lib.Impl";

    ClassSubject itf = inspector.clazz(itfClassName);
    assertThat(itf, isPresentAndRenamed());

    ClassSubject impl = inspector.clazz(implClassName);
    assertThat(impl, isPresentAndNotRenamed());
    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = impl.getKmClass();
    assertThat(kmClass, isPresent());
    List<ClassSubject> superTypes = kmClass.getSuperTypes();
    assertTrue(superTypes.stream().noneMatch(
        supertype -> supertype.getFinalDescriptor().contains("Itf")));
    assertTrue(superTypes.stream().anyMatch(
        supertype -> supertype.getFinalDescriptor().equals(itf.getFinalDescriptor())));
    List<ClassSubject> functionReturnTypes = kmClass.getReturnTypesInFunctions();
    assertTrue(functionReturnTypes.stream().anyMatch(
        returnType -> returnType.getFinalDescriptor().equals(itf.getFinalDescriptor())));
  }
}


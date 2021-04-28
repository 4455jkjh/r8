// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewritePrunedObjectsTest extends KotlinMetadataTestBase {

  private final String EXPECTED = StringUtils.lines("42", "0", "Goodbye World");
  private static final String PKG_LIB = PKG + ".pruned_lib";
  private static final String PKG_APP = PKG + ".pruned_app";

  private static final KotlinCompileMemoizer libJars =
      getCompileMemoizer(
              getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_LIB), "lib"))
          .configure(
              kotlinCompilerTool -> {
                kotlinCompilerTool.addClasspathFiles(ToolHelper.getClassPathForTests());
              });
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build());
  }

  public MetadataRewritePrunedObjectsTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = libJars.getForConfiguration(kotlinc, targetVersion);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();
    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(kotlinc), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataForLib() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(libJars.getForConfiguration(kotlinc, targetVersion))
            .enableInliningAnnotations()
            .addClasspathFiles(
                ToolHelper.getKotlinStdlibJar(kotlinc), ToolHelper.getKotlinAnnotationJar(kotlinc))
            .addKeepRules(
                "-keep class " + PKG_LIB + ".Sub { <init>(); *** kept(); *** keptProperty; }")
            .addKeepClassAndMembersRules(PKG_LIB + ".SubUser")
            .addKeepRuntimeVisibleAnnotations()
            .noMinification()
            .compile()
            .inspect(this::checkPruned)
            .writeToZip();
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(DescriptorUtils.getBinaryNameFromJavaType(PKG_APP), "main"))
            .compile();
    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(kotlinc), libJar)
        .addProgramFiles(output)
        .run(parameters.getRuntime(), PKG_APP + ".MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void checkPruned(CodeInspector inspector) {
    ClassSubject base = inspector.clazz(PKG_LIB + ".Base");
    assertThat(base, not(isPresent()));
    ClassSubject sub = inspector.clazz(PKG_LIB + ".Sub");
    assertThat(sub, isPresent());
    KmClassSubject kmClass = sub.getKmClass();
    assertThat(kmClass, isPresent());
    assertEquals(0, kmClass.getSuperTypes().size());
    // Ensure that we do not prune the constructors.
    assertEquals(1, kmClass.getConstructors().size());
    // Assert that we have removed the metadata for a function that is removed.
    assertThat(kmClass.kmFunctionWithUniqueName("notKept"), not(isPresent()));
    assertThat(kmClass.kmFunctionWithUniqueName("keptWithoutPinning"), not(isPresent()));
    // Check that we have not pruned the property information for a kept field.
    assertThat(kmClass.kmPropertyWithUniqueName("keptProperty"), isPresent());
    // TODO(b/186508801): This should be removed.
    assertThat(kmClass.kmPropertyWithUniqueName("notExposedProperty"), isPresent());
  }
}

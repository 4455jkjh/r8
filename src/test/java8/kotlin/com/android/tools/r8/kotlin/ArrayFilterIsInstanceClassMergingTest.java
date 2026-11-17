// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.DescriptorUtils;
import java.nio.file.Path;
import java.util.List;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayFilterIsInstanceClassMergingTest extends KotlinTestBase {

  private static final String PKG =
      ArrayFilterIsInstanceClassMergingTest.class.getPackage().getName() + ".filterisinstance";

  private static final String SINGLE_CLASS_MAIN = PKG + ".SingleClassKt";
  private static final ClassReference SINGLE_A = Reference.classFromTypeName(PKG + ".SingleA");
  private static final ClassReference SINGLE_B = Reference.classFromTypeName(PKG + ".SingleB");
  private static final ClassReference SINGLE_C = Reference.classFromTypeName(PKG + ".SingleC");
  private static final ClassReference SINGLE_D = Reference.classFromTypeName(PKG + ".SingleD");
  private static final String[] SINGLE_CLASS_EXPECTED = {"A", "C:1", "D:2", "B"};

  private static final String SINGLE_INTERFACE_MAIN = PKG + ".SingleInterfaceKt";
  private static final ClassReference MARKER = Reference.classFromTypeName(PKG + ".Marker");
  private static final ClassReference IMPL = Reference.classFromTypeName(PKG + ".Impl");
  private static final ClassReference NON_IMPL = Reference.classFromTypeName(PKG + ".NonImpl");
  private static final String[] SINGLE_INTERFACE_EXPECTED = {"Impl", "NonImpl", "1"};

  private static final String MULTI_CALL_MAIN = PKG + ".MultiCallKt";
  private static final ClassReference FIRST = Reference.classFromTypeName(PKG + ".First");
  private static final ClassReference SECOND = Reference.classFromTypeName(PKG + ".Second");
  private static final ClassReference SUPER = Reference.classFromTypeName(PKG + ".Super");
  private static final ClassReference SUB = Reference.classFromTypeName(PKG + ".Sub");
  private static final ClassReference UNRELATED = Reference.classFromTypeName(PKG + ".Unrelated");
  private static final ClassReference MERGED_SUPER =
      Reference.classFromTypeName(PKG + ".MergedSuper");
  private static final ClassReference MERGED_SUB = Reference.classFromTypeName(PKG + ".MergedSub");
  private static final ClassReference SIBLING_A = Reference.classFromTypeName(PKG + ".SiblingA");
  private static final ClassReference SIBLING_B = Reference.classFromTypeName(PKG + ".SiblingB");
  private static final ClassReference REIFIED = Reference.classFromTypeName(PKG + ".Reified");
  private static final String[] MULTI_CALL_EXPECTED = {
    "First", "Second", "Sub", "Reified", "Unrelated", "MergedSub", "SiblingA:1", "SiblingB:2"
  };

  private static KotlinCompileMemoizer singleClassJars;
  private static KotlinCompileMemoizer singleInterfaceJars;
  private static KotlinCompileMemoizer multiCallJars;

  private final TestParameters parameters;

  @BeforeClass
  public static void setup() throws Exception {
    Path annotationJar = writeClassesToJar(NeverClassInline.class, NeverInline.class);
    String folder = DescriptorUtils.getInternalNameFromJavaType(PKG);
    singleClassJars =
        getCompileMemoizer(getKotlinSourceFileFromResources(folder, "SingleClass"))
            .configure(compiler -> compiler.addClasspathFiles(annotationJar));
    singleInterfaceJars =
        getCompileMemoizer(getKotlinSourceFileFromResources(folder, "SingleInterface"))
            .configure(compiler -> compiler.addClasspathFiles(annotationJar));
    multiCallJars =
        getCompileMemoizer(getKotlinSourceFileFromResources(folder, "MultiCall"))
            .configure(compiler -> compiler.addClasspathFiles(annotationJar));
  }

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilers().build());
  }

  public ArrayFilterIsInstanceClassMergingTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(kotlinParameters.isFirst());
    Path stdlib = kotlinc.getKotlinStdlibJar();
    testForJvm(parameters)
        .addProgramFiles(singleClassJars.getForConfiguration(kotlinParameters))
        .addRunClasspathFiles(stdlib)
        .run(parameters.getRuntime(), SINGLE_CLASS_MAIN)
        .assertSuccessWithOutputLines(SINGLE_CLASS_EXPECTED);
    testForJvm(parameters)
        .addProgramFiles(singleInterfaceJars.getForConfiguration(kotlinParameters))
        .addRunClasspathFiles(stdlib)
        .run(parameters.getRuntime(), SINGLE_INTERFACE_MAIN)
        .assertSuccessWithOutputLines(SINGLE_INTERFACE_EXPECTED);
    testForJvm(parameters)
        .addProgramFiles(multiCallJars.getForConfiguration(kotlinParameters))
        .addRunClasspathFiles(stdlib)
        .run(parameters.getRuntime(), MULTI_CALL_MAIN)
        .assertSuccessWithOutputLines(MULTI_CALL_EXPECTED);
  }

  @Test
  public void testSingleClassFilterIsInstance() throws Exception {
    configureR8(singleClassJars, SINGLE_CLASS_MAIN)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(SINGLE_A, SINGLE_C, SINGLE_D)
                    .assertClassReferencesNotMerged(SINGLE_B))
        .run(parameters.getRuntime(), SINGLE_CLASS_MAIN)
        .assertSuccessWithOutputLines(SINGLE_CLASS_EXPECTED);
  }

  @Test
  public void testSingleInterfaceFilterIsInstance() throws Exception {
    configureR8(singleInterfaceJars, SINGLE_INTERFACE_MAIN)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertClassReferencesNotMerged(MARKER, IMPL, NON_IMPL))
        .run(parameters.getRuntime(), SINGLE_INTERFACE_MAIN)
        .assertSuccessWithOutputLines(SINGLE_INTERFACE_EXPECTED)
        .inspect(inspector -> assertThat(inspector.clazz(MARKER), isPresent()));
  }

  @Test
  public void testMultiCallAndVerticalClassMerging() throws Exception {
    configureR8(multiCallJars, MULTI_CALL_MAIN)
        .addVerticallyMergedClassesInspector(
            inspector -> inspector.assertMergedIntoSubtype(MERGED_SUPER))
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(
                        UNRELATED, MERGED_SUB, SIBLING_A, SIBLING_B, REIFIED)
                    .assertClassReferencesNotMerged(FIRST, SECOND, SUPER, SUB))
        .run(parameters.getRuntime(), MULTI_CALL_MAIN)
        .assertSuccessWithOutputLines(MULTI_CALL_EXPECTED)
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(SUPER), isPresent());
              assertThat(inspector.clazz(SUB), isPresent());
            });
  }

  @Test
  public void testR8Partial() throws Exception {
    testForR8Partial(parameters)
        .addProgramFiles(
            singleClassJars.getForConfiguration(kotlinParameters),
            kotlinc.getKotlinStdlibJar(),
            kotlinc.getKotlinAnnotationJar())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addKeepMainRule(SINGLE_CLASS_MAIN)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setR8PartialConfiguration(builder -> builder.addJavaTypeIncludePattern(PKG + ".**"))
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(SINGLE_A, SINGLE_C, SINGLE_D)
                    .assertClassReferencesNotMerged(SINGLE_B))
        .run(parameters.getRuntime(), SINGLE_CLASS_MAIN)
        .assertSuccessWithOutputLines(SINGLE_CLASS_EXPECTED);
  }

  @Test
  public void testR8PartialExcludedCaller() throws Exception {
    testForR8Partial(parameters)
        .addProgramFiles(
            singleClassJars.getForConfiguration(kotlinParameters),
            kotlinc.getKotlinStdlibJar(),
            kotlinc.getKotlinAnnotationJar())
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setR8PartialConfiguration(
            builder ->
                builder
                    .addJavaTypeIncludePattern(PKG + ".**")
                    .addJavaTypeExcludePattern(SINGLE_CLASS_MAIN))
        .run(parameters.getRuntime(), SINGLE_CLASS_MAIN)
        .assertSuccessWithOutputLines(SINGLE_CLASS_EXPECTED)
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(SINGLE_A), isPresentAndRenamed());
              assertThat(inspector.clazz(SINGLE_B), isPresentAndNotRenamed());
            });
  }

  private R8TestBuilder<?, ?, ?> configureR8(KotlinCompileMemoizer jars, String mainClass) {
    return testForR8(parameters)
        .addProgramFiles(
            jars.getForConfiguration(kotlinParameters),
            kotlinc.getKotlinStdlibJar(),
            kotlinc.getKotlinAnnotationJar())
        .addKeepMainRule(mainClass)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations();
  }
}

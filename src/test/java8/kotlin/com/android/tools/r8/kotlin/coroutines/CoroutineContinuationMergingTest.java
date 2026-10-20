// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.coroutines;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinCompileMemoizer;
import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
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
public class CoroutineContinuationMergingTest extends KotlinTestBase {

  private static final String PKG =
      CoroutineContinuationMergingTest.class.getPackage().getName() + ".merging";
  private static final String MAIN_CLASS = PKG + ".MainKt";

  // ContinuationImpl for a top-level suspend function (no final `this$0` receiver field).
  private static final ClassReference TOP_LEVEL_CONTINUATION =
      Reference.classFromTypeName(PKG + ".MainKt$topLevelSuspend$1");
  // ContinuationImpls for member suspend functions on Worker (both have `final Worker this$0`,
  // with different non-final spill fields).
  private static final ClassReference ONE_SUSPEND_CONTINUATION =
      Reference.classFromTypeName(PKG + ".Worker$oneSuspend$1");
  private static final ClassReference TWO_SUSPENDS_CONTINUATION =
      Reference.classFromTypeName(PKG + ".Worker$twoSuspends$1");

  // SuspendLambda capturing two final fields (`$worker` and `$tag`).
  private static final ClassReference TWO_CAPTURES_LAMBDA =
      Reference.classFromTypeName(PKG + ".MainKt$main$twoCapturesLambda$1");
  // SuspendLambdas each capturing one final field (`$tag`), with different non-final spill fields.
  private static final ClassReference ONE_SUSPEND_LAMBDA =
      Reference.classFromTypeName(PKG + ".MainKt$main$oneSuspendLambda$1");
  private static final ClassReference TWO_SUSPENDS_LAMBDA =
      Reference.classFromTypeName(PKG + ".MainKt$main$twoSuspendsLambda$1");

  private static final String[] EXPECTED_OUTPUT = {
    "twoSuspends: worker, 6, v1, v2",
    "oneSuspend: worker",
    "topLevel: tag, v3",
    "oneSuspendLambda: tag, v4",
    "twoSuspendsLambda: tag, 2, v5, v6"
  };

  private static KotlinCompileMemoizer compiledJars;

  private final TestParameters parameters;

  @BeforeClass
  public static void setup() throws Exception {
    Path annotationJar = writeClassesToJar(NeverClassInline.class, NeverInline.class);
    compiledJars =
        getCompileMemoizer(
                getKotlinSourceFileFromResources(
                    DescriptorUtils.getInternalNameFromJavaType(PKG), "Main"))
            .configure(compiler -> compiler.addClasspathFiles(annotationJar));
  }

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilers().build());
  }

  public CoroutineContinuationMergingTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  @Test
  public void testContinuationHorizontalMerging() throws Exception {
    testForR8WithCoroutines()
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(ONE_SUSPEND_CONTINUATION, TWO_SUSPENDS_CONTINUATION)
                    .assertIsCompleteMergeGroup(ONE_SUSPEND_LAMBDA, TWO_SUSPENDS_LAMBDA)
                    .assertClassReferencesNotMerged(TOP_LEVEL_CONTINUATION, TWO_CAPTURES_LAMBDA))
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(ONE_SUSPEND_CONTINUATION), isPresent());
              assertThat(inspector.clazz(TWO_SUSPENDS_CONTINUATION), isAbsent());
              assertThat(inspector.clazz(TOP_LEVEL_CONTINUATION), isPresent());
              assertTrue(
                  inspector
                      .clazz(ONE_SUSPEND_CONTINUATION)
                      .uniqueFieldWithOriginalName("this$0")
                      .isFinal());
              assertThat(inspector.clazz(ONE_SUSPEND_LAMBDA), isPresent());
              assertThat(inspector.clazz(TWO_SUSPENDS_LAMBDA), isAbsent());
              assertThat(inspector.clazz(TWO_CAPTURES_LAMBDA), isPresent());
              assertTrue(
                  inspector
                      .clazz(ONE_SUSPEND_LAMBDA)
                      .uniqueFieldWithOriginalName("$tag")
                      .isFinal());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  // Keeping @DebugMetadata gives each coroutine class a distinct runtime-visible class annotation,
  // which prevents horizontal merging via NoClassAnnotationCollisions.
  @Test
  public void testKeptDebugMetadataPreventsMerging() throws Exception {
    testForR8WithCoroutines()
        .addKeepRuntimeVisibleAnnotations()
        .addKeepRules(
            "-keep @interface kotlin.coroutines.jvm.internal.DebugMetadata { *; }",
            "-keep,allowshrinking,allowoptimization,allowobfuscation class * extends"
                + " kotlin.coroutines.jvm.internal.BaseContinuationImpl")
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertClassReferencesNotMerged(
                    TOP_LEVEL_CONTINUATION,
                    ONE_SUSPEND_CONTINUATION,
                    TWO_SUSPENDS_CONTINUATION,
                    TWO_CAPTURES_LAMBDA,
                    ONE_SUSPEND_LAMBDA,
                    TWO_SUSPENDS_LAMBDA))
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutputLines(EXPECTED_OUTPUT);
  }

  private R8TestBuilder<?, ?, ?> testForR8WithCoroutines() {
    return testForR8(parameters)
        .addProgramFiles(
            compiledJars.getForConfiguration(kotlinParameters),
            kotlinc.getKotlinStdlibJar(),
            kotlinc.getKotlinAnnotationJar())
        .addKeepMainRule(MAIN_CLASS)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations();
  }
}

// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin.lambda;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_5_0;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_9_21;
import static com.android.tools.r8.utils.PredicateUtils.not;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestBase;
import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KotlinLambdaMergingTrivialJavaStyleTest extends KotlinTestBase {

  private final boolean allowAccessModification;
  private final TestParameters parameters;

  @Parameters(name = "{0}, {1}, allow access modification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersLambdaGenerationsAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public KotlinLambdaMergingTrivialJavaStyleTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(kotlinParameters);
    this.allowAccessModification = allowAccessModification;
    this.parameters = parameters;
  }

  @Test
  public void testJVM() throws Exception {
    assumeFalse(allowAccessModification);
    assumeTrue(parameters.isCfRuntime());
    assumeTrue(kotlinParameters.isFirst());
    testForJvm(parameters)
        .addProgramFiles(getProgramFiles())
        .run(parameters.getRuntime(), getMainClassName())
        .assertSuccessWithOutput(getExpectedOutput());
  }

  @Test
  public void testR8() throws Exception {
    // Get the Kotlin lambdas in the input.
    KotlinLambdasInInput lambdasInInput =
        KotlinLambdasInInput.create(getProgramFiles(), getTestName());
    assertEquals(
        hasKotlinCGeneratedLambdaClasses() ? 39 : 0, lambdasInInput.getNumberOfJStyleLambdas());
    assertEquals(0, lambdasInInput.getNumberOfKStyleLambdas());

    testForR8(parameters.getBackend())
        .addProgramFiles(getProgramFiles())
        .addKeepMainRule(getMainClassName())
        .addHorizontallyMergedClassesInspector(inspector -> inspect(inspector, lambdasInInput))
        .allowAccessModification(allowAccessModification)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(
            containsString("Resource 'META-INF/MANIFEST.MF' already exists."))
        .inspect(inspector -> inspect(inspector, lambdasInInput))
        .run(parameters.getRuntime(), getMainClassName())
        .assertSuccessWithOutput(getExpectedOutput());
  }

  private void inspect(
      HorizontallyMergedClassesInspector inspector, KotlinLambdasInInput lambdasInInput) {
    if (hasKotlinCGeneratedLambdaClasses()
        && kotlinParameters.getCompilerVersion().isLessThan(KOTLINC_1_5_0)) {
      // Don't check exactly how J-style Kotlin lambdas are merged for kotlinc before 1.5.0.
      assertEquals(
          parameters.isDexRuntime() && parameters.canUseDefaultAndStaticInterfaceMethods() ? 3 : 10,
          inspector.getMergeGroups().size());
      return;
    }

    if (!allowAccessModification && hasKotlinCGeneratedLambdaClasses()) {
      // Only a subset of all J-style Kotlin lambdas are merged without -allowaccessmodification.
      Set<ClassReference> unmergedLambdas =
          ImmutableSet.of(
              lambdasInInput.getJStyleLambdaReferenceFromTypeName(
                  getTestName(), "inner.InnerKt$testInner1$1"),
              lambdasInInput.getJStyleLambdaReferenceFromTypeName(
                  getTestName(), "inner.InnerKt$testInner1$2"),
              lambdasInInput.getJStyleLambdaReferenceFromTypeName(
                  getTestName(), "inner.InnerKt$testInner1$3"),
              lambdasInInput.getJStyleLambdaReferenceFromTypeName(
                  getTestName(), "inner.InnerKt$testInner1$4"),
              lambdasInInput.getJStyleLambdaReferenceFromTypeName(
                  getTestName(), "inner.InnerKt$testInner1$5"));
      inspector
          .assertIsCompleteMergeGroup(
              lambdasInInput.getJStyleLambdas().stream()
                  .filter(not(unmergedLambdas::contains))
                  .collect(Collectors.toList()))
          .assertClassReferencesNotMerged(unmergedLambdas);
      return;
    }

    if (hasKotlinCGeneratedLambdaClasses()) {
      // All J-style Kotlin lambdas are merged with -allowaccessmodification or because they are
      // generated by R8.
      inspector.assertIsCompleteMergeGroup(lambdasInInput.getJStyleLambdas());
      return;
    }

    ClassReference mainClassReference = Reference.classFromTypeName(getTestName() + ".MainKt");
    ClassReference innerClassReference =
        Reference.classFromTypeName(getTestName() + ".inner.InnerKt");
    if (parameters.isCfRuntime()) {
      inspector.assertClassReferencesNotMerged(
          SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 0),
          SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 1),
          SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 2),
          SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 3));
      for (int id = 0; id < 30; id++) {
        inspector.assertClassReferencesNotMerged(
            SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, id));
      }
    } else if (parameters.canUseDefaultAndStaticInterfaceMethods()) {
      inspector
          .applyIf(
              kotlinc.getCompilerVersion().isLessThanOrEqualTo(KOTLINC_1_9_21),
              i -> {
                i.assertIsCompleteMergeGroup(
                        SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 0),
                        SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 1))
                    .assertIsCompleteMergeGroup(
                        SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 0),
                        SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 2),
                        SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 3))
                    .assertIsCompleteMergeGroup(
                        SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 1),
                        SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 4),
                        SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 5));
                for (int id = 6; id < 30; id++) {
                  inspector.assertClassReferencesNotMerged(
                      SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, id));
                }
              },
              i -> {
                i.assertIsCompleteMergeGroup(
                        SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 0),
                        SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 1),
                        SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 0))
                    .assertIsCompleteMergeGroup(
                        SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 2),
                        SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 3),
                        SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 1));
                for (int id = 4; id < 30; id++) {
                  inspector.assertClassReferencesNotMerged(
                      SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, id));
                }
              })
          .assertClassReferencesNotMerged(
              SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 2),
              SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 3));
    } else {
      inspector
          .assertIsCompleteMergeGroup(
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 1),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 2),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 3),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 4))
          .assertIsCompleteMergeGroup(
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 0),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 9),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 10),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 11),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 12),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 21),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 22),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 23),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 24),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 25),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 26),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 27),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 28))
          .assertIsCompleteMergeGroup(
              SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 0),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 13),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 14))
          .assertIsCompleteMergeGroup(
              SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 1),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 15),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 16))
          .assertIsCompleteMergeGroup(
              SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 2),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 17),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 18))
          .assertIsCompleteMergeGroup(
              SyntheticItemsTestUtils.syntheticLambdaClass(innerClassReference, 3),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 19),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 20))
          .assertIsCompleteMergeGroup(
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 5),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 6))
          .assertIsCompleteMergeGroup(
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 7),
              SyntheticItemsTestUtils.syntheticLambdaClass(mainClassReference, 8));
    }
  }

  private void inspect(CodeInspector inspector, KotlinLambdasInInput lambdasInInput) {
    List<ClassReference> lambdasInOutput = new ArrayList<>();
    for (ClassReference classReference : lambdasInInput.getAllLambdas()) {
      if (inspector.clazz(classReference).isPresent()) {
        lambdasInOutput.add(classReference);
      }
    }
    assertEquals(
        kotlinParameters.getCompilerVersion().isGreaterThanOrEqualTo(KOTLINC_1_5_0)
            ? 0
            : (parameters.isDexRuntime() && parameters.canUseDefaultAndStaticInterfaceMethods()
                ? 3
                : 8),
        lambdasInOutput.size());
  }

  private boolean hasKotlinCGeneratedLambdaClasses() {
    return kotlinParameters.isOlderThan(KOTLINC_1_5_0);
  }

  private String getExpectedOutput() {
    return StringUtils.lines(
        "{005:4}",
        "{007:6}",
        "009:{008}:{0}",
        "011:{010}:{0}",
        "013:{012}:{0}:{1}",
        "015:{014}:{0}:{1}",
        "017:{Local(id=016)}:{0}:{1}:{2}",
        "019:{Local(id=018)}:{0}:{1}:{2}",
        "021:{Local(id=Local(id=020))}:{0}:{1}:{2}:{3}",
        "023:{Local(id=Local(id=022))}:{0}:{1}:{2}:{3}",
        "27",
        "kotlin.Unit",
        "28",
        "kotlin.Unit",
        "029:024",
        "kotlin.Unit",
        "030:024",
        "kotlin.Unit",
        "031:024",
        "kotlin.Unit",
        "032:024",
        "kotlin.Unit",
        "Local(id=033):024:025",
        "kotlin.Unit",
        "Local(id=034):024:025",
        "kotlin.Unit",
        "Local(id=Local(id=035)):024:025:026",
        "kotlin.Unit",
        "Local(id=Local(id=036)):024:025:026",
        "kotlin.Unit",
        "037:038:039",
        "039:037:038",
        "038:039:037",
        "Local(id=037):038:039",
        "Local(id=038):037:039",
        "037:Local(id=038):039",
        "037:Local(id=039):038",
        "Local(id=Local(id=037)):Local(id=Local(id=038)):Local(id=Local(id=039))",
        "Local(id=Local(id=039)):Local(id=Local(id=037)):Local(id=Local(id=038))",
        "040:Local(id=041):Local(id=Local(id=042))",
        "Local(id=Local(id=042)):040:Local(id=041)",
        "Local(id=041):Local(id=Local(id=042)):040",
        "Local(id=040):Local(id=041):Local(id=Local(id=042))",
        "Local(id=Local(id=041)):040:Local(id=Local(id=042))",
        "040:Local(id=Local(id=041)):Local(id=Local(id=042))",
        "040:Local(id=Local(id=Local(id=042))):Local(id=041)",
        "Local(id=Local(id=040)):Local(id=Local(id=Local(id=041))):Local(id=Local(id=Local(id=Local(id=042))))",
        "Local(id=Local(id=Local(id=Local(id=042)))):Local(id=Local(id=040)):Local(id=Local(id=Local(id=041)))",
        "043:044:045",
        "045:043:044",
        "044:045:043",
        "Local(id=043):044:045",
        "Local(id=044):043:045",
        "046:Local(id=047):Local(id=048)",
        "Local(id=048):046:Local(id=047)",
        "Local(id=047):Local(id=048):046",
        "Local(id=046):Local(id=047):Local(id=048)",
        "Local(id=Local(id=047)):046:Local(id=048)",
        "{053:100}",
        "055:{054}:{49}",
        "057:{056}:{49}:{50}",
        "059:{InnerLocal(id=058)}:{49}:{50}:{51}",
        "061:{InnerLocal(id=InnerLocal(id=060))}:{49}:{50}:{51}:{52");
  }

  private Path getJavaJarFile() {
    return getJavaJarFile(getTestName());
  }

  private String getMainClassName() {
    return getTestName() + ".MainKt";
  }

  private List<Path> getProgramFiles() {
    Path kotlinJarFile =
        getCompileMemoizer(getKotlinFilesInResource(getTestName()), getTestName())
            .configure(kotlinCompilerTool -> kotlinCompilerTool.includeRuntime().noReflect())
            .getForConfiguration(kotlinParameters);
    return ImmutableList.of(kotlinJarFile, getJavaJarFile(), kotlinc.getKotlinAnnotationJar());
  }

  private String getTestName() {
    return "lambdas_jstyle_trivial";
  }
}

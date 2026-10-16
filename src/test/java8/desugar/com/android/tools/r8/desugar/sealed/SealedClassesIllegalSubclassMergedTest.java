// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.Assert.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.internal.StringUtils;
import com.google.common.collect.ImmutableList;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesIllegalSubclassMergedTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  static final Matcher<String> EXPECTED_BEFORE_JDK25 =
      containsString("cannot inherit from sealed class");
  static final Matcher<String> EXPECTED_FROM_JDK25 =
      containsString("Failed listed permitted subclass check");
  static final String EXPECTED_WITHOUT_PERMITTED_SUBCLASSES_ATTRIBUTE_OR_FIXED_ATTRIBUTE =
      StringUtils.lines("Sub1", "Sub2", "AnotherSub1", "AnotherSub2", "AnotherSub3", "AnotherSub4");

  @Parameters(name = "{0}, keepPermittedSubclasses = {1}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK17)
        .withDexRuntimesWithout17_1()
        .withAllApiLevels()
        .withPartialCompilation()
        .build();
  }

  private void addTestClasses(TestBuilder<?, ?> builder) throws Exception {
    builder
        .addProgramClasses(
            TestClass.class,
            Sub1.class,
            Sub2.class,
            AnotherSub1.class,
            AnotherSub2.class,
            AnotherSub3.class,
            AnotherSub4.class)
        .addProgramClassFileData(getTransformedSuper())
        .addProgramClassFileData(getTransformedAnotherSuper());
  }

  private Matcher<String> illegalPermittedSubclassesMatcher() {
    return parameters.isCfRuntime() && parameters.asCfRuntime().isOlderThan(CfVm.JDK25)
        ? EXPECTED_BEFORE_JDK25
        : EXPECTED_FROM_JDK25;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    testForJvm(parameters)
        .apply(this::addTestClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatMatches(illegalPermittedSubclassesMatcher());
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Super.class);
    assertThat(clazz, isPresentAndRenamed());
    ClassSubject sub1 = inspector.clazz(Sub1.class);
    assertThat(sub1, isPresentAndRenamed());
    assertEquals(
        parameters.hasSealedClassesSupport()
            ? ImmutableList.of(sub1.asTypeSubject())
            : ImmutableList.of(),
        clazz.getFinalPermittedSubclassAttributes());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters)
        .apply(this::addTestClasses)
        .addKeepAttributePermittedSubclasses()
        .addKeepClassRulesWithAllowObfuscation(Super.class)
        .addKeepClassRulesWithAllowObfuscation(AnotherSuper.class)
        .addKeepMainRule(TestClass.class)
        .addHorizontallyMergedClassesInspectorIf(
            !parameters.isRandomPartialCompilation(),
            inspector ->
                inspector
                    .assertIsCompleteMergeGroup(AnotherSub1.class, AnotherSub2.class)
                    .assertIsCompleteMergeGroup(AnotherSub3.class, AnotherSub4.class)
                    .assertNoOtherClassesMerged())
        .compile()
        .inspectIf(!parameters.isRandomPartialCompilation(), this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isCfRuntime(),
            r -> r.assertFailureWithErrorThatMatches(illegalPermittedSubclassesMatcher()),
            r ->
                r.assertSuccessWithOutput(
                    EXPECTED_WITHOUT_PERMITTED_SUBCLASSES_ATTRIBUTE_OR_FIXED_ATTRIBUTE));
  }

  public byte[] getTransformedSuper() throws Exception {
    return transformer(Super.class).setPermittedSubclasses(Super.class, Sub1.class).transform();
  }

  public byte[] getTransformedAnotherSuper() throws Exception {
    return transformer(AnotherSuper.class)
        .setPermittedSubclasses(AnotherSuper.class, AnotherSub1.class, AnotherSub2.class)
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new Sub1());
      System.out.println(new Sub2());
      System.out.println(new AnotherSub1());
      System.out.println(new AnotherSub2());
      System.out.println(new AnotherSub3());
      System.out.println(new AnotherSub4());
    }
  }

  abstract static class Super /* permits Sub1 */ {}

  static class Sub1 extends Super {

    @Override
    public String toString() {
      return "Sub1";
    }
  }

  static class Sub2 extends Super {

    @Override
    public String toString() {
      return "Sub2";
    }
  }

  abstract static class AnotherSuper /* permits AnotherSub1, AnotherSub2 */ {}

  static class AnotherSub1 extends AnotherSuper {

    @Override
    public String toString() {
      return "AnotherSub1";
    }
  }

  static class AnotherSub2 extends AnotherSuper {

    @Override
    public String toString() {
      return "AnotherSub2";
    }
  }

  static class AnotherSub3 extends AnotherSuper {

    @Override
    public String toString() {
      return "AnotherSub3";
    }
  }

  static class AnotherSub4 extends AnotherSuper {

    @Override
    public String toString() {
      return "AnotherSub4";
    }
  }
}

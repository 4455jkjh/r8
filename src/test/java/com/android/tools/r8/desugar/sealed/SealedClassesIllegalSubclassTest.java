// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.Assert.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesIllegalSubclassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean keepPermittedSubclassesAttribute;

  static final Matcher<String> EXPECTED_BEFORE_JDK25 =
      containsString("cannot inherit from sealed class");
  static final Matcher<String> EXPECTED_FROM_JDK25 =
      containsString("Failed listed permitted subclass check");
  static final String EXPECTED_WITHOUT_PERMITTED_SUBCLASSES_ATTRIBUTE =
      StringUtils.lines("Sub1", "Sub2", "Sub3");

  @Parameters(name = "{0}, keepPermittedSubclasses = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withAllRuntimes()
            .withAllApiLevelsAlsoForCf()
            .withPartialCompilation()
            .build(),
        BooleanUtils.values());
  }

  private void addTestClasses(TestBuilder<?, ?> builder) throws Exception {
    builder
        .addProgramClasses(TestClass.class, Sub1.class, Sub2.class, Sub3.class)
        .addProgramClassFileData(getTransformedClasses());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(keepPermittedSubclassesAttribute);
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    testForJvm(parameters)
        .apply(this::addTestClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatMatches(
            parameters.isCfRuntime() && parameters.asCfRuntime().isOlderThan(CfVm.JDK25)
                ? EXPECTED_BEFORE_JDK25
                : EXPECTED_FROM_JDK25);
  }

  @Test
  public void testDesugaring() throws Exception {
    assumeTrue(keepPermittedSubclassesAttribute);
    testForDesugaring(parameters)
        .apply(this::addTestClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            DesugarTestConfiguration::isNotJavac,
            r -> r.assertSuccessWithOutput(EXPECTED_WITHOUT_PERMITTED_SUBCLASSES_ATTRIBUTE),
            c -> parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK25),
            r -> r.assertFailureWithErrorThatMatches(EXPECTED_FROM_JDK25),
            c -> parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertFailureWithErrorThatMatches(EXPECTED_BEFORE_JDK25),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Super.class);
    assertThat(clazz, isPresentAndRenamed());
    ClassSubject sub1 = inspector.clazz(Sub1.class);
    ClassSubject sub2 = inspector.clazz(Sub2.class);
    ClassSubject sub3 = inspector.clazz(Sub3.class);
    assertThat(sub1, isPresentAndNotRenamed());
    assertThat(sub2, isPresentAndNotRenamed());
    assertThat(sub3, isPresentAndNotRenamed());
    assertEquals(
        hasSealedClassesSupport(parameters) && keepPermittedSubclassesAttribute
            ? ImmutableList.of(sub1.asTypeSubject(), sub2.asTypeSubject())
            : ImmutableList.of(),
        clazz.getFinalPermittedSubclassAttributes());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeTrue(
        parameters.isDexRuntime() || parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    assumeFalse(parameters.isDexRuntime() && keepPermittedSubclassesAttribute);
    testForR8(parameters)
        .apply(this::addTestClasses)
        .applyIf(
            keepPermittedSubclassesAttribute,
            TestShrinkerBuilder::addKeepAttributePermittedSubclasses)
        .addKeepPermittedSubclasses(Super.class)
        .addKeepRules("-keep class * extends " + Super.class.getTypeName())
        .addKeepMainRule(TestClass.class)
        .compile()
        .inspectIf(!parameters.isRandomPartialCompilation(), this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isDexRuntime()
                || (!keepPermittedSubclassesAttribute
                    && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17)),
            r -> r.assertSuccessWithOutput(EXPECTED_WITHOUT_PERMITTED_SUBCLASSES_ATTRIBUTE),
            parameters.isCfRuntime()
                && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK25)
                && keepPermittedSubclassesAttribute,
            r -> r.assertFailureWithErrorThatMatches(EXPECTED_FROM_JDK25),
            parameters.isCfRuntime()
                && parameters.asCfRuntime().isNewerThan(CfVm.JDK17)
                && keepPermittedSubclassesAttribute,
            r -> r.assertFailureWithErrorThatMatches(EXPECTED_BEFORE_JDK25),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  public byte[] getTransformedClasses() throws Exception {
    return transformer(Super.class)
        .setPermittedSubclasses(Super.class, Sub1.class, Sub2.class)
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new Sub1());
      System.out.println(new Sub2());
      System.out.println(new Sub3());
    }
  }

  abstract static class Super /* permits Sub1, Sub2 */ {}

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

  static class Sub3 extends Super {

    @Override
    public String toString() {
      return "Sub3";
    }
  }
}

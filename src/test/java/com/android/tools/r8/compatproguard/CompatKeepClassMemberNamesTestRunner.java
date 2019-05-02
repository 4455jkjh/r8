// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compatproguard;

import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.BaseCompilerCommand;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.compatproguard.CompatKeepClassMemberNamesTest.Bar;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for compatibility with Proguard -keepclassmember{s,names}. b/119076934. */
@RunWith(Parameterized.class)
public class CompatKeepClassMemberNamesTestRunner extends TestBase {

  private static Class<?> MAIN_CLASS = CompatKeepClassMemberNamesTest.class;
  private static Class<?> BAR_CLASS = CompatKeepClassMemberNamesTest.Bar.class;
  private static Collection<Class<?>> CLASSES = ImmutableList.of(MAIN_CLASS, BAR_CLASS);

  private static String EXPLICIT_RULE =
      "class "
          + Bar.class.getTypeName()
          + " { static "
          + Bar.class.getTypeName()
          + " instance(); void <init>(); int i; }";

  private static String EXPECTED = StringUtils.lines("42", "null");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  private final TestParameters parameters;

  public CompatKeepClassMemberNamesTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  // Test reference implementation.

  @Test
  public void testJvm() throws Exception {
    testForJvm()
        .addProgramClasses(CLASSES)
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  // Helpers to check that the Bar is absent or the Bar.instance() call has not been inlined.

  private <CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
      void assertBarIsAbsent(CR compileResult) throws Exception {
    compileResult
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertFalse(inspector.clazz(BAR_CLASS).isPresent());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("ClassNotFoundException"), containsString(BAR_CLASS.getTypeName())));
  }

  private static void assertBarGetInstanceIsNotInlined(CodeInspector inspector) {
    assertTrue(
        inspector
            .clazz(MAIN_CLASS)
            .uniqueMethodWithName("main")
            .streamInstructions()
            .anyMatch(i -> i.isInvoke() && i.getMethod().qualifiedName().contains("instance")));
  }

  // Tests with just keep main and no additional rules.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      void testWithoutRules(TestShrinkerBuilder<C, B, CR, RR, T> builder) throws Exception {
    assertBarIsAbsent(
        builder.addProgramClasses(CLASSES).addKeepMainRule(MAIN_CLASS).noMinification().compile());
  }

  @Test
  public void testWithoutRulesPG() throws Exception {
    testWithoutRules(testForProguard());
  }

  @Test
  public void testWithoutRulesCompatR8() throws Exception {
    testWithoutRules(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testWithoutRulesFullR8() throws Exception {
    // Running without rules is the same in full mode as for compat mode. The class is removed.
    testWithoutRules(testForR8(parameters.getBackend()));
  }

  // Tests for -keepclassmembers and *no* minification.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      T buildWithMembersRule(TestShrinkerBuilder<C, B, CR, RR, T> builder) {
    return buildWithMembersRuleAllowRenaming(builder).noMinification();
  }

  private <CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
      void assertMembersRuleCompatResult(CR compileResult) throws Exception {
    compileResult
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertTrue(inspector.clazz(BAR_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              assertTrue(inspector.clazz(BAR_CLASS).uniqueFieldWithName("i").isPresent());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testWithMembersRulePG() throws Exception {
    assertMembersRuleCompatResult(buildWithMembersRule(testForProguard()).compile());
  }

  @Test
  public void testWithMembersRuleCompatR8() throws Exception {
    assertMembersRuleCompatResult(
        buildWithMembersRule(testForR8Compat(parameters.getBackend())).compile());
  }

  @Test
  public void testWithMembersRuleFullR8() throws Exception {
    // In full mode for R8 we do *not* expect a -keepclassmembers to cause retention of the class.
    assertBarIsAbsent(buildWithMembersRule(testForR8(parameters.getBackend())).compile());
  }

  // Tests for -keepclassmembers and minification.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      T buildWithMembersRuleAllowRenaming(TestShrinkerBuilder<C, B, CR, RR, T> builder) {
    return builder
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepRules("-keepclassmembers " + EXPLICIT_RULE);
  }

  private <CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
      void assertMembersRuleAllowRenamingCompatResult(CR compileResult) throws Exception {
    compileResult
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              // Bar is renamed but its member names are kept.
              ClassSubject bar = inspector.clazz(BAR_CLASS);
              assertTrue(bar.isRenamed());
              FieldSubject barI = bar.uniqueFieldWithName("i");
              assertTrue(barI.isPresent());
              assertFalse(barI.isRenamed());
              MethodSubject barInit = bar.uniqueMethodWithName("<init>");
              assertTrue(barInit.isPresent());
              assertFalse(barInit.isRenamed());
              MethodSubject barInstance = bar.uniqueMethodWithName("instance");
              assertTrue(barInstance.isPresent());
              assertFalse(barInstance.isRenamed());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        // Allowing minification will rename Bar, failing the reflective get.
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("ClassNotFoundException"), containsString(BAR_CLASS.getTypeName())));
  }

  @Test
  public void testWithMembersRuleAllowRenamingPG() throws Exception {
    assertMembersRuleAllowRenamingCompatResult(
        buildWithMembersRuleAllowRenaming(testForProguard()).compile());
  }

  @Test
  @Ignore("b/119076934")
  // TODO(b/119076934): This fails the Bar.instance() is not inlined check.
  public void testWithMembersRuleAllowRenamingCompatR8() throws Exception {
    assertMembersRuleAllowRenamingCompatResult(
        buildWithMembersRuleAllowRenaming(testForR8Compat(parameters.getBackend())).compile());
  }

  @Test
  public void testWithMembersRuleAllowRenamingFullR8() throws Exception {
    assertBarIsAbsent(
        buildWithMembersRuleAllowRenaming(testForR8(parameters.getBackend())).compile());
  }

  // Tests for "-keepclassmembers class Bar", i.e, with no members specified.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      void testWithMembersStarRule(TestShrinkerBuilder<C, B, CR, RR, T> builder) throws Exception {
    assertBarIsAbsent(
        builder
            .addProgramClasses(CLASSES)
            .addKeepMainRule(MAIN_CLASS)
            .noMinification()
            .addKeepRules("-keepclassmembers class " + Bar.class.getTypeName())
            .compile());
  }

  @Test
  public void testWithMembersStarRulePG() throws Exception {
    testWithMembersStarRule(testForProguard());
  }

  @Test
  public void testWithMembersStarRuleCompatR8() throws Exception {
    testWithMembersStarRule(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testWithMembersStarRuleFullR8() throws Exception {
    testWithMembersStarRule(testForR8(parameters.getBackend()));
  }

  // Tests for "-keepclassmembernames" and *no* minification.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      T buildWithMemberNamesRule(TestShrinkerBuilder<C, B, CR, RR, T> builder) {
    return buildWithMemberNamesRuleAllowRenaming(builder).noMinification();
  }

  private <CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
      void assertMemberNamesRuleCompatResult(CR compileResult) throws Exception {
    compileResult
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              ClassSubject bar = inspector.clazz(BAR_CLASS);
              assertTrue(bar.isPresent());
              assertTrue(bar.uniqueMethodWithName("instance").isPresent());
              // Reflected on fields are not kept.
              assertFalse(bar.uniqueMethodWithName("<init>").isPresent());
              assertFalse(bar.uniqueFieldWithName("i").isPresent());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        // Keeping the instance and its accessed members does not keep the reflected <init> and i.
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("NoSuchMethodException"),
                containsString(BAR_CLASS.getTypeName() + ".<init>()")));
  }

  @Test
  public void testWithMemberNamesRulePG() throws Exception {
    assertMemberNamesRuleCompatResult(buildWithMemberNamesRule(testForProguard()).compile());
  }

  @Test
  @Ignore("b/119076934")
  // TODO(b/119076934): This fails the Bar.instance() is not inlined check.
  public void testWithMemberNamesRuleCompatR8() throws Exception {
    assertMemberNamesRuleCompatResult(
        buildWithMemberNamesRule(testForR8Compat(parameters.getBackend())).compile());
  }

  @Test
  public void testWithMemberNamesRuleFullR8() throws Exception {
    assertBarIsAbsent(buildWithMemberNamesRule(testForR8Compat(parameters.getBackend())).compile());
  }

  // Tests for "-keepclassmembernames" and *no* minification.

  private <
          C extends BaseCompilerCommand,
          B extends BaseCompilerCommand.Builder<C, B>,
          CR extends TestCompileResult<CR, RR>,
          RR extends TestRunResult<RR>,
          T extends TestShrinkerBuilder<C, B, CR, RR, T>>
      T buildWithMemberNamesRuleAllowRenaming(TestShrinkerBuilder<C, B, CR, RR, T> builder) {
    return builder
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MAIN_CLASS)
        .addKeepRules("-keepclassmembernames " + EXPLICIT_RULE);
  }

  private <CR extends TestCompileResult<CR, RR>, RR extends TestRunResult<RR>>
      void assertMemberNamesRuleAllowRenamingCompatResult(CR compileResult) throws Exception {
    compileResult
        .inspect(
            inspector -> {
              assertTrue(inspector.clazz(MAIN_CLASS).isPresent());
              assertBarGetInstanceIsNotInlined(inspector);
              ClassSubject bar = inspector.clazz(BAR_CLASS);
              assertTrue(bar.isPresent());
              assertTrue(bar.isRenamed());
              assertFalse(bar.uniqueFieldWithName("i").isPresent());
              assertFalse(bar.uniqueMethodWithName("<init>").isPresent());
              MethodSubject barInstance = bar.uniqueMethodWithName("instance");
              assertTrue(barInstance.isPresent());
              assertFalse(barInstance.isRenamed());
            })
        .run(parameters.getRuntime(), MAIN_CLASS)
        // Allowing minification will rename Bar, failing the reflective get.
        .assertFailureWithErrorThatMatches(
            allOf(
                containsString("ClassNotFoundException"), containsString(BAR_CLASS.getTypeName())));
  }

  @Test
  public void testWithMemberNamesRuleAllowRenamingPG() throws Exception {
    assertMemberNamesRuleAllowRenamingCompatResult(
        buildWithMemberNamesRuleAllowRenaming(testForProguard()).compile());
  }

  @Test
  @Ignore("b/119076934")
  // TODO(b/119076934): This fails the Bar.instance() is not inlined check.
  public void testWithMemberNamesRuleAllowRenamingCompatR8() throws Exception {
    assertMemberNamesRuleAllowRenamingCompatResult(
        buildWithMemberNamesRuleAllowRenaming(testForR8Compat(parameters.getBackend())).compile());
  }

  @Test
  public void testWithMemberNamesRuleAllowRenamingFullR8() throws Exception {
    assertBarIsAbsent(
        buildWithMemberNamesRuleAllowRenaming(testForR8(parameters.getBackend())).compile());
  }
}

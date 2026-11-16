// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.negatedrules;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.UnusedProguardKeepRuleDiagnostic;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Tests rules for {@code !Name,**}, which match all classes if there is no class {@code Name}. */
@RunWith(Parameterized.class)
public class NegatedClassNameMatchAllTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testAssumeRulesWithNegatedNameNotInApp() throws Exception {
    testAssumeRules("!absent.Name,**");
  }

  @Test
  public void testAssumeRulesWithNegatedAndPositiveNamesBeforeMatchAll() throws Exception {
    testAssumeRules("!absent.Name," + A.class.getTypeName() + ",**");
  }

  @Test
  public void testUnusedAssumeRulePreservesOriginalClassNames() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-assumenosideeffects class !absent.Name,** { void nonExistent(); }")
        .allowUnusedProguardConfigurationRules()
        .enableInliningAnnotations()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertInfosMatch(
                    allOf(
                        diagnosticType(UnusedProguardKeepRuleDiagnostic.class),
                        diagnosticMessage(containsString("!absent.Name,**")))));
  }

  @Test
  public void testAssumeRulesWithNegatedNameInApp() throws Exception {
    // The rules do not match A, but they match B and thereby the members that B inherits from A.
    testAssumeRules("!" + A.class.getTypeName() + ",**");
  }

  @Test
  public void testAssumeRulesWithAllMatchingClassesNegated() throws Exception {
    String classNames = "!" + A.class.getTypeName() + ",!" + B.class.getTypeName() + ",**";
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumenosideeffects class " + classNames + " { static void print(); }",
            "-assumevalues class " + classNames + " { int f return 42; }")
        .allowUnusedProguardConfigurationRules()
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A", "1", "B");
  }

  private void testAssumeRules(String classNames) throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules(
            "-assumenosideeffects class " + classNames + " { void log(); }",
            "-assumevalues class " + classNames + " { int f return 42; }")
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42", "B");
  }

  @Test
  public void testKeepClassMembersCompat() throws Exception {
    // B inherits log() from A, so in compat mode the rule also keeps B instantiated when B is live.
    testForR8Compat(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keepclassmembers class !absent.Name,** { void log(); }")
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(B.class).uniqueMethodWithOriginalName("toString"),
                    isPresent()));
  }

  static class Main {

    public static void main(String[] args) {
      A a = new A();
      a.log();
      System.out.println(a.f);
      B.print();
    }
  }

  static class A {

    int f = System.currentTimeMillis() > 0 ? 1 : 2;

    @NeverInline
    void log() {
      System.out.println("A");
    }
  }

  static class B extends A {

    @NeverInline
    static void print() {
      System.out.println("B");
    }

    @Override
    public String toString() {
      return "B";
    }
  }
}

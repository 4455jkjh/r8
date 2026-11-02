// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.assistant.cli.KeepRuleMatcher.Coverage;
import com.android.tools.r8.assistant.cli.KeepRuleMatcher.RuleCoverage;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ClassNameIndexTest extends AssistantCommandTestBase {

  private static final List<String> RULES =
      ImmutableList.of(
          "-keep class com.example.model.User { <init>(); }",
          "-keepclassmembers class com.example.model.** { <fields>; }",
          "-keep class com.example.ui.*Activity",
          "-keep class com.example.?pi.Service",
          "-keepnames class com.example.model.User,com.example.json.**",
          "-keep class !com.example.model.internal.**,com.example.model.** { *; }",
          "-keep class com.example.ui.**,!com.example.model.**",
          "-keep class !com.example.model.**",
          "-keep class * extends com.example.Base { <init>(...); }",
          "-keep interface ** { *; }",
          "-keep class *** { *; }",
          "-if class com.example.Base -keep class com.example.model.User",
          "-if class com.example.model.** -keep class com.example.model.<1>Adapter",
          "-if class * -keepclassmembers class <1> { <init>(); }",
          "-dontwarn com.example.**",
          "-assumenosideeffects class com.example.model.User { *; }");

  private static final List<String> CLASSES =
      ImmutableList.of(
          "com.example.model.User",
          "com.example.model.UserAdapter",
          "com.example.model.internal.Cache",
          "com.example.model.User$Address",
          "com.example.json.Reader",
          "com.example.ui.MainActivity",
          "com.example.ui.main.MainActivity",
          "com.example.api.Service",
          "com.example.spi.Service",
          "com.example.Base",
          "com.other.Plugin",
          "Main");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testCandidateRules() throws Exception {
    KeepRuleSet rules = KeepRuleSet.parseString(String.join("\n", RULES));
    assertEquals(
        ImmutableList.of(
            "-keep class com.example.model.User { <init>(); }",
            "-keepclassmembers class com.example.model.** { <fields>; }",
            "-keep class com.example.?pi.Service",
            "-keepnames class com.example.model.User,com.example.json.**",
            "-keep class !com.example.model.internal.**,com.example.model.** { *; }",
            "-keep class com.example.ui.**,!com.example.model.**",
            "-keep class !com.example.model.**",
            "-keep class * extends com.example.Base { <init>(...); }",
            "-keep interface ** { *; }",
            "-keep class *** { *; }",
            "-if class com.example.Base -keep class com.example.model.User",
            "-if class com.example.model.** -keep class com.example.model.<1>Adapter",
            "-if class * -keepclassmembers class <1> { <init>(); }"),
        getCandidates(rules, "com.example.model.User"));
    // Exact rules for com.example.model.User are not candidates for inner or suffixed classes.
    assertEquals(
        ImmutableList.of(
            "-keepclassmembers class com.example.model.** { <fields>; }",
            "-keep class com.example.?pi.Service",
            "-keep class !com.example.model.internal.**,com.example.model.** { *; }",
            "-keep class com.example.ui.**,!com.example.model.**",
            "-keep class !com.example.model.**",
            "-keep class * extends com.example.Base { <init>(...); }",
            "-keep interface ** { *; }",
            "-keep class *** { *; }",
            "-if class com.example.model.** -keep class com.example.model.<1>Adapter",
            "-if class * -keepclassmembers class <1> { <init>(); }"),
        getCandidates(rules, "com.example.model.User$Address"));
    assertEquals(
        ImmutableList.of(
            "-keep class com.example.ui.*Activity",
            "-keep class com.example.?pi.Service",
            "-keep class com.example.ui.**,!com.example.model.**",
            "-keep class !com.example.model.**",
            "-keep class * extends com.example.Base { <init>(...); }",
            "-keep interface ** { *; }",
            "-keep class *** { *; }",
            "-if class * -keepclassmembers class <1> { <init>(); }"),
        getCandidates(rules, "com.example.ui.MainActivity"));
    assertEquals(
        ImmutableList.of(
            "-keep class com.example.ui.**,!com.example.model.**",
            "-keep class !com.example.model.**",
            "-keep class * extends com.example.Base { <init>(...); }",
            "-keep interface ** { *; }",
            "-keep class *** { *; }",
            "-if class * -keepclassmembers class <1> { <init>(); }"),
        getCandidates(rules, "com.other.Plugin"));
  }

  @Test
  public void testCandidatesIncludeMatchingRules() throws Exception {
    KeepRuleSet rules = KeepRuleSet.parseString(String.join("\n", RULES));
    for (String className : CLASSES) {
      DexType type = rules.getType(className);
      List<KeepRuleSet.Rule> candidates = rules.getCandidateRules(className);
      for (KeepRuleSet.Rule rule : rules.getRules()) {
        ProguardKeepRuleBase keepRule = getKeepRule(rule.get());
        if (keepRule != null && keepRule.getClassNames().matches(type)) {
          assertTrue(rule + " matches " + className, candidates.contains(rule));
        }
      }
    }
  }

  @Test
  public void testIfPreconditionIndexing() throws Exception {
    String caller = "com.example.Main.main(Main.java:1)";
    Path log =
        writeFile(
            "if_rules.jsonl",
            request(1, "CLASS_FOR_NAME", caller, "com.example.model.User", "true", LOADER),
            returned(1, "com.example.model.User"),
            request(2, "CLASS_FOR_NAME", caller, "com.example.model.User$1", "true", LOADER),
            returned(2, "com.example.model.User$1"),
            request(3, "CLASS_FOR_NAME", caller, "com.example.model.UserAdapter", "true", LOADER),
            returned(3, "com.example.model.UserAdapter"),
            request(4, "CLASS_FOR_NAME", caller, "com.other.Plugin", "true", LOADER),
            returned(4, "com.other.Plugin"),
            request(
                5,
                "CLASS_GET_DECLARED_METHOD",
                caller,
                "void",
                "com.example.ui.MainActivity",
                "onUserLoaded",
                "com.example.model.User"),
            returned(5, "void onUserLoaded(com.example.model.User)"));
    ParsedLogs parsed = ParsedLogs.read(Collections.singletonList(log));
    KeepRequirements facts = KeepRequirements.analyze(parsed.getEvents(), parsed::threw);

    // <1> combined with ? (and no *) in MatchTypePattern.
    assertCoverage(
        facts,
        "-if class com.example.model.* -keep class com.example.model.<1>$?",
        KeepRequirement.forClass("com.example.model.User$1", false),
        Coverage.CONDITIONAL,
        "applies if com.example.model.User is kept");
    assertCoverage(
        facts,
        "-if class com.example.model.* -keep class com.example.model.<1>$?",
        KeepRequirement.forClass("com.example.model.User$Address", false),
        Coverage.NONE,
        null);

    // Uncaptured <1> (annotation wildcard on -if) combined with captured <2>.
    assertCoverage(
        facts,
        "-if @com.example.* class com.example.model.* -keep class <1>Plugin,"
            + " com.example.model.<2>Adapter",
        KeepRequirement.forClass("com.example.model.UserAdapter", false),
        Coverage.CONDITIONAL,
        "applies if com.example.model.User is kept and its -if precondition holds"
            + " (cannot be verified from the log alone)");

    // PositiveClassNameList where <1> materializes to a class already in the list.
    assertCoverage(
        facts,
        "-if class com.example.model.* -keep class com.example.model.User, com.example.model.<1>",
        KeepRequirement.forClass("com.example.model.User", false),
        Coverage.CONDITIONAL,
        "applies if com.example.model.User is kept");

    // MixedClassNameList where !com.example.model.<1> collides with an earlier positive entry
    // (first-match-wins) or an earlier negated entry (trailing negation preserved).
    assertCoverage(
        facts,
        "-if class com.example.model.* -keep class com.example.model.User,"
            + " !com.example.model.<1>, com.example.Base",
        KeepRequirement.forClass("com.example.model.User", false),
        Coverage.CONDITIONAL,
        "applies if com.example.model.User is kept");
    assertCoverage(
        facts,
        "-if class com.example.model.* -keep class !com.example.model.User, com.example.Base,"
            + " !com.example.model.<1>",
        KeepRequirement.forClass("com.other.Plugin", false),
        Coverage.CONDITIONAL,
        "applies if com.example.model.User is kept");

    // Back reference in a member parameter type, prechecked by member name and parameter count.
    String memberRule =
        "-if class com.example.model.* -keepclassmembers class com.example.ui.* {"
            + " void onUserLoaded(com.example.model.<1>); }";
    assertCoverage(
        facts,
        memberRule,
        KeepRequirement.forMember(
            "com.example.ui.MainActivity",
            MemberSignature.method(
                "void", "onUserLoaded", Collections.singletonList("com.example.model.User")),
            false,
            false),
        Coverage.CONDITIONAL,
        "applies if com.example.model.User is kept");
    assertCoverage(
        facts,
        memberRule,
        KeepRequirement.forMember(
            "com.example.ui.MainActivity",
            MemberSignature.method("void", "onUserLoaded", Collections.emptyList()),
            false,
            false),
        Coverage.NONE,
        null);
    assertCoverage(
        facts,
        memberRule,
        KeepRequirement.forMember(
            "com.example.ui.MainActivity",
            MemberSignature.method(
                "void", "otherMethod", Collections.singletonList("com.example.model.User")),
            false,
            false),
        Coverage.NONE,
        null);
  }

  private static void assertCoverage(
      KeepRequirements facts,
      String ruleText,
      KeepRequirement requirement,
      Coverage expectedCoverage,
      String expectedReason)
      throws Exception {
    KeepRuleSet rules = KeepRuleSet.parseString(ruleText);
    KeepRuleMatcher matcher = new KeepRuleMatcher(rules, facts);
    RuleCoverage coverage = matcher.check(requirement, rules.getRules().get(0));
    assertEquals(expectedCoverage, coverage.getCoverage());
    assertEquals(expectedReason, coverage.getReason());
  }

  private static ProguardKeepRuleBase getKeepRule(ProguardConfigurationRule rule) {
    if (rule.isProguardIfRule()) {
      // Back references cannot be matched without matching the precondition.
      return !rule.asProguardIfRule().getSubsequentRule().hasBackReferences()
          ? rule.asProguardIfRule().getSubsequentRule()
          : null;
    }
    return rule.isProguardKeepRule() ? rule.asProguardKeepRule() : null;
  }

  private static List<String> getCandidates(KeepRuleSet rules, String className) {
    List<String> result = new ArrayList<>();
    for (KeepRuleSet.Rule rule : rules.getCandidateRules(className)) {
      result.add(rule.getSourceText().replaceAll("\\s+", " "));
    }
    return result;
  }
}

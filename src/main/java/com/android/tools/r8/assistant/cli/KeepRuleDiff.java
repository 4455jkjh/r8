// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.assistant.cli.KeepRuleMatcher.Coverage;
import com.android.tools.r8.assistant.cli.KeepRuleMatcher.RequirementCoverage;
import com.android.tools.r8.assistant.cli.KeepRuleMatcher.RuleCoverage;
import com.android.tools.r8.assistant.cli.KeepRuleWriter.RenderedRule;
import com.android.tools.r8.shaking.ProguardKeepRule;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Compares keep rules with the requirements in reflection logs: which rules are justified by the
 * logs, which can be narrowed to what the logs show, which keep nothing that was observed, and
 * which requirements the rules do not cover.
 */
final class KeepRuleDiff {

  /** The verdict for a single rule. */
  enum Verdict {
    // The rule keeps exactly what the logs require.
    JUSTIFIED("justified"),
    // The rule keeps more than the logs require and can be replaced by narrower rules.
    NARROWED("narrowed"),
    // Nothing the rule keeps was used reflectively in the logged runs.
    NOT_OBSERVED("not observed"),
    // What the rule keeps cannot be decided from the logs, so it is kept as is.
    UNDECIDED("undecided"),
    // A -if rule, which is kept as is.
    CONDITIONAL("conditional"),
    // Not a keep rule, e.g. -assumenosideeffects, which is kept as is.
    NOT_ANALYZED("not analyzed");

    private final String label;

    Verdict(String label) {
      this.label = label;
    }

    String getLabel() {
      return label;
    }
  }

  /** A requirement and how a rule covers it. */
  private static final class RuleMatch {

    private final KeepRequirement requirement;
    private final RuleCoverage coverage;

    private RuleMatch(KeepRequirement requirement, RuleCoverage coverage) {
      this.requirement = requirement;
      this.coverage = coverage;
    }
  }

  /** The analysis of a single rule. */
  static final class RuleResult {

    private final KeepRuleSet.Rule rule;
    private final Verdict verdict;
    private final String reason;
    private final List<KeepRequirement> covered;
    private final List<String> replacement;

    private RuleResult(
        KeepRuleSet.Rule rule,
        Verdict verdict,
        String reason,
        List<KeepRequirement> covered,
        List<String> replacement) {
      this.rule = rule;
      this.verdict = verdict;
      this.reason = reason;
      this.covered = covered;
      this.replacement = replacement;
    }

    KeepRuleSet.Rule getRule() {
      return rule;
    }

    Verdict getVerdict() {
      return verdict;
    }

    /** A short explanation of the verdict. */
    String getReason() {
      return reason;
    }

    /** The requirements the rule covers. */
    List<KeepRequirement> getCovered() {
      return covered;
    }

    /** For {@link Verdict#NARROWED}, the rules replacing the rule. */
    List<String> getReplacement() {
      return replacement;
    }
  }

  private final ParsedLogs logs;
  private final KeepRequirements requirements;
  private final List<RuleResult> ruleResults = new ArrayList<>();
  private final List<RequirementCoverage> coverages = new ArrayList<>();
  private final List<RequirementCoverage> missing;
  private final List<RenderedRule> additionalRules;
  private final boolean addNestingAttributes;
  private final boolean addSignatureAttribute;
  private final String proposedRules;

  private KeepRuleDiff(ParsedLogs logs, KeepRuleSet ruleSet, String ruleFiles) {
    this.logs = logs;
    this.requirements = KeepRequirements.analyze(logs.getEvents(), logs::threw);
    KeepRuleMatcher matcher = new KeepRuleMatcher(ruleSet, requirements);
    List<KeepRequirement> all = requirements.getRequirements();
    for (KeepRequirement requirement : all) {
      coverages.add(matcher.cover(requirement));
    }
    for (KeepRuleSet.Rule rule : ruleSet.getRules()) {
      ruleResults.add(analyzeRule(rule, all, matcher));
    }
    missing = KeepRuleMatcher.getMissing(coverages);
    List<KeepRequirement> missingRequirements = new ArrayList<>();
    for (RequirementCoverage coverage : missing) {
      missingRequirements.add(coverage.getRequirement());
    }
    additionalRules = KeepRuleWriter.render(missingRequirements);
    addNestingAttributes =
        !requirements.getNestingAttributeEvents().isEmpty() && !ruleSet.keepsNestingAttributes();
    addSignatureAttribute =
        !requirements.getSignatureAttributeEvents().isEmpty() && !ruleSet.keepsSignatureAttribute();
    proposedRules = renderProposedRules(ruleFiles);
  }

  static KeepRuleDiff compute(ParsedLogs logs, KeepRuleSet ruleSet, String ruleFiles) {
    return new KeepRuleDiff(logs, ruleSet, ruleFiles);
  }

  private RuleResult analyzeRule(
      KeepRuleSet.Rule rule, List<KeepRequirement> all, KeepRuleMatcher matcher) {
    List<RuleMatch> covered = new ArrayList<>();
    Set<String> possibleReasons = new LinkedHashSet<>();
    if (!rule.isKeepRule()) {
      return new RuleResult(
          rule,
          Verdict.NOT_ANALYZED,
          "not a keep rule",
          Collections.emptyList(),
          Collections.emptyList());
    }
    for (KeepRequirement requirement : all) {
      RuleCoverage coverage = matcher.check(requirement, rule);
      if (coverage.getCoverage() == Coverage.NONE
          && requirement.isAllMembers()
          && requirement.hasResultMembers()) {
        // The rule may keep some of the members returned by the bulk lookup.
        for (KeepRequirement member : KeepRuleMatcher.expandResultMembers(requirement)) {
          classify(member, matcher.check(member, rule), covered, possibleReasons);
        }
      } else {
        classify(requirement, coverage, covered, possibleReasons);
      }
    }
    List<KeepRequirement> coveredRequirements = new ArrayList<>();
    for (RuleMatch match : covered) {
      coveredRequirements.add(match.requirement);
    }
    if (rule.get().isProguardIfRule()) {
      return new RuleResult(
          rule,
          Verdict.CONDITIONAL,
          "-if rules are kept as is",
          coveredRequirements,
          Collections.emptyList());
    }
    ProguardKeepRule keepRule = rule.get().asProguardKeepRule();
    String selection = KeepRuleMatcher.getSelectionNotInLog(keepRule);
    if (selection != null) {
      return new RuleResult(
          rule,
          Verdict.UNDECIDED,
          "the rule " + selection + ", which the logs do not show",
          coveredRequirements,
          Collections.emptyList());
    }
    if (!possibleReasons.isEmpty()) {
      return new RuleResult(
          rule,
          Verdict.UNDECIDED,
          String.join("; ", possibleReasons),
          coveredRequirements,
          Collections.emptyList());
    }
    if (covered.isEmpty()) {
      return new RuleResult(
          rule,
          Verdict.NOT_OBSERVED,
          "nothing it keeps was used reflectively in the logged runs",
          coveredRequirements,
          Collections.emptyList());
    }
    List<String> replacement = new ArrayList<>();
    boolean justified = computeReplacement(keepRule, covered, matcher, replacement);
    if (justified) {
      return new RuleResult(
          rule,
          Verdict.JUSTIFIED,
          "keeps exactly what the logs require",
          coveredRequirements,
          Collections.emptyList());
    }
    return new RuleResult(
        rule,
        Verdict.NARROWED,
        "keeps more than the logs require",
        coveredRequirements,
        replacement);
  }

  private static void classify(
      KeepRequirement requirement,
      RuleCoverage coverage,
      List<RuleMatch> covered,
      Set<String> possibleReasons) {
    switch (coverage.getCoverage()) {
      case COVERED:
      case PARTIAL:
        covered.add(new RuleMatch(requirement, coverage));
        break;
      case CONDITIONAL:
      case UNDECIDED:
        possibleReasons.add(coverage.getReason());
        break;
      case NONE:
        break;
    }
  }

  /**
   * Computes the rules that keep only the covered requirements, with the type, modifiers and class
   * type of the original rule. Returns true if they are equivalent to the original rule.
   */
  private static boolean computeReplacement(
      ProguardKeepRule keepRule,
      List<RuleMatch> covered,
      KeepRuleMatcher matcher,
      List<String> replacement) {
    Map<String, Set<KeepRequirement>> byClass = new TreeMap<>();
    Set<String> partialClasses = new HashSet<>();
    boolean anyPartial = false;
    for (RuleMatch match : covered) {
      KeepRequirement requirement = match.requirement;
      String base = requirement.getBundleBaseName();
      String key = base != null ? base + ", " + base + "_*" : requirement.getClassName();
      byClass.computeIfAbsent(key, k -> new TreeSet<>()).add(requirement);
      if (match.coverage.getCoverage() == Coverage.PARTIAL) {
        partialClasses.add(key);
        anyPartial = true;
      }
    }
    Map<String, Set<String>> membersByClass = new TreeMap<>();
    byClass.forEach(
        (className, classRequirements) -> {
          String keyword =
              getKeyword(keepRule, classRequirements, partialClasses.contains(className))
                  + " "
                  + getClassType(keepRule);
          List<String> memberLines =
              KeepRuleWriter.renderMembers(
                  new ArrayList<>(classRequirements),
                  requirement -> getMemberLine(requirement, keepRule, matcher));
          replacement.add(KeepRuleWriter.renderRule(keyword, className, memberLines));
          Set<String> normalized = new TreeSet<>();
          for (String line : memberLines) {
            normalized.add(normalizeMember(line));
          }
          membersByClass.put(className, normalized);
        });
    return !anyPartial && isEquivalent(keepRule, membersByClass);
  }

  /** The member line for a requirement, reusing the original member rule if it is specific. */
  private static String getMemberLine(
      KeepRequirement requirement, ProguardKeepRule keepRule, KeepRuleMatcher matcher) {
    if (requirement.isMember()) {
      for (ProguardMemberRule memberRule : matcher.getMatchingMemberRules(requirement, keepRule)) {
        if (isSpecific(memberRule)) {
          return formatMemberRule(memberRule).replace(",", ", ") + ";";
        }
      }
    }
    return requirement.toRuleMember();
  }

  private static String formatMemberRule(ProguardMemberRule memberRule) {
    String text = memberRule.toString();
    if (!memberRule.getNegatedAccessFlags().isDefaultFlags()) {
      String negated = memberRule.getNegatedAccessFlags().toString().replace(" ", " !");
      String positive = memberRule.getAccessFlags().toString();
      int offset = positive.isEmpty() ? 0 : positive.length() + 1;
      if (text.startsWith(negated, offset)) {
        text = text.substring(0, offset) + "!" + text.substring(offset);
      }
    }
    switch (memberRule.getRuleType()) {
      case FIELD:
      case ALL_FIELDS:
        // Constants.ACC_VOLATILE and Constants.ACC_BRIDGE share the 0x40 bit in
        // ProguardAccessFlags.
        return text.replace("volatile bridge ", "volatile ")
            .replace("!volatile !bridge ", "!volatile ");
      default:
        return text;
    }
  }

  private static boolean isSpecific(ProguardMemberRule memberRule) {
    if (!memberRule.isSpecific()) {
      return false;
    }
    if (memberRule.getArguments() != null) {
      for (ProguardTypeMatcher argument : memberRule.getArguments()) {
        if (argument.isTripleDotPattern()) {
          return false;
        }
      }
    }
    return true;
  }

  private static boolean isEquivalent(
      ProguardKeepRule keepRule, Map<String, Set<String>> membersByClass) {
    Set<String> classNames = new TreeSet<>();
    for (String pattern : keepRule.getClassNames().toString().split(",", -1)) {
      classNames.add(pattern.trim());
    }
    Set<String> expectedClassNames = new TreeSet<>();
    for (String key : membersByClass.keySet()) {
      for (String pattern : key.split(",", -1)) {
        expectedClassNames.add(pattern.trim());
      }
    }
    if (!classNames.equals(expectedClassNames)) {
      return false;
    }
    Set<String> members = new TreeSet<>();
    for (ProguardMemberRule memberRule : keepRule.getMemberRules()) {
      members.add(normalizeMember(formatMemberRule(memberRule)));
    }
    for (Set<String> classMembers : membersByClass.values()) {
      if (!classMembers.equals(members)) {
        return false;
      }
    }
    return true;
  }

  private static String normalizeMember(String line) {
    String text = line.trim();
    if (text.endsWith(";")) {
      text = text.substring(0, text.length() - 1);
    }
    return text.replaceAll("\\s*,\\s*", ",").replaceAll("\\s+", " ").trim();
  }

  private static String getKeyword(
      ProguardKeepRule keepRule, Set<KeepRequirement> classRequirements, boolean hasPartial) {
    if (!hasPartial) {
      return getKeyword(keepRule);
    }
    boolean allowShrinking = true;
    for (KeepRequirement requirement : classRequirements) {
      if (requirement.getKind() == KeepRequirement.Kind.CLASS || !requirement.isClassLevel()) {
        allowShrinking = false;
        break;
      }
    }
    if (allowShrinking) {
      return "-keepnames";
    }
    return "-" + keepRule.getType().toString();
  }

  /** The rule keyword with modifiers, e.g. {@code -keepnames} or {@code -keep,allowobfuscation}. */
  static String getKeyword(ProguardKeepRule keepRule) {
    String modifiers = keepRule.getModifiers().toString();
    if (modifiers.equals("allowshrinking")) {
      switch (keepRule.getType()) {
        case KEEP:
          return "-keepnames";
        case KEEP_CLASS_MEMBERS:
          return "-keepclassmembernames";
        case KEEP_CLASSES_WITH_MEMBERS:
          return "-keepclasseswithmembernames";
        default:
          break;
      }
    }
    return "-" + keepRule.getType().toString() + (modifiers.isEmpty() ? "" : "," + modifiers);
  }

  private static String getClassType(ProguardKeepRule keepRule) {
    return (keepRule.getClassTypeNegated() ? "!" : "") + keepRule.getClassType().toString();
  }

  private String renderProposedRules(String ruleFiles) {
    StringBuilder builder = new StringBuilder();
    builder.append("# Keep rules proposed by the R8 Assistant\n");
    builder.append("# Replaces the rules of: ").append(ruleFiles).append('\n');
    builder.append("# Options such as -keepattributes and -dontwarn are not included; copy them\n");
    builder.append("# from the original files.\n");
    Set<String> emitted = new HashSet<>();
    for (RuleResult result : ruleResults) {
      KeepRuleSet.Rule rule = result.getRule();
      builder.append('\n');
      switch (result.getVerdict()) {
        case JUSTIFIED:
          builder.append("# Justified: ").append(rule.getLocation()).append('\n');
          appendRule(builder, rule.getSourceText(), emitted);
          break;
        case NARROWED:
          builder.append("# Narrowed: ").append(rule).append('\n');
          for (String text : result.getReplacement()) {
            appendRule(builder, text, emitted);
          }
          break;
        case NOT_OBSERVED:
          builder.append("# Removed, not observed in the logs: ").append(rule).append('\n');
          break;
        case UNDECIDED:
          builder
              .append("# Undecided, kept as is: ")
              .append(rule.getLocation())
              .append(" (")
              .append(result.getReason())
              .append(")\n");
          appendRule(builder, rule.getSourceText(), emitted);
          break;
        case CONDITIONAL:
          builder.append("# Conditional, kept as is: ").append(rule.getLocation()).append('\n');
          appendRule(builder, rule.getSourceText(), emitted);
          break;
        case NOT_ANALYZED:
          builder.append("# Not analyzed, kept as is: ").append(rule.getLocation()).append('\n');
          appendRule(builder, rule.getSourceText(), emitted);
          break;
      }
    }
    Map<KeepRequirement, RequirementCoverage> missingCoverages = getMissingCoverages();
    for (RenderedRule rule : additionalRules) {
      builder.append('\n');
      builder.append(describeAddition(rule, missingCoverages)).append('\n');
      for (String comment : rule.getComments()) {
        builder.append(comment).append('\n');
      }
      appendRule(builder, rule.getText(), emitted);
    }
    if (addNestingAttributes) {
      builder.append('\n');
      builder.append("# Added: needed for getSimpleName(), getCanonicalName() and the nesting\n");
      builder.append("# flags of nested classes\n");
      builder.append(KeepRuleWriter.NESTING_ATTRIBUTES_RULE).append('\n');
    }
    if (addSignatureAttribute) {
      builder.append('\n');
      builder.append("# Added: needed for getGenericSuperclass() and getGenericInterfaces()\n");
      builder.append(KeepRuleWriter.SIGNATURE_ATTRIBUTE_RULE).append('\n');
    }
    return builder.toString();
  }

  private static void appendRule(StringBuilder builder, String text, Set<String> emitted) {
    if (emitted.add(text.replaceAll("\\s+", " ").trim())) {
      builder.append(text).append('\n');
    } else {
      builder.append("# (same rule as above)\n");
    }
  }

  private Map<KeepRequirement, RequirementCoverage> getMissingCoverages() {
    Map<KeepRequirement, RequirementCoverage> result = new IdentityHashMap<>();
    for (RequirementCoverage coverage : missing) {
      result.put(coverage.getRequirement(), coverage);
    }
    return result;
  }

  private static String describeAddition(
      RenderedRule rule, Map<KeepRequirement, RequirementCoverage> missingCoverages) {
    boolean notCovered = false;
    for (KeepRequirement requirement : rule.getRequirements()) {
      RequirementCoverage coverage = missingCoverages.get(requirement);
      if (coverage == null
          || coverage.getCoverage() == Coverage.NONE
          || coverage.getCoverage() == Coverage.PARTIAL) {
        notCovered = true;
      }
    }
    return notCovered
        ? "# Added: not kept by the existing rules"
        : "# Added: the existing rules may keep this, but the logs cannot show it";
  }

  ParsedLogs getLogs() {
    return logs;
  }

  List<RuleResult> getRuleResults() {
    return ruleResults;
  }

  List<RuleResult> getRuleResults(Verdict verdict) {
    List<RuleResult> result = new ArrayList<>();
    for (RuleResult ruleResult : ruleResults) {
      if (ruleResult.getVerdict() == verdict) {
        result.add(ruleResult);
      }
    }
    return result;
  }

  /** The coverage of each requirement by all rules. */
  List<RequirementCoverage> getCoverages() {
    return coverages;
  }

  int countCoverages(Coverage coverage) {
    int count = 0;
    for (RequirementCoverage requirementCoverage : coverages) {
      if (requirementCoverage.getCoverage() == coverage) {
        count++;
      }
    }
    return count;
  }

  /** The requirements that the rules do not definitely cover. */
  List<RequirementCoverage> getMissing() {
    return missing;
  }

  boolean needsNestingAttributes() {
    return addNestingAttributes;
  }

  boolean needsSignatureAttribute() {
    return addSignatureAttribute;
  }

  String getProposedRules() {
    return proposedRules;
  }
}

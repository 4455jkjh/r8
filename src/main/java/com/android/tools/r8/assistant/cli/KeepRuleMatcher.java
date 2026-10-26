// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.assistant.cli.KeepRequirements.KnownMember;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardAccessFlags;
import com.android.tools.r8.shaking.ProguardClassType;
import com.android.tools.r8.shaking.ProguardIfRule;
import com.android.tools.r8.shaking.ProguardKeepRule;
import com.android.tools.r8.shaking.ProguardKeepRuleBase;
import com.android.tools.r8.shaking.ProguardKeepRuleModifiers;
import com.android.tools.r8.shaking.ProguardKeepRuleType;
import com.android.tools.r8.shaking.ProguardMemberRule;
import com.android.tools.r8.shaking.ProguardMemberType;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Decides whether keep rules cover keep requirements, using R8's class name, type and name
 * matchers.
 *
 * <p>The reflection log has no access flags, annotations or class hierarchy, so the matching is
 * conservative: a rule only covers a requirement when that can be decided from the log. When it
 * depends on information that is not in the log, the coverage is {@link Coverage#UNDECIDED} and the
 * reason says what is missing.
 */
final class KeepRuleMatcher {

  /** How a rule covers a requirement, from best to worst. */
  enum Coverage {
    // The rule keeps what the requirement needs.
    COVERED,
    // A -if rule that keeps what the requirement needs when its precondition holds.
    CONDITIONAL,
    // The rule may keep what the requirement needs; the log does not have enough information.
    UNDECIDED,
    // The rule matches but does not keep everything the requirement needs.
    PARTIAL,
    // The rule does not keep what the requirement needs.
    NONE
  }

  /** The coverage of a requirement by a single rule. */
  static final class RuleCoverage {

    private final KeepRuleSet.Rule rule;
    private final Coverage coverage;
    private final String reason;

    private RuleCoverage(KeepRuleSet.Rule rule, Coverage coverage, String reason) {
      this.rule = rule;
      this.coverage = coverage;
      this.reason = reason;
    }

    KeepRuleSet.Rule getRule() {
      return rule;
    }

    Coverage getCoverage() {
      return coverage;
    }

    /** Why the rule does not definitely cover the requirement, or null if it does. */
    String getReason() {
      return reason;
    }

    String describe() {
      String verb;
      switch (coverage) {
        case CONDITIONAL:
          verb = "conditionally kept by ";
          break;
        case PARTIAL:
          verb = "partially kept by ";
          break;
        case COVERED:
          verb = "kept by ";
          break;
        default:
          verb = "possibly kept by ";
          break;
      }
      return verb + rule.getLocation() + (reason == null ? "" : ": " + reason);
    }

    @Override
    public String toString() {
      return coverage + " " + rule + (reason == null ? "" : " (" + reason + ")");
    }
  }

  /** The coverage of a requirement by all rules. */
  static final class RequirementCoverage {

    private final KeepRequirement requirement;
    private final Coverage coverage;
    private final List<RuleCoverage> ruleCoverages;
    private final List<RequirementCoverage> memberCoverages;

    private RequirementCoverage(
        KeepRequirement requirement,
        Coverage coverage,
        List<RuleCoverage> ruleCoverages,
        List<RequirementCoverage> memberCoverages) {
      this.requirement = requirement;
      this.coverage = coverage;
      this.ruleCoverages = ruleCoverages;
      this.memberCoverages = memberCoverages;
    }

    KeepRequirement getRequirement() {
      return requirement;
    }

    Coverage getCoverage() {
      return coverage;
    }

    boolean isCovered() {
      return coverage == Coverage.COVERED;
    }

    /** The rules that cover the requirement, possibly or partially; NONE results are omitted. */
    List<RuleCoverage> getRuleCoverages() {
      return ruleCoverages;
    }

    /**
     * For all-members requirements with known results, the coverage of each returned member, or an
     * empty list if a single rule covers all members.
     */
    List<RequirementCoverage> getMemberCoverages() {
      return memberCoverages;
    }
  }

  /** The tri-state result of matching against information that may not be in the log. */
  private static final class Match {

    private static final Match YES = new Match(true, null);
    private static final Match NO = new Match(false, null);

    private final boolean matches;
    private final String undecidedReason;

    private Match(boolean matches, String undecidedReason) {
      this.matches = matches;
      this.undecidedReason = undecidedReason;
    }

    static Match maybe(String reason) {
      return new Match(false, reason);
    }

    static Match of(List<String> undecidedReasons) {
      return undecidedReasons.isEmpty() ? YES : maybe(String.join("; ", undecidedReasons));
    }

    boolean isYes() {
      return matches;
    }

    boolean isNo() {
      return !matches && undecidedReason == null;
    }

    boolean isMaybe() {
      return undecidedReason != null;
    }
  }

  private final KeepRuleSet rules;
  private final KeepRequirements facts;

  KeepRuleMatcher(KeepRuleSet rules, KeepRequirements facts) {
    this.rules = rules;
    this.facts = facts;
  }

  /** Computes the coverage of a requirement by all rules. */
  RequirementCoverage cover(KeepRequirement requirement) {
    List<RuleCoverage> ruleCoverages = new ArrayList<>();
    Coverage best = Coverage.NONE;
    for (KeepRuleSet.Rule rule : rules.getRules()) {
      RuleCoverage ruleCoverage = check(requirement, rule);
      if (ruleCoverage.getCoverage() != Coverage.NONE) {
        ruleCoverages.add(ruleCoverage);
        if (ruleCoverage.getCoverage().compareTo(best) < 0) {
          best = ruleCoverage.getCoverage();
        }
      }
    }
    List<RequirementCoverage> memberCoverages = new ArrayList<>();
    if (best != Coverage.COVERED
        && requirement.isAllMembers()
        && requirement.hasResultMembers()
        && !requirement.getResultMembers().isEmpty()) {
      // Without a rule keeping all members, each returned member must be kept by some rule.
      boolean allCovered = true;
      boolean anyUncovered = false;
      Coverage bestNonCovered = Coverage.NONE;
      for (KeepRequirement member : expandResultMembers(requirement)) {
        RequirementCoverage memberCoverage = cover(member);
        memberCoverages.add(memberCoverage);
        if (!memberCoverage.isCovered()) {
          allCovered = false;
          if (memberCoverage.getCoverage() == Coverage.NONE) {
            anyUncovered = true;
          } else if (memberCoverage.getCoverage().compareTo(bestNonCovered) < 0) {
            bestNonCovered = memberCoverage.getCoverage();
          }
        }
      }
      if (allCovered) {
        best = Coverage.COVERED;
      } else if (anyUncovered) {
        best = Coverage.NONE;
      } else if (bestNonCovered.compareTo(best) < 0) {
        best = bestNonCovered;
      }
    }
    return new RequirementCoverage(requirement, best, ruleCoverages, memberCoverages);
  }

  /**
   * The member requirements returned by a bulk lookup, as member requirements justified by the same
   * events.
   */
  static List<KeepRequirement> expandResultMembers(KeepRequirement requirement) {
    List<KeepRequirement> result = new ArrayList<>();
    for (MemberSignature member : requirement.getResultMembers()) {
      KeepRequirement memberRequirement =
          KeepRequirement.forMember(
              requirement.getClassName(), member, requirement.isPublicOnly(), false);
      for (ReflectiveEvent event : requirement.getEvents()) {
        memberRequirement.addEvent(event);
      }
      result.add(memberRequirement);
    }
    return result;
  }

  /**
   * The coverages of the requirements that are not definitely covered. For a bulk lookup of which
   * some returned members are covered, only the members that are not covered are included.
   */
  static List<RequirementCoverage> getMissing(List<RequirementCoverage> coverages) {
    List<RequirementCoverage> missing = new ArrayList<>();
    for (RequirementCoverage coverage : coverages) {
      if (coverage.isCovered()) {
        continue;
      }
      boolean someMembersCovered = false;
      for (RequirementCoverage memberCoverage : coverage.getMemberCoverages()) {
        someMembersCovered |= memberCoverage.isCovered();
      }
      if (!someMembersCovered) {
        missing.add(coverage);
        continue;
      }
      for (RequirementCoverage memberCoverage : coverage.getMemberCoverages()) {
        if (!memberCoverage.isCovered()) {
          missing.add(memberCoverage);
        }
      }
    }
    return missing;
  }

  /** Computes the coverage of a requirement by a single rule. */
  RuleCoverage check(KeepRequirement requirement, KeepRuleSet.Rule rule) {
    if (rule.get().isProguardIfRule()) {
      return checkIfRule(requirement, rule.get().asProguardIfRule(), rule);
    }
    if (rule.get().isProguardKeepRule()) {
      return checkKeepRule(requirement, rule.get().asProguardKeepRule(), rule);
    }
    return new RuleCoverage(rule, Coverage.NONE, null);
  }

  private RuleCoverage checkIfRule(
      KeepRequirement requirement, ProguardIfRule ifRule, KeepRuleSet.Rule rule) {
    ProguardKeepRule subsequentRule = ifRule.getSubsequentRule();
    boolean hasBackReferences = subsequentRule.getBackReferences().iterator().hasNext();
    Set<String> candidates = new TreeSet<>(facts.getObservedClasses());
    candidates.add(requirement.getClassName());
    for (String candidate : candidates) {
      DexType candidateType = rules.getType(candidate);
      if (candidateType == null || !ifRule.getClassNames().matches(candidateType)) {
        continue;
      }
      // The class names of the precondition have been matched, so back references in the
      // subsequent rule refer to the matched candidate.
      RuleCoverage subsequentCoverage = checkKeepRule(requirement, subsequentRule, rule);
      if (subsequentCoverage.getCoverage() != Coverage.NONE) {
        String condition = "applies if " + candidate + " is kept";
        if (ifRule.hasInheritanceClassName()
            || !ifRule.getMemberRules().isEmpty()
            || !ifRule.getClassAnnotations().isEmpty()
            || !ifRule.getClassAccessFlags().isDefaultFlags()
            || !ifRule.getNegatedClassAccessFlags().isDefaultFlags()) {
          condition += " and its -if precondition holds (cannot be verified from the log alone)";
        }
        return conditional(subsequentCoverage, condition);
      }
      if (!hasBackReferences) {
        return subsequentCoverage;
      }
    }
    if (hasBackReferences) {
      return new RuleCoverage(rule, Coverage.NONE, null);
    }
    RuleCoverage subsequentCoverage = checkKeepRule(requirement, subsequentRule, rule);
    if (subsequentCoverage.getCoverage() == Coverage.NONE) {
      return subsequentCoverage;
    }
    return conditional(subsequentCoverage, "applies only if a class not seen in the log is kept");
  }

  private static RuleCoverage conditional(RuleCoverage subsequentCoverage, String condition) {
    String reason =
        subsequentCoverage.getReason() == null
            ? condition
            : condition + "; " + subsequentCoverage.getReason();
    Coverage coverage =
        subsequentCoverage.getCoverage() == Coverage.COVERED
            ? Coverage.CONDITIONAL
            : subsequentCoverage.getCoverage();
    return new RuleCoverage(subsequentCoverage.getRule(), coverage, reason);
  }

  private RuleCoverage checkKeepRule(
      KeepRequirement requirement, ProguardKeepRule keepRule, KeepRuleSet.Rule rule) {
    RuleCoverage none = new RuleCoverage(rule, Coverage.NONE, null);
    ProguardKeepRuleType type = keepRule.getType();
    if (type != ProguardKeepRuleType.KEEP
        && type != ProguardKeepRuleType.KEEP_CLASS_MEMBERS
        && type != ProguardKeepRuleType.KEEP_CLASSES_WITH_MEMBERS) {
      return none;
    }
    if (requirement.isClassLevel() && type == ProguardKeepRuleType.KEEP_CLASS_MEMBERS) {
      return none;
    }
    String className = requirement.getClassName();
    DexType classType = rules.getType(className);
    if (classType == null || !keepRule.getClassNames().matches(classType)) {
      return none;
    }
    List<String> undecided = new ArrayList<>();
    Match classTypeMatch = matchClassType(keepRule, className);
    if (classTypeMatch.isNo()) {
      return none;
    }
    addReason(classTypeMatch, undecided);
    if (!keepRule.getClassAccessFlags().isDefaultFlags()
        || !keepRule.getNegatedClassAccessFlags().isDefaultFlags()) {
      undecided.add("class modifiers are not in the log");
    }
    if (!keepRule.getClassAnnotations().isEmpty()) {
      undecided.add("class annotations are not in the log");
    }
    if (keepRule.hasInheritanceClassName()) {
      Match inheritanceMatch = matchInheritance(keepRule, className);
      if (inheritanceMatch.isNo()) {
        return none;
      }
      addReason(inheritanceMatch, undecided);
    }
    if (type == ProguardKeepRuleType.KEEP_CLASSES_WITH_MEMBERS) {
      addReason(matchClassHasMembers(keepRule, className), undecided);
    }
    List<String> partial = new ArrayList<>();
    ProguardKeepRuleModifiers modifiers = keepRule.getModifiers();
    if (requirement.isClassLevel()) {
      // Any keep rule for the class keeps its generic signature and keeps it from being merged.
      if (requirement.getKind() == KeepRequirement.Kind.CLASS
          || requirement.getKind() == KeepRequirement.Kind.NAME) {
        if (requirement.getKind() == KeepRequirement.Kind.CLASS && modifiers.allowsShrinking) {
          partial.add("allows shrinking, so the class is removed if it is otherwise unused");
        }
        if (modifiers.allowsObfuscation) {
          partial.add("allows obfuscation, so the class can be renamed");
        }
        if (modifiers.allowsRepackaging) {
          partial.add("allows repackaging, so the class can be moved to another package");
        }
      }
    } else {
      Match memberMatch;
      if (requirement.isMember()) {
        memberMatch =
            matchMemberRules(
                keepRule.getMemberRules(),
                requirement.getMember(),
                requirement.isPublic(),
                requirement.isVolatile());
      } else {
        memberMatch = matchAllMembers(keepRule.getMemberRules(), requirement);
      }
      if (memberMatch.isNo()) {
        return none;
      }
      addReason(memberMatch, undecided);
      if (modifiers.allowsShrinking) {
        partial.add("allows shrinking, so the members are removed if they are otherwise unused");
      }
      if (modifiers.allowsObfuscation) {
        partial.add("allows obfuscation, so the members can be renamed");
      }
    }
    if (!undecided.isEmpty()) {
      undecided.addAll(partial);
      return new RuleCoverage(rule, Coverage.UNDECIDED, String.join("; ", undecided));
    }
    if (!partial.isEmpty()) {
      return new RuleCoverage(rule, Coverage.PARTIAL, String.join("; ", partial));
    }
    return new RuleCoverage(rule, Coverage.COVERED, null);
  }

  private static void addReason(Match match, List<String> reasons) {
    if (match.isMaybe()) {
      reasons.add(match.undecidedReason);
    }
  }

  private Match matchClassType(ProguardKeepRuleBase keepRule, String className) {
    ProguardClassType classType = keepRule.getClassType();
    boolean negated = keepRule.getClassTypeNegated();
    String flag = getClassFlagName(classType);
    if (flag == null) {
      return negated ? Match.maybe("the class type is not in the log") : Match.YES;
    }
    Boolean value = facts.getClassFlag(className, flag);
    if (value == null) {
      return Match.maybe(
          "whether the class is "
              + (negated ? "not " : "")
              + "'"
              + classType.toString()
              + "' is not in the log");
    }
    return value != negated ? Match.YES : Match.NO;
  }

  /** The {@code CLASS_FLAG} name for the class type, or null for {@code class}. */
  private static String getClassFlagName(ProguardClassType classType) {
    switch (classType) {
      case INTERFACE:
        return "INTERFACE";
      case ENUM:
        return "ENUM";
      case ANNOTATION_INTERFACE:
        return "ANNOTATION";
      default:
        return null;
    }
  }

  /**
   * Matches the extends or implements clause of a rule against the supertypes of the class that the
   * log shows, such as the results of getSuperclass() and getGenericInterfaces().
   */
  private Match matchInheritance(ProguardKeepRuleBase keepRule, String className) {
    String clause =
        (keepRule.getInheritanceIsExtends() ? "extends " : "implements ")
            + keepRule.getInheritanceClassName();
    if (!keepRule.getInheritanceAnnotations().isEmpty()) {
      return Match.maybe("the annotations of the super types (" + clause + ") are not in the log");
    }
    for (String supertype : facts.getKnownSupertypes(className)) {
      DexType supertypeType = rules.getType(supertype);
      if (supertypeType != null && keepRule.getInheritanceClassName().matches(supertypeType)) {
        return Match.YES;
      }
    }
    return Match.maybe("the class hierarchy (" + clause + ") is not in the log");
  }

  private Match matchClassHasMembers(ProguardKeepRule keepRule, String className) {
    List<KnownMember> knownMembers = facts.getKnownMembers(className);
    for (ProguardMemberRule memberRule : keepRule.getMemberRules()) {
      boolean found = false;
      for (KnownMember knownMember : knownMembers) {
        if (matchMember(
                memberRule,
                knownMember.getSignature(),
                knownMember.isPublic(),
                knownMember.isVolatile())
            .isYes()) {
          found = true;
          break;
        }
      }
      if (!found) {
        return Match.maybe(
            "-keepclasseswithmembers only applies if the class has a member matching '"
                + memberRule
                + "', which the log does not show");
      }
    }
    return Match.YES;
  }

  private Match matchMemberRules(
      List<ProguardMemberRule> memberRules,
      MemberSignature member,
      boolean isPublic,
      boolean isVolatile) {
    String undecidedReason = null;
    for (ProguardMemberRule memberRule : memberRules) {
      Match match = matchMember(memberRule, member, isPublic, isVolatile);
      if (match.isYes()) {
        return match;
      }
      if (match.isMaybe() && undecidedReason == null) {
        undecidedReason = match.undecidedReason;
      }
    }
    return undecidedReason == null ? Match.NO : Match.maybe(undecidedReason);
  }

  /** Matches the member rules against all members of a kind, as needed by a bulk lookup. */
  private Match matchAllMembers(List<ProguardMemberRule> memberRules, KeepRequirement requirement) {
    MemberSignature.Kind kind = requirement.getAllMembersKind();
    String undecidedReason = null;
    boolean hasMemberRulesOfKind = false;
    for (ProguardMemberRule memberRule : memberRules) {
      if (!appliesToKind(memberRule, kind)) {
        continue;
      }
      hasMemberRulesOfKind = true;
      if (!isWildcardFor(memberRule, kind)) {
        continue;
      }
      List<String> reasons = new ArrayList<>();
      if (!memberRule.getAnnotations().isEmpty()) {
        reasons.add("member annotations are not in the log");
      }
      Set<String> flags =
          getFlagNames(memberRule.getAccessFlags(), kind == MemberSignature.Kind.FIELD);
      Set<String> negatedFlags =
          getFlagNames(memberRule.getNegatedAccessFlags(), kind == MemberSignature.Kind.FIELD);
      boolean onlyPublicRequired = flags.size() == 1 && flags.contains("public");
      if (!flags.isEmpty() && !(onlyPublicRequired && requirement.isPublicOnly())) {
        reasons.add("the member modifiers (" + String.join(" ", flags) + ") are not in the log");
      }
      if (!negatedFlags.isEmpty()) {
        reasons.add(
            "the member modifiers (!" + String.join(" !", negatedFlags) + ") are not in the log");
      }
      if (reasons.isEmpty()) {
        return Match.YES;
      }
      if (undecidedReason == null) {
        undecidedReason = String.join("; ", reasons);
      }
    }
    if (undecidedReason != null) {
      return Match.maybe(undecidedReason);
    }
    if (hasMemberRulesOfKind && !requirement.hasResultMembers()) {
      return Match.maybe(
          "the members returned by the bulk lookup are not in the log (no after-call results)");
    }
    return Match.NO;
  }

  private static boolean appliesToKind(ProguardMemberRule memberRule, MemberSignature.Kind kind) {
    switch (memberRule.getRuleType()) {
      case ALL:
        return true;
      case ALL_FIELDS:
      case FIELD:
        return kind == MemberSignature.Kind.FIELD;
      case ALL_METHODS:
      case METHOD:
        return kind != MemberSignature.Kind.FIELD;
      case INIT:
      case CONSTRUCTOR:
        return kind == MemberSignature.Kind.CONSTRUCTOR;
      default:
        return false;
    }
  }

  /** True if the member rule matches all members of the kind, disregarding modifiers. */
  private static boolean isWildcardFor(ProguardMemberRule memberRule, MemberSignature.Kind kind) {
    ProguardMemberType ruleType = memberRule.getRuleType();
    switch (ruleType) {
      case ALL:
        return true;
      case ALL_FIELDS:
        return kind == MemberSignature.Kind.FIELD;
      case ALL_METHODS:
        return kind != MemberSignature.Kind.FIELD;
      case FIELD:
        return kind == MemberSignature.Kind.FIELD
            && isMatchAllNames(memberRule)
            && isMatchAllTypes(memberRule.getType());
      case METHOD:
        return kind != MemberSignature.Kind.FIELD
            && isMatchAllNames(memberRule)
            && isMatchAllTypes(memberRule.getType())
            && isAnyArguments(memberRule.getArguments());
      case INIT:
      case CONSTRUCTOR:
        return kind == MemberSignature.Kind.CONSTRUCTOR
            && (ruleType == ProguardMemberType.INIT || isMatchAllNames(memberRule))
            && isAnyArguments(memberRule.getArguments());
      default:
        return false;
    }
  }

  private static boolean isMatchAllNames(ProguardMemberRule memberRule) {
    if (memberRule.getName() == null) {
      return false;
    }
    String pattern = memberRule.getName().toString();
    return pattern.equals("*") || pattern.equals("**");
  }

  private static boolean isMatchAllTypes(ProguardTypeMatcher matcher) {
    return matcher == null || matcher.toString().equals("***");
  }

  private static boolean isAnyArguments(List<ProguardTypeMatcher> arguments) {
    return arguments != null && arguments.size() == 1 && arguments.get(0).isTripleDotPattern();
  }

  private Match matchMember(
      ProguardMemberRule memberRule, MemberSignature member, boolean isPublic, boolean isVolatile) {
    List<String> undecided = new ArrayList<>();
    switch (memberRule.getRuleType()) {
      case ALL:
        break;
      case ALL_FIELDS:
        if (!member.isField()) {
          return Match.NO;
        }
        break;
      case ALL_METHODS:
        if (member.isField()) {
          return Match.NO;
        }
        break;
      case FIELD:
        {
          if (!member.isField() || !memberRule.getName().matches(member.getName())) {
            return Match.NO;
          }
          Match typeMatch = matchType(memberRule.getType(), member.getType());
          if (typeMatch.isNo()) {
            return Match.NO;
          }
          addReason(typeMatch, undecided);
          break;
        }
      case METHOD:
        {
          if (member.isField() || !memberRule.getName().matches(member.getName())) {
            return Match.NO;
          }
          Match typeMatch = matchType(memberRule.getType(), member.getType());
          if (typeMatch.isNo()) {
            return Match.NO;
          }
          addReason(typeMatch, undecided);
          Match argumentsMatch = matchArguments(memberRule.getArguments(), member.getParameters());
          if (argumentsMatch.isNo()) {
            return Match.NO;
          }
          addReason(argumentsMatch, undecided);
          break;
        }
      case INIT:
      case CONSTRUCTOR:
        {
          if (!member.isConstructor()
              || (memberRule.getName() != null
                  && !memberRule.getName().matches(member.getName()))) {
            return Match.NO;
          }
          Match argumentsMatch = matchArguments(memberRule.getArguments(), member.getParameters());
          if (argumentsMatch.isNo()) {
            return Match.NO;
          }
          addReason(argumentsMatch, undecided);
          break;
        }
      default:
        return Match.NO;
    }
    if (!memberRule.getAnnotations().isEmpty()) {
      undecided.add("member annotations are not in the log");
    }
    if (!matchAccessFlags(memberRule, member.isField(), isPublic, isVolatile, undecided)) {
      return Match.NO;
    }
    return Match.of(undecided);
  }

  private static boolean matchAccessFlags(
      ProguardMemberRule memberRule,
      boolean isField,
      boolean isPublic,
      boolean isVolatile,
      List<String> undecided) {
    Set<String> flags = getFlagNames(memberRule.getAccessFlags(), isField);
    Set<String> negatedFlags = getFlagNames(memberRule.getNegatedAccessFlags(), isField);
    List<String> unknown = new ArrayList<>();
    // Visibility flags match if any of them matches.
    boolean hasVisibility =
        flags.contains("public") || flags.contains("protected") || flags.contains("private");
    if (hasVisibility) {
      if (isPublic) {
        if (!flags.contains("public")) {
          return false;
        }
      } else {
        unknown.add(String.join(" ", visibilityFlags(flags)));
      }
    }
    for (String flag : flags) {
      if (flag.equals("public") || flag.equals("protected") || flag.equals("private")) {
        continue;
      }
      if (!(flag.equals("volatile") && isVolatile)) {
        unknown.add(flag);
      }
    }
    for (String flag : negatedFlags) {
      if (flag.equals("public") || flag.equals("volatile")) {
        if (flag.equals("public") ? isPublic : isVolatile) {
          return false;
        }
        unknown.add("!" + flag);
      } else if (!((flag.equals("protected") || flag.equals("private")) && isPublic)) {
        unknown.add("!" + flag);
      }
    }
    if (!unknown.isEmpty()) {
      undecided.add("the member modifiers (" + String.join(" ", unknown) + ") are not in the log");
    }
    return true;
  }

  private static List<String> visibilityFlags(Set<String> flags) {
    List<String> result = new ArrayList<>();
    for (String flag : Arrays.asList("public", "protected", "private")) {
      if (flags.contains(flag)) {
        result.add(flag);
      }
    }
    return result;
  }

  private static Set<String> getFlagNames(ProguardAccessFlags flags, boolean isField) {
    Set<String> result = new TreeSet<>();
    if (!flags.isDefaultFlags()) {
      for (String flag : flags.toString().split(" ", -1)) {
        if (!flag.isEmpty()) {
          result.add(flag);
        }
      }
      // Constants.ACC_VOLATILE and Constants.ACC_BRIDGE share the 0x40 bit in ProguardAccessFlags.
      result.remove(isField ? "bridge" : "volatile");
    }
    return result;
  }

  private Match matchType(ProguardTypeMatcher matcher, String typeName) {
    if (matcher == null || isMatchAllTypes(matcher)) {
      return Match.YES;
    }
    if (typeName == null || typeName.equals(MemberSignature.UNKNOWN_TYPE)) {
      return Match.maybe("the member type is not in the log");
    }
    DexType type = rules.getType(typeName);
    if (type == null) {
      return Match.maybe("the member type is not in the log");
    }
    return matcher.matches(type) ? Match.YES : Match.NO;
  }

  private Match matchArguments(List<ProguardTypeMatcher> arguments, List<String> parameters) {
    if (isAnyArguments(arguments)) {
      return Match.YES;
    }
    if (parameters == null) {
      return Match.maybe("the parameter types are not in the log");
    }
    if (arguments == null || arguments.size() != parameters.size()) {
      return Match.NO;
    }
    List<String> undecided = new ArrayList<>();
    for (int i = 0; i < parameters.size(); i++) {
      Match match = matchType(arguments.get(i), parameters.get(i));
      if (match.isNo()) {
        return Match.NO;
      }
      addReason(match, undecided);
    }
    return Match.of(undecided);
  }

  /**
   * The member rules of a keep rule that definitely match a member or all-members requirement, so
   * that narrowed rules can reuse them.
   */
  List<ProguardMemberRule> getMatchingMemberRules(
      KeepRequirement requirement, ProguardKeepRuleBase keepRule) {
    List<ProguardMemberRule> result = new ArrayList<>();
    for (ProguardMemberRule memberRule : keepRule.getMemberRules()) {
      Match match;
      if (requirement.isMember()) {
        match =
            matchMember(
                memberRule,
                requirement.getMember(),
                requirement.isPublic(),
                requirement.isVolatile());
      } else if (requirement.isAllMembers()) {
        match = matchAllMembers(Collections.singletonList(memberRule), requirement);
      } else {
        continue;
      }
      if (match.isYes()) {
        result.add(memberRule);
      }
    }
    return result;
  }

  /**
   * Describes how the rule selects classes or members by information that is not in the log, such
   * as the class hierarchy, annotations or native methods, or returns null.
   */
  static String getSelectionNotInLog(ProguardKeepRuleBase keepRule) {
    List<String> reasons = new ArrayList<>();
    if (keepRule.getType() == ProguardKeepRuleType.KEEP_CLASSES_WITH_MEMBERS) {
      reasons.add("selects classes by their members");
    }
    if (!keepRule.getClassAccessFlags().isDefaultFlags()
        || !keepRule.getNegatedClassAccessFlags().isDefaultFlags()) {
      reasons.add("selects classes by modifiers");
    }
    if (!keepRule.getClassAnnotations().isEmpty()) {
      reasons.add("selects classes by annotations");
    }
    if (keepRule.hasInheritanceClassName()) {
      reasons.add("selects classes by their super types");
    }
    boolean memberAnnotations = false;
    boolean nativeMethods = false;
    for (ProguardMemberRule memberRule : keepRule.getMemberRules()) {
      memberAnnotations |= !memberRule.getAnnotations().isEmpty();
      nativeMethods |= memberRule.getAccessFlags().isNative();
    }
    if (memberAnnotations) {
      reasons.add("selects members by annotations");
    }
    if (nativeMethods) {
      reasons.add("keeps native methods, which are called from JNI");
    }
    return reasons.isEmpty() ? null : String.join(", ", reasons);
  }
}

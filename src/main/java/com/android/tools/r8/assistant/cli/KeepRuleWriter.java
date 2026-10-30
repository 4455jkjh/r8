// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.assistant.cli.KeepRequirement.Kind;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/** Renders keep requirements as keep rules, with comments naming the justifying events. */
final class KeepRuleWriter {

  private static final int MAX_EVENTS_IN_COMMENT = 5;

  // Needed for getSimpleName(), getCanonicalName() and the nesting flags of nested classes.
  static final String NESTING_ATTRIBUTES_RULE = "-keepattributes InnerClasses,EnclosingMethod";

  // Needed for getGenericSuperclass() and getGenericInterfaces().
  static final String SIGNATURE_ATTRIBUTE_RULE = "-keepattributes Signature";

  /** A rendered keep rule and the requirements it keeps. */
  static final class RenderedRule {

    private final String text;
    private final List<KeepRequirement> requirements;
    private final List<String> notes;

    private RenderedRule(String text, List<KeepRequirement> requirements, List<String> notes) {
      this.text = text;
      this.requirements = requirements;
      this.notes = notes;
    }

    /** The rule, e.g. {@code -keep class com.example.Foo { <init>(); }} over multiple lines. */
    String getText() {
      return text;
    }

    List<KeepRequirement> getRequirements() {
      return requirements;
    }

    /** The comment lines naming the events that justify the rule, followed by notes on the rule. */
    List<String> getComments() {
      List<String> comments =
          describeEvents(collectEvents(requirements), MAX_EVENTS_IN_COMMENT, "# ");
      for (String note : notes) {
        comments.add("# " + note);
      }
      return comments;
    }
  }

  private KeepRuleWriter() {}

  static List<ReflectiveEvent> collectEvents(Collection<KeepRequirement> requirements) {
    List<ReflectiveEvent> events = new ArrayList<>();
    for (KeepRequirement requirement : requirements) {
      for (ReflectiveEvent event : requirement.getEvents()) {
        if (!events.contains(event)) {
          events.add(event);
        }
      }
    }
    return events;
  }

  /**
   * Renders the requirements as rules, sorted by the class they are about. Requirements on a class
   * that must be found by name share a single {@code -keep} rule; otherwise names are kept with
   * {@code -keepnames} and members with {@code -keepclassmembers}. The classes of a ResourceBundle
   * family share a rule for all locales, and generic signatures with a supertype in the app are
   * kept through the supertype ({@code class * extends <supertype>}).
   */
  static List<RenderedRule> render(Collection<KeepRequirement> requirements) {
    Map<String, List<KeepRequirement>> byClass = new TreeMap<>();
    Map<String, List<KeepRequirement>> byBundle = new TreeMap<>();
    Map<String, List<KeepRequirement>> bySupertype = new TreeMap<>();
    for (KeepRequirement requirement : requirements) {
      if (requirement.getBundleBaseName() != null) {
        add(byBundle, requirement.getBundleBaseName(), requirement);
      } else if (requirement.hasProgramSupertype()) {
        add(bySupertype, requirement.getSupertype(), requirement);
      } else {
        add(byClass, requirement.getClassName(), requirement);
      }
    }
    // The rules for each class, with the rules for its subclasses last.
    Map<String, List<RenderedRule>> rules = new TreeMap<>();
    byBundle.forEach(
        (baseName, bundleRequirements) ->
            getRules(rules, baseName).add(renderBundle(baseName, bundleRequirements)));
    byClass.forEach(
        (className, classRequirements) ->
            renderClass(className, classRequirements, getRules(rules, className)));
    bySupertype.forEach(
        (supertype, signatureRequirements) ->
            getRules(rules, supertype).add(renderSubclasses(supertype, signatureRequirements)));
    List<RenderedRule> result = new ArrayList<>();
    rules.values().forEach(result::addAll);
    return result;
  }

  private static void add(
      Map<String, List<KeepRequirement>> map, String key, KeepRequirement requirement) {
    map.computeIfAbsent(key, k -> new ArrayList<>()).add(requirement);
  }

  private static List<RenderedRule> getRules(Map<String, List<RenderedRule>> rules, String key) {
    return rules.computeIfAbsent(key, k -> new ArrayList<>());
  }

  /** Keeps the bundle classes of all locales, since the log only shows the device locale. */
  private static RenderedRule renderBundle(String baseName, List<KeepRequirement> requirements) {
    List<KeepRequirement> sorted = new ArrayList<>(new TreeSet<>(requirements));
    List<String> memberLines = new ArrayList<>();
    for (KeepRequirement requirement : sorted) {
      if (requirement.isMember()) {
        String line = requirement.toRuleMember();
        if (!memberLines.contains(line)) {
          memberLines.add(line);
        }
      }
    }
    return new RenderedRule(
        renderRule("-keep class", baseName + ", " + baseName + "_*", memberLines),
        sorted,
        Collections.emptyList());
  }

  /**
   * Keeps the generic signatures of the subclasses of a supertype in the app. The subclasses are
   * not named, since they are often anonymous classes whose names change with the code.
   */
  private static RenderedRule renderSubclasses(
      String supertype, List<KeepRequirement> requirements) {
    boolean isInterface = false;
    for (KeepRequirement requirement : requirements) {
      isInterface |= requirement.isSupertypeInterface();
    }
    String keyword =
        "-keep,allowobfuscation,allowshrinking class * " + (isInterface ? "implements" : "extends");
    return new RenderedRule(
        renderRule(keyword, supertype, Collections.emptyList()),
        new ArrayList<>(new TreeSet<>(requirements)),
        Collections.emptyList());
  }

  private static void renderClass(
      String className, List<KeepRequirement> requirements, List<RenderedRule> result) {
    List<KeepRequirement> classLevel = new ArrayList<>();
    List<KeepRequirement> members = new ArrayList<>();
    boolean keepClass = false;
    boolean keepName = false;
    boolean keepSignature = false;
    boolean unresolvedSupertype = false;
    boolean allInterfaces = true;
    for (KeepRequirement requirement : new TreeSet<>(requirements)) {
      if (requirement.isClassLevel()) {
        classLevel.add(requirement);
        if (requirement.getKind() == Kind.CLASS) {
          keepClass = true;
          allInterfaces &= requirement.isInterface();
        } else if (requirement.getKind() == Kind.NAME) {
          keepName = true;
        } else if (requirement.getKind() == Kind.SIGNATURE) {
          keepSignature = true;
          unresolvedSupertype |= requirement.getSupertype() == null;
        }
      } else {
        members.add(requirement);
      }
    }
    List<String> notes = new ArrayList<>();
    if (unresolvedSupertype) {
      notes.add(
          "The raw type of a parameterized supertype of "
              + className
              + " could not be resolved at runtime, so the class is kept by its name.");
    }
    if (keepSignature && TypeNames.isAnonymousClassName(className)) {
      notes.add(
          className
              + " is an anonymous class: its name can change when the code changes, and the rule"
              + " no longer matches it.");
    }
    List<String> memberLines = renderMembers(members);
    if (keepClass) {
      List<KeepRequirement> all = new ArrayList<>(classLevel);
      all.addAll(members);
      String keyword = allInterfaces ? "-keep interface" : "-keep class";
      result.add(new RenderedRule(renderRule(keyword, className, memberLines), all, notes));
      return;
    }
    if (!classLevel.isEmpty()) {
      // Any keep rule for the class keeps its generic signature and keeps it from being merged
      // into another class, so the class can be renamed and removed unless its name is needed.
      String keyword =
          keepName ? "-keepnames class" : "-keep,allowobfuscation,allowshrinking class";
      result.add(
          new RenderedRule(
              renderRule(keyword, className, Collections.emptyList()), classLevel, notes));
    }
    if (!members.isEmpty()) {
      result.add(
          new RenderedRule(
              renderRule("-keepclassmembers class", className, memberLines),
              members,
              Collections.emptyList()));
    }
  }

  /** Renders the member lines, leaving out members that are kept by wildcards anyway. */
  static List<String> renderMembers(List<KeepRequirement> members) {
    return renderMembers(members, KeepRequirement::toRuleMember);
  }

  /**
   * Renders the member lines with the given text for each requirement, leaving out members that are
   * kept by wildcards anyway.
   */
  static List<String> renderMembers(
      List<KeepRequirement> members, Function<KeepRequirement, String> lineFunction) {
    Set<Kind> allKinds = new TreeSet<>();
    Set<Kind> publicKinds = new TreeSet<>();
    for (KeepRequirement requirement : members) {
      if (requirement.isAllMembers()) {
        (requirement.isPublicOnly() ? publicKinds : allKinds).add(requirement.getKind());
      }
    }
    List<String> lines = new ArrayList<>();
    for (KeepRequirement requirement : members) {
      if (requirement.isClassLevel()) {
        continue;
      }
      Kind wildcard = getAllMembersKind(requirement.getKind());
      boolean subsumed;
      if (requirement.isAllMembers()) {
        subsumed = requirement.isPublicOnly() && allKinds.contains(requirement.getKind());
      } else {
        subsumed =
            !requirement.isVolatile()
                && (allKinds.contains(wildcard)
                    || (requirement.isPublic() && publicKinds.contains(wildcard)));
      }
      String line = lineFunction.apply(requirement);
      if (!subsumed && !lines.contains(line)) {
        lines.add(line);
      }
    }
    return lines;
  }

  private static Kind getAllMembersKind(Kind kind) {
    switch (kind) {
      case CONSTRUCTOR:
      case ALL_CONSTRUCTORS:
        return Kind.ALL_CONSTRUCTORS;
      case FIELD:
      case ALL_FIELDS:
        return Kind.ALL_FIELDS;
      case METHOD:
      case ALL_METHODS:
        return Kind.ALL_METHODS;
      default:
        return kind;
    }
  }

  static String renderRule(String keyword, String className, List<String> memberLines) {
    StringBuilder builder = new StringBuilder(keyword).append(' ').append(className);
    if (!memberLines.isEmpty()) {
      builder.append(" {\n");
      for (String line : memberLines) {
        builder.append("    ").append(line).append('\n');
      }
      builder.append('}');
    }
    return builder.toString();
  }

  /**
   * Appends the {@code -keepattributes} rules needed for nested-class and generic-signature
   * reflection, and returns the number of rules appended.
   */
  static int appendAttributeRules(
      StringBuilder builder,
      KeepRequirements requirements,
      boolean keepNestingAttributes,
      boolean keepSignatureAttribute) {
    int count = 0;
    if (keepNestingAttributes) {
      builder.append('\n');
      builder.append("# Needed for getSimpleName(), getCanonicalName() and the nesting flags of\n");
      builder.append("# nested classes:\n");
      for (String line :
          describeEvents(requirements.getNestingAttributeEvents(), MAX_EVENTS_IN_COMMENT, "# ")) {
        builder.append(line).append('\n');
      }
      builder.append(NESTING_ATTRIBUTES_RULE).append('\n');
      count++;
    }
    if (keepSignatureAttribute) {
      builder.append('\n');
      builder.append(
          "# Needed for the parameterized types returned by getGenericSuperclass() and\n");
      builder.append("# getGenericInterfaces():\n");
      for (String line :
          describeEvents(requirements.getSignatureAttributeEvents(), MAX_EVENTS_IN_COMMENT, "# ")) {
        builder.append(line).append('\n');
      }
      builder.append(SIGNATURE_ATTRIBUTE_RULE).append('\n');
      count++;
    }
    return count;
  }

  /**
   * Describes events as lines like {@code Class.forName com.example.Foo <-
   * com.example.App.load(App.java:12)}, sorted, with at most {@code limit} lines.
   */
  static List<String> describeEvents(
      Collection<ReflectiveEvent> events, int limit, String linePrefix) {
    Set<String> descriptions = new TreeSet<>();
    for (ReflectiveEvent event : events) {
      descriptions.add(
          ReflectiveEvents.describe(event) + " <- " + ReflectiveEvents.getCallerDescription(event));
    }
    List<String> lines = new ArrayList<>();
    for (String description : descriptions) {
      if (lines.size() == limit) {
        lines.add(linePrefix + "... and " + (descriptions.size() - limit) + " more");
        break;
      }
      lines.add(linePrefix + description);
    }
    return lines;
  }
}

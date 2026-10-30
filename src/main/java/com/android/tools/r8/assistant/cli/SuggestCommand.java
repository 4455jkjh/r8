// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.assistant.cli.KeepRuleMatcher.RequirementCoverage;
import com.android.tools.r8.assistant.cli.KeepRuleMatcher.RuleCoverage;
import com.android.tools.r8.assistant.cli.KeepRuleSet.RulesException;
import com.android.tools.r8.assistant.cli.KeepRuleWriter.RenderedRule;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Suggests keep rules for the reflection recorded in reflection logs. */
public class SuggestCommand extends AssistantCommand {

  public static final String NAME = "suggest";

  private static final int MAX_COVERAGE_NOTES = 3;

  private final List<Path> logs = new ArrayList<>();
  private final List<Path> rules = new ArrayList<>();
  private Path output;

  public SuggestCommand() {}

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return "Suggest minimal keep rules for the reflection recorded in reflection logs.";
  }

  @Override
  public String getUsage() {
    return CliParserUtils.getUsageMessage(createParser(new ArrayList<>()));
  }

  private static CliParser<SuggestCommand> createParser(List<String> errors) {
    String header =
        StringUtils.joinLines(
            "Usage: " + AssistantCommandLine.INVOCATION + " " + NAME + " --log <file>... [options]",
            " Suggests minimal keep rules for the classes and members that the app accessed",
            " reflectively according to the reflection logs of an instrumented run. Only requests",
            " that returned get rules. Requests that threw, such as probes for optional classes,",
            " are listed as comments. Logs without after-call results are treated as if every",
            " request returned. Exit codes: 0 on success, 1 if the logs contain no events, 2 for",
            " invalid input.",
            " Options:");
    return new CliParser<SuggestCommand>(header)
        .option1(
            "--log",
            "<file>",
            "Reflection log (JSON lines) of an instrumented run. Can be repeated.",
            (command, value) -> command.logs.add(CommandUtils.toPath("--log", value, errors)))
        .option1(
            "--rules",
            "<file>",
            "Existing keep rules. Rules for what they already keep are left out. Can be repeated.",
            (command, value) -> command.rules.add(CommandUtils.toPath("--rules", value, errors)))
        .option1(
            "--output",
            "<file>",
            "Write the rules to <file> instead of standard output.",
            (command, value) -> command.output = CommandUtils.toPath("--output", value, errors))
        .apply(CliParserUtils.addHelpOption(command -> {}));
  }

  @Override
  public void parseArguments(List<String> args) throws AssistantUsageException {
    List<String> errors = new ArrayList<>();
    createParser(errors).parse(args.toArray(new String[0]), this, errors::add);
    if (logs.isEmpty()) {
      errors.add("Missing reflection log: pass --log <file>.");
    }
    CommandUtils.checkNoErrors(errors);
  }

  @Override
  public int run(PrintStream out, PrintStream err) throws IOException {
    if (!CommandUtils.checkFilesExist("--log", logs, err)
        || !CommandUtils.checkFilesExist("--rules", rules, err)) {
      return AssistantCommandLine.EXIT_USAGE_ERROR;
    }
    ParsedLogs parsedLogs = ParsedLogs.read(logs);
    CommandUtils.printWarnings(parsedLogs.getWarnings(), err);
    if (parsedLogs.isEmpty()) {
      err.println(
          "Error: no reflection events in "
              + CommandUtils.describePaths(logs)
              + ". Run the instrumented app through the relevant journeys first.");
      return AssistantCommandLine.EXIT_FAILURE;
    }
    KeepRuleSet ruleSet = null;
    if (!rules.isEmpty()) {
      try {
        ruleSet = KeepRuleSet.parse(rules);
      } catch (RulesException e) {
        err.println("Error: invalid keep rules:");
        err.println(e.getMessage());
        return AssistantCommandLine.EXIT_USAGE_ERROR;
      }
      CommandUtils.printWarnings(ruleSet.getWarnings(), err);
    }
    Suggestion suggestion = suggest(parsedLogs, ruleSet);
    if (output == null) {
      out.print(suggestion.text);
    } else {
      CommandUtils.writeFile(output, suggestion.text);
      out.println(
          "Wrote "
              + CommandUtils.plural(suggestion.ruleCount, "rule", "rules")
              + " to "
              + output
              + ".");
    }
    return AssistantCommandLine.EXIT_SUCCESS;
  }

  /** The suggested rules, as the text of a rules file. */
  private static final class Suggestion {

    private final String text;
    private final int ruleCount;

    private Suggestion(String text, int ruleCount) {
      this.text = text;
      this.ruleCount = ruleCount;
    }
  }

  private Suggestion suggest(ParsedLogs parsedLogs, KeepRuleSet ruleSet) {
    KeepRequirements requirements =
        KeepRequirements.analyze(parsedLogs.getEvents(), parsedLogs::threw);
    List<KeepRequirement> all = requirements.getRequirements();
    List<KeepRequirement> needed = new ArrayList<>();
    Map<KeepRequirement, RequirementCoverage> coverages = new IdentityHashMap<>();
    int coveredCount = 0;
    if (ruleSet == null) {
      needed.addAll(all);
    } else {
      KeepRuleMatcher matcher = new KeepRuleMatcher(ruleSet, requirements);
      List<RequirementCoverage> allCoverages = new ArrayList<>();
      for (KeepRequirement requirement : all) {
        RequirementCoverage coverage = matcher.cover(requirement);
        allCoverages.add(coverage);
        if (coverage.isCovered()) {
          coveredCount++;
        }
      }
      for (RequirementCoverage coverage : KeepRuleMatcher.getMissing(allCoverages)) {
        needed.add(coverage.getRequirement());
        coverages.put(coverage.getRequirement(), coverage);
      }
    }
    List<RenderedRule> rendered = KeepRuleWriter.render(needed);
    boolean keepNestingAttributes =
        !requirements.getNestingAttributeEvents().isEmpty()
            && (ruleSet == null || !ruleSet.keepsNestingAttributes());
    boolean keepSignatureAttribute =
        !requirements.getSignatureAttributeEvents().isEmpty()
            && (ruleSet == null || !ruleSet.keepsSignatureAttribute());

    StringBuilder builder = new StringBuilder();
    builder.append("# Keep rules suggested by the R8 Assistant\n");
    builder.append("# Logs: ").append(CommandUtils.describePaths(logs)).append('\n');
    builder.append("# Outcomes: ").append(parsedLogs.describeOutcomes()).append('\n');
    builder
        .append("# Events: ")
        .append(parsedLogs.getEvents().size())
        .append(" (")
        .append(parsedLogs.countThrown(parsedLogs.getEvents()))
        .append(" threw), requirements: ")
        .append(all.size())
        .append('\n');
    if (ruleSet != null) {
      builder
          .append("# Existing rules: ")
          .append(CommandUtils.describePaths(rules))
          .append(" (cover ")
          .append(coveredCount)
          .append(" of the requirements)\n");
    }
    builder.append("# Only reflection in the logged runs is covered.\n");

    int ruleCount = 0;
    for (RenderedRule rule : rendered) {
      builder.append('\n');
      for (String comment : rule.getComments()) {
        builder.append(comment).append('\n');
      }
      for (String note : getCoverageNotes(rule, coverages)) {
        builder.append(note).append('\n');
      }
      builder.append(rule.getText()).append('\n');
      ruleCount++;
    }
    ruleCount +=
        KeepRuleWriter.appendAttributeRules(
            builder, requirements, keepNestingAttributes, keepSignatureAttribute);
    if (ruleCount == 0) {
      builder.append('\n');
      builder.append(
          ruleSet == null
              ? "# No keep rules are needed for the logged reflection.\n"
              : "# The existing rules cover all logged reflection.\n");
    }
    List<ReflectiveEvent> probes = requirements.getProbes();
    if (!probes.isEmpty()) {
      builder.append('\n');
      builder.append(
          "# Optional probes: these requests threw in the logged run, so no rules are\n");
      builder.append("# suggested for them:\n");
      for (String line : KeepRuleWriter.describeEvents(probes, Integer.MAX_VALUE, "#   ")) {
        builder.append(line).append('\n');
      }
    }
    return new Suggestion(builder.toString(), ruleCount);
  }

  /** Notes on existing rules that may already keep what the rendered rule keeps. */
  private static List<String> getCoverageNotes(
      RenderedRule rule, Map<KeepRequirement, RequirementCoverage> coverages) {
    Set<String> notes = new LinkedHashSet<>();
    for (KeepRequirement requirement : rule.getRequirements()) {
      RequirementCoverage coverage = coverages.get(requirement);
      if (coverage == null) {
        continue;
      }
      for (RuleCoverage ruleCoverage : coverage.getRuleCoverages()) {
        String description = ruleCoverage.describe();
        notes.add("# " + Character.toUpperCase(description.charAt(0)) + description.substring(1));
      }
    }
    List<String> result = new ArrayList<>();
    for (String note : notes) {
      if (result.size() == MAX_COVERAGE_NOTES) {
        result.add("# ... and " + (notes.size() - MAX_COVERAGE_NOTES) + " more existing rules");
        break;
      }
      result.add(note);
    }
    return result;
  }
}

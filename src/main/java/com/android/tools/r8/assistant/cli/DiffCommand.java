// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.assistant.cli.KeepRuleDiff.RuleResult;
import com.android.tools.r8.assistant.cli.KeepRuleDiff.Verdict;
import com.android.tools.r8.assistant.cli.KeepRuleMatcher.Coverage;
import com.android.tools.r8.assistant.cli.KeepRuleMatcher.RequirementCoverage;
import com.android.tools.r8.assistant.cli.KeepRuleMatcher.RuleCoverage;
import com.android.tools.r8.assistant.cli.KeepRuleSet.RulesException;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Compares keep rules with the reflection recorded in reflection logs. */
public class DiffCommand extends AssistantCommand {

  public static final String NAME = "diff";

  static final int MAX_EVENTS = 3;
  static final int MAX_RULES = 3;

  private final List<Path> logs = new ArrayList<>();
  private final List<Path> rules = new ArrayList<>();
  private Path output;

  public DiffCommand() {}

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return "Compare keep rules with reflection logs and propose narrowed rules.";
  }

  @Override
  public String getUsage() {
    return CliParserUtils.getUsageMessage(createParser(new ArrayList<>()));
  }

  private static CliParser<DiffCommand> createParser(List<String> errors) {
    String header =
        StringUtils.joinLines(
            "Usage: "
                + AssistantCommandLine.INVOCATION
                + " "
                + NAME
                + " --log <file>... --rules <file>... [options]",
            " Compares keep rules with the reflection recorded in reflection logs. For each",
            " rule it reports whether the logs justify it, whether it can be narrowed to what",
            " the logs show, or whether nothing it keeps was observed. It lists what the logs",
            " require but the rules do not keep (a crash risk in the release build), and",
            " proposes rules. Rules are matched with R8's matchers. The logs show no class",
            " hierarchy, annotations or most modifiers, so rules that select by them are",
            " reported as undecided and kept as is, as are -if rules. Removing a rule that was",
            " not observed is only safe if the logged runs exercised every code path that needs",
            " it. Exit codes: 0 on success, 1 if the logs contain no events, 2 for invalid",
            " input.",
            " Options:");
    return new CliParser<DiffCommand>(header)
        .option1(
            "--log",
            "<file>",
            "Reflection log (JSON lines) of an instrumented run. Can be repeated.",
            (command, value) -> command.logs.add(CommandUtils.toPath("--log", value, errors)))
        .option1(
            "--rules",
            "<file>",
            "Keep rules to compare. Can be repeated.",
            (command, value) -> command.rules.add(CommandUtils.toPath("--rules", value, errors)))
        .option1(
            "--output",
            "<file>",
            "Write the proposed rules to <file> instead of standard output.",
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
    if (rules.isEmpty()) {
      errors.add("Missing keep rules: pass --rules <file>.");
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
              + ". Without events every rule would look unused.");
      return AssistantCommandLine.EXIT_FAILURE;
    }
    KeepRuleSet ruleSet;
    try {
      ruleSet = KeepRuleSet.parse(rules);
    } catch (RulesException e) {
      err.println("Error: invalid keep rules:");
      err.println(e.getMessage());
      return AssistantCommandLine.EXIT_USAGE_ERROR;
    }
    CommandUtils.printWarnings(ruleSet.getWarnings(), err);
    KeepRuleDiff diff =
        KeepRuleDiff.compute(parsedLogs, ruleSet, CommandUtils.describePaths(rules));
    printSummary(diff, out);
    out.println();
    if (output != null) {
      CommandUtils.writeFile(output, diff.getProposedRules());
      out.println("Proposed rules written to " + output + ".");
    } else {
      out.println("Proposed rules:");
      out.println();
      out.print(diff.getProposedRules());
    }
    return AssistantCommandLine.EXIT_SUCCESS;
  }

  private void printSummary(KeepRuleDiff diff, PrintStream out) {
    out.println("R8 Assistant keep rule diff");
    out.println(
        "Logs: "
            + CommandUtils.describePaths(logs)
            + "; outcomes "
            + diff.getLogs().describeOutcomes());
    out.println("Rules: " + CommandUtils.describePaths(rules));
    out.println("Requirements in the logs: " + describeRequirementCounts(diff));
    out.println("Rules: " + describeVerdictCounts(diff));
    out.println();
    out.println("Rules:");
    for (RuleResult result : diff.getRuleResults()) {
      String label = result.getVerdict().getLabel().toUpperCase(Locale.ROOT);
      out.println("  " + label + "  " + result.getRule());
      switch (result.getVerdict()) {
        case NARROWED:
          printRuleEvents(result, out);
          out.println("      " + result.getReason() + "; replace with:");
          for (String replacement : result.getReplacement()) {
            for (String line : replacement.split("\n", -1)) {
              out.println("        " + line);
            }
          }
          break;
        case NOT_OBSERVED:
        case UNDECIDED:
          out.println("      " + result.getReason());
          break;
        case JUSTIFIED:
          printRuleEvents(result, out);
          break;
        case CONDITIONAL:
        case NOT_ANALYZED:
          break;
      }
    }
    List<RequirementCoverage> notKept = new ArrayList<>();
    List<RequirementCoverage> undecided = new ArrayList<>();
    for (RequirementCoverage coverage : diff.getMissing()) {
      if (isNotKept(coverage)) {
        notKept.add(coverage);
      } else {
        undecided.add(coverage);
      }
    }
    if (!notKept.isEmpty()) {
      out.println();
      out.println("Not kept by the rules (crash risk in the release build):");
      printMissing(notKept, out);
    }
    if (!undecided.isEmpty()) {
      out.println();
      out.println("Possibly not kept by the rules (the logs cannot show it):");
      printMissing(undecided, out);
    }
    if (diff.needsNestingAttributes()) {
      out.println();
      out.println(
          "Not kept by the rules: the InnerClasses and EnclosingMethod attributes, used by"
              + " getSimpleName() and the nesting flags of nested classes.");
    }
    if (diff.needsSignatureAttribute()) {
      out.println();
      out.println(
          "Not kept by the rules: the Signature attribute (-keepattributes Signature), used by"
              + " getGenericSuperclass() and getGenericInterfaces().");
    }
  }

  private static void printRuleEvents(RuleResult result, PrintStream out) {
    for (String line :
        KeepRuleWriter.describeEvents(
            KeepRuleWriter.collectEvents(result.getCovered()), MAX_EVENTS, "      ")) {
      out.println(line);
    }
  }

  static boolean isNotKept(RequirementCoverage coverage) {
    return coverage.getCoverage() == Coverage.NONE || coverage.getCoverage() == Coverage.PARTIAL;
  }

  private static void printMissing(List<RequirementCoverage> coverages, PrintStream out) {
    for (RequirementCoverage coverage : coverages) {
      KeepRequirement requirement = coverage.getRequirement();
      out.println("  " + requirement.describe());
      for (String line : KeepRuleWriter.describeEvents(requirement.getEvents(), MAX_EVENTS, "")) {
        out.println("      " + line);
      }
      if (coverage.getCoverage() == Coverage.NONE && requirement.describeMissingRule() != null) {
        out.println("      " + requirement.describeMissingRule());
      }
      for (String line : describeRuleCoverages(coverage, MAX_RULES)) {
        out.println("      " + line);
      }
    }
  }

  /** Describes the rules that possibly or partially keep a requirement. */
  static List<String> describeRuleCoverages(RequirementCoverage coverage, int limit) {
    List<String> lines = new ArrayList<>();
    List<RuleCoverage> ruleCoverages = coverage.getRuleCoverages();
    for (RuleCoverage ruleCoverage : ruleCoverages) {
      if (lines.size() == limit) {
        lines.add("... and " + (ruleCoverages.size() - limit) + " more rules");
        break;
      }
      lines.add(ruleCoverage.describe());
    }
    return lines;
  }

  static String describeRequirementCounts(KeepRuleDiff diff) {
    List<String> parts = new ArrayList<>();
    parts.add(diff.countCoverages(Coverage.COVERED) + " kept");
    addCount(parts, diff.countCoverages(Coverage.CONDITIONAL), "kept by -if rules");
    addCount(parts, diff.countCoverages(Coverage.UNDECIDED), "undecided");
    addCount(parts, diff.countCoverages(Coverage.PARTIAL), "partially kept");
    addCount(parts, diff.countCoverages(Coverage.NONE), "not kept");
    return diff.getCoverages().size() + " (" + String.join(", ", parts) + ")";
  }

  static String describeVerdictCounts(KeepRuleDiff diff) {
    List<String> parts = new ArrayList<>();
    for (Verdict verdict : Verdict.values()) {
      addCount(parts, diff.getRuleResults(verdict).size(), verdict.getLabel());
    }
    return diff.getRuleResults().size() + " (" + String.join(", ", parts) + ")";
  }

  private static void addCount(List<String> parts, int count, String label) {
    if (count > 0) {
      parts.add(count + " " + label);
    }
  }
}

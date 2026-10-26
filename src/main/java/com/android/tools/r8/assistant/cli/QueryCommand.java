// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.assistant.cli.KeepRuleSet.RulesException;
import com.android.tools.r8.assistant.postprocessing.model.ReflectiveEvent;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.utils.CliParserUtils;
import com.android.tools.r8.utils.internal.CliParser;
import com.android.tools.r8.utils.internal.StringUtils;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Lists the events of reflection logs that match filters. */
public class QueryCommand extends AssistantCommand {

  public static final String NAME = "query";

  /** A class name pattern in keep rule syntax, matched with R8's class name matcher. */
  private static final class ClassPattern {

    private final KeepRuleSet ruleSet;
    private final ProguardClassNameList classNames;

    private ClassPattern(KeepRuleSet ruleSet, ProguardClassNameList classNames) {
      this.ruleSet = ruleSet;
      this.classNames = classNames;
    }

    /** Parses the pattern, or reports an error and returns null if it is invalid. */
    static ClassPattern parse(String pattern, List<String> errors) {
      try {
        KeepRuleSet ruleSet = KeepRuleSet.parseString("-keep class " + pattern);
        if (ruleSet.getRules().size() == 1
            && ruleSet.getRules().get(0).get().isProguardKeepRule()) {
          return new ClassPattern(ruleSet, ruleSet.getRules().get(0).get().getClassNames());
        }
      } catch (RulesException e) {
        // Reported below.
      }
      errors.add("Invalid class pattern for --class: " + pattern);
      return null;
    }

    boolean matches(String className) {
      DexType type = ruleSet.getType(className);
      return type != null && classNames.matches(type);
    }
  }

  private final List<Path> logs = new ArrayList<>();
  private final List<ClassPattern> classPatterns = new ArrayList<>();
  private final List<Pattern> memberPatterns = new ArrayList<>();
  private final List<Pattern> eventPatterns = new ArrayList<>();
  private boolean stacks;

  public QueryCommand() {}

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getDescription() {
    return "List the events in reflection logs that match class, member and event filters.";
  }

  @Override
  public String getUsage() {
    return CliParserUtils.getUsageMessage(createParser(new ArrayList<>()));
  }

  private static CliParser<QueryCommand> createParser(List<String> errors) {
    String header =
        StringUtils.joinLines(
            "Usage: " + AssistantCommandLine.INVOCATION + " " + NAME + " --log <file>... [options]",
            " Lists the events in reflection logs that match the filters, in log order, with their",
            " outcome, caller, results and log file. Repeated filters of the same kind match if"
                + " any",
            " of them matches; filters of different kinds must all match.",
            " Options:");
    return new CliParser<QueryCommand>(header)
        .option1(
            "--log",
            "<file>",
            "Reflection log (JSON lines) of an instrumented run. Can be repeated.",
            (command, value) -> command.logs.add(CommandUtils.toPath("--log", value, errors)))
        .option1(
            "--class",
            "<pattern>",
            "Class name in keep rule syntax, such as com.example.**, *.Model or"
                + " !com.example.internal.**,com.example.**. Matches the classes that the event is"
                + " about.",
            (command, value) -> command.classPatterns.add(ClassPattern.parse(value, errors)))
        .option1(
            "--member",
            "<pattern>",
            "Field, method or constructor (<init>) name, where * and ? are wildcards. Bulk"
                + " lookups match through the members that they returned.",
            (command, value) -> command.memberPatterns.add(globToPattern(value, false)))
        .option1(
            "--event",
            "<type>",
            "Event type, such as CLASS_FOR_NAME, or API, such as Class.forName, where * and ? are"
                + " wildcards and case is ignored.",
            (command, value) -> command.eventPatterns.add(globToPattern(value, true)))
        .option0(
            "--stacks", "Also print the logged stack frames.", command -> command.stacks = true)
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

  static Pattern globToPattern(String glob, boolean ignoreCase) {
    StringBuilder regex = new StringBuilder();
    StringBuilder literal = new StringBuilder();
    for (int i = 0; i < glob.length(); i++) {
      char c = glob.charAt(i);
      if (c == '*' || c == '?') {
        if (literal.length() > 0) {
          regex.append(Pattern.quote(literal.toString()));
          literal.setLength(0);
        }
        regex.append(c == '*' ? ".*" : ".");
      } else {
        literal.append(c);
      }
    }
    if (literal.length() > 0) {
      regex.append(Pattern.quote(literal.toString()));
    }
    return Pattern.compile(regex.toString(), ignoreCase ? Pattern.CASE_INSENSITIVE : 0);
  }

  @Override
  public int run(PrintStream out, PrintStream err) throws IOException {
    if (!CommandUtils.checkFilesExist("--log", logs, err)) {
      return AssistantCommandLine.EXIT_USAGE_ERROR;
    }
    ParsedLogs parsedLogs = ParsedLogs.read(logs);
    CommandUtils.printWarnings(parsedLogs.getWarnings(), err);
    int matches = 0;
    for (ReflectiveEvent event : parsedLogs.getEvents()) {
      if (!matches(event)) {
        continue;
      }
      matches++;
      print(event, parsedLogs, out);
    }
    out.println(
        matches
            + " of "
            + CommandUtils.plural(
                parsedLogs.getEvents().size(), "event matches.", "events match."));
    return AssistantCommandLine.EXIT_SUCCESS;
  }

  private boolean matches(ReflectiveEvent event) {
    return matchesClass(event) && matchesMember(event) && matchesEvent(event);
  }

  private boolean matchesClass(ReflectiveEvent event) {
    if (classPatterns.isEmpty()) {
      return true;
    }
    for (String name : ReflectiveEvents.getClassNames(event)) {
      String className = TypeNames.getClassName(name);
      if (className == null) {
        continue;
      }
      for (ClassPattern pattern : classPatterns) {
        if (pattern.matches(className)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean matchesMember(ReflectiveEvent event) {
    if (memberPatterns.isEmpty()) {
      return true;
    }
    List<String> names = new ArrayList<>();
    MemberSignature member = ReflectiveEvents.getMember(event);
    if (member != null) {
      names.add(member.getName());
    }
    for (String summary : ReflectiveEvents.getResultMembers(event)) {
      names.add(getMemberName(summary));
    }
    for (String name : names) {
      for (Pattern pattern : memberPatterns) {
        if (pattern.matcher(name).matches()) {
          return true;
        }
      }
    }
    return false;
  }

  /** The name in a member summary such as {@code int count} or {@code void run(int)}. */
  private static String getMemberName(String summary) {
    String head = summary;
    int paren = head.indexOf('(');
    if (paren >= 0) {
      head = head.substring(0, paren);
    }
    head = head.trim();
    return head.substring(head.lastIndexOf(' ') + 1);
  }

  private boolean matchesEvent(ReflectiveEvent event) {
    if (eventPatterns.isEmpty()) {
      return true;
    }
    for (Pattern pattern : eventPatterns) {
      if (pattern.matcher(event.getEventType().name()).matches()
          || pattern.matcher(ReflectiveEvents.getApiName(event)).matches()) {
        return true;
      }
    }
    return false;
  }

  private void print(ReflectiveEvent event, ParsedLogs parsedLogs, PrintStream out) {
    out.println(
        "["
            + parsedLogs.getOutcome(event).name().toLowerCase(Locale.ROOT)
            + "] "
            + ReflectiveEvents.describe(event)
            + " ("
            + event.getEventType().name()
            + ")");
    out.println("  caller: " + ReflectiveEvents.getCallerDescription(event));
    if (!event.getResults().isEmpty()) {
      if (ReflectiveEvents.hasResultMembers(event)) {
        out.println("  returned members:");
        for (String member : ReflectiveEvents.getResultMembers(event)) {
          out.println("    " + member);
        }
      } else {
        out.println("  result: " + String.join(" | ", ReflectiveEvents.getResultValues(event)));
      }
    }
    out.println("  log: " + parsedLogs.getFile(event));
    if (stacks) {
      out.println("  stack:");
      for (String frame : ReflectiveEvents.getStack(event)) {
        out.println("    " + (frame == null ? "null" : ReflectiveEvents.stripFramePrefix(frame)));
      }
    }
  }
}

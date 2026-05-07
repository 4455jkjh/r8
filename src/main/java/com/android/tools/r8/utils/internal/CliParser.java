// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.internal;

import static java.util.Collections.*;

import com.android.tools.r8.ParseFlagInfo;
import com.android.tools.r8.ParseFlagInfoImpl;
import com.android.tools.r8.ParseFlagPrinter;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CliParser<B> {

  private final Map<String, Consumer<B>> options0 = new HashMap<>();
  private final Map<String, BiConsumer<B, String>> options1 = new HashMap<>();
  private BiConsumer<B, String> positionalHandler;
  private final List<HelpInfo> helpInfos = new ArrayList<>();
  private final String usageHeader;

  private static final int HELP_WIDTH = 25;

  /**
   * @param usageHeader can contain line breaks and will not be automatically wrapped.
   */
  public CliParser(String usageHeader) {
    this.usageHeader = usageHeader;
  }

  private static class HelpInfo {

    final String name;
    final List<String> paramLabels;
    final String description;

    HelpInfo(String name, List<String> paramLabels, String description) {
      this.name = name;
      this.paramLabels = paramLabels;
      this.description = description;
    }
  }

  /**
   * @param name must start with {@code -} and must be unique
   * @param description must not contains line breaks, it is automatically wrapped
   */
  public CliParser<B> option0(String name, String description, Consumer<B> action) {
    assert assertThatOptionIsNew(name);
    assert assertValidName(name);
    assert assertValidDescription(description);
    options0.put(name, action);
    helpInfos.add(new HelpInfo(name, emptyList(), description));
    return this;
  }

  /**
   * @param name must start with {@code -} and must be unique
   * @param paramLabel must be surrounded by {@code <} and {@code >}
   * @param description must not contains line breaks, it is automatically wrapped
   */
  public CliParser<B> option1(
      String name, String paramLabel, String description, BiConsumer<B, String> action) {
    assert assertThatOptionIsNew(name);
    assert assertValidName(name);
    assert assertValidParam(paramLabel);
    assert assertValidDescription(description);
    options1.put(name, action);
    helpInfos.add(new HelpInfo(name, ImmutableList.of(paramLabel), description));
    return this;
  }

  /**
   * @param action only one positional handler can be bound
   */
  public CliParser<B> positional(BiConsumer<B, String> action) {
    assert assertValidPositional();
    this.positionalHandler = action;
    return this;
  }

  public void parse(String[] args, B builder, Consumer<String> errorReporter) {
    parseInternal(DequeUtils.newArrayDeque(args), builder, errorReporter);
  }

  public List<ParseFlagInfo> getFlagInfos() {
    int idealWidth = 100;
    int descriptionWidth = idealWidth - HELP_WIDTH;

    List<ParseFlagInfo> flags = new ArrayList<>();
    for (HelpInfo info : helpInfos) {
      List<String> helpLines = StringUtils.wrapToWidth(info.description, descriptionWidth);
      flags.add(new ParseFlagInfoImpl(null, commandString(info), ImmutableList.of(), helpLines));
    }
    return flags;
  }

  public String getUsageMessage() {
    StringBuilder builder = new StringBuilder(usageHeader).append(System.lineSeparator());

    new ParseFlagPrinter()
        .setHelpColumn(HELP_WIDTH)
        .addFlags(getFlagInfos())
        .appendLinesToBuilder(builder);
    return builder.toString();
  }

  /** Returns a string like {@code --output <file>} */
  private static String commandString(HelpInfo info) {
    var sb = new StringBuilder(info.name);
    for (var label : info.paramLabels) {
      sb.append(' ').append(label);
    }
    return sb.toString();
  }

  private void parseInternal(Deque<String> args, B builder, Consumer<String> errorReporter) {
    while (!args.isEmpty()) {
      String rawArg = args.removeFirst();
      String arg = rawArg;
      String eqValue = null;

      if (rawArg.startsWith("-")) {
        int equalsIndex = rawArg.indexOf('=');
        if (equalsIndex > 0) {
          arg = rawArg.substring(0, equalsIndex);
          eqValue = rawArg.substring(equalsIndex + 1);
        }
      }

      if (options0.containsKey(arg)) {
        if (eqValue != null) {
          errorReporter.accept("Option " + arg + " does not take a value.");
        } else {
          options0.get(arg).accept(builder);
        }
      } else if (options1.containsKey(arg)) {
        if (eqValue != null) {
          options1.get(arg).accept(builder, eqValue);
        } else if (!args.isEmpty()) {
          options1.get(arg).accept(builder, args.removeFirst());
        } else {
          errorReporter.accept("Missing parameter for " + arg + ".");
          break;
        }
      } else if (rawArg.startsWith("-")) {
        errorReporter.accept("Unknown option: " + rawArg);
      } else if (positionalHandler != null) {
        positionalHandler.accept(builder, rawArg);
      } else {
        errorReporter.accept("Unexpected argument: " + rawArg);
      }
    }
  }

  private boolean assertThatOptionIsNew(String name) {
    assert !options0.containsKey(name) && !options1.containsKey(name)
        : name + " is already an option";
    return true;
  }

  private boolean assertValidName(String name) {
    assert name.startsWith("-") : name + " does not start with -";
    assert !name.contains("=") : name + " contains '='";
    return true;
  }

  private boolean assertValidParam(String param) {
    assert param.startsWith("<") && param.endsWith(">") : param + " is no surrounded by <>.";
    return true;
  }

  private boolean assertValidDescription(String description) {
    assert !description.contains("\n")
        : "descriptions should rely on automatic wrapping: " + description;
    return true;
  }

  private boolean assertValidPositional() {
    assert positionalHandler == null : "A positional handler was already bound.";
    return true;
  }
}

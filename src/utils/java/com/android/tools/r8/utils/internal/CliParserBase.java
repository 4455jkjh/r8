// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.internal;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class CliParserBase<B> {

  private final Map<String, Consumer<B>> options0 = new HashMap<>();
  private final Map<String, BiConsumer<B, String>> options1 = new HashMap<>();
  private BiConsumer<B, String> positionalHandler;
  private final List<OptionInfo> optionInfos = new ArrayList<>();
  private final String usageHeader;

  /**
   * @param usageHeader can contain line breaks and will not be automatically wrapped.
   */
  public CliParserBase(String usageHeader) {
    this.usageHeader = usageHeader;
  }

  public static class OptionInfo {

    public final String name;
    public final String shorthand;
    public final ImmutableList<String> paramLabels;
    public final String description;

    OptionInfo(
        String name, String shorthand, ImmutableList<String> paramLabels, String description) {
      assert name != null;
      assert paramLabels != null;
      assert description != null;
      this.name = name;
      this.shorthand = shorthand;
      this.paramLabels = paramLabels;
      this.description = description;
    }
  }

  /**
   * @param name must be unique
   */
  public CliParserBase<B> option0(String name, String description, Consumer<B> action) {
    addOption0(name, action);
    addHelp(name, null, ImmutableList.of(), description);
    return this;
  }

  /**
   * @param name must be unique
   * @param shorthand must be unique
   */
  public CliParserBase<B> option0(
      String name, String description, Consumer<B> action, String shorthand) {
    addOption0(name, action);
    addOption0(shorthand, action);
    addHelp(name, shorthand, ImmutableList.of(), description);
    return this;
  }

  /**
   * @param name must be unique
   */
  public CliParserBase<B> option1(
      String name, String paramLabel, String description, BiConsumer<B, String> action) {
    addOption1(name, action);
    addHelp(name, null, ImmutableList.of(paramLabel), description);
    return this;
  }

  /**
   * @param name must be unique
   * @param shorthand must be unique
   */
  public CliParserBase<B> option1(
      String name,
      String paramLabel,
      String description,
      BiConsumer<B, String> action,
      String shorthand) {
    addOption1(name, action);
    addOption1(shorthand, action);
    addHelp(name, shorthand, ImmutableList.of(paramLabel), description);
    return this;
  }

  /**
   * @param action only one positional handler can be bound
   */
  public CliParserBase<B> positional(BiConsumer<B, String> action) {
    assert assertValidPositional();
    this.positionalHandler = action;
    return this;
  }

  public void parse(String[] args, B builder, Consumer<String> errorReporter) {
    parseInternal(DequeUtils.newArrayDeque(args), builder, errorReporter);
  }

  public String getUsageHeader() {
    return usageHeader;
  }

  public List<OptionInfo> getOptionInfo() {
    return ListUtils.unmodifiableForTesting(optionInfos);
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

  private void addOption0(String name, Consumer<B> action) {
    assert assertThatOptionIsNew(name);
    options0.put(name, action);
  }

  private void addOption1(String name, BiConsumer<B, String> action) {
    assert assertThatOptionIsNew(name);
    options1.put(name, action);
  }

  private void addHelp(
      String name, String shorthand, ImmutableList<String> paramLabels, String description) {
    assert !name.contains("=") : name + " contains '='";
    if (shorthand != null) {
      assert !name.equals(shorthand) : "Shorthand is the same as the main name: " + name;
      assert !shorthand.contains("=") : shorthand + " contains '='";
    }
    optionInfos.add(new OptionInfo(name, shorthand, paramLabels, description));
  }

  private boolean assertValidPositional() {
    assert positionalHandler == null : "A positional handler was already bound.";
    return true;
  }
}

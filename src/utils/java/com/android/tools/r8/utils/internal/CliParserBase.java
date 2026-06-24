// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.internal;

import com.android.tools.r8.utils.internal.collections.Pair;
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
  private final Map<String, BiConsumer<B, String>> prefix0 = new HashMap<>();
  private final Map<String, TriConsumer<B, String, String>> prefix1 = new HashMap<>();
  private final Map<String, QuadConsumer<B, String, String, String>> prefix2 = new HashMap<>();
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
    public final String suffixLabel;
    public final ImmutableList<String> paramLabels;
    public final String description;

    OptionInfo(
        String name,
        String shorthand,
        String suffixLabel,
        ImmutableList<String> paramLabels,
        String description) {
      assert name != null;
      assert paramLabels != null;
      assert description != null;
      this.name = name;
      this.shorthand = shorthand;
      this.suffixLabel = suffixLabel;
      this.paramLabels = paramLabels;
      this.description = description;
    }
  }

  /**
   * @param name must be unique and non-overlapping
   */
  public CliParserBase<B> option0(String name, String description, Consumer<B> action) {
    addOption0(name, action);
    addHelp(name, null, null, ImmutableList.of(), description);
    return this;
  }

  /**
   * @param name must be unique and non-overlapping
   * @param shorthand must be unique and non-overlapping
   */
  public CliParserBase<B> option0(
      String name, String description, Consumer<B> action, String shorthand) {
    addOption0(name, action);
    addOption0(shorthand, action);
    addHelp(name, shorthand, null, ImmutableList.of(), description);
    return this;
  }

  /**
   * @param name must be unique and non-overlapping
   */
  public CliParserBase<B> option1(
      String name, String paramLabel, String description, BiConsumer<B, String> action) {
    addOption1(name, action);
    addHelp(name, null, null, ImmutableList.of(paramLabel), description);
    return this;
  }

  /**
   * @param name must be unique and non-overlapping
   * @param shorthand must be unique and non-overlapping
   */
  public CliParserBase<B> option1(
      String name,
      String paramLabel,
      String description,
      BiConsumer<B, String> action,
      String shorthand) {
    addOption1(name, action);
    addOption1(shorthand, action);
    addHelp(name, shorthand, null, ImmutableList.of(paramLabel), description);
    return this;
  }

  /**
   * @param prefix must start with {@code --} and must be unique and non-overlapping
   */
  public CliParserBase<B> prefix0(
      String prefix, String suffixLabel, String description, BiConsumer<B, String> action) {
    addPrefix0(prefix, action);
    addHelp(prefix, null, suffixLabel, ImmutableList.of(), description);
    return this;
  }

  /**
   * @param prefix must start with {@code --} and must be unique and non-overlapping
   */
  public CliParserBase<B> prefix1(
      String prefix,
      String suffixLabel,
      String paramLabel,
      String description,
      TriConsumer<B, String, String> action) {
    addPrefix1(prefix, action);
    addHelp(prefix, null, suffixLabel, ImmutableList.of(paramLabel), description);
    return this;
  }

  /**
   * @param prefix must start with {@code --} and must be unique and non-overlapping
   */
  public CliParserBase<B> prefix2(
      String prefix,
      String suffixLabel,
      String paramLabel1,
      String paramLabel2,
      String description,
      QuadConsumer<B, String, String, String> action) {
    addPrefix2(prefix, action);
    addHelp(prefix, null, suffixLabel, ImmutableList.of(paramLabel1, paramLabel2), description);
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

  @SuppressWarnings("StatementWithEmptyBody")
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

      if (tryParseOption0(arg, eqValue, builder, errorReporter)) {
        // Matched.
      } else if (tryParseOption1(arg, eqValue, args, builder, errorReporter)) {
        // Matched.
      } else if (tryParsePrefix0(arg, eqValue, builder, errorReporter)) {
        // Matched.
      } else if (tryParsePrefix1(arg, eqValue, args, builder, errorReporter)) {
        // Matched.
      } else if (tryParsePrefix2(arg, eqValue, args, builder, errorReporter)) {
        // Matched.
      } else if (tryParsePositional(rawArg, builder)) {
        // Matched.
      } else {
        errorReporter.accept("Unexpected argument: " + rawArg);
      }
    }
  }

  private boolean tryParseOption0(
      String arg, String eqValue, B builder, Consumer<String> errorReporter) {
    if (!options0.containsKey(arg)) {
      return false;
    }
    if (eqValue != null) {
      errorReporter.accept("Option " + arg + " does not take a value.");
    } else {
      options0.get(arg).accept(builder);
    }
    return true;
  }

  private boolean tryParseOption1(
      String arg, String eqValue, Deque<String> args, B builder, Consumer<String> errorReporter) {
    if (!options1.containsKey(arg)) {
      return false;
    }
    if (eqValue != null) {
      options1.get(arg).accept(builder, eqValue);
    } else if (!args.isEmpty()) {
      options1.get(arg).accept(builder, args.removeFirst());
    } else {
      errorReporter.accept("Missing parameter for " + arg + ".");
      args.clear();
    }
    return true;
  }

  private boolean tryParsePrefix0(
      String arg, String eqValue, B builder, Consumer<String> errorReporter) {
    Pair<String, BiConsumer<B, String>> match = findMatchedPrefix(arg, prefix0);
    if (match == null) {
      return false;
    }
    if (eqValue != null) {
      errorReporter.accept("Option " + arg + " does not take a value.");
    } else {
      match.getSecond().accept(builder, match.getFirst());
    }
    return true;
  }

  private boolean tryParsePrefix1(
      String arg, String eqValue, Deque<String> args, B builder, Consumer<String> errorReporter) {
    Pair<String, TriConsumer<B, String, String>> match = findMatchedPrefix(arg, prefix1);
    if (match == null) {
      return false;
    }
    if (eqValue != null) {
      match.getSecond().accept(builder, match.getFirst(), eqValue);
    } else if (!args.isEmpty()) {
      match.getSecond().accept(builder, match.getFirst(), args.removeFirst());
    } else {
      errorReporter.accept("Missing parameter for " + arg + ".");
      args.clear();
    }
    return true;
  }

  private boolean tryParsePrefix2(
      String arg, String eqValue, Deque<String> args, B builder, Consumer<String> errorReporter) {
    Pair<String, QuadConsumer<B, String, String, String>> match = findMatchedPrefix(arg, prefix2);
    if (match == null) {
      return false;
    }
    String arg1;
    String arg2;
    if (eqValue != null) {
      arg1 = eqValue;
      if (!args.isEmpty()) {
        arg2 = args.removeFirst();
      } else {
        errorReporter.accept("Missing parameter for " + arg + ".");
        args.clear();
        return true;
      }
    } else {
      if (args.size() >= 2) {
        arg1 = args.removeFirst();
        arg2 = args.removeFirst();
      } else {
        errorReporter.accept("Missing parameter for " + arg + ".");
        args.clear();
        return true;
      }
    }
    match.getSecond().accept(builder, match.getFirst(), arg1, arg2);
    return true;
  }

  private boolean tryParsePositional(String rawArg, B builder) {
    if (positionalHandler == null) {
      return false;
    }
    positionalHandler.accept(builder, rawArg);
    return true;
  }

  /** The returned string in the pair is the suffix part of arg. */
  private <V> Pair<String, V> findMatchedPrefix(String arg, Map<String, V> prefixMap) {
    for (String prefix : prefixMap.keySet()) {
      if (arg.startsWith(prefix)) {
        String suffix = arg.substring(prefix.length());
        return Pair.create(suffix, prefixMap.get(prefix));
      }
    }
    return null;
  }

  private void addOption0(String name, Consumer<B> action) {
    assert assertThatOptionIsNew(name);
    options0.put(name, action);
  }

  private void addOption1(String name, BiConsumer<B, String> action) {
    assert assertThatOptionIsNew(name);
    options1.put(name, action);
  }

  private void addPrefix0(String prefix, BiConsumer<B, String> action) {
    assert assertThatPrefixIsNew(prefix);
    prefix0.put(prefix, action);
  }

  private void addPrefix1(String prefix, TriConsumer<B, String, String> action) {
    assert assertThatPrefixIsNew(prefix);
    prefix1.put(prefix, action);
  }

  private void addPrefix2(String prefix, QuadConsumer<B, String, String, String> action) {
    assert assertThatPrefixIsNew(prefix);
    prefix2.put(prefix, action);
  }

  private void addHelp(
      String name,
      String shorthand,
      String suffixLabel,
      ImmutableList<String> paramLabels,
      String description) {
    assert !name.contains("=") : name + " contains '='";
    if (shorthand != null) {
      assert !name.equals(shorthand) : "Shorthand is the same as the main name: " + name;
      assert !shorthand.contains("=") : shorthand + " contains '='";
    }
    optionInfos.add(new OptionInfo(name, shorthand, suffixLabel, paramLabels, description));
  }

  private void forEachOption(Consumer<String> action) {
    options0.keySet().forEach(action);
    options1.keySet().forEach(action);
  }

  private void forEachPrefix(Consumer<String> action) {
    prefix0.keySet().forEach(action);
    prefix1.keySet().forEach(action);
    prefix2.keySet().forEach(action);
  }

  private boolean assertThatOptionIsNew(String name) {
    forEachOption(
        existing -> {
          assert !name.equals(existing)
              : "Overlap detected: Option " + name + " and option " + existing;
        });
    forEachPrefix(
        existing -> {
          assert !name.startsWith(existing)
              : "Overlap detected: Option " + name + " and prefix " + existing;
        });
    return true;
  }

  private boolean assertThatPrefixIsNew(String name) {
    forEachOption(
        existing -> {
          assert !existing.startsWith(name)
              : "Overlap detected: Prefix " + name + " and option " + existing;
        });
    forEachPrefix(
        existing -> {
          assert !name.startsWith(existing) && !existing.startsWith(name)
              : "Overlap detected: Prefix " + name + " and prefix " + existing;
        });
    return true;
  }

  private boolean assertValidPositional() {
    assert positionalHandler == null : "A positional handler was already bound.";
    return true;
  }
}

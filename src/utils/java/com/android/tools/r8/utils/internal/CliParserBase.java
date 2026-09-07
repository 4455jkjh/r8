// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.internal;

import com.android.tools.r8.utils.internal.collections.Pair;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
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
  private final Map<String, TriConsumer<B, String, String>> options2 = new HashMap<>();
  private final Map<String, BiConsumer<B, String>> prefix0 = new HashMap<>();
  private final Map<String, TriConsumer<B, String, String>> prefix1 = new HashMap<>();
  private final Map<String, QuadConsumer<B, String, String, String>> prefix2 = new HashMap<>();
  private BiConsumer<B, String> positionalHandler;
  private final List<OptionInfo> optionInfos = new ArrayList<>();
  private final List<HelpInfo> helpInfos = new ArrayList<>();
  private final String usageHeader;

  /**
   * @param usageHeader can contain line breaks and will not be automatically wrapped.
   */
  public CliParserBase(String usageHeader) {
    this.usageHeader = usageHeader;
  }

  public interface HelpInfo {

    default boolean isOption() {
      return false;
    }

    default OptionInfo asOption() {
      return null;
    }

    default boolean isHelpText() {
      return false;
    }

    default HelpTextInfo asHelpText() {
      return null;
    }
  }

  public static class OptionInfo implements HelpInfo {

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

    @Override
    public boolean isOption() {
      return true;
    }

    @Override
    public OptionInfo asOption() {
      return this;
    }
  }

  public static class HelpTextInfo implements HelpInfo {

    public final String text;

    HelpTextInfo(String text) {
      assert text != null;
      this.text = text;
    }

    @Override
    public boolean isHelpText() {
      return true;
    }

    @Override
    public HelpTextInfo asHelpText() {
      return this;
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
   * @param name must be unique and non-overlapping
   */
  public CliParserBase<B> option2(
      String name,
      String paramLabel1,
      String paramLabel2,
      String description,
      TriConsumer<B, String, String> action) {
    addOption2(name, action);
    addHelp(name, null, null, ImmutableList.of(paramLabel1, paramLabel2), description);
    return this;
  }

  /**
   * @param name must be unique and non-overlapping
   * @param shorthand must be unique and non-overlapping
   */
  public CliParserBase<B> option2(
      String name,
      String paramLabel1,
      String paramLabel2,
      String description,
      TriConsumer<B, String, String> action,
      String shorthand) {
    addOption2(name, action);
    addOption2(shorthand, action);
    addHelp(name, shorthand, null, ImmutableList.of(paramLabel1, paramLabel2), description);
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
   * @param shorthand must be unique and non-overlapping
   */
  public CliParserBase<B> prefix0(
      String prefix,
      String suffixLabel,
      String description,
      BiConsumer<B, String> action,
      String shorthand) {
    addPrefix0(prefix, action);
    addPrefix0(shorthand, action);
    addHelp(prefix, shorthand, suffixLabel, ImmutableList.of(), description);
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
   * @param shorthand must be unique and non-overlapping
   */
  public CliParserBase<B> prefix1(
      String prefix,
      String suffixLabel,
      String paramLabel,
      String description,
      TriConsumer<B, String, String> action,
      String shorthand) {
    addPrefix1(prefix, action);
    addPrefix1(shorthand, action);
    addHelp(prefix, shorthand, suffixLabel, ImmutableList.of(paramLabel), description);
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
   * @param prefix must start with {@code --} and must be unique and non-overlapping
   * @param shorthand must be unique and non-overlapping
   */
  public CliParserBase<B> prefix2(
      String prefix,
      String suffixLabel,
      String paramLabel1,
      String paramLabel2,
      String description,
      QuadConsumer<B, String, String, String> action,
      String shorthand) {
    addPrefix2(prefix, action);
    addPrefix2(shorthand, action);
    addHelp(
        prefix, shorthand, suffixLabel, ImmutableList.of(paramLabel1, paramLabel2), description);
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

  public CliParserBase<B> addHelpText(String text) {
    assert text != null;
    helpInfos.add(new HelpTextInfo(text));
    return this;
  }

  public List<OptionInfo> getOptionInfo() {
    return ListUtils.unmodifiableForTesting(optionInfos);
  }

  public List<HelpInfo> getHelpInfo() {
    return ListUtils.unmodifiableForTesting(helpInfos);
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

      Pair<String, String> prefixMatch;
      if (tryParseOption0(arg, eqValue, builder, errorReporter)) {
        // Matched.
      } else if (tryParseOption1(arg, eqValue, args, builder, errorReporter)) {
        // Matched.
      } else if (tryParseOption2(arg, eqValue, args, builder, errorReporter)) {
        // Matched.
      } else if ((prefixMatch = findLongestMatchingPrefix(arg)) != null) {
        String prefix = prefixMatch.getFirst();
        String suffix = prefixMatch.getSecond();
        if (tryParsePrefix0(prefix, suffix, eqValue, builder, errorReporter)) {
          // Matched.
        } else if (tryParsePrefix1(prefix, suffix, eqValue, args, builder, errorReporter)) {
          // Matched.
        } else if (tryParsePrefix2(prefix, suffix, eqValue, args, builder, errorReporter)) {
          // Matched.
        } else {
          throw new Unreachable(
              "The found prefix "
                  + prefix
                  + " of "
                  + arg
                  + " was not handled (suffix: "
                  + suffix
                  + ")");
        }
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
      errorReporter.accept("Missing argument for " + arg + ".");
      args.clear();
    }
    return true;
  }

  private boolean tryParseOption2(
      String arg, String eqValue, Deque<String> args, B builder, Consumer<String> errorReporter) {
    if (!options2.containsKey(arg)) {
      return false;
    }
    if (eqValue != null) {
      errorReporter.accept(
          "Cannot use '--option=argument' syntax for " + arg + " (expects 2 arguments).");
      args.clear();
      return true;
    }

    String arg1;
    if (!args.isEmpty()) {
      arg1 = args.removeFirst();
    } else {
      errorReporter.accept("Missing arguments for " + arg + " (expects 2 arguments).");
      args.clear();
      return true;
    }

    String arg2;
    if (!args.isEmpty()) {
      arg2 = args.removeFirst();
    } else {
      errorReporter.accept("Missing second argument for " + arg + " (expects 2 arguments).");
      args.clear();
      return true;
    }

    options2.get(arg).accept(builder, arg1, arg2);
    return true;
  }

  private boolean tryParsePrefix0(
      String prefix, String suffix, String eqValue, B builder, Consumer<String> errorReporter) {
    BiConsumer<B, String> handler = prefix0.get(prefix);
    if (handler == null) {
      return false;
    }
    if (eqValue != null) {
      errorReporter.accept("Option " + prefix + suffix + " does not take an argument.");
    } else {
      handler.accept(builder, suffix);
    }
    return true;
  }

  private boolean tryParsePrefix1(
      String prefix,
      String suffix,
      String eqValue,
      Deque<String> args,
      B builder,
      Consumer<String> errorReporter) {
    TriConsumer<B, String, String> handler = prefix1.get(prefix);
    if (handler == null) {
      return false;
    }
    if (eqValue != null) {
      handler.accept(builder, suffix, eqValue);
    } else if (!args.isEmpty()) {
      handler.accept(builder, suffix, args.removeFirst());
    } else {
      errorReporter.accept("Missing argument for " + prefix + suffix + ".");
      args.clear();
    }
    return true;
  }

  private boolean tryParsePrefix2(
      String prefix,
      String suffix,
      String eqValue,
      Deque<String> args,
      B builder,
      Consumer<String> errorReporter) {
    QuadConsumer<B, String, String, String> handler = prefix2.get(prefix);
    if (handler == null) {
      return false;
    }
    if (eqValue != null) {
      errorReporter.accept(
          "Cannot use '--option=argument' syntax for "
              + prefix
              + suffix
              + " (expects 2 arguments).");
      args.clear();
      return true;
    }

    String arg1;
    if (!args.isEmpty()) {
      arg1 = args.removeFirst();
    } else {
      errorReporter.accept("Missing arguments for " + prefix + suffix + " (expects 2 arguments).");
      args.clear();
      return true;
    }

    String arg2;
    if (!args.isEmpty()) {
      arg2 = args.removeFirst();
    } else {
      errorReporter.accept(
          "Missing second argument for " + prefix + suffix + " (expects 2 arguments).");
      args.clear();
      return true;
    }
    handler.accept(builder, suffix, arg1, arg2);
    return true;
  }

  private boolean tryParsePositional(String rawArg, B builder) {
    if (positionalHandler == null) {
      return false;
    }
    positionalHandler.accept(builder, rawArg);
    return true;
  }

  /** Returns (prefix, suffix) for the longest prefix match in {@link #forEachPrefix} if any. */
  private Pair<String, String> findLongestMatchingPrefix(String arg) {
    final Box<String> bestMatchBox = new Box<>(null);
    forEachPrefix(
        prefix -> {
          if (arg.startsWith(prefix)) {
            String bestMatch = bestMatchBox.get();
            if (bestMatch == null || prefix.length() > bestMatch.length()) {
              bestMatchBox.set(prefix);
            }
          }
        });
    String bestMatch = bestMatchBox.get();
    if (bestMatch != null) {
      String suffix = arg.substring(bestMatch.length());
      return Pair.create(bestMatch, suffix);
    }
    return null;
  }

  private void addOption0(String name, Consumer<B> action) {
    assert name != null;
    assert action != null;
    assert assertThatOptionIsNew(name);
    options0.put(name, action);
  }

  private void addOption1(String name, BiConsumer<B, String> action) {
    assert name != null;
    assert action != null;
    assert assertThatOptionIsNew(name);
    options1.put(name, action);
  }

  private void addOption2(String name, TriConsumer<B, String, String> action) {
    assert name != null;
    assert action != null;
    assert assertThatOptionIsNew(name);
    options2.put(name, action);
  }

  private void addPrefix0(String prefix, BiConsumer<B, String> action) {
    assert prefix != null;
    assert action != null;
    assert assertThatPrefixIsNew(prefix);
    prefix0.put(prefix, action);
  }

  private void addPrefix1(String prefix, TriConsumer<B, String, String> action) {
    assert prefix != null;
    assert action != null;
    assert assertThatPrefixIsNew(prefix);
    prefix1.put(prefix, action);
  }

  private void addPrefix2(String prefix, QuadConsumer<B, String, String, String> action) {
    assert prefix != null;
    assert action != null;
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
    OptionInfo optionInfo = new OptionInfo(name, shorthand, suffixLabel, paramLabels, description);
    optionInfos.add(optionInfo);
    helpInfos.add(optionInfo);
  }

  private void forEachOption(Consumer<String> action) {
    options0.keySet().forEach(action);
    options1.keySet().forEach(action);
    options2.keySet().forEach(action);
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
          assert !name.equals(existing) : "Duplicate prefix: " + name;
        });
    return true;
  }

  private boolean assertValidPositional() {
    assert positionalHandler == null : "A positional handler was already bound.";
    return true;
  }
}

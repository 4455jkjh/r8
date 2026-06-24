// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.internal;


import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** A wrapper around {@link CliParserBase} with enforced conventions. */
public class CliParser<B> {

  private final CliParserBase<B> base;
  private BiConsumer<B, String> originalPositionalHandler;
  private Consumer<String> currentErrorReporter;

  public CliParser(String usageHeader) {
    this(usageHeader, false);
  }

  public CliParser(String usageHeader, boolean unknownOptionPassthrough) {
    this.base = new CliParserBase<>(usageHeader);
    if (unknownOptionPassthrough) {
      this.base.positional(
          (b, arg) -> {
            if (originalPositionalHandler != null) {
              originalPositionalHandler.accept(b, arg);
            }
          });
    } else {
      // Intercept '--<something>' before the positional handler.
      this.base.positional(
          (b, arg) -> {
            if (arg.startsWith("-")) {
              currentErrorReporter.accept("Unknown option: " + arg);
            } else if (originalPositionalHandler != null) {
              originalPositionalHandler.accept(b, arg);
            } else {
              // This is CliParserBase behaviour, conceptually it should return "don't handle
              // anyway" here.
              currentErrorReporter.accept("Unexpected argument: " + arg);
            }
          });
    }
  }

  public CliParserBase<B> baseParser() {
    return base;
  }

  public CliParser<B> withBaseParser(Consumer<CliParserBase<B>> action) {
    action.accept(base);
    return this;
  }

  public CliParser<B> positional(BiConsumer<B, String> action) {
    assert originalPositionalHandler == null : "A positional handler was already bound.";
    this.originalPositionalHandler = action;
    return this;
  }

  public void parse(String[] args, B builder, Consumer<String> errorReporter) {
    this.currentErrorReporter = errorReporter;
    base.parse(args, builder, errorReporter);
  }

  /**
   * @param name must start with {@code --} and must be unique and non-overlapping
   * @param description must not contains line breaks, must end with {@code .}, and is automatically
   *     wrapped
   */
  public CliParser<B> option0(String name, String description, Consumer<B> action) {
    checkOptionName(name);
    checkDescription(description);
    base.option0(name, description, action);
    return this;
  }

  /**
   * @param name must start with {@code --} and must be unique and non-overlapping
   * @param description must not contains line breaks, must end with {@code .}, and is automatically
   *     wrapped
   * @param shorthand must start with {@code -} and must be unique and non-overlapping
   */
  public CliParser<B> option0(
      String name, String description, Consumer<B> action, String shorthand) {
    checkOptionName(name);
    checkShorthand(shorthand);
    checkDescription(description);
    base.option0(name, description, action, shorthand);
    return this;
  }

  /**
   * @param name must start with {@code --} and must be unique and non-overlapping
   * @param paramLabel must be surrounded by {@code <} and {@code >}
   * @param description must not contains line breaks, must end with {@code .}, and is automatically
   *     wrapped
   */
  public CliParser<B> option1(
      String name, String paramLabel, String description, BiConsumer<B, String> action) {
    checkOptionName(name);
    checkParam(paramLabel);
    checkDescription(description);
    base.option1(name, paramLabel, description, action);
    return this;
  }

  /**
   * @param name must start with {@code --} and must be unique and non-overlapping
   * @param paramLabel must be surrounded by {@code <} and {@code >}
   * @param description must not contains line breaks, must end with {@code .}, and is automatically
   *     wrapped
   * @param shorthand must start with {@code -} and must be unique and non-overlapping
   */
  public CliParser<B> option1(
      String name,
      String paramLabel,
      String description,
      BiConsumer<B, String> action,
      String shorthand) {
    checkOptionName(name);
    checkShorthand(shorthand);
    checkParam(paramLabel);
    checkDescription(description);
    base.option1(name, paramLabel, description, action, shorthand);
    return this;
  }

  /**
   * It is assumed that the suffix is optional.
   *
   * @param prefix must start with {@code --} and must be unique and non-overlapping
   * @param suffixLabel must be surrounded by {@code [} and {@code ]}
   * @param description must not contains line breaks, must end with {@code .}, and is automatically
   *     wrapped
   */
  public CliParser<B> prefix0(
      String prefix, String suffixLabel, String description, BiConsumer<B, String> action) {
    checkOptionName(prefix);
    checkOptionalParam(suffixLabel);
    checkDescription(description);
    base.prefix0(prefix, suffixLabel, description, action);
    return this;
  }

  /**
   * It is assumed that the suffix is optional.
   *
   * @param prefix must start with {@code --} and must be unique and non-overlapping
   * @param suffixLabel must be surrounded by {@code [} and {@code ]}
   * @param paramLabel must be surrounded by {@code <} and {@code >}
   * @param description must not contains line breaks, must end with {@code .}, and is automatically
   *     wrapped
   */
  public CliParser<B> prefix1(
      String prefix,
      String suffixLabel,
      String paramLabel,
      String description,
      TriConsumer<B, String, String> action) {
    checkOptionName(prefix);
    checkOptionalParam(suffixLabel);
    checkParam(paramLabel);
    checkDescription(description);
    base.prefix1(prefix, suffixLabel, paramLabel, description, action);
    return this;
  }

  /**
   * It is assumed that the suffix is optional.
   *
   * @param prefix must start with {@code --} and must be unique and non-overlapping
   * @param suffixLabel must be surrounded by {@code [} and {@code ]}
   * @param paramLabel1 must be surrounded by {@code <} and {@code >}
   * @param paramLabel2 must be surrounded by {@code <} and {@code >}
   * @param description must not contains line breaks, must end with {@code .}, and is automatically
   *     wrapped
   */
  public CliParser<B> prefix2(
      String prefix,
      String suffixLabel,
      String paramLabel1,
      String paramLabel2,
      String description,
      QuadConsumer<B, String, String, String> action) {
    checkOptionName(prefix);
    checkOptionalParam(suffixLabel);
    checkParam(paramLabel1);
    checkParam(paramLabel2);
    checkDescription(description);
    base.prefix2(prefix, suffixLabel, paramLabel1, paramLabel2, description, action);
    return this;
  }

  private void checkOptionName(String name) {
    assert name.startsWith("--") : name + " does not start with --";
    assert name.length() > 2 : name + " is an empty name";
    assert name.charAt(2) != '-' : name + " has a third '-'";
  }

  private void checkShorthand(String shorthand) {
    assert shorthand.startsWith("-") : shorthand + " does not start with -";
    assert shorthand.length() > 1 : shorthand + " is an empty name";
    assert shorthand.charAt(1) != '-' : shorthand + " has a second '-'";
  }

  private void checkParam(String param) {
    assert param.startsWith("<") && param.endsWith(">") : param + " is not surrounded by <>.";
    assert param.length() > 2 : "parameter label content is empty: " + param;
  }

  private void checkOptionalParam(String param) {
    assert param.startsWith("[") && param.endsWith("]") : param + " is not surrounded by [].";
    assert param.length() > 2 : "optional parameter label content is empty: " + param;
  }

  private void checkDescription(String description) {
    assert !description.contains("\n")
        : "descriptions should rely on automatic wrapping: " + description;
    assert !description.trim().isEmpty() : "description is empty.";
    assert description.endsWith(".") : "description doesn't end with '.': " + description;
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.position.TextPosition;
import com.android.tools.r8.position.TextRange;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.shaking.ProguardConfigurationParserOptions;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardConfigurationSource;
import com.android.tools.r8.shaking.ProguardConfigurationSourceFile;
import com.android.tools.r8.shaking.ProguardConfigurationSourceStrings;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.AbortException;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Reporter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Keep rules parsed with R8's ProGuard configuration parser. */
final class KeepRuleSet {

  /** A parsed rule with its location. */
  static final class Rule {

    private final ProguardConfigurationRule rule;
    private final String file;
    private final int line;
    private final String sourceText;

    private Rule(ProguardConfigurationRule rule) {
      this.rule = rule;
      this.file = getFileName(rule.getOrigin());
      this.line = getLine(rule.getPosition());
      String source = rule.getSource();
      this.sourceText = source == null ? rule.toString() : source.trim();
    }

    private static int getLine(Position position) {
      if (position instanceof TextRange) {
        return ((TextRange) position).getStart().getLine();
      }
      return position instanceof TextPosition ? ((TextPosition) position).getLine() : -1;
    }

    private static String getFileName(Origin origin) {
      if (origin instanceof PathOrigin) {
        return ((PathOrigin) origin).getPath().toString();
      }
      return origin.toString();
    }

    ProguardConfigurationRule get() {
      return rule;
    }

    /** The location of the rule, e.g. {@code proguard-rules.pro:12}. */
    String getLocation() {
      return line >= 0 ? file + ":" + line : file;
    }

    /** The rule text as written, possibly over multiple lines. */
    String getSourceText() {
      return sourceText;
    }

    boolean isKeepRule() {
      return rule.isProguardKeepRule() || rule.isProguardIfRule();
    }

    @Override
    public String toString() {
      return getLocation() + " " + sourceText.replaceAll("\\s+", " ");
    }
  }

  /** Thrown when the rules cannot be parsed. */
  static final class RulesException extends Exception {

    RulesException(String message) {
      super(message);
    }
  }

  private final DexItemFactory factory;
  private final List<Rule> rules;
  private final ProguardKeepAttributes keepAttributes;
  private final List<String> warnings;
  private final Map<String, DexType> types = new HashMap<>();

  private KeepRuleSet(
      DexItemFactory factory,
      List<Rule> rules,
      ProguardKeepAttributes keepAttributes,
      List<String> warnings) {
    this.factory = factory;
    this.rules = rules;
    this.keepAttributes = keepAttributes;
    this.warnings = warnings;
  }

  static KeepRuleSet parse(List<Path> files) throws RulesException {
    List<ProguardConfigurationSource> sources = new ArrayList<>();
    for (Path file : files) {
      sources.add(new ProguardConfigurationSourceFile(file));
    }
    return parseSources(sources);
  }

  /** Parses rules given as a string, e.g. for checking rendered rules. */
  static KeepRuleSet parseString(String rules) throws RulesException {
    List<ProguardConfigurationSource> sources = new ArrayList<>();
    sources.add(new ProguardConfigurationSourceStrings(rules, null, Origin.unknown()));
    return parseSources(sources);
  }

  private static KeepRuleSet parseSources(List<ProguardConfigurationSource> sources)
      throws RulesException {
    List<String> errors = new ArrayList<>();
    List<String> warnings = new ArrayList<>();
    Reporter reporter =
        new Reporter(
            new DiagnosticsHandler() {
              @Override
              public void error(Diagnostic error) {
                errors.add(format(error));
              }

              @Override
              public void warning(Diagnostic warning) {
                warnings.add(format(warning));
              }

              @Override
              public void info(Diagnostic info) {
                // Ignored.
              }
            });
    DexItemFactory factory = new DexItemFactory();
    ProguardConfigurationParserOptions parserOptions =
        ProguardConfigurationParserOptions.builder()
            .setEnableLegacyFullModeForKeepRules(false)
            .setEnableTestingOptions(false)
            .build();
    ProguardConfiguration.Builder builder = ProguardConfiguration.builder(factory, reporter);
    ProguardConfigurationParser parser =
        new ProguardConfigurationParser(factory, reporter, parserOptions, null, builder);
    try {
      parser.parse(sources);
    } catch (RuntimeException e) {
      // The errors have been collected by the diagnostics handler. Reporter.failIfPendingErrors()
      // throws the AbortException wrapped in a RuntimeException.
      if (!(e instanceof AbortException) && !(e.getCause() instanceof AbortException)) {
        throw e;
      }
    }
    if (!errors.isEmpty()) {
      throw new RulesException(String.join("\n", errors));
    }
    ProguardConfiguration configuration = builder.build(parserOptions);
    List<Rule> rules = new ArrayList<>();
    for (ProguardConfigurationRule rule : configuration.getRules()) {
      rules.add(new Rule(rule));
    }
    return new KeepRuleSet(factory, rules, configuration.getKeepAttributes(), warnings);
  }

  private static String format(Diagnostic diagnostic) {
    String location =
        Origin.unknown().equals(diagnostic.getOrigin()) ? "" : diagnostic.getOrigin() + "";
    Position position = diagnostic.getPosition();
    if (position instanceof TextPosition) {
      location += ":" + ((TextPosition) position).getLine();
    }
    return location.isEmpty()
        ? diagnostic.getDiagnosticMessage()
        : location + ": " + diagnostic.getDiagnosticMessage();
  }

  List<Rule> getRules() {
    return rules;
  }

  List<String> getWarnings() {
    return warnings;
  }

  /** True if the rules keep the InnerClasses and EnclosingMethod attributes. */
  boolean keepsNestingAttributes() {
    return keepAttributes.innerClasses && keepAttributes.enclosingMethod;
  }

  boolean keepsSignatureAttribute() {
    return keepAttributes.signature;
  }

  /**
   * The type for a Java type name, created in the factory used for parsing the rules, or null if
   * the name is not a valid type name.
   */
  DexType getType(String typeName) {
    if (!isValidTypeName(typeName)) {
      return null;
    }
    return types.computeIfAbsent(
        typeName, name -> factory.createType(DescriptorUtils.javaTypeToDescriptor(name)));
  }

  private static boolean isValidTypeName(String typeName) {
    if (typeName == null || typeName.isEmpty()) {
      return false;
    }
    for (int i = 0; i < typeName.length(); i++) {
      char c = typeName.charAt(i);
      if (Character.isWhitespace(c) || c == '/' || c == ';' || c == '(' || c == ')') {
        return false;
      }
    }
    return true;
  }
}

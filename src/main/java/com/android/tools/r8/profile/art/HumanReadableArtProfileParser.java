// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.diagnostic.HumanReadableArtProfileParserErrorDiagnostic;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.Action;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.Reporter;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.function.Consumer;

public class HumanReadableArtProfileParser {

  private final Consumer<HumanReadableArtProfileParserErrorDiagnostic> diagnosticConsumer;
  private final ArtProfileBuilder profileBuilder;
  private final ArtProfileRulePredicate rulePredicate;
  private final Reporter reporter;

  HumanReadableArtProfileParser(
      Consumer<HumanReadableArtProfileParserErrorDiagnostic> diagnosticConsumer,
      ArtProfileBuilder profileBuilder,
      ArtProfileRulePredicate rulePredicate,
      Reporter reporter) {
    this.diagnosticConsumer = diagnosticConsumer;
    this.profileBuilder = profileBuilder;
    this.rulePredicate = rulePredicate;
    this.reporter = reporter;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void parse(TextInputStream textInputStream, Origin origin) {
    try {
      try (InputStreamReader inputStreamReader =
              new InputStreamReader(
                  textInputStream.getInputStream(), textInputStream.getCharset());
          BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
        int lineNumber = 1;
        while (bufferedReader.ready()) {
          String line = bufferedReader.readLine();
          String lineWithoutComment = removeCommentFromLine(line);
          if (isWhitespace(lineWithoutComment)) {
            // Skip.
          } else if (!parseRule(lineWithoutComment)) {
            parseError(line, lineNumber, origin);
          }
          lineNumber++;
        }
      }
      if (reporter != null) {
        reporter.failIfPendingErrors();
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void parseError(String rule, int lineNumber, Origin origin) {
    if (diagnosticConsumer != null) {
      diagnosticConsumer.accept(
          new HumanReadableArtProfileParserErrorDiagnostic(rule, lineNumber, origin));
    }
  }

  private boolean isWhitespace(String line) {
    for (int i = 0; i < line.length(); i++) {
      if (!Character.isWhitespace(line.charAt(i))) {
        return false;
      }
    }
    return true;
  }

  public boolean parseRule(String rule) {
    try {
      ArtProfileMethodRuleInfoImpl.Builder methodRuleInfoBuilder =
          ArtProfileMethodRuleInfoImpl.builder();
      rule = parseFlag(rule, 'H', methodRuleInfoBuilder::setIsHot);
      rule = parseFlag(rule, 'S', methodRuleInfoBuilder::setIsStartup);
      rule = parseFlag(rule, 'P', methodRuleInfoBuilder::setIsPostStartup);
      return parseClassOrMethodRule(rule, methodRuleInfoBuilder.build());
    } catch (Throwable t) {
      return false;
    }
  }

  private static String parseFlag(String rule, char c, Action action) {
    if (!rule.isEmpty() && rule.charAt(0) == c) {
      action.execute();
      return rule.substring(1);
    }
    return rule;
  }

  private boolean parseClassOrMethodRule(String rule, ArtProfileMethodRuleInfoImpl methodRuleInfo) {
    int arrowStartIndex = rule.indexOf("->");
    if (arrowStartIndex >= 0) {
      return parseMethodRule(rule, methodRuleInfo, arrowStartIndex);
    } else if (methodRuleInfo.isEmpty()) {
      return parseClassRule(rule);
    } else {
      return false;
    }
  }

  private boolean parseClassRule(String descriptor) {
    TypeReference typeReference = Reference.typeFromDescriptor(descriptor);
    if (typeReference == null) {
      return false;
    }
    if (typeReference.isArray()) {
      typeReference = typeReference.asArray().getBaseType();
    }
    if (typeReference.isPrimitive()) {
      return false;
    }
    assert typeReference.isClass();
    ClassReference classReference = typeReference.asClass();
    if (rulePredicate.testClassRule(classReference, ArtProfileClassRuleInfoImpl.empty())) {
      profileBuilder.addClassRule(
          classRuleBuilder -> classRuleBuilder.setClassReference(classReference));
    }
    return true;
  }

  private boolean parseMethodRule(
      String rule, ArtProfileMethodRuleInfoImpl methodRuleInfo, int arrowStartIndex) {
    int inlineCacheStartIndex = rule.indexOf('+', arrowStartIndex + 2);
    String descriptor = inlineCacheStartIndex > 0 ? rule.substring(0, inlineCacheStartIndex) : rule;
    MethodReference methodReference =
        MethodReferenceUtils.parseSmaliString(descriptor, arrowStartIndex);
    if (methodReference == null) {
      return false;
    }
    if (rulePredicate.testMethodRule(methodReference, methodRuleInfo)) {
      profileBuilder.addMethodRule(
          methodRuleBuilder ->
              methodRuleBuilder
                  .setMethodReference(methodReference)
                  .setMethodRuleInfo(
                      methodRuleInfoBuilder ->
                          methodRuleInfoBuilder
                              .setIsHot(methodRuleInfo.isHot())
                              .setIsStartup(methodRuleInfo.isStartup())
                              .setIsPostStartup(methodRuleInfo.isPostStartup())));
    }
    return true;
  }

  private static String removeCommentFromLine(String line) {
    int commentStartIndex = line.indexOf('#');
    if (commentStartIndex >= 0) {
      return line.substring(0, commentStartIndex).stripTrailing();
    }
    return line;
  }

  public static class Builder implements HumanReadableArtProfileParserBuilder {

    private Consumer<HumanReadableArtProfileParserErrorDiagnostic> diagnosticConsumer;
    private ArtProfileBuilder profileBuilder;
    private ArtProfileRulePredicate rulePredicate = new AlwaysTrueArtProfileRulePredicate();
    private Reporter reporter;

    public Builder setDiagnosticConsumer(
        Consumer<HumanReadableArtProfileParserErrorDiagnostic> diagnosticConsumer) {
      this.diagnosticConsumer = diagnosticConsumer;
      return this;
    }

    public Builder setReporter(Reporter reporter) {
      this.reporter = reporter;
      return this;
    }

    @Override
    public Builder setRulePredicate(ArtProfileRulePredicate rulePredicate) {
      this.rulePredicate = rulePredicate;
      return this;
    }

    public Builder setProfileBuilder(ArtProfileBuilder profileBuilder) {
      this.profileBuilder = profileBuilder;
      return this;
    }

    public HumanReadableArtProfileParser build() {
      if (diagnosticConsumer == null && reporter != null) {
        diagnosticConsumer = reporter::error;
      }
      return new HumanReadableArtProfileParser(
          diagnosticConsumer, profileBuilder, rulePredicate, reporter);
    }
  }
}

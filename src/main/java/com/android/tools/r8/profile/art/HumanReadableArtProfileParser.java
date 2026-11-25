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
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.internal.Action;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import it.unimi.dsi.fastutil.chars.CharSet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.util.LinkedHashSet;
import java.util.Set;
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
      rule = parseFlags(rule, methodRuleInfoBuilder);
      return parseClassOrMethodRule(rule, methodRuleInfoBuilder.build());
    } catch (Throwable t) {
      return false;
    }
  }

  private static String parseFlags(
      String rule, ArtProfileMethodRuleInfoImpl.Builder methodRuleInfoBuilder) {
    CharSet seenFlags = new CharArraySet(3);
    String previousRule;
    do {
      previousRule = rule;
      rule = parseFlagIfNotSeen(rule, 'H', methodRuleInfoBuilder::setIsHot, seenFlags);
      rule = parseFlagIfNotSeen(rule, 'S', methodRuleInfoBuilder::setIsStartup, seenFlags);
      rule = parseFlagIfNotSeen(rule, 'P', methodRuleInfoBuilder::setIsPostStartup, seenFlags);
    } while (!rule.equals(previousRule));
    return rule;
  }

  private static String parseFlagIfNotSeen(String rule, char c, Action action, CharSet seenFlags) {
    if (!rule.isEmpty() && rule.charAt(0) == c && seenFlags.add(c)) {
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
    descriptor = DescriptorUtils.toBaseDescriptor(descriptor);
    if (!DescriptorUtils.isValidClassDescriptor(descriptor)) {
      return false;
    }
    TypeReference typeReference = Reference.typeFromDescriptor(descriptor);
    assert typeReference != null;
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
    int inlineCacheStartIndex =
        rule.indexOf(ArtProfileInlineCacheData.INLINE_CACHE_SEPARATOR, arrowStartIndex + 2);
    String descriptor =
        inlineCacheStartIndex >= 0 ? rule.substring(0, inlineCacheStartIndex) : rule;
    MethodReference methodReference =
        MethodReferenceUtils.parseSmaliString(descriptor, arrowStartIndex);
    if (methodReference == null) {
      return false;
    }
    if (!DescriptorUtils.isValidClassDescriptor(methodReference.getHolderClass().getDescriptor())) {
      return false;
    }
    for (TypeReference formalType : methodReference.getFormalTypes()) {
      if (!DescriptorUtils.isValidDescriptor(formalType.getDescriptor())) {
        return false;
      }
    }
    if (methodReference.getReturnType() != null
        && !DescriptorUtils.isValidDescriptor(methodReference.getReturnType().getDescriptor())) {
      return false;
    }
    ArtProfileInlineCacheData inlineCacheData =
        inlineCacheStartIndex >= 0
            ? parseInlineCacheData(rule.substring(inlineCacheStartIndex + 1))
            : ArtProfileInlineCacheData.empty();
    if (inlineCacheData == null) {
      return false;
    }
    if (rulePredicate.testMethodRule(methodReference, methodRuleInfo)) {
      profileBuilder.addMethodRule(
          methodRuleBuilder -> {
            methodRuleBuilder
                .setMethodReference(methodReference)
                .setMethodRuleInfo(
                    methodRuleInfoBuilder ->
                        methodRuleInfoBuilder
                            .setIsHot(methodRuleInfo.isHot())
                            .setIsStartup(methodRuleInfo.isStartup())
                            .setIsPostStartup(methodRuleInfo.isPostStartup()));
            if (!inlineCacheData.isEmpty()
                && methodRuleBuilder instanceof InternalArtProfileMethodRuleBuilder) {
              ((InternalArtProfileMethodRuleBuilder) methodRuleBuilder)
                  .setInlineCacheData(inlineCacheData);
            }
          });
    }
    return true;
  }

  private static ArtProfileInlineCacheData parseInlineCacheData(String icString) {
    if (icString.isEmpty()
        || icString.indexOf(ArtProfileInlineCacheData.INLINE_CACHE_SEPARATOR) >= 0) {
      return null;
    }
    if (icString.charAt(0) != ArtProfileInlineCacheData.TARGET_SEPARATOR) {
      if (icString.indexOf(ArtProfileInlineCacheData.TARGET_SEPARATOR) >= 0) {
        return null;
      }
      ArtProfileInlineCacheData.EntryValue entryValue = parseInlineCacheEntryValue(icString);
      return entryValue != null ? ArtProfileInlineCacheData.createSingle(entryValue) : null;
    }
    ArtProfileInlineCacheData.Builder builder = ArtProfileInlineCacheData.builder();
    int segmentStart = 1;
    while (segmentStart <= icString.length()) {
      int nextTargetIndex =
          icString.indexOf(ArtProfileInlineCacheData.TARGET_SEPARATOR, segmentStart);
      String segment =
          nextTargetIndex >= 0
              ? icString.substring(segmentStart, nextTargetIndex)
              : icString.substring(segmentStart);
      if (!parseTargetedInlineCacheSegment(segment, builder)) {
        return null;
      }
      if (nextTargetIndex < 0) {
        break;
      }
      segmentStart = nextTargetIndex + 1;
    }
    return builder.build();
  }

  private static boolean parseTargetedInlineCacheSegment(
      String segment, ArtProfileInlineCacheData.Builder builder) {
    if (segment.isEmpty()) {
      return false;
    }
    int dimensions = 0;
    while (dimensions < segment.length() && segment.charAt(dimensions) == '[') {
      dimensions++;
    }
    if (dimensions > 255 || dimensions >= segment.length()) {
      return false;
    }
    int receiverEndIndex;
    if (segment.charAt(dimensions) == 'L') {
      int semicolonIndex = segment.indexOf(';', dimensions + 1);
      if (semicolonIndex < 0) {
        return false;
      }
      receiverEndIndex = semicolonIndex + 1;
    } else if (dimensions > 0) {
      receiverEndIndex = dimensions + 1;
    } else {
      return false;
    }
    String receiverDescriptor = segment.substring(0, receiverEndIndex);
    if (!isValidInlineCacheTypeDescriptor(receiverDescriptor)) {
      return false;
    }
    ArtProfileInlineCacheData.EntryValue entryValue =
        parseInlineCacheEntryValue(segment.substring(receiverEndIndex));
    if (entryValue == null) {
      return false;
    }
    builder.addTargetedInlineCache(Reference.typeFromDescriptor(receiverDescriptor), entryValue);
    return true;
  }

  private static ArtProfileInlineCacheData.EntryValue parseInlineCacheEntryValue(
      String valueString) {
    if (valueString.isEmpty()) {
      return null;
    }
    if (valueString.equals(ArtProfileInlineCacheData.MISSING_TYPES_MARKER)) {
      return ArtProfileInlineCacheData.EntryValue.missingTypes();
    }
    if (valueString.equals(ArtProfileInlineCacheData.MEGAMORPHIC_TYPES_MARKER)) {
      return ArtProfileInlineCacheData.EntryValue.megamorphicTypes();
    }
    Set<TypeReference> types = new LinkedHashSet<>();
    int start = 0;
    while (true) {
      int commaIndex = valueString.indexOf(ArtProfileInlineCacheData.TYPE_SEPARATOR, start);
      String token =
          commaIndex >= 0 ? valueString.substring(start, commaIndex) : valueString.substring(start);
      if (!isValidInlineCacheTypeDescriptor(token)) {
        return null;
      }
      types.add(Reference.typeFromDescriptor(token));
      if (commaIndex < 0) {
        break;
      }
      start = commaIndex + 1;
    }
    return ArtProfileInlineCacheData.EntryValue.ofTypes(types);
  }

  private static boolean isValidInlineCacheTypeDescriptor(String descriptor) {
    if (descriptor == null || descriptor.isEmpty()) {
      return false;
    }
    int dimensions = 0;
    while (dimensions < descriptor.length() && descriptor.charAt(dimensions) == '[') {
      dimensions++;
    }
    if (dimensions == 0) {
      return DescriptorUtils.isValidClassDescriptor(descriptor);
    }
    if (dimensions > 255 || dimensions == descriptor.length()) {
      return false;
    }
    String baseDescriptor = descriptor.substring(dimensions);
    return DescriptorUtils.isPrimitiveDescriptor(baseDescriptor)
        || DescriptorUtils.isValidClassDescriptor(baseDescriptor);
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

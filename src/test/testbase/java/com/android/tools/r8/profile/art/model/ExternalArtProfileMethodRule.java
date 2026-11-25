// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.model;

import com.android.tools.r8.profile.art.ArtProfileInlineCacheData;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfoImpl;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** Represents a method rule from an ART baseline profile, backed by {@link MethodReference}. */
public class ExternalArtProfileMethodRule extends ExternalArtProfileRule {

  private final MethodReference methodReference;
  private final ArtProfileMethodRuleInfo methodRuleInfo;
  private final ArtProfileInlineCacheData inlineCacheData;

  ExternalArtProfileMethodRule(
      MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo) {
    this(methodReference, methodRuleInfo, ArtProfileInlineCacheData.empty());
  }

  ExternalArtProfileMethodRule(
      MethodReference methodReference,
      ArtProfileMethodRuleInfo methodRuleInfo,
      ArtProfileInlineCacheData inlineCacheData) {
    assert methodReference != null;
    assert methodRuleInfo != null;
    assert inlineCacheData != null;
    this.methodReference = methodReference;
    this.methodRuleInfo = methodRuleInfo;
    this.inlineCacheData = inlineCacheData;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void accept(
      Consumer<ExternalArtProfileClassRule> classRuleConsumer,
      Consumer<ExternalArtProfileMethodRule> methodRuleConsumer) {
    methodRuleConsumer.accept(this);
  }

  public MethodReference getMethodReference() {
    return methodReference;
  }

  public ArtProfileMethodRuleInfo getMethodRuleInfo() {
    return methodRuleInfo;
  }

  public ArtProfileInlineCacheData getInlineCacheData() {
    return inlineCacheData;
  }

  @Override
  public boolean test(
      Predicate<ExternalArtProfileClassRule> classRuleConsumer,
      Predicate<ExternalArtProfileMethodRule> methodRuleConsumer) {
    return methodRuleConsumer.test(this);
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    ExternalArtProfileMethodRule methodRule = (ExternalArtProfileMethodRule) obj;
    return methodReference.equals(methodRule.methodReference)
        && methodRuleInfo.equals(methodRule.methodRuleInfo)
        && inlineCacheData.equals(methodRule.inlineCacheData);
  }

  @Override
  public int hashCode() {
    return methodReference.hashCode();
  }

  @Override
  public String toString() {
    return methodRuleInfo.toString()
        + MethodReferenceUtils.toSmaliString(methodReference)
        + inlineCacheData.toString();
  }

  public static class Builder {

    private MethodReference methodReference;
    private ArtProfileMethodRuleInfo methodRuleInfo = ArtProfileMethodRuleInfoImpl.empty();
    private ArtProfileInlineCacheData inlineCacheData = ArtProfileInlineCacheData.empty();

    public Builder setMethodReference(MethodReference methodReference) {
      this.methodReference = methodReference;
      return this;
    }

    public Builder setMethodRuleInfo(ArtProfileMethodRuleInfo methodRuleInfo) {
      this.methodRuleInfo = methodRuleInfo;
      return this;
    }

    public Builder setInlineCacheData(ArtProfileInlineCacheData inlineCacheData) {
      this.inlineCacheData = inlineCacheData;
      return this;
    }

    public ExternalArtProfileMethodRule build() {
      return new ExternalArtProfileMethodRule(methodReference, methodRuleInfo, inlineCacheData);
    }
  }
}

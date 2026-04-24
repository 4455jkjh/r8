// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.utils.internal.ObjectUtils;
import com.android.tools.r8.utils.internal.OptionalBool;

public class ProguardKeepRuleModifiers {
  public static class Builder {

    // The default value of the fields typed as OptionalBool is determined by InternalOptions.
    private OptionalBool allowsAccessModification = OptionalBool.unknown();
    private boolean allowsAnnotationRemoval = false;
    private OptionalBool allowsCodeReplacement = OptionalBool.unknown();
    private OptionalBool allowsFinalModification = OptionalBool.unknown();
    private boolean allowsRepackaging = false;
    private boolean allowsShrinking = false;
    private boolean allowsOptimization = false;
    private boolean allowsObfuscation = false;
    private boolean includeDescriptorClasses = false;
    private boolean allowsPermittedSubclassesRemoval = false;

    private Builder() {}

    public Builder setAllowsAll() {
      setAllowsAccessModification(true);
      setAllowsAnnotationRemoval(true);
      setAllowsCodeReplacement(false);
      setAllowsFinalModification(true);
      setAllowsObfuscation(true);
      setAllowsOptimization(true);
      setAllowsRepackaging(true);
      setAllowsShrinking(true);
      setAllowsPermittedSubclassesRemoval(true);
      return this;
    }

    public Builder setAllowsAccessModification(boolean allowsAccessModification) {
      this.allowsAccessModification = OptionalBool.of(allowsAccessModification);
      return this;
    }

    public Builder setAllowsAnnotationRemoval(boolean allowsAnnotationRemoval) {
      this.allowsAnnotationRemoval = allowsAnnotationRemoval;
      return this;
    }

    public Builder setAllowsCodeReplacement(boolean allowsCodeReplacement) {
      this.allowsCodeReplacement = OptionalBool.of(allowsCodeReplacement);
      return this;
    }

    public Builder setAllowsFinalModification(boolean allowsFinalModification) {
      this.allowsFinalModification = OptionalBool.of(allowsFinalModification);
      return this;
    }

    public Builder setAllowsShrinking(boolean allowsShrinking) {
      this.allowsShrinking = allowsShrinking;
      return this;
    }

    public Builder setAllowsOptimization(boolean allowsOptimization) {
      this.allowsOptimization = allowsOptimization;
      return this;
    }

    public Builder setAllowsObfuscation(boolean allowsObfuscation) {
      this.allowsObfuscation = allowsObfuscation;
      if (allowsObfuscation) {
        this.allowsRepackaging = true;
      }
      return this;
    }

    public Builder setAllowsRepackaging(boolean allowsRepackaging) {
      this.allowsRepackaging = allowsRepackaging;
      return this;
    }

    public Builder setAllowsPermittedSubclassesRemoval(boolean allowsPermittedSubclassesRemoval) {
      this.allowsPermittedSubclassesRemoval = allowsPermittedSubclassesRemoval;
      return this;
    }

    public void setIncludeDescriptorClasses(boolean includeDescriptorClasses) {
      this.includeDescriptorClasses = includeDescriptorClasses;
    }

    public ProguardKeepRuleModifiers build() {
      return new ProguardKeepRuleModifiers(
          allowsAccessModification,
          allowsAnnotationRemoval,
          allowsCodeReplacement,
          allowsFinalModification,
          allowsRepackaging,
          allowsShrinking,
          allowsOptimization,
          allowsObfuscation,
          includeDescriptorClasses,
          allowsPermittedSubclassesRemoval);
    }
  }

  public final OptionalBool allowsAccessModification;
  public final boolean allowsAnnotationRemoval;
  public final OptionalBool allowsCodeReplacement;
  public final OptionalBool allowsFinalModification;
  public final boolean allowsRepackaging;
  public final boolean allowsShrinking;
  public final boolean allowsOptimization;
  public final boolean allowsObfuscation;
  public final boolean includeDescriptorClasses;
  public final boolean allowsPermittedSubclassesRemoval;

  private ProguardKeepRuleModifiers(
      OptionalBool allowsAccessModification,
      boolean allowsAnnotationRemoval,
      OptionalBool allowsCodeReplacement,
      OptionalBool allowsFinalModification,
      boolean allowsRepackaging,
      boolean allowsShrinking,
      boolean allowsOptimization,
      boolean allowsObfuscation,
      boolean includeDescriptorClasses,
      boolean allowsPermittedSubclassesRemoval) {
    this.allowsAccessModification = allowsAccessModification;
    this.allowsAnnotationRemoval = allowsAnnotationRemoval;
    this.allowsCodeReplacement = allowsCodeReplacement;
    this.allowsFinalModification = allowsFinalModification;
    this.allowsRepackaging = allowsRepackaging;
    this.allowsShrinking = allowsShrinking;
    this.allowsOptimization = allowsOptimization;
    this.allowsObfuscation = allowsObfuscation;
    this.includeDescriptorClasses = includeDescriptorClasses;
    this.allowsPermittedSubclassesRemoval = allowsPermittedSubclassesRemoval;
  }

  /**
   * Create a new empty builder.
   */
  public static Builder builder() {
    return new Builder();
  }

  public boolean isBottom() {
    return allowsAccessModification.isTrue()
        && allowsAnnotationRemoval
        && allowsCodeReplacement.isFalse()
        && allowsFinalModification.isTrue()
        && allowsRepackaging
        && allowsObfuscation
        && allowsOptimization
        && allowsShrinking
        && !includeDescriptorClasses
        && allowsPermittedSubclassesRemoval;
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProguardKeepRuleModifiers)) {
      return false;
    }
    ProguardKeepRuleModifiers that = (ProguardKeepRuleModifiers) o;
    return allowsAccessModification.equals(that.allowsAccessModification)
        && allowsAnnotationRemoval == that.allowsAnnotationRemoval
        && allowsCodeReplacement.equals(that.allowsCodeReplacement)
        && allowsFinalModification.equals(that.allowsFinalModification)
        && allowsRepackaging == that.allowsRepackaging
        && allowsShrinking == that.allowsShrinking
        && allowsOptimization == that.allowsOptimization
        && allowsObfuscation == that.allowsObfuscation
        && includeDescriptorClasses == that.includeDescriptorClasses
        && allowsPermittedSubclassesRemoval == that.allowsPermittedSubclassesRemoval;
  }

  @Override
  public int hashCode() {
    return ObjectUtils.hashZZZZZZZIII(
        allowsAnnotationRemoval,
        allowsRepackaging,
        allowsShrinking,
        allowsOptimization,
        allowsObfuscation,
        includeDescriptorClasses,
        allowsPermittedSubclassesRemoval,
        allowsAccessModification.ordinal(),
        allowsCodeReplacement.ordinal(),
        allowsFinalModification.ordinal());
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    appendWithComma(builder, "accessmodification", allowsAccessModification);
    appendWithComma(builder, "allowannotationremoval", allowsAnnotationRemoval);
    appendWithComma(builder, "codereplacement", allowsCodeReplacement);
    appendWithComma(builder, "finalmodification", allowsFinalModification);
    appendWithComma(builder, "allowrepackaging", allowsRepackaging);
    appendWithComma(builder, "allowobfuscation", allowsObfuscation);
    appendWithComma(builder, "allowshrinking", allowsShrinking);
    appendWithComma(builder, "allowoptimization", allowsOptimization);
    appendWithComma(builder, "includedescriptorclasses", includeDescriptorClasses);
    appendWithComma(builder, "allowpermittedsubclassesremoval", allowsPermittedSubclassesRemoval);
    return builder.toString();
  }

  private void appendWithComma(StringBuilder builder, String text) {
    if (builder.length() != 0) {
      builder.append(',');
    }
    builder.append(text);
  }

  private void appendWithComma(StringBuilder builder, String text, boolean predicate) {
    if (predicate) {
      appendWithComma(builder, text);
    }
  }

  private void appendWithComma(StringBuilder builder, String text, OptionalBool predicate) {
    if (predicate.isTrueOrFalse()) {
      if (predicate.isTrue()) {
        appendWithComma(builder, "allow" + text);
      } else {
        appendWithComma(builder, "disallow" + text);
      }
    }
  }
}

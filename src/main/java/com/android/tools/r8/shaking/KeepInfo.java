// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.shaking.KeepInfo.Builder;

/** Keep information that can be associated with any item, i.e., class, method or field. */
public abstract class KeepInfo<B extends Builder<B, K>, K extends KeepInfo<B, K>> {

  private final boolean pinned;
  private final boolean allowAccessModification;
  private final boolean allowAnnotationRemoval;
  private final boolean allowMinification;
  private final boolean requireAccessModificationForRepackaging;

  private KeepInfo(
      boolean pinned,
      boolean allowAccessModification,
      boolean allowAnnotationRemoval,
      boolean allowMinification,
      boolean requireAccessModificationForRepackaging) {
    this.pinned = pinned;
    this.allowAccessModification = allowAccessModification;
    this.allowAnnotationRemoval = allowAnnotationRemoval;
    this.allowMinification = allowMinification;
    this.requireAccessModificationForRepackaging = requireAccessModificationForRepackaging;
  }

  KeepInfo(B builder) {
    this(
        builder.isPinned(),
        builder.isAccessModificationAllowed(),
        builder.isAnnotationRemovalAllowed(),
        builder.isMinificationAllowed(),
        builder.isAccessModificationRequiredForRepackaging());
  }

  abstract B builder();

  /**
   * True if an item may have all of its annotations removed.
   *
   * <p>If this returns false, some annotations may still be removed if the configuration does not
   * keep all annotation attributes.
   */
  public boolean isAnnotationRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isAnnotationRemovalEnabled() && internalIsAnnotationRemovalAllowed();
  }

  boolean internalIsAnnotationRemovalAllowed() {
    return allowAnnotationRemoval;
  }

  /**
   * True if an item must be present in the output.
   *
   * @deprecated Prefer task dependent predicates.
   */
  @Deprecated
  public boolean isPinned() {
    return pinned;
  }

  /**
   * True if an item may have its name minified/changed.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public boolean isMinificationAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isMinificationEnabled() && internalIsMinificationAllowed();
  }

  boolean internalIsMinificationAllowed() {
    return allowMinification;
  }

  /**
   * True if an item may be repackaged.
   *
   * <p>This method requires knowledge of the global configuration as that can override the concrete
   * value on a given item.
   */
  public abstract boolean isRepackagingAllowed(GlobalKeepInfoConfiguration configuration);

  boolean internalIsAccessModificationRequiredForRepackaging() {
    return requireAccessModificationForRepackaging;
  }

  /**
   * True if an item may have its access flags modified.
   *
   * <p>This method requires knowledge of the global access modification as that will override the
   * concrete value on a given item.
   *
   * @param configuration Global configuration object to determine access modification.
   */
  public boolean isAccessModificationAllowed(GlobalKeepInfoConfiguration configuration) {
    return configuration.isAccessModificationEnabled() && internalIsAccessModificationAllowed();
  }

  // Internal accessor for the items access-modification bit.
  boolean internalIsAccessModificationAllowed() {
    return allowAccessModification;
  }

  public boolean isSignatureAttributeRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    if (!configuration.isKeepAttributesSignatureEnabled()) {
      return true;
    }
    return !(configuration.isForceProguardCompatibilityEnabled() || isPinned());
  }

  public boolean isEnclosingMethodAttributeRemovalAllowed(
      GlobalKeepInfoConfiguration configuration,
      EnclosingMethodAttribute enclosingMethodAttribute,
      AppView<AppInfoWithLiveness> appView) {
    if (!configuration.isKeepEnclosingMethodAttributeEnabled()) {
      return true;
    }
    if (configuration.isForceProguardCompatibilityEnabled()) {
      return false;
    }
    return !isPinned() || !enclosingMethodAttribute.isEnclosingPinned(appView);
  }

  public boolean isInnerClassesAttributeRemovalAllowed(GlobalKeepInfoConfiguration configuration) {
    if (!configuration.isKeepInnerClassesAttributeEnabled()) {
      return true;
    }
    return !(configuration.isForceProguardCompatibilityEnabled() || isPinned());
  }

  public boolean isInnerClassesAttributeRemovalAllowed(
      GlobalKeepInfoConfiguration configuration,
      EnclosingMethodAttribute enclosingMethodAttribute) {
    if (!configuration.isKeepInnerClassesAttributeEnabled()) {
      return true;
    }
    if (configuration.isForceProguardCompatibilityEnabled()) {
      return false;
    }
    // The inner class is dependent on the enclosingMethodAttribute and since it has been pruned
    // we can also remove this inner class relationship.
    return enclosingMethodAttribute == null || !isPinned();
  }

  public abstract boolean isTop();

  public abstract boolean isBottom();

  public boolean isLessThanOrEquals(K other) {
    // An item is less, aka, lower in the lattice, if each of its attributes is at least as
    // permissive of that on other.
    return (!pinned || other.isPinned())
        && (allowAccessModification || !other.internalIsAccessModificationAllowed());
  }

  /** Builder to construct an arbitrary keep info object. */
  public abstract static class Builder<B extends Builder<B, K>, K extends KeepInfo<B, K>> {

    abstract B self();

    abstract K doBuild();

    abstract K getTopInfo();

    abstract K getBottomInfo();

    abstract boolean isEqualTo(K other);

    private K original;
    private boolean pinned;
    private boolean allowAccessModification;
    private boolean allowAnnotationRemoval;
    private boolean allowMinification;
    private boolean requireAccessModificationForRepackaging;

    Builder() {
      // Default initialized. Use should be followed by makeTop/makeBottom.
    }

    Builder(K original) {
      this.original = original;
      pinned = original.isPinned();
      allowAccessModification = original.internalIsAccessModificationAllowed();
      allowAnnotationRemoval = original.internalIsAnnotationRemovalAllowed();
      allowMinification = original.internalIsMinificationAllowed();
      requireAccessModificationForRepackaging =
          original.internalIsAccessModificationRequiredForRepackaging();
    }

    B makeTop() {
      pin();
      disallowAccessModification();
      disallowAnnotationRemoval();
      disallowMinification();
      requireAccessModificationForRepackaging();
      return self();
    }

    B makeBottom() {
      unpin();
      allowAccessModification();
      allowAnnotationRemoval();
      allowMinification();
      unsetRequireAccessModificationForRepackaging();
      return self();
    }

    public K build() {
      if (original != null) {
        if (internalIsEqualTo(original)) {
          return original;
        }
        if (internalIsEqualTo(getTopInfo())) {
          return getTopInfo();
        }
        if (internalIsEqualTo(getBottomInfo())) {
          return getBottomInfo();
        }
      }
      return doBuild();
    }

    boolean internalIsEqualTo(K other) {
      return isPinned() == other.isPinned()
          && isAccessModificationAllowed() == other.internalIsAccessModificationAllowed()
          && isAnnotationRemovalAllowed() == other.internalIsAnnotationRemovalAllowed()
          && isMinificationAllowed() == other.internalIsMinificationAllowed()
          && isAccessModificationRequiredForRepackaging()
              == other.internalIsAccessModificationRequiredForRepackaging();
    }

    public boolean isPinned() {
      return pinned;
    }

    public boolean isAccessModificationRequiredForRepackaging() {
      return requireAccessModificationForRepackaging;
    }

    public boolean isAccessModificationAllowed() {
      return allowAccessModification;
    }

    public boolean isAnnotationRemovalAllowed() {
      return allowAnnotationRemoval;
    }

    public boolean isMinificationAllowed() {
      return allowMinification;
    }

    public B setPinned(boolean pinned) {
      this.pinned = pinned;
      return self();
    }

    public B pin() {
      return setPinned(true);
    }

    public B unpin() {
      return setPinned(false);
    }

    public B setAllowMinification(boolean allowMinification) {
      this.allowMinification = allowMinification;
      return self();
    }

    public B allowMinification() {
      return setAllowMinification(true);
    }

    public B disallowMinification() {
      return setAllowMinification(false);
    }

    public B setRequireAccessModificationForRepackaging(
        boolean requireAccessModificationForRepackaging) {
      this.requireAccessModificationForRepackaging = requireAccessModificationForRepackaging;
      return self();
    }

    public B requireAccessModificationForRepackaging() {
      return setRequireAccessModificationForRepackaging(true);
    }

    public B unsetRequireAccessModificationForRepackaging() {
      return setRequireAccessModificationForRepackaging(false);
    }

    public B setAllowAccessModification(boolean allowAccessModification) {
      this.allowAccessModification = allowAccessModification;
      return self();
    }

    public B allowAccessModification() {
      return setAllowAccessModification(true);
    }

    public B disallowAccessModification() {
      return setAllowAccessModification(false);
    }

    public B setAllowAnnotationRemoval(boolean allowAnnotationRemoval) {
      this.allowAnnotationRemoval = allowAnnotationRemoval;
      return self();
    }

    public B allowAnnotationRemoval() {
      return setAllowAnnotationRemoval(true);
    }

    public B disallowAnnotationRemoval() {
      return setAllowAnnotationRemoval(false);
    }
  }

  /** Joiner to construct monotonically increasing keep info object. */
  public abstract static class Joiner<
      J extends Joiner<J, B, K>, B extends Builder<B, K>, K extends KeepInfo<B, K>> {

    abstract J self();

    private final Builder<B, K> builder;

    Joiner(Builder<B, K> builder) {
      this.builder = builder;
    }

    public boolean isTop() {
      return builder.isEqualTo(builder.getTopInfo());
    }

    public J top() {
      builder.makeTop();
      return self();
    }

    public J pin() {
      builder.pin();
      return self();
    }

    public J disallowAccessModification() {
      builder.disallowAccessModification();
      return self();
    }

    public J disallowAnnotationRemoval() {
      builder.disallowAnnotationRemoval();
      return self();
    }

    public J disallowMinification() {
      builder.disallowMinification();
      return self();
    }

    public J requireAccessModificationForRepackaging() {
      builder.requireAccessModificationForRepackaging();
      return self();
    }

    public K join() {
      K joined = builder.build();
      K original = builder.original;
      assert original.isLessThanOrEquals(joined);
      return joined;
    }
  }
}

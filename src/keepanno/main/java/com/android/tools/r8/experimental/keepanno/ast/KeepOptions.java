// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public final class KeepOptions {

  public boolean isKeepAll() {
    return allowIfSet ? options.isEmpty() : options.size() == KeepOption.values().length;
  }

  public enum KeepOption {
    SHRINKING,
    OPTIMIZING,
    OBFUSCATING,
    ACCESS_MODIFING,
  }

  public static KeepOptions keepAll() {
    if (ALLOW_NONE_INSTANCE == null) {
      ALLOW_NONE_INSTANCE = new KeepOptions(true, Collections.emptySet());
    }
    return ALLOW_NONE_INSTANCE;
  }

  public static Builder allowBuilder() {
    return new Builder(true);
  }

  public static Builder disallowBuilder() {
    return new Builder(false);
  }

  public static KeepOptions allow(KeepOption... options) {
    return allowBuilder().addAll(options).build();
  }

  public static KeepOptions disallow(KeepOption... options) {
    return disallowBuilder().addAll(options).build();
  }

  public static class Builder {
    public final boolean allowIfSet;
    public Set<KeepOption> options = new HashSet<>();

    private Builder(boolean allowIfSet) {
      this.allowIfSet = allowIfSet;
    }

    public Builder add(KeepOption option) {
      options.add(option);
      return this;
    }

    public Builder addAll(KeepOption... options) {
      return addAll(Arrays.asList(options));
    }

    public Builder addAll(Collection<KeepOption> options) {
      this.options.addAll(options);
      return this;
    }

    public KeepOptions build() {
      if (options.isEmpty()) {
        if (allowIfSet) {
          return keepAll();
        }
        throw new KeepEdgeException("Invalid keep options that disallow nothing.");
      }
      if (options.size() == KeepOption.values().length) {
        if (!allowIfSet) {
          return keepAll();
        }
        throw new KeepEdgeException("Invalid keep options that allow everything.");
      }
      return new KeepOptions(allowIfSet, Collections.unmodifiableSet(options));
    }
  }

  private static KeepOptions ALLOW_NONE_INSTANCE = null;

  private final boolean allowIfSet;
  private final Set<KeepOption> options;

  private KeepOptions(boolean allowIfSet, Set<KeepOption> options) {
    this.allowIfSet = allowIfSet;
    this.options = options;
  }

  public boolean isAllowed(KeepOption option) {
    return options.contains(option) == allowIfSet;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    // This does not actually capture equivalence. We should normalize the builder the 'allow'
    // variant always.
    KeepOptions that = (KeepOptions) o;
    return allowIfSet == that.allowIfSet && options.equals(that.options);
  }

  @Override
  public int hashCode() {
    return Objects.hash(allowIfSet, options);
  }
}

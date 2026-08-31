// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.numberunboxer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.lens.GraphLens;
import com.google.common.collect.ImmutableMultiset;
import java.util.Arrays;

/**
 * The value boxing status represents the result of the number unboxer analysis on a given value,
 * such as a method argument, return value or a field. It contains a boxingDelta which encodes the
 * number of boxing operation that would be introduced or removed if the value is unboxed and
 * optionally a list of transitive dependency. To use the value boxing status, the number unboxer
 * needs to decide for each of the transitive dependency if they're going to be unboxed or not,
 * compute the concrete boxing delta and unbox if relevant.
 */
public class ValueBoxingStatus {

  public static final ValueBoxingStatus NOT_UNBOXABLE =
      new ValueBoxingStatus(0, ImmutableMultiset.of());
  private final int boxingDelta;
  private final ImmutableMultiset<TransitiveDependency> transitiveDependencies;

  public static ValueBoxingStatus[] notUnboxableArray(int size) {
    ValueBoxingStatus[] valueBoxingStatuses = new ValueBoxingStatus[size];
    Arrays.fill(valueBoxingStatuses, ValueBoxingStatus.NOT_UNBOXABLE);
    return valueBoxingStatuses;
  }

  public static ValueBoxingStatus with(int boxingDelta, NumberUnboxerOptions numberUnboxerOptions) {
    return checkedWith(boxingDelta, ImmutableMultiset.of(), numberUnboxerOptions);
  }

  public static ValueBoxingStatus with(
      TransitiveDependency transitiveDependency, NumberUnboxerOptions numberUnboxerOptions) {
    return checkedWith(0, ImmutableMultiset.of(transitiveDependency), numberUnboxerOptions);
  }

  private static ValueBoxingStatus checkedWith(
      int boxingDelta,
      ImmutableMultiset<TransitiveDependency> transitiveDependencies,
      NumberUnboxerOptions numberUnboxerOptions) {
    if (transitiveDependencies.size() > numberUnboxerOptions.getMaxTransitiveDependencies()) {
      return NOT_UNBOXABLE;
    }
    return uncheckedWith(boxingDelta, transitiveDependencies);
  }

  private static ValueBoxingStatus uncheckedWith(
      int boxingDelta, ImmutableMultiset<TransitiveDependency> transitiveDependencies) {
    return new ValueBoxingStatus(boxingDelta, transitiveDependencies);
  }

  private ValueBoxingStatus(
      int boxingDelta, ImmutableMultiset<TransitiveDependency> transitiveDependencies) {
    this.boxingDelta = boxingDelta;
    this.transitiveDependencies = transitiveDependencies;
  }

  public boolean mayBeUnboxable() {
    return !isNotUnboxable();
  }

  public boolean isNotUnboxable() {
    return this == NOT_UNBOXABLE;
  }

  public int getBoxingDelta() {
    assert mayBeUnboxable();
    return boxingDelta;
  }

  public ImmutableMultiset<TransitiveDependency> getTransitiveDependencies() {
    return transitiveDependencies;
  }

  public ValueBoxingStatus merge(
      ValueBoxingStatus unboxingStatus, NumberUnboxerOptions numberUnboxerOptions) {
    if (isNotUnboxable() || unboxingStatus.isNotUnboxable()) {
      return NOT_UNBOXABLE;
    }
    int newDelta = boxingDelta + unboxingStatus.getBoxingDelta();
    if (unboxingStatus.getTransitiveDependencies().isEmpty()) {
      if (newDelta == boxingDelta) {
        return this;
      }
      return checkedWith(newDelta, transitiveDependencies, numberUnboxerOptions);
    }
    if (transitiveDependencies.isEmpty()) {
      if (newDelta == unboxingStatus.getBoxingDelta()) {
        return unboxingStatus;
      }
      return checkedWith(
          newDelta, unboxingStatus.getTransitiveDependencies(), numberUnboxerOptions);
    }
    ImmutableMultiset<TransitiveDependency> newDeps =
        ImmutableMultiset.<TransitiveDependency>builder()
            .addAll(transitiveDependencies)
            .addAll(unboxingStatus.getTransitiveDependencies())
            .build();
    return checkedWith(newDelta, newDeps, numberUnboxerOptions);
  }

  public ValueBoxingStatus rewrittenWithLens(
      AppView<?> appView, GraphLens graphLens, GraphLens codeLens) {
    if (transitiveDependencies.isEmpty()) {
      return this;
    }
    ImmutableMultiset.Builder<TransitiveDependency> newTransitiveDependencies =
        ImmutableMultiset.builder();
    boolean diff = false;
    for (TransitiveDependency transitiveDependency : transitiveDependencies) {
      TransitiveDependency newTransitiveDependency =
          transitiveDependency.rewrittenWithLens(appView, graphLens, codeLens);
      diff |= newTransitiveDependency != transitiveDependency;
      newTransitiveDependencies.add(newTransitiveDependency);
    }
    return diff
        ? ValueBoxingStatus.uncheckedWith(boxingDelta, newTransitiveDependencies.build())
        : this;
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("ValueBoxingStatus[");
    if (isNotUnboxable()) {
      sb.append("NOT_UNBOXABLE");
    } else {
      sb.append(boxingDelta);
      for (TransitiveDependency transitiveDependency : transitiveDependencies) {
        sb.append(";");
        sb.append(transitiveDependency.toString());
      }
    }
    sb.append("]");
    return sb.toString();
  }
}

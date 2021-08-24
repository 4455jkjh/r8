// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Timing;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

abstract class MethodStateCollection<K> {

  private final Map<K, MethodState> methodStates;

  MethodStateCollection(Map<K, MethodState> methodStates) {
    assert methodStates.values().stream().noneMatch(MethodState::isBottom);
    this.methodStates = methodStates;
  }

  abstract K getKey(ProgramMethod method);

  public void addMethodState(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method, MethodState methodState) {
    addMethodState(appView, getKey(method), methodState);
  }

  private void addMethodState(
      AppView<AppInfoWithLiveness> appView, K method, MethodState methodState) {
    if (methodState.isUnknown()) {
      methodStates.put(method, methodState);
    } else {
      methodStates.compute(
          method,
          (ignore, existingMethodState) -> {
            MethodState newMethodState;
            if (existingMethodState == null) {
              newMethodState = methodState.mutableCopy();
            } else {
              newMethodState = existingMethodState.mutableJoin(appView, methodState);
            }
            assert !newMethodState.isBottom();
            return newMethodState;
          });
    }
  }

  /**
   * This intentionally takes a {@link Supplier<MethodState>} to avoid computing the method state
   * for a given call site when nothing is known about the arguments of the method.
   */
  public void addTemporaryMethodState(
      AppView<AppInfoWithLiveness> appView,
      K method,
      Supplier<MethodState> methodStateSupplier,
      Timing timing) {
    methodStates.compute(
        method,
        (ignore, existingMethodState) -> {
          if (existingMethodState == null) {
            MethodState newMethodState = methodStateSupplier.get();
            assert !newMethodState.isBottom();
            return newMethodState;
          }
          assert !existingMethodState.isBottom();
          timing.begin("Join temporary method state");
          MethodState joinResult = existingMethodState.mutableJoin(appView, methodStateSupplier);
          assert !joinResult.isBottom();
          timing.end();
          return joinResult;
        });
  }

  public void addMethodStates(
      AppView<AppInfoWithLiveness> appView, MethodStateCollection<K> other) {
    other.methodStates.forEach(
        (method, methodState) -> addMethodState(appView, method, methodState));
  }

  public void forEach(BiConsumer<K, MethodState> consumer) {
    methodStates.forEach(consumer);
  }

  public MethodState get(ProgramMethod method) {
    return methodStates.getOrDefault(getKey(method), MethodState.bottom());
  }

  public boolean isEmpty() {
    return methodStates.isEmpty();
  }

  public MethodState remove(ProgramMethod method) {
    return removeOrElse(method, MethodState.bottom());
  }

  public MethodState removeOrElse(ProgramMethod method, MethodState defaultValue) {
    MethodState removed = methodStates.remove(getKey(method));
    return removed != null ? removed : defaultValue;
  }

  public void set(ProgramMethod method, MethodState methodState) {
    set(getKey(method), methodState);
  }

  private void set(K method, MethodState methodState) {
    if (methodState.isBottom()) {
      methodStates.remove(method);
    } else {
      methodStates.put(method, methodState);
    }
  }
}

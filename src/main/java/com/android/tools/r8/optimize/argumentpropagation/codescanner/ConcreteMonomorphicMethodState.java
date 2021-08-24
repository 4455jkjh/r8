// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.List;

public class ConcreteMonomorphicMethodState extends ConcreteMethodState
    implements ConcreteMonomorphicMethodStateOrUnknown {

  List<ParameterState> parameterStates;

  public ConcreteMonomorphicMethodState(List<ParameterState> parameterStates) {
    assert Streams.stream(Iterables.skip(parameterStates, 1))
        .noneMatch(x -> x.isConcrete() && x.asConcrete().isReceiverParameter());
    assert Iterables.any(parameterStates, parameterState -> !parameterState.isUnknown())
        : "Must use UnknownMethodState instead";
    this.parameterStates = parameterStates;
  }

  public ParameterState getParameterState(int index) {
    return parameterStates.get(index);
  }

  public List<ParameterState> getParameterStates() {
    return parameterStates;
  }

  @Override
  public ConcreteMonomorphicMethodState mutableCopy() {
    List<ParameterState> copiedParametersStates = new ArrayList<>(size());
    for (ParameterState parameterState : getParameterStates()) {
      copiedParametersStates.add(parameterState.mutableCopy());
    }
    return new ConcreteMonomorphicMethodState(copiedParametersStates);
  }

  public ConcreteMonomorphicMethodStateOrUnknown mutableJoin(
      AppView<AppInfoWithLiveness> appView, ConcreteMonomorphicMethodState methodState) {
    if (size() != methodState.size()) {
      assert false;
      return unknown();
    }

    for (int i = 0; i < size(); i++) {
      ParameterState parameterState = parameterStates.get(i);
      ParameterState otherParameterState = methodState.parameterStates.get(i);
      parameterStates.set(i, parameterState.mutableJoin(appView, otherParameterState));
      assert i == 0
          || !parameterStates.get(i).isConcrete()
          || !parameterStates.get(i).asConcrete().isReceiverParameter();
    }

    if (Iterables.all(parameterStates, ParameterState::isUnknown)) {
      return unknown();
    }
    return this;
  }

  @Override
  public boolean isMonomorphic() {
    return true;
  }

  @Override
  public ConcreteMonomorphicMethodState asMonomorphic() {
    return this;
  }

  public void setParameterState(int index, ParameterState parameterState) {
    assert index == 0
        || !parameterState.isConcrete()
        || !parameterState.asConcrete().isReceiverParameter();
    parameterStates.set(index, parameterState);
  }

  public int size() {
    return parameterStates.size();
  }
}

// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.ir.analysis.value.AbstractValueJoiner;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.internal.IntObjToObjFunction;
import java.util.function.Function;

public abstract class ConcreteMethodState extends MethodStateBase {

  @Override
  public boolean isConcrete() {
    return true;
  }

  @Override
  public ConcreteMethodState asConcrete() {
    return this;
  }

  @Override
  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      IntObjToObjFunction<AppView<AppInfoWithLiveness>, AbstractValueJoiner> abstractValueJoiner,
      DexMethodSignature methodSignature,
      MethodState methodState,
      StateCloner cloner) {
    if (methodState.isBottom()) {
      return this;
    }
    if (methodState.isUnknown()) {
      return methodState;
    }
    return mutableJoin(
        appView, abstractValueJoiner, methodSignature, methodState.asConcrete(), cloner);
  }

  @Override
  public MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      IntObjToObjFunction<AppView<AppInfoWithLiveness>, AbstractValueJoiner> abstractValueJoiner,
      DexMethodSignature methodSignature,
      Function<MethodState, MethodState> methodStateSupplier,
      StateCloner cloner) {
    return mutableJoin(
        appView, abstractValueJoiner, methodSignature, methodStateSupplier.apply(this), cloner);
  }

  private MethodState mutableJoin(
      AppView<AppInfoWithLiveness> appView,
      IntObjToObjFunction<AppView<AppInfoWithLiveness>, AbstractValueJoiner> abstractValueJoiner,
      DexMethodSignature methodSignature,
      ConcreteMethodState methodState,
      StateCloner cloner) {
    if (isMonomorphic() && methodState.isMonomorphic()) {
      ConcreteMonomorphicMethodState self = asMonomorphic();
      return self.mutableJoin(
          appView, abstractValueJoiner, methodSignature, methodState.asMonomorphic(), cloner);
    }
    if (isPolymorphic() && methodState.isPolymorphic()) {
      return asPolymorphic()
          .mutableJoin(appView, methodSignature, methodState.asPolymorphic(), cloner);
    }
    assert false;
    return unknown();
  }
}

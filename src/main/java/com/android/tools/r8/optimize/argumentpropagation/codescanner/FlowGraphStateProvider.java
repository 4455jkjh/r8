// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.optimize.argumentpropagation.propagation.FlowGraph;
import com.android.tools.r8.utils.InternalOptions;
import java.util.function.Supplier;

public interface FlowGraphStateProvider {

  static FlowGraphStateProvider create(FlowGraph flowGraph, AbstractFunction abstractFunction) {
    if (!InternalOptions.assertionsEnabled()) {
      return flowGraph;
    }
    // If the abstract function needs to perform state lookups, we restrict state lookups to the
    // declared base in flow. This is important for arriving at the correct fix point.
    if (abstractFunction.usesFlowGraphStateProvider()) {
      assert abstractFunction.isIfThenElseAbstractFunction()
          || abstractFunction.isInstanceFieldReadAbstractFunction();
      return new FlowGraphStateProvider() {

        @Override
        public ValueState getState(DexField field) {
          assert abstractFunction.verifyContainsBaseInFlow(new FieldValue(field));
          return flowGraph.getState(field);
        }

        @Override
        public ValueState getState(
            MethodParameter methodParameter, Supplier<ValueState> defaultStateProvider) {
          assert abstractFunction.verifyContainsBaseInFlow(methodParameter);
          return flowGraph.getState(methodParameter, defaultStateProvider);
        }
      };
    }
    // Otherwise, the abstract function is a canonical function, or the abstract function has a
    // single declared input, meaning we should never perform any state lookups.
    assert abstractFunction.isIdentity()
        || abstractFunction.isCastAbstractFunction()
        || abstractFunction.isUpdateChangedFlagsAbstractFunction();
    return new FlowGraphStateProvider() {

      @Override
      public ValueState getState(DexField field) {
        throw new Unreachable();
      }

      @Override
      public ValueState getState(
          MethodParameter methodParameter, Supplier<ValueState> defaultStateProvider) {
        throw new Unreachable();
      }
    };
  }

  ValueState getState(DexField field);

  ValueState getState(MethodParameter methodParameter, Supplier<ValueState> defaultStateProvider);

  default ValueState getState(BaseInFlow inFlow, Supplier<ValueState> defaultStateProvider) {
    if (inFlow.isFieldValue()) {
      return getState(inFlow.asFieldValue().getField());
    } else {
      assert inFlow.isMethodParameter();
      return getState(inFlow.asMethodParameter(), defaultStateProvider);
    }
  }
}

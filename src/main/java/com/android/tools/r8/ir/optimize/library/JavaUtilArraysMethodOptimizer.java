// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.Set;

public class JavaUtilArraysMethodOptimizer extends StatelessLibraryMethodModelCollection
    implements MethodOptimizerCapabilities {

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public JavaUtilArraysMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  @Override
  public AppView<?> getAppView() {
    return appView;
  }

  @Override
  public DexType getType() {
    return factory.arraysType;
  }

  @Override
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    DexMethod singleTargetReference = singleTarget.getReference();
    DexItemFactory.JavaUtilArraysMethods methods = factory.javaUtilArraysMethods;
    if (singleTargetReference.isIdenticalTo(methods.hashCodeIntArray)) {
      Value arg = invoke.getFirstArgument().getAliasedValue();
      AbstractValue abstractValue = arg.getAbstractValue(appView, code.context());
      if (abstractValue.isNull() || arg.isAlwaysNull(appView)) {
        instructionIterator.replaceCurrentInstructionWithConstInt(code, 0);
      }
    } else if (singleTargetReference.isIdenticalTo(methods.equalsByteArray)) {
      Value first = invoke.getFirstArgument().getAliasedValue();
      Value second = invoke.getSecondArgument().getAliasedValue();
      boolean firstIsNull =
          first.getAbstractValue(appView, code.context()).isNull() || first.isAlwaysNull(appView);
      boolean secondIsNull =
          second.getAbstractValue(appView, code.context()).isNull() || second.isAlwaysNull(appView);
      boolean firstIsDefinitelyNonNull = first.isNeverNull();
      boolean secondIsDefinitelyNonNull = second.isNeverNull();

      if (firstIsNull && secondIsNull) {
        instructionIterator.replaceCurrentInstructionWithConstBoolean(code, true);
      } else if ((firstIsNull && secondIsDefinitelyNonNull)
          || (secondIsNull && firstIsDefinitelyNonNull)) {
        instructionIterator.replaceCurrentInstructionWithConstBoolean(code, false);
      }
    }
    return instructionIterator;
  }
}

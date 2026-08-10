// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
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

public class AndroidTextTextUtilsMethodOptimizer extends StatelessLibraryMethodModelCollection
    implements MethodOptimizerCapabilities {

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public AndroidTextTextUtilsMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  @Override
  public AppView<?> getAppView() {
    return appView;
  }

  @Override
  public DexType getType() {
    return factory.androidTextTextUtilsType;
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
    DexItemFactory.AndroidTextTextUtilsMembers members = factory.androidTextTextUtilsMembers;
    if (singleTargetReference.isIdenticalTo(members.equals)) {
      optimizeEquals(code, instructionIterator, invoke);
    } else if (singleTargetReference.isIdenticalTo(members.isEmpty)) {
      optimizeIsEmpty(code, instructionIterator, invoke);
    }
    return instructionIterator;
  }

  private void optimizeEquals(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Value first = invoke.getFirstArgument().getAliasedValue();
    Value second = invoke.getSecondArgument().getAliasedValue();
    if (first == second) {
      instructionIterator.replaceCurrentInstructionWithConstBoolean(code, true);
      return;
    }
    boolean firstIsNull =
        first.getAbstractValue(appView, code.context()).isNull() || first.isAlwaysNull(appView);
    boolean secondIsNull =
        second.getAbstractValue(appView, code.context()).isNull() || second.isAlwaysNull(appView);
    if (firstIsNull && secondIsNull) {
      instructionIterator.replaceCurrentInstructionWithConstBoolean(code, true);
      return;
    }
    boolean firstIsDefinitelyNonNull = first.isNeverNull();
    boolean secondIsDefinitelyNonNull = second.isNeverNull();
    if ((firstIsNull && secondIsDefinitelyNonNull) || (secondIsNull && firstIsDefinitelyNonNull)) {
      instructionIterator.replaceCurrentInstructionWithConstBoolean(code, false);
      return;
    }
    DexString firstStr = first.getConstStringOrNull(appView, code);
    DexString secondStr = second.getConstStringOrNull(appView, code);
    if (firstStr != null && secondStr != null) {
      instructionIterator.replaceCurrentInstructionWithConstBoolean(
          code, firstStr.toString().equals(secondStr.toString()));
    }
  }

  private void optimizeIsEmpty(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke) {
    Value arg = invoke.getFirstArgument().getAliasedValue();
    AbstractValue abstractValue = arg.getAbstractValue(appView, code.context());
    if (abstractValue.isNull() || arg.isAlwaysNull(appView)) {
      instructionIterator.replaceCurrentInstructionWithConstBoolean(code, true);
      return;
    }
    DexString dexString = arg.getConstStringOrNull(appView, code);
    if (dexString != null) {
      instructionIterator.replaceCurrentInstructionWithConstBoolean(
          code, dexString.toString().isEmpty());
    }
  }
}

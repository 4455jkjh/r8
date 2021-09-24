// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.library.StatelessLibraryMethodModelCollection.State;
import java.util.Set;

public abstract class StatelessLibraryMethodModelCollection
    implements LibraryMethodModelCollection<State> {

  @Override
  public final State createInitialState(MethodProcessor methodProcessor) {
    return null;
  }

  public abstract void optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove);

  @Override
  public final void optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues,
      Set<BasicBlock> blocksToRemove,
      State state,
      MethodProcessingContext methodProcessingContext) {
    assert state == null;
    optimize(
        code,
        blockIterator,
        instructionIterator,
        invoke,
        singleTarget,
        affectedValues,
        blocksToRemove);
  }

  static class State implements LibraryMethodModelCollection.State {}
}

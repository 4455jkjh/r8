// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info.initializer.readbeforewrite;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.FailedTransferFunctionResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Value;
import java.util.Set;

public class ReadBeforeWriteAnalysisStateFactory {

  private final int maxSetSize;

  public ReadBeforeWriteAnalysisStateFactory(IRCode code) {
    this.maxSetSize = getMaxStateSize(code);
  }

  private static int getMaxStateSize(IRCode code) {
    // The dataflow analysis stores the exit state of each block. This limits the total state size
    // to 1000 (per set). This should roughly handle code with 40 blocks that assign 25 instance
    // fields, which is quite generous.
    return Math.max(1, 1000 / code.getBlocks().size());
  }

  /** Creates a ReadBeforeWriteAnalysisState if the state is not too big. */
  public TransferFunctionResult<ReadBeforeWriteAnalysisState> create(
      boolean isThisEscaped,
      boolean isThisInitialized,
      Set<DexEncodedField> readBeforeWriteSet,
      Set<DexEncodedField> writtenBeforeReadSet,
      Set<Value> thisAliases) {
    if (readBeforeWriteSet != null && readBeforeWriteSet.size() > maxSetSize) {
      return new FailedTransferFunctionResult<>();
    }
    if (writtenBeforeReadSet.size() > maxSetSize) {
      return new FailedTransferFunctionResult<>();
    }
    if (thisAliases.size() > maxSetSize) {
      return new FailedTransferFunctionResult<>();
    }
    return new ReadBeforeWriteAnalysisState(
        isThisEscaped, isThisInitialized, readBeforeWriteSet, writtenBeforeReadSet, thisAliases);
  }
}

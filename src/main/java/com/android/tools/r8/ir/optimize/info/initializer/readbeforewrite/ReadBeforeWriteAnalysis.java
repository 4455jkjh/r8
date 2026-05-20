// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info.initializer.readbeforewrite;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.IntraproceduralDataflowAnalysis;
import com.android.tools.r8.ir.code.IRCode;

public class ReadBeforeWriteAnalysis
    extends IntraproceduralDataflowAnalysis<ReadBeforeWriteAnalysisState> {

  public ReadBeforeWriteAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code) {
    super(
        appView,
        ReadBeforeWriteAnalysisState.bottom(),
        code,
        new ReadBeforeWriteAnalysisTransferFunction(appView, code));
  }
}

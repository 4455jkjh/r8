// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.utils.timing.Timing;

public class IRToCfFinalizer extends IRFinalizer<CfCode> {

  private final DeadCodeRemover deadCodeRemover;

  public IRToCfFinalizer(AppView<?> appView, DeadCodeRemover deadCodeRemover) {
    super(appView);
    this.deadCodeRemover = deadCodeRemover;
  }

  @Override
  public CfCode finalizeCode(
      IRCode code,
      BytecodeMetadataProvider bytecodeMetadataProvider,
      Timing timing,
      String previousPrintString) {
    ProgramMethod method = code.context();
    timing.begin("Finalize CF code");
    CfCode build =
        new CfBuilder(appView, method, code, bytecodeMetadataProvider)
            .build(deadCodeRemover, timing, previousPrintString);
    timing.end();
    return build;
  }
}

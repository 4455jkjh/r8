// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.finalizer.passes;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.utils.timing.Timing;

public abstract class FinalizerRewriterPass<T extends AppInfo> {

  protected final AppView<?> appView;

  protected FinalizerRewriterPass(AppView<?> appView) {
    this.appView = appView;
  }

  public final CodeRewriterResult run(IRCode code, RegisterAllocator allocator, Timing timing) {
    return timing.time(
        getRewriterId(),
        () -> {
          if (shouldRewriteCode(code)) {
            assert verifyConsistentCode(code, "before");
            CodeRewriterResult result = rewriteCode(code, allocator);
            assert result.hasChanged().isFalse() || verifyConsistentCode(code, "after");
            return result;
          }
          return CodeRewriterResult.NO_CHANGE;
        });
  }

  protected boolean verifyConsistentCode(IRCode code, String preposition) {
    boolean result;
    String message =
        "Invalid code " + preposition + " " + getRewriterId() + " in " + code.context();
    try {
      result = code.isConsistentGraph(appView, false);
    } catch (AssertionError ae) {
      throw new AssertionError(message, ae);
    }
    assert result : message;
    return true;
  }

  protected abstract String getRewriterId();

  protected abstract CodeRewriterResult rewriteCode(IRCode code, RegisterAllocator allocator);

  protected abstract boolean shouldRewriteCode(IRCode code);
}

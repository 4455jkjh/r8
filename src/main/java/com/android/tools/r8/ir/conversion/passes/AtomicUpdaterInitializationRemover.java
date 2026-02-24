// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility.Reporter.reportInfo;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRCodeUtils;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AtomicFieldUpdaterInstrumentor.AtomicFieldUpdaterInstrumentorInfo;
import com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility.Event;
import com.android.tools.r8.ir.optimize.info.atomicupdaters.eligibility.Reason;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class AtomicUpdaterInitializationRemover extends CodeRewriterPass<AppInfoWithLiveness> {

  public AtomicUpdaterInitializationRemover(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "AtomicUpdaterInitializationRemover";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return methodProcessor.isPostMethodProcessor()
        && code.context().getDefinition().isClassInitializer()
        && appView.getAtomicFieldUpdaterInstrumentorInfo() != null
        && appView
            .getAtomicFieldUpdaterInstrumentorInfo()
            .isInstrumented(code.context().getHolderType());
  }

  @Override
  protected CodeRewriterResult rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext) {
    // Assumes an earlier run of TrivialFieldAccessReprocessor.
    DexType holder = code.context().getHolderType();
    AtomicFieldUpdaterInstrumentorInfo info = appView.getAtomicFieldUpdaterInstrumentorInfo();
    var updaterFields = info.getInstrumentationsOrNull(holder);
    var offsetFields = info.getOffsetFieldsOrNull(holder);
    var it = code.instructionListIterator();
    var changed = false;
    while (it.hasNext()) {
      StaticPut staticPut = it.nextUntil(Instruction::isStaticPut);
      if (staticPut == null) {
        continue;
      }
      if (updaterFields.containsKey(staticPut.getField())) {
        if (!staticPut.canBeDeadCode(appView, code).isDeadIfOutValueIsDead()) {
          reportInfo(appView, new Event.CannotRemove(staticPut.getField()), Reason.NOT_UNUSED);
          continue;
        }
        reportInfo(appView, new Event.CanRemove(staticPut.getField()));
        changed = true;
        // This removes the effectful initialization of the field, which is okay based on previous
        // analysis.
        IRCodeUtils.removeInstructionAndTransitiveInputsIfNotUsed(staticPut);
      } else if (offsetFields.contains(staticPut.getField())) {
        if (!staticPut.canBeDeadCode(appView, code).isDeadIfOutValueIsDead()) {
          continue;
        }
        changed = true;
        // This removes the effectful initialization of the field, which is okay based on previous
        // analysis.
        IRCodeUtils.removeInstructionAndTransitiveInputsIfNotUsed(staticPut);
      }
    }
    return CodeRewriterResult.hasChanged(changed);
  }
}

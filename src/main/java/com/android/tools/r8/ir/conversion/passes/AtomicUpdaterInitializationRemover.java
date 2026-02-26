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
    // If the original updater field or the instrumented offset field is unused, the regular
    // compiler flow will remove them. This will however leave the initialization code behind
    // (e.g. AtomicReferenceFieldUpdater.newUpdater(..)).
    // This pass finds fields without reads and removes their initialization code.
    // The offset and updater field uses functions that are generally effectful but side-effect free
    // in this case based on previous analysis.

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
        if (isFieldRead(code, staticPut)) {
          reportInfo(appView, new Event.CannotRemove(staticPut.getField()), Reason.NOT_UNUSED);
        } else {
          reportInfo(appView, new Event.CanRemove(staticPut.getField()));
          changed = true;
          IRCodeUtils.removeInstructionAndTransitiveInputsIfNotUsed(staticPut);
        }
      } else if (offsetFields.contains(staticPut.getField())) {
        if (!isFieldRead(code, staticPut)) {
          changed = true;
          IRCodeUtils.removeInstructionAndTransitiveInputsIfNotUsed(staticPut);
        }
      }
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  private boolean isFieldRead(IRCode code, StaticPut staticPut) {
    return staticPut.instructionMayHaveSideEffects(appView, code.context());
  }
}

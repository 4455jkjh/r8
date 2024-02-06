// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.naming.IdentifierMinifier;
import com.android.tools.r8.shaking.ProguardClassFilter;

public class AdaptClassStringsRewriter extends CodeRewriterPass<AppInfoWithClassHierarchy> {

  private final ProguardClassFilter adaptClassStrings;

  public AdaptClassStringsRewriter(AppView<? extends AppInfoWithClassHierarchy> appView) {
    super(appView);
    this.adaptClassStrings = appView.options().getProguardConfiguration().getAdaptClassStrings();
  }

  @Override
  protected String getRewriterId() {
    return "AdaptClassStringsRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return !adaptClassStrings.isEmpty()
        && adaptClassStrings.matches(code.context().getHolderType())
        && code.metadata().mayHaveConstString();
  }

  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean hasChanged = false;
    InstructionListIterator iterator = code.instructionListIterator();
    while (iterator.hasNext()) {
      ConstString instruction = iterator.next().asConstString();
      if (instruction != null) {
        DexString replacement =
            IdentifierMinifier.getRenamedStringLiteral(appView(), instruction.getValue());
        if (replacement.isNotIdenticalTo(instruction.getValue())) {
          iterator.replaceCurrentInstructionWithConstString(appView, code, replacement, null);
          hasChanged = true;
        }
      }
    }
    return CodeRewriterResult.hasChanged(hasChanged);
  }
}

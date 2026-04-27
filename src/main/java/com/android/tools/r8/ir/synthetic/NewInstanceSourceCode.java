// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirEncodingStrategy;
import com.android.tools.r8.lightir.LirStrategy;
import java.util.ArrayList;
import java.util.List;

// Source code representing a simple call to NewInstance.
public final class NewInstanceSourceCode extends SyntheticCfCodeProvider {

  private final DexType newInstanceType;

  private NewInstanceSourceCode(AppView<?> appView, DexType holder, DexType newInstanceType) {
    super(appView, holder);
    this.newInstanceType = newInstanceType;
  }

  public static NewInstanceSourceCode create(
      AppView<?> appView, DexType holder, DexType newInstanceType) {
    return new NewInstanceSourceCode(appView, holder, newInstanceType);
  }

  public Code generate(DexMethod method) {
    return appView.testing().isSupportedLirPhase() ? generateLirCode(method) : generateCfCode();
  }

  @Override
  public CfCode generateCfCode() {
    List<CfInstruction> instructions = new ArrayList<>();
    instructions.add(new CfNew(newInstanceType));
    instructions.add(new CfReturnVoid());
    return standardCfCodeFromInstructions(instructions);
  }

  public LirCode<?> generateLirCode(DexMethod method) {
    boolean isD8R8Synthesized = true;
    LirEncodingStrategy<Value, Integer> strategy =
        LirStrategy.getDefaultStrategy().getEncodingStrategy();
    LirBuilder<Value, Integer> lirBuilder =
        LirCode.builder(method, isD8R8Synthesized, strategy, appView.options());
    lirBuilder.addNewInstance(newInstanceType);
    lirBuilder.addReturnVoid();
    return lirBuilder.build();
  }
}

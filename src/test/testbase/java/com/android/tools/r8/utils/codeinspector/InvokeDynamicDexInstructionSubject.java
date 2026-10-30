// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.graph.DexCallSite;

public class InvokeDynamicDexInstructionSubject extends DexInstructionSubject
    implements InvokeDynamicInstructionSubject {

  public InvokeDynamicDexInstructionSubject(DexInstruction instruction, MethodSubject method) {
    super(instruction, method);
    assert isInvokeCustom();
  }

  @Override
  public DexCallSite getCallSite() {
    return instruction.getCallSite();
  }
  ;

  @Override
  public InvokeDynamicInstructionSubject asInvokeDynamic() {
    return this;
  }
}

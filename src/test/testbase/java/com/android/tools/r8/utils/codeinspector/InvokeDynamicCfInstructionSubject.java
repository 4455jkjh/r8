// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.graph.DexCallSite;

public class InvokeDynamicCfInstructionSubject extends CfInstructionSubject
    implements InvokeDynamicInstructionSubject {
  public InvokeDynamicCfInstructionSubject(CfInstruction instruction, MethodSubject method) {
    super(instruction, method);
    assert isInvokeDynamic();
  }

  @Override
  public DexCallSite getCallSite() {
    return ((CfInvokeDynamic) instruction).getCallSite();
  }
  ;

  @Override
  public InvokeDynamicInstructionSubject asInvokeDynamic() {
    return this;
  }
}

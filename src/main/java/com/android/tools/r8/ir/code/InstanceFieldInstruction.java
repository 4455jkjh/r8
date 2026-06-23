// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.FieldResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Instruction.SideEffectAssumption;

public interface InstanceFieldInstruction {

  DexField getField();

  boolean hasOutValue();

  Value outValue();

  Value object();

  boolean instructionInstanceCanThrow(
      AppView<?> appView, ProgramMethod context, SideEffectAssumption assumption);

  boolean instructionMayHaveSideEffects(
      AppView<?> appView, ProgramMethod context, SideEffectAssumption assumption);

  FieldInstruction asFieldInstruction();

  boolean isInstanceFieldInstruction();

  boolean isInstanceGet();

  InstanceGet asInstanceGet();

  boolean isInstancePut();

  InstancePut asInstancePut();

  FieldResolutionResult resolveField(
      AppView<? extends AppInfoWithClassHierarchy> appView, ProgramMethod context);
}

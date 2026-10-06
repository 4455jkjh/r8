// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.typeswitch;

import com.android.tools.r8.cf.code.CfIinc;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.internal.ObjectUtils;
import java.util.List;

/**
 * Analyzes the {@code restart} argument of a {@code typeSwitch} or {@code enumSwitch} {@link
 * CfInvokeDynamic} to determine whether the switch is restartable, i.e., whether the restart index
 * may be different from {@code 0}.
 */
public class CfRestartIndexAnalysis {

  /**
   * Returns false if the restart index is guaranteed to be {@code 0}, and true otherwise
   * (conservatively).
   */
  public static boolean isRestartable(CfInvokeDynamic invokeDynamic, ProgramMethod context) {
    List<CfInstruction> instructions =
        context.getDefinition().getCode().asCfCode().getInstructions();
    int index = indexOf(instructions, invokeDynamic);
    if (index < 1) {
      return true;
    }
    CfInstruction previous = instructions.get(index - 1);
    // Standard kotlinc (-Xwhen-expressions=indy) pattern:
    //   <load selector>
    //   iconst_0
    //   invokedynamic typeSwitch/enumSwitch
    if (isConstZero(previous)) {
      return false;
    }
    // Standard javac pattern:
    //   iconst_0
    //   istore <var>
    //   aload <selector>
    //   iload <var>
    //   invokedynamic typeSwitch/enumSwitch
    // For switches with guards, javac also stores the next restart index into <var> before
    // jumping back to the invokedynamic, so require that <var> is not an argument and is only
    // ever assigned the constant 0.
    if (!previous.isLoad() || previous.asLoad().getType() != ValueType.INT) {
      return true;
    }
    int local = previous.asLoad().getLocalIndex();
    if (local < getArgumentSlots(context)) {
      return true;
    }
    for (int i = 0; i < instructions.size(); i++) {
      CfInstruction instruction = instructions.get(i);
      if (instruction.isStore() && instruction.asStore().getLocalIndex() == local) {
        if (i == 0 || !isConstZero(instructions.get(i - 1))) {
          return true;
        }
      } else if (instruction instanceof CfIinc && ((CfIinc) instruction).getLocalIndex() == local) {
        return true;
      }
    }
    return false;
  }

  private static int getArgumentSlots(ProgramMethod method) {
    int slots = method.getAccessFlags().isStatic() ? 0 : 1;
    for (DexType parameter : method.getParameters()) {
      slots += parameter.getRequiredRegisters();
    }
    return slots;
  }

  private static int indexOf(List<CfInstruction> instructions, CfInstruction instruction) {
    for (int i = 0; i < instructions.size(); i++) {
      if (ObjectUtils.identical(instructions.get(i), instruction)) {
        return i;
      }
    }
    return -1;
  }

  private static boolean isConstZero(CfInstruction instruction) {
    return instruction.isConstNumber()
        && instruction.asConstNumber().getType() == ValueType.INT
        && instruction.asConstNumber().getIntValue() == 0;
  }
}

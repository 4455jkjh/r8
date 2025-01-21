// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexInvokeSuperRange extends DexInvokeMethodRange {

  public static final int OPCODE = 0x75;
  public static final String NAME = "InvokeSuperRange";
  public static final String SMALI_NAME = "invoke-super/range";

  DexInvokeSuperRange(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getMethodMap());
  }

  public DexInvokeSuperRange(int firstArgumentRegister, int argumentCount, DexMethod method) {
    super(firstArgumentRegister, argumentCount, method);
  }

  @Override
  public InvokeType getInvokeType() {
    return InvokeType.SUPER;
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public InvokeType getType() {
    return InvokeType.SUPER;
  }

  @Override
  public void registerUse(UseRegistry<?> registry) {
    registry.registerInvokeSuper(getMethod());
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addInvokeRange(InvokeType.SUPER, getMethod(), getProto(), AA, CCCC);
  }

  @Override
  public DexInvokeSuperRange withMethod(DexMethod method) {
    DexInvokeSuperRange instruction = new DexInvokeSuperRange(CCCC, AA, method);
    instruction.setOffset(getOffset());
    return instruction;
  }
}

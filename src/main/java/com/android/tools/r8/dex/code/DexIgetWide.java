// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexIgetWide extends DexIgetOrIput implements CfOrDexInstanceFieldRead {

  public static final int OPCODE = 0x53;
  public static final String NAME = "IgetWide";
  public static final String SMALI_NAME = "iget-wide";

  /*package*/ DexIgetWide(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getFieldMap());
  }

  public DexIgetWide(int destRegister, int objectRegister, DexField field) {
    super(destRegister, objectRegister, field);
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
  public void registerUse(UseRegistry<?> registry) {
    registry.registerInstanceFieldReadInstruction(this);
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addInstanceGet(A, B, getField());
  }

  @Override
  public DexIgetWide withField(DexField field) {
    DexIgetWide instruction = new DexIgetWide(A, B, field);
    instruction.setOffset(getOffset());
    return instruction;
  }
}

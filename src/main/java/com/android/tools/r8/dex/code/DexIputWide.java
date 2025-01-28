// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexIputWide extends DexIgetOrIput {

  public static final int OPCODE = 0x5a;
  public static final String NAME = "IputWide";
  public static final String SMALI_NAME = "iput-wide";

  /*package*/ DexIputWide(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getFieldMap());
  }

  public DexIputWide(int valueRegister, int objectRegister, DexField field) {
    super(valueRegister, objectRegister, field);
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
    registry.registerInstanceFieldWrite(getField());
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addInstancePut(A, B, getField());
  }

  @Override
  public DexIputWide withField(DexField field) {
    DexIputWide instruction = new DexIputWide(A, B, field);
    instruction.setOffset(getOffset());
    return instruction;
  }
}

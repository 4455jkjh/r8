// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class ProtoStringObject extends ProtoObject {

  private final DexString value;

  public ProtoStringObject(DexString value) {
    this.value = value;
  }

  public DexString getValue() {
    return value;
  }

  @Override
  public List<Instruction> buildIR(AppView<?> appView, IRCode code) {
    ConstString instruction = code.createStringConstant(appView, value);
    return ImmutableList.of(instruction);
  }

  @Override
  public boolean isProtoStringObject() {
    return true;
  }

  @Override
  public ProtoStringObject asProtoStringObject() {
    return this;
  }
}

// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class ProtoConstIntObject extends ProtoObject {

  private final int value;

  public ProtoConstIntObject(int value) {
    this.value = value;
  }

  @Override
  public List<Instruction> buildIR(AppView<?> appView, IRCode code) {
    return ImmutableList.of(code.createIntConstant(value));
  }

  @Override
  public boolean isProtoConstIntObject() {
    return true;
  }

  @Override
  public ProtoConstIntObject asProtoConstIntObject() {
    return this;
  }
}

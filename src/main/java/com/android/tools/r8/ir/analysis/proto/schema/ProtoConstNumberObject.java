// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class ProtoConstNumberObject extends ProtoObject {

  private final long value;
  private final PrimitiveTypeElement type;

  public ProtoConstNumberObject(long value, PrimitiveTypeElement type) {
    this.value = value;
    this.type = type;
  }

  @Override
  public List<Instruction> buildIR(AppView<?> appView, IRCode code) {
    return ImmutableList.of(code.createNumberConstant(value, type));
  }

  @Override
  public boolean isProtoConstNumberObject() {
    return true;
  }

  @Override
  public ProtoConstNumberObject asProtoConstNumberObject() {
    return this;
  }
}

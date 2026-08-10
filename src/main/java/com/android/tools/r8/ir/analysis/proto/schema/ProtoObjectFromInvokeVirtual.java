// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class ProtoObjectFromInvokeVirtual extends ProtoObject {

  private final ProtoObject receiver;
  private final DexMethod method;

  public ProtoObjectFromInvokeVirtual(ProtoObject receiver, DexMethod method) {
    this.receiver = receiver;
    this.method = method;
  }

  public DexMethod getMethod() {
    return method;
  }

  @Override
  public List<Instruction> buildIR(AppView<?> appView, IRCode code) {
    List<Instruction> instructions = receiver.buildIR(appView, code);
    Value value =
        code.createValue(
            TypeElement.fromDexType(method.getReturnType(), Nullability.maybeNull(), appView));
    ImmutableList.Builder<Instruction> builder = ImmutableList.builder();
    builder.addAll(instructions);
    Value receiverValue = instructions.get(instructions.size() - 1).outValue();
    builder.add(new InvokeVirtual(method, value, ImmutableList.of(receiverValue)));
    return builder.build();
  }

  @Override
  public boolean isProtoObjectFromInvokeVirtual() {
    return true;
  }

  @Override
  public ProtoObjectFromInvokeVirtual asProtoObjectFromInvokeVirtual() {
    return this;
  }
}

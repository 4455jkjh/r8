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
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class ProtoBoxedPrimitiveObject extends ProtoObject {

  private final DexMethod valueOfMethod;
  private final ProtoObject object;

  public ProtoBoxedPrimitiveObject(DexMethod valueOfMethod, ProtoObject object) {
    this.valueOfMethod = valueOfMethod;
    this.object = object;
  }

  @Override
  public List<Instruction> buildIR(AppView<?> appView, IRCode code) {
    Value outValue =
        code.createValue(
            TypeElement.fromDexType(
                valueOfMethod.getReturnType(), Nullability.maybeNull(), appView));
    List<Instruction> instructions = object.buildIR(appView, code);
    ImmutableList.Builder<Instruction> builder = ImmutableList.builder();
    builder.addAll(instructions);
    Value primitiveValue = instructions.get(instructions.size() - 1).outValue();
    builder.add(new InvokeStatic(valueOfMethod, outValue, ImmutableList.of(primitiveValue)));
    return builder.build();
  }

  @Override
  public boolean isProtoBoxedPrimitiveObject() {
    return true;
  }

  @Override
  public ProtoBoxedPrimitiveObject asProtoBoxedPrimitiveObject() {
    return this;
  }
}

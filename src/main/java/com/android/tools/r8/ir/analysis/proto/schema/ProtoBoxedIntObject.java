// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class ProtoBoxedIntObject extends ProtoObject {

  private final int value;

  public ProtoBoxedIntObject(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }

  @Override
  public List<Instruction> buildIR(AppView<?> appView, IRCode code) {
    ConstNumber constant = code.createIntConstant(value);
    DexMethod valueOfMethod = appView.dexItemFactory().integerMembers.valueOf;
    Value outValue =
        code.createValue(
            TypeElement.fromDexType(
                valueOfMethod.getReturnType(), Nullability.maybeNull(), appView));
    InvokeStatic invoke =
        new InvokeStatic(valueOfMethod, outValue, ImmutableList.of(constant.outValue()));
    return ImmutableList.of(constant, invoke);
  }

  @Override
  public boolean isProtoBoxedIntObject() {
    return true;
  }

  @Override
  public ProtoBoxedIntObject asProtoBoxedIntObject() {
    return this;
  }
}

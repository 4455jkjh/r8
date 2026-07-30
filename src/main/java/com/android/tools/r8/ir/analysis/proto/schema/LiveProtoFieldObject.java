// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.naming.dexitembasedstring.FieldNameComputationInfo;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class LiveProtoFieldObject extends ProtoFieldObject {

  private final DexField field;

  public LiveProtoFieldObject(DexField field) {
    this.field = field;
  }

  public DexField getField() {
    return field;
  }

  @Override
  public List<Instruction> buildIR(AppView<?> appView, IRCode code) {
    Value value =
        code.createValue(TypeElement.stringClassType(appView, Nullability.definitelyNotNull()));
    if (appView.options().isMinifying()) {
      return ImmutableList.of(
          new DexItemBasedConstString(value, field, FieldNameComputationInfo.forFieldName()));
    }
    return ImmutableList.of(new ConstString(value, field.name));
  }

  @Override
  public boolean isLiveProtoFieldObject() {
    return true;
  }

  @Override
  public LiveProtoFieldObject asLiveProtoFieldObject() {
    return this;
  }

  @Override
  public String toString() {
    return "LiveProtoFieldObject(" + field.toSourceString() + ")";
  }
}

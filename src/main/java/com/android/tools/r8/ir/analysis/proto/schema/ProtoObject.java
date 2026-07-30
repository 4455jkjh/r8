// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import java.util.List;

public abstract class ProtoObject {

  public abstract List<Instruction> buildIR(AppView<?> appView, IRCode code);

  public boolean isProtoFieldObject() {
    return false;
  }

  public ProtoFieldObject asProtoFieldObject() {
    return null;
  }

  public boolean isDeadProtoFieldObject() {
    return false;
  }

  public boolean isLiveProtoFieldObject() {
    return false;
  }

  public LiveProtoFieldObject asLiveProtoFieldObject() {
    return null;
  }

  public boolean isProtoObjectFromStaticGet() {
    return false;
  }

  public ProtoObjectFromStaticGet asProtoObjectFromStaticGet() {
    return null;
  }

  public boolean isProtoObjectFromInvokeStatic() {
    return false;
  }

  public ProtoObjectFromInvokeStatic asProtoObjectFromInvokeStatic() {
    return null;
  }

  public boolean isProtoTypeObject() {
    return false;
  }

  public ProtoTypeObject asProtoTypeObject() {
    return null;
  }

  public boolean isProtoMapEntryLiteObject() {
    return false;
  }

  public ProtoMapEntryLiteObject asProtoMapEntryLiteObject() {
    return null;
  }

  public boolean isProtoStringObject() {
    return false;
  }

  public ProtoStringObject asProtoStringObject() {
    return null;
  }

  public boolean isProtoBoxedIntObject() {
    return false;
  }

  public ProtoBoxedIntObject asProtoBoxedIntObject() {
    return null;
  }
}

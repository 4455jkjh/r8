// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.graph.DexItemFactory;

public class ProtoBoxedBooleanObject extends ProtoObjectFromStaticGet {

  public ProtoBoxedBooleanObject(boolean value, DexItemFactory factory) {
    super(value ? factory.booleanMembers.TRUE : factory.booleanMembers.FALSE);
  }

  @Override
  public boolean isProtoBoxedBooleanObject() {
    return true;
  }

  @Override
  public ProtoBoxedBooleanObject asProtoBoxedBooleanObject() {
    return this;
  }
}

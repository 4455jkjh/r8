// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto.schema;

import com.android.tools.r8.ir.code.Value;
import java.util.List;
import java.util.OptionalInt;

public class ProtoFieldInfo {

  private final int number;
  private final ProtoFieldType type;

  private final OptionalInt auxData;
  // TODO(b/112437944): Create an abstract representation of the object values to ensure that this
  //  is detached from the IR.
  private final List<Value> objects;

  public ProtoFieldInfo(int number, ProtoFieldType type, OptionalInt auxData, List<Value> objects) {
    this.number = number;
    this.type = type;
    this.auxData = auxData;
    this.objects = objects;
  }

  public boolean hasAuxData() {
    return auxData.isPresent();
  }

  public int getAuxData() {
    assert hasAuxData();
    return auxData.getAsInt();
  }

  public int getNumber() {
    return number;
  }

  public ProtoFieldType getType() {
    return type;
  }
}

// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.DexField;

public interface FieldPut {

  DexField getField();

  Position getPosition();

  int getValueIndex();

  Value value();

  void setValue(Value value);

  FieldInstruction asFieldInstruction();

  boolean isInstancePut();

  InstancePut asInstancePut();

  boolean isStaticPut();

  StaticPut asStaticPut();
}

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
import java.util.ArrayList;
import java.util.List;

public class ProtoMapEntryLiteObject extends ProtoObject {

  private final DexMethod method;
  private final ProtoObject keyType;
  private final ProtoObject defaultKey;
  private final ProtoObject valueType;
  private final ProtoObject defaultValue;

  public ProtoMapEntryLiteObject(
      DexMethod method,
      ProtoObject keyType,
      ProtoObject defaultKey,
      ProtoObject valueType,
      ProtoObject defaultValue) {
    this.method = method;
    this.keyType = keyType;
    this.defaultKey = defaultKey;
    this.valueType = valueType;
    this.defaultValue = defaultValue;
  }

  public DexMethod getMethod() {
    return method;
  }

  public ProtoObject getKeyType() {
    return keyType;
  }

  public ProtoObject getDefaultKey() {
    return defaultKey;
  }

  public ProtoObject getValueType() {
    return valueType;
  }

  public ProtoObject getDefaultValue() {
    return defaultValue;
  }

  @Override
  public List<Instruction> buildIR(AppView<?> appView, IRCode code) {
    List<Instruction> instructions = new ArrayList<>();

    List<Instruction> keyTypeInstructions = keyType.buildIR(appView, code);
    instructions.addAll(keyTypeInstructions);
    Value keyTypeValue = keyTypeInstructions.get(keyTypeInstructions.size() - 1).outValue();

    List<Instruction> defaultKeyInstructions = defaultKey.buildIR(appView, code);
    instructions.addAll(defaultKeyInstructions);
    Value defaultKeyValue =
        defaultKeyInstructions.get(defaultKeyInstructions.size() - 1).outValue();

    List<Instruction> valueTypeInstructions = valueType.buildIR(appView, code);
    instructions.addAll(valueTypeInstructions);
    Value valueTypeValue = valueTypeInstructions.get(valueTypeInstructions.size() - 1).outValue();

    List<Instruction> defaultValueInstructions = defaultValue.buildIR(appView, code);
    instructions.addAll(defaultValueInstructions);
    Value defaultValueValue =
        defaultValueInstructions.get(defaultValueInstructions.size() - 1).outValue();

    Value value =
        code.createValue(
            TypeElement.fromDexType(method.proto.returnType, Nullability.maybeNull(), appView));
    instructions.add(
        new InvokeStatic(
            method,
            value,
            ImmutableList.of(keyTypeValue, defaultKeyValue, valueTypeValue, defaultValueValue)));

    return instructions;
  }

  @Override
  public boolean isProtoMapEntryLiteObject() {
    return true;
  }

  @Override
  public ProtoMapEntryLiteObject asProtoMapEntryLiteObject() {
    return this;
  }
}

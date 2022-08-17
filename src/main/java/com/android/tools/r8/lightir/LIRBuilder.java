// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.code.IRMetadata;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.List;

/**
 * Builder for constructing LIR code from IR.
 *
 * @param <V> Type of SSA values. This is abstract to ensure that value internals are not used in
 *     building.
 */
public class LIRBuilder<V> {

  public interface ValueIndexGetter<V> {
    int getValueIndex(V value);
  }

  private final ByteArrayWriter byteWriter = new ByteArrayWriter();
  private final LIRWriter writer = new LIRWriter(byteWriter);
  private final Reference2IntMap<DexItem> constants;
  private final ValueIndexGetter<V> valueIndexGetter;
  private int argumentCount = 0;
  private int instructionCount = 0;
  private IRMetadata metadata = null;

  public LIRBuilder(ValueIndexGetter<V> valueIndexGetter) {
    constants = new Reference2IntOpenHashMap<>();
    this.valueIndexGetter = valueIndexGetter;
  }

  private int getConstantIndex(DexItem item) {
    int nextIndex = constants.size();
    Integer oldIndex = constants.putIfAbsent(item, nextIndex);
    return oldIndex != null ? oldIndex : nextIndex;
  }

  private int constantIndexSize(DexItem item) {
    return 4;
  }

  private void writeConstantIndex(DexItem item) {
    int index = getConstantIndex(item);
    ByteUtils.writeEncodedInt(index, writer::writeOperand);
  }

  private int getValueIndex(V value) {
    return valueIndexGetter.getValueIndex(value);
  }

  private int valueIndexSize(int index) {
    return ByteUtils.intEncodingSize(index);
  }

  private void writeValueIndex(int index) {
    ByteUtils.writeEncodedInt(index, writer::writeOperand);
  }

  public LIRBuilder<V> setMetadata(IRMetadata metadata) {
    this.metadata = metadata;
    return this;
  }

  public LIRBuilder<V> writeConstantReferencingInstruction(int opcode, DexItem item) {
    writer.writeInstruction(opcode, constantIndexSize(item));
    writeConstantIndex(item);
    return this;
  }

  public LIRBuilder<V> addArgument(int index, boolean knownToBeBoolean) {
    // Arguments are implicitly given by method descriptor and not an actual instruction.
    assert argumentCount == index;
    argumentCount++;
    return this;
  }

  public LIRBuilder<V> addNop() {
    instructionCount++;
    writer.writeOneByteInstruction(LIROpcodes.NOP);
    return this;
  }

  public LIRBuilder<V> addConstNull() {
    instructionCount++;
    writer.writeOneByteInstruction(LIROpcodes.ACONST_NULL);
    return this;
  }

  public LIRBuilder<V> addConstInt(int value) {
    instructionCount++;
    if (0 <= value && value <= 5) {
      writer.writeOneByteInstruction(LIROpcodes.ICONST_0 + value);
    } else {
      writer.writeInstruction(LIROpcodes.ICONST, ByteUtils.intEncodingSize(value));
      ByteUtils.writeEncodedInt(value, writer::writeOperand);
    }
    return this;
  }

  public LIRBuilder<V> addConstString(DexString string) {
    instructionCount++;
    return writeConstantReferencingInstruction(LIROpcodes.LDC, string);
  }

  public LIRBuilder<V> addStaticGet(DexField field) {
    instructionCount++;
    return writeConstantReferencingInstruction(LIROpcodes.GETSTATIC, field);
  }

  public LIRBuilder<V> addInvokeInstruction(int opcode, DexMethod method, List<V> arguments) {
    instructionCount++;
    int argumentOprandSize = constantIndexSize(method);
    int[] argumentIndexes = new int[arguments.size()];
    int i = 0;
    for (V argument : arguments) {
      int argumentIndex = getValueIndex(argument);
      argumentIndexes[i++] = argumentIndex;
      argumentOprandSize += valueIndexSize(argumentIndex);
    }
    writer.writeInstruction(opcode, argumentOprandSize);
    writeConstantIndex(method);
    for (int argumentIndex : argumentIndexes) {
      writeValueIndex(argumentIndex);
    }
    return this;
  }

  public LIRBuilder<V> addInvokeDirect(DexMethod method, List<V> arguments) {
    return addInvokeInstruction(LIROpcodes.INVOKEDIRECT, method, arguments);
  }

  public LIRBuilder<V> addInvokeVirtual(DexMethod method, List<V> arguments) {
    return addInvokeInstruction(LIROpcodes.INVOKEVIRTUAL, method, arguments);
  }

  public LIRBuilder<V> addReturn(V value) {
    return this;
  }

  public LIRBuilder<V> addReturnVoid() {
    instructionCount++;
    writer.writeInstruction(LIROpcodes.RETURN, 0);
    return this;
  }

  public LIRCode build() {
    assert metadata != null;
    int constantsCount = constants.size();
    DexItem[] constantTable = new DexItem[constantsCount];
    constants.forEach((item, index) -> constantTable[index] = item);
    return new LIRCode(
        metadata, constantTable, argumentCount, byteWriter.toByteArray(), instructionCount);
  }
}

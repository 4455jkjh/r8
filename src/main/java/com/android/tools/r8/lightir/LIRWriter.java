// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

/**
 * Lowest level writer for constructing LIR encoded data.
 *
 * <p>This writer deals with just the instruction and operand encodings. For higher level structure,
 * such as the constant pool, see LIRBuilder.
 */
public class LIRWriter {

  private final ByteWriter writer;
  private int pendingOperandBytes = 0;

  public LIRWriter(ByteWriter writer) {
    this.writer = writer;
  }

  public void writeOneByteInstruction(int opcode) {
    assert LIROpcodes.isOneByteInstruction(opcode);
    assert pendingOperandBytes == 0;
    writer.put(ByteUtils.ensureU1(opcode));
  }

  public void writeInstruction(int opcode, int operandsSizeInBytes) {
    assert pendingOperandBytes == 0;
    writer.put(ByteUtils.ensureU1(opcode));
    writer.put(ByteUtils.ensureU1(operandsSizeInBytes));
    pendingOperandBytes = operandsSizeInBytes;
  }

  public void writeOperand(int u1) {
    assert pendingOperandBytes > 0;
    pendingOperandBytes--;
    writer.put(ByteUtils.ensureU1(u1));
  }
}

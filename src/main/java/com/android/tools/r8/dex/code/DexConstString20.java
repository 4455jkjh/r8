// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.StringOffsetProvider;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.StructuralSpecification;

public class DexConstString20 extends DexFormat21c20<DexString> {

  public static int MIN_REFERENCE_INCLUSIVE = Constants.U16BIT_SIZE;
  public static int MAX_REFERENCE_INCLUSIVE = Constants.U16BIT_MAX + Constants.U20BIT_SIZE;

  public static final int OPCODE = 0x42;
  public static final String NAME = "ConstString20";
  public static final String SMALI_NAME = "const-string/20";

  DexConstString20(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping.getStringMap());
  }

  public DexConstString20(int register, DexString string) {
    super(register, string);
  }

  public boolean needsJumboStringRewriting(StringOffsetProvider offsets, InternalOptions options) {
    if (!options.enableConstString20()) {
      return true;
    }
    int offset = offsets.getOffsetFor(getString());
    // The reference of a const-string/20 instruction is offset by 2^16. If the reference is below
    // 2^16 we cannot reference the string using const-string/20 (need to use const-string).
    if (offset < MIN_REFERENCE_INCLUSIVE) {
      return true;
    }
    // Otherwise check if we are above the max reference (need to use const-string/jumbo).
    return offset > MAX_REFERENCE_INCLUSIVE;
  }

  @Override
  protected int encodeIndex(int index) {
    return index - Constants.U16BIT_SIZE;
  }

  @Override
  protected int decodeIndex(int index) {
    return index + Constants.U16BIT_SIZE;
  }

  @Override
  void internalSubSpecify(StructuralSpecification<DexFormat21c20<DexString>, ?> spec) {
    spec.withItem(i -> i.BBBB);
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addConstString(A, BBBB);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  public void collectIndexedItems(
      AppView<?> appView,
      GraphLens codeLens,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter) {
    getString().collectIndexedItems(indexedItems);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  public DexString getString() {
    return BBBB;
  }

  @Override
  public boolean isConstString20() {
    return true;
  }

  @Override
  public DexConstString20 asConstString20() {
    return this;
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + A + ", \"" + BBBB + "\"");
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString("v" + A + ", \"" + BBBB + "\"");
  }
}

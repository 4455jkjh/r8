// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.IndexedDexItem;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.nio.ShortBuffer;
import java.util.function.BiPredicate;

abstract class DexFormat21c20<T extends IndexedDexItem> extends DexBase2Format {

  public final byte A;
  public T BBBB;

  // B|A | op | [type|field|string]@BBBB
  DexFormat21c20(int high, BytecodeStream stream, T[] map) {
    super(stream);
    this.A = (byte) (high & 0xf);
    int indexHigh = (high >> 4) & 0xf;
    int indexLow = read16BitValue(stream);
    int index = (indexHigh << 16) | indexLow;
    this.BBBB = map[decodeIndex(index)];
  }

  protected DexFormat21c20(int A, T BBBB) {
    assert 0 <= A;
    assert A <= Constants.U4BIT_MAX;
    this.A = (byte) A;
    this.BBBB = BBBB;
  }

  protected int encodeIndex(int index) {
    return index;
  }

  protected int decodeIndex(int index) {
    return index;
  }

  @Override
  public final int hashCode() {
    return ((BBBB.hashCode() << 8) | (A & 0xff)) ^ getClass().hashCode();
  }

  @SuppressWarnings("unchecked")
  @Override
  final int internalAcceptCompareTo(DexInstruction other, CompareToVisitor visitor) {
    return visitor.visit(
        this,
        (DexFormat21c20<T>) other,
        spec -> spec.withInt(i -> i.A).withSpec(this::internalSubSpecify));
  }

  @Override
  final void internalAcceptHashing(HashingVisitor visitor) {
    visitor.visitInt(A);
    visitor.visit(this, this::internalSubSpecify);
  }

  abstract void internalSubSpecify(StructuralSpecification<DexFormat21c20<T>, ?> spec);

  @Override
  public void write(
      ShortBuffer dest,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      ObjectToOffsetMapping mapping,
      LensCodeRewriterUtils rewriter) {
    int index = BBBB.getOffset(mapping);
    int encodedIndex = encodeIndex(index);
    assert encodedIndex == (encodedIndex & 0xfffff);
    write4BitValueAnd20BitValue(A, encodedIndex, dest);
  }

  @Override
  public String toString(RetracerForCodePrinting retracer) {
    return formatString("v" + A + ", " + retracer.toDescriptor(BBBB));
  }

  @Override
  public String toSmaliString(RetracerForCodePrinting retracer) {
    return formatSmaliString("v" + A + ", " + BBBB.toSmaliString());
  }

  @Override
  public boolean equals(
      DexInstruction other, BiPredicate<IndexedDexItem, IndexedDexItem> equality) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    DexFormat21c20<?> o = (DexFormat21c20<?>) other;
    return A == o.A && equality.test(BBBB, o.BBBB);
  }
}

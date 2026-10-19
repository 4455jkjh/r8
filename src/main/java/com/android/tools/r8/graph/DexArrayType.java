// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

public class DexArrayType extends DexType {

  private final DexType baseType;
  private final DexType elementType;
  private final int dimensions;

  public DexArrayType(DexString descriptor, DexType elementType) {
    super(descriptor);
    this.baseType = elementType.getBaseType();
    this.elementType = elementType;
    this.dimensions = getArrayTypeDimensions(descriptor);
    assert dimensions > 0;
    assert baseType.isIdenticalTo(elementType) || dimensions > 1;
  }

  @Override
  public DexType getArrayElementType() {
    return elementType;
  }

  public DexType getArrayElementTypeAfterDimension(int dimensionsToRemove) {
    assert dimensions >= dimensionsToRemove;
    DexType current = this;
    while (dimensionsToRemove > 0) {
      current = current.asArrayType().getArrayElementType();
      dimensionsToRemove--;
    }
    return current;
  }

  @Override
  public int getArrayTypeDimensions() {
    return dimensions;
  }

  private static int getArrayTypeDimensions(DexString descriptor) {
    int leadingSquareBrackets = 0;
    while (descriptor.content[leadingSquareBrackets] == '[') {
      leadingSquareBrackets++;
    }
    return leadingSquareBrackets;
  }

  @Override
  public DexType getBaseType() {
    return baseType;
  }

  public int getElementSizeForPrimitiveArrayType() {
    assert isPrimitiveArrayType();
    return baseType.getPrimitiveTypeSizeInBytes();
  }

  @Override
  public boolean isArrayType() {
    return true;
  }

  @Override
  public boolean isPrimitiveArrayType() {
    return elementType.isPrimitiveType();
  }

  @Override
  public DexArrayType asArrayType() {
    return this;
  }

  public DexType replaceBaseType(DexType newBase, DexItemFactory dexItemFactory) {
    return newBase.isIdenticalTo(baseType)
        ? this
        : newBase.toArrayType(dexItemFactory, getArrayTypeDimensions());
  }

  @Override
  public char toShorty() {
    return 'L';
  }
}

// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.code.CfFrame.BooleanFrameType;
import com.android.tools.r8.cf.code.CfFrame.ByteFrameType;
import com.android.tools.r8.cf.code.CfFrame.CharFrameType;
import com.android.tools.r8.cf.code.CfFrame.DoubleFrameType;
import com.android.tools.r8.cf.code.CfFrame.FloatFrameType;
import com.android.tools.r8.cf.code.CfFrame.InitializedReferenceFrameType;
import com.android.tools.r8.cf.code.CfFrame.IntFrameType;
import com.android.tools.r8.cf.code.CfFrame.LongFrameType;
import com.android.tools.r8.cf.code.CfFrame.OneWord;
import com.android.tools.r8.cf.code.CfFrame.ShortFrameType;
import com.android.tools.r8.cf.code.CfFrame.SinglePrimitiveFrameType;
import com.android.tools.r8.cf.code.CfFrame.TwoWord;
import com.android.tools.r8.cf.code.CfFrame.UninitializedFrameType;
import com.android.tools.r8.cf.code.CfFrame.UninitializedNew;
import com.android.tools.r8.cf.code.CfFrame.UninitializedThis;
import com.android.tools.r8.cf.code.frame.InitializedFrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.cf.code.frame.PrimitiveFrameType;
import com.android.tools.r8.cf.code.frame.SingleFrameType;
import com.android.tools.r8.cf.code.frame.WideFrameType;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.naming.NamingLens;
import java.util.function.Function;

public interface FrameType {

  static BooleanFrameType booleanType() {
    return BooleanFrameType.SINGLETON;
  }

  static ByteFrameType byteType() {
    return ByteFrameType.SINGLETON;
  }

  static CharFrameType charType() {
    return CharFrameType.SINGLETON;
  }

  static DoubleFrameType doubleType() {
    return DoubleFrameType.SINGLETON;
  }

  static FloatFrameType floatType() {
    return FloatFrameType.SINGLETON;
  }

  static IntFrameType intType() {
    return IntFrameType.SINGLETON;
  }

  static LongFrameType longType() {
    return LongFrameType.SINGLETON;
  }

  static ShortFrameType shortType() {
    return ShortFrameType.SINGLETON;
  }

  static InitializedFrameType initialized(DexType type) {
    if (type.isPrimitiveType()) {
      return primitive(type);
    }
    return new InitializedReferenceFrameType(type);
  }

  static PrimitiveFrameType primitive(DexType type) {
    assert type.isPrimitiveType();
    char c = (char) type.getDescriptor().content[0];
    switch (c) {
      case 'Z':
        return booleanType();
      case 'B':
        return byteType();
      case 'C':
        return charType();
      case 'D':
        return doubleType();
      case 'F':
        return floatType();
      case 'I':
        return intType();
      case 'J':
        return longType();
      case 'S':
        return shortType();
      default:
        throw new Unreachable("Unexpected primitive type: " + type.getTypeName());
    }
  }

  static UninitializedNew uninitializedNew(CfLabel label, DexType typeToInitialize) {
    return new UninitializedNew(label, typeToInitialize);
  }

  static UninitializedThis uninitializedThis() {
    return UninitializedThis.SINGLETON;
  }

  static OneWord oneWord() {
    return OneWord.SINGLETON;
  }

  static TwoWord twoWord() {
    return TwoWord.SINGLETON;
  }

  static PrimitiveFrameType fromNumericType(NumericType numericType, DexItemFactory factory) {
    return FrameType.primitive(numericType.toDexType(factory));
  }

  static InitializedFrameType fromPreciseMemberType(MemberType memberType, DexItemFactory factory) {
    assert memberType.isPrecise();
    switch (memberType) {
      case OBJECT:
        return FrameType.initialized(factory.objectType);
      case BOOLEAN_OR_BYTE:
      case CHAR:
      case SHORT:
      case INT:
        return intType();
      case FLOAT:
        return floatType();
      case LONG:
        return longType();
      case DOUBLE:
        return doubleType();
      default:
        throw new Unreachable("Unexpected MemberType: " + memberType);
    }
  }

  DexType getInitializedType(DexItemFactory dexItemFactory);

  DexType getObjectType(DexType context);

  Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens);

  CfLabel getUninitializedLabel();

  DexType getUninitializedNewType();

  int getWidth();

  boolean isBoolean();

  boolean isByte();

  boolean isChar();

  boolean isDouble();

  boolean isFloat();

  boolean isInitialized();

  InitializedReferenceFrameType asInitializedReferenceType();

  boolean isInt();

  boolean isLong();

  boolean isNullType();

  boolean isObject();

  boolean isOneWord();

  boolean isPrecise();

  PreciseFrameType asPrecise();

  boolean isPrimitive();

  PrimitiveFrameType asPrimitive();

  boolean isShort();

  boolean isSingle();

  SingleFrameType asSingle();

  SinglePrimitiveFrameType asSinglePrimitive();

  boolean isTwoWord();

  boolean isUninitialized();

  UninitializedFrameType asUninitialized();

  boolean isUninitializedNew();

  UninitializedNew asUninitializedNew();

  boolean isUninitializedThis();

  UninitializedThis asUninitializedThis();

  boolean isWide();

  WideFrameType asWide();

  default FrameType map(Function<DexType, DexType> fn) {
    assert !isPrecise();
    return this;
  }
}

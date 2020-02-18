// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.COMPANION_CLASS_NAME_SUFFIX;
import static com.android.tools.r8.ir.desugar.InterfaceMethodRewriter.DISPATCH_CLASS_NAME_SUFFIX;
import static com.android.tools.r8.ir.desugar.LambdaRewriter.LAMBDA_CLASS_NAME_PREFIX;
import static com.android.tools.r8.ir.desugar.LambdaRewriter.LAMBDA_GROUP_CLASS_NAME_PREFIX;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.desugar.Java8MethodRewriter;
import com.android.tools.r8.ir.desugar.NestBasedAccessDesugaring;
import com.android.tools.r8.ir.desugar.TwrCloseResourceRewriter;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Predicate;

public class DexType extends DexReference implements PresortedComparable<DexType> {
  public static final DexType[] EMPTY_ARRAY = {};

  public final DexString descriptor;
  private String toStringCache = null;

  DexType(DexString descriptor) {
    assert !descriptor.toString().contains(".");
    this.descriptor = descriptor;
  }

  @Override
  public int computeHashCode() {
    return descriptor.hashCode();
  }

  @Override
  public boolean computeEquals(Object other) {
    if (other instanceof DexType) {
      return descriptor.equals(((DexType) other).descriptor);
    }
    return false;
  }

  public boolean classInitializationMayHaveSideEffects(DexDefinitionSupplier definitions) {
    return classInitializationMayHaveSideEffects(
        definitions, Predicates.alwaysFalse(), Sets.newIdentityHashSet());
  }

  public boolean classInitializationMayHaveSideEffects(
      DexDefinitionSupplier definitions, Predicate<DexType> ignore, Set<DexType> seen) {
    DexClass clazz = definitions.definitionFor(this);
    return clazz == null || clazz.classInitializationMayHaveSideEffects(definitions, ignore, seen);
  }

  public boolean initializationOfParentTypesMayHaveSideEffects(AppInfo appInfo) {
    return initializationOfParentTypesMayHaveSideEffects(
        appInfo, Predicates.alwaysFalse(), Sets.newIdentityHashSet());
  }

  public boolean initializationOfParentTypesMayHaveSideEffects(
      AppInfo appInfo, Predicate<DexType> ignore, Set<DexType> seen) {
    DexClass clazz = appInfo.definitionFor(this);
    return clazz == null
        || clazz.initializationOfParentTypesMayHaveSideEffects(appInfo, ignore, seen);
  }

  public boolean isAlwaysNull(AppView<AppInfoWithLiveness> appView) {
    if (isClassType()) {
      DexClass clazz = appView.definitionFor(this);
      return clazz != null
          && clazz.isProgramClass()
          && !appView.appInfo().isInstantiatedDirectlyOrIndirectly(this);
    }
    return false;
  }

  public boolean isSamePackage(DexType other) {
    return getPackageDescriptor().equals(other.getPackageDescriptor());
  }

  public String toDescriptorString() {
    return descriptor.toString();
  }

  public String toBinaryName() {
    String descriptor = toDescriptorString();
    assert descriptor.length() > 1
        && descriptor.charAt(0) == 'L'
        && descriptor.charAt(descriptor.length() - 1) == ';';
    return descriptor.substring(1, descriptor.length() - 1);
  }

  @Override
  public String toSourceString() {
    if (toStringCache == null) {
      // TODO(ager): Pass in a ProguardMapReader to map names back to original names.
      if (DexItemFactory.isInternalSentinel(this)) {
        toStringCache = descriptor.toString();
      } else {
        toStringCache = DescriptorUtils.descriptorToJavaType(toDescriptorString());
      }
    }
    return toStringCache;
  }

  public char toShorty() {
    char c = (char) descriptor.content[0];
    return c == '[' ? 'L' : c;
  }

  @Override
  public String toSmaliString() {
    return toDescriptorString();
  }

  @Override
  public String toString() {
    return toSourceString();
  }

  @Override
  public void collectIndexedItems(IndexedItemCollection collection,
      DexMethod method, int instructionOffset) {
    if (collection.addType(this)) {
      collection.getRenamedDescriptor(this).collectIndexedItems(collection, method,
          instructionOffset);
    }
  }

  @Override
  public void flushCachedValues() {
    super.flushCachedValues();
    toStringCache = null;
  }

  @Override
  public int getOffset(ObjectToOffsetMapping mapping) {
    return mapping.getOffsetFor(this);
  }

  @Override
  public boolean isDexType() {
    return true;
  }

  @Override
  public DexType asDexType() {
    return this;
  }

  @Override
  public int compareTo(DexType other) {
    return sortedCompareTo(other.getSortedIndex());
  }

  @Override
  public int slowCompareTo(DexType other) {
    return descriptor.slowCompareTo(other.descriptor);
  }

  @Override
  public int slowCompareTo(DexType other, NamingLens namingLens) {
    DexString thisDescriptor = namingLens.lookupDescriptor(this);
    DexString otherDescriptor = namingLens.lookupDescriptor(other);
    return thisDescriptor.slowCompareTo(otherDescriptor);
  }

  @Override
  public int layeredCompareTo(DexType other, NamingLens namingLens) {
    DexString thisDescriptor = namingLens.lookupDescriptor(this);
    DexString otherDescriptor = namingLens.lookupDescriptor(other);
    return thisDescriptor.compareTo(otherDescriptor);
  }

  public boolean isPrimitiveType() {
    return isPrimitiveType((char) descriptor.content[0]);
  }

  private boolean isPrimitiveType(char c) {
    return c == 'Z' || c == 'B' || c == 'S' || c == 'C' || c == 'I' || c == 'F' || c == 'J'
        || c == 'D';
  }

  public boolean isVoidType() {
    return (char) descriptor.content[0] == 'V';
  }

  public boolean isBooleanType() {
    return descriptor.content[0] == 'Z';
  }

  public boolean isByteType() {
    return descriptor.content[0] == 'B';
  }

  public boolean isCharType() {
    return descriptor.content[0] == 'C';
  }

  public boolean isShortType() {
    return descriptor.content[0] == 'S';
  }

  public boolean isIntType() {
    return descriptor.content[0] == 'I';
  }

  public boolean isFloatType() {
    return descriptor.content[0] == 'F';
  }

  public boolean isLongType() {
    return descriptor.content[0] == 'J';
  }

  public boolean isDoubleType() {
    return descriptor.content[0] == 'D';
  }

  public boolean isArrayType() {
    char firstChar = (char) descriptor.content[0];
    return firstChar == '[';
  }

  public boolean isClassType() {
    char firstChar = (char) descriptor.content[0];
    return firstChar == 'L';
  }

  public boolean isReferenceType() {
    boolean isReferenceType = isArrayType() || isClassType();
    assert isReferenceType != isPrimitiveType() || isVoidType();
    return isReferenceType;
  }

  public boolean isPrimitiveArrayType() {
    if (!isArrayType()) {
      return false;
    }
    return isPrimitiveType((char) descriptor.content[1]);
  }

  public boolean isD8R8SynthesizedClassType() {
    String name = toSourceString();
    return name.contains(COMPANION_CLASS_NAME_SUFFIX)
        || name.contains(DISPATCH_CLASS_NAME_SUFFIX)
        || name.contains(LAMBDA_CLASS_NAME_PREFIX)
        || name.contains(LAMBDA_GROUP_CLASS_NAME_PREFIX)
        || name.contains(OutlineOptions.CLASS_NAME)
        || name.contains(TwrCloseResourceRewriter.UTILITY_CLASS_NAME)
        || name.contains(NestBasedAccessDesugaring.NEST_CONSTRUCTOR_NAME)
        || name.contains(Java8MethodRewriter.UTILITY_CLASS_NAME_PREFIX);
  }

  public boolean isProgramType(DexDefinitionSupplier definitions) {
    DexClass clazz = definitions.definitionFor(this);
    return clazz != null && clazz.isProgramClass();
  }

  public boolean isResolvable(AppView<?> appView) {
    DexClass clazz = appView.definitionFor(this);
    return clazz != null && clazz.isResolvable(appView);
  }

  public int elementSizeForPrimitiveArrayType() {
    assert isPrimitiveArrayType();
    switch (descriptor.content[1]) {
      case 'Z':  // boolean
      case 'B':  // byte
        return 1;
      case 'S':  // short
      case 'C':  // char
        return 2;
      case 'I':  // int
      case 'F':  // float
        return 4;
      case 'J':  // long
      case 'D':  // double
        return 8;
      default:
        throw new Unreachable("Not array of primitives '" + descriptor + "'");
    }
  }

  public int getNumberOfLeadingSquareBrackets() {
    int leadingSquareBrackets = 0;
    while (descriptor.content[leadingSquareBrackets] == '[') {
      leadingSquareBrackets++;
    }
    return leadingSquareBrackets;
  }

  public DexType toBaseType(DexItemFactory dexItemFactory) {
    int leadingSquareBrackets = getNumberOfLeadingSquareBrackets();
    if (leadingSquareBrackets == 0) {
      return this;
    }
    DexString newDesc = dexItemFactory.createString(descriptor.size - leadingSquareBrackets,
        Arrays.copyOfRange(descriptor.content, leadingSquareBrackets, descriptor.content.length));
    return dexItemFactory.createType(newDesc);
  }

  public DexType replaceBaseType(DexType newBase, DexItemFactory dexItemFactory) {
    assert this.isArrayType();
    assert !newBase.isArrayType();
    int leadingSquareBrackets = getNumberOfLeadingSquareBrackets();
    byte[] content = new byte[newBase.descriptor.content.length + leadingSquareBrackets];
    Arrays.fill(content, 0, leadingSquareBrackets, (byte) '[');
    System.arraycopy(newBase.descriptor.content, 0, content, leadingSquareBrackets,
        newBase.descriptor.content.length);
    DexString newDesc = dexItemFactory
        .createString(newBase.descriptor.size + leadingSquareBrackets, content);
    return dexItemFactory.createType(newDesc);
  }

  public DexType toArrayElementType(DexItemFactory dexItemFactory) {
    assert this.isArrayType();
    DexString newDesc = dexItemFactory.createString(descriptor.size - 1,
        Arrays.copyOfRange(descriptor.content, 1, descriptor.content.length));
    return dexItemFactory.createType(newDesc);
  }

  private String getPackageOrName(boolean packagePart) {
    assert isClassType();
    String descriptor = toDescriptorString();
    int lastSeparator = descriptor.lastIndexOf('/');
    if (lastSeparator == -1) {
      return packagePart ? "" : descriptor.substring(1, descriptor.length() - 1);
    } else {
      return packagePart ? descriptor.substring(1, lastSeparator)
          : descriptor.substring(lastSeparator + 1, descriptor.length() - 1);
    }
  }

  public String getPackageDescriptor() {
    return getPackageOrName(true);
  }

  public String getName() {
    if (isPrimitiveType()) {
      return toSourceString();
    }
    return getPackageOrName(false);
  }

  /** Get the fully qualified name using '/' in place of '.', aka the "internal type name" in ASM */
  public String getInternalName() {
    assert isClassType() || isArrayType();
    return DescriptorUtils.descriptorToInternalName(toDescriptorString());
  }

  public String getPackageName() {
    return DescriptorUtils.getPackageNameFromBinaryName(toBinaryName());
  }
}

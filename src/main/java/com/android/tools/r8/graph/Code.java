// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.code.CfOrDexInstruction;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeInstructionMetadata;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumberGenerator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.origin.Origin;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;

public abstract class Code extends CachedHashValueDexItem {

  public abstract IRCode buildIR(ProgramMethod method, AppView<?> appView, Origin origin);

  public IRCode buildInliningIR(
      ProgramMethod context,
      ProgramMethod method,
      AppView<?> appView,
      GraphLens codeLens,
      NumberGenerator valueNumberGenerator,
      Position callerPosition,
      Origin origin,
      RewrittenPrototypeDescription protoChanges) {
    throw new Unreachable("Unexpected attempt to build IR graph for inlining from: "
        + getClass().getCanonicalName());
  }

  public BytecodeInstructionMetadata getMetadata(CfOrDexInstruction instruction) {
    return null;
  }

  public abstract void registerCodeReferences(ProgramMethod method, UseRegistry registry);

  public abstract void registerCodeReferencesForDesugaring(
      ClasspathMethod method, UseRegistry registry);

  public void registerArgumentReferences(DexEncodedMethod method, ArgumentUse registry) {
    throw new Unreachable();
  }

  public Int2ReferenceMap<DebugLocalInfo> collectParameterInfo(
      DexEncodedMethod encodedMethod, AppView<?> appView) {
    throw new Unreachable();
  }

  @Override
  public abstract String toString();

  public abstract String toString(DexEncodedMethod method, ClassNameMapper naming);

  public boolean isCfCode() {
    return false;
  }

  public boolean isCfWritableCode() {
    return false;
  }

  public boolean isDefaultInstanceInitializerCode() {
    return false;
  }

  public DefaultInstanceInitializerCode asDefaultInstanceInitializerCode() {
    return null;
  }

  public boolean isDexCode() {
    return false;
  }

  public boolean isDexWritableCode() {
    return false;
  }

  public boolean isHorizontalClassMergingCode() {
    return false;
  }

  public boolean isOutlineCode() {
    return false;
  }

  public boolean isSharedCodeObject() {
    return false;
  }

  public boolean isThrowNullCode() {
    return false;
  }

  public ThrowNullCode asThrowNullCode() {
    return null;
  }

  /** Estimate the number of IR instructions emitted by buildIR(). */
  public int estimatedSizeForInlining() {
    return Integer.MAX_VALUE;
  }

  /** Compute estimatedSizeForInlining() <= threshold. */
  public boolean estimatedSizeForInliningAtMost(int threshold) {
    return estimatedSizeForInlining() <= threshold;
  }

  public abstract int estimatedDexCodeSizeUpperBoundInBytes();

  public CfCode asCfCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asCfCode()");
  }

  public CfWritableCode asCfWritableCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asCfWritableCode()");
  }

  public LazyCfCode asLazyCfCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asLazyCfCode()");
  }

  public DexCode asDexCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asDexCode()");
  }

  public DexWritableCode asDexWritableCode() {
    throw new Unreachable(getClass().getCanonicalName() + ".asDexWritableCode()");
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection collection) {
    throw new Unreachable();
  }

  public abstract boolean isEmptyVoidMethod();

  public boolean verifyNoInputReaders() {
    return true;
  }

  public Code getCodeAsInlining(DexMethod caller, DexMethod callee) {
    throw new Unreachable();
  }
}

// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCodeStackMapValidatingException;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.InitClassLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import org.objectweb.asm.MethodVisitor;

public class CfJsrRet extends CfInstruction {

  private static CompilationError error() {
    throw new CompilationError(
        "Invalid compilation of code with reachable jump subroutine RET instruction");
  }

  private final int local;

  public CfJsrRet(int local) {
    this.local = local;
  }

  @Override
  public int getCompareToId() {
    throw error();
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    throw error();
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    throw error();
  }

  @Override
  public int bytecodeSizeUpperBound() {
    throw error();
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    throw error();
  }

  @Override
  public ConstraintWithTarget inliningConstraint(
      InliningConstraints inliningConstraints, CfCode code, ProgramMethod context) {
    throw error();
  }

  @Override
  public void evaluate(
      CfFrameVerificationHelper frameBuilder,
      DexMethod context,
      AppView<?> appView,
      DexItemFactory dexItemFactory) {
    // JSR/RET instructions cannot be verified since we have not type-checking way for addresses
    // on the stack/locals. We have to abandon.
    throw CfCodeStackMapValidatingException.error("Unexpected JSR/RET instruction");
  }

  @Override
  public CfFrameState evaluate(
      CfFrameState frame,
      CfCode code,
      ProgramMethod context,
      AppView<?> appView,
      DexItemFactory dexItemFactory) {
    return CfFrameState.error("Unexpected JSR/RET instruction");
  }

  public int getLocal() {
    return local;
  }
}

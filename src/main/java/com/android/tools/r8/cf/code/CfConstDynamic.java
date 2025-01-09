// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCompareHelper;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.InitClassLens;
import com.android.tools.r8.ir.conversion.CfSourceCode;
import com.android.tools.r8.ir.conversion.CfState;
import com.android.tools.r8.ir.conversion.IRBuilder;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.desugar.constantdynamic.ConstantDynamicReference;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.optimize.interfaces.analysis.CfAnalysisConfig;
import com.android.tools.r8.optimize.interfaces.analysis.CfFrameState;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import java.util.List;
import java.util.ListIterator;
import java.util.function.Supplier;
import org.objectweb.asm.ConstantDynamic;
import org.objectweb.asm.MethodVisitor;

public class CfConstDynamic extends CfInstruction implements CfTypeInstruction {

  private final ConstantDynamicReference reference;

  public CfConstDynamic(ConstantDynamicReference constantDynamicReference) {
    reference = constantDynamicReference;
  }

  @Override
  public CfConstDynamic asConstDynamic() {
    return this;
  }

  @Override
  public boolean isConstDynamic() {
    return true;
  }

  public ConstantDynamicReference getReference() {
    return reference;
  }

  public DexString getName() {
    return reference.getName();
  }

  public DexMethodHandle getBootstrapMethod() {
    return reference.getBootstrapMethod();
  }

  public List<DexValue> getBootstrapMethodArguments() {
    return reference.getBootstrapMethodArguments();
  }

  public static CfConstDynamic fromAsmConstantDynamic(
      ConstantDynamic insn,
      JarApplicationReader application,
      DexType clazz,
      Supplier<Reference2IntMap<ConstantDynamic>> constantDynamicSymbolicReferencesSupplier) {
    assert insn.getBootstrapMethodArgumentCount() == 0;
    return new CfConstDynamic(
        ConstantDynamicReference.fromAsmConstantDynamic(
            insn, application, clazz, constantDynamicSymbolicReferencesSupplier));
  }

  @Override
  public int getAsmOpcode() {
    return -1;
  }

  @Override
  public boolean hasAsmOpcode() {
    return false;
  }

  @Override
  public int getCompareToId() {
    return CfCompareHelper.CONST_DYNAMIC_COMPARE_ID;
  }

  @Override
  public int internalAcceptCompareTo(
      CfInstruction other, CompareToVisitor visitor, CfCompareHelper helper) {
    return reference.acceptCompareTo(((CfConstDynamic) other).reference, visitor);
  }

  @Override
  public void internalAcceptHashing(HashingVisitor visitor) {
    reference.acceptHashing(visitor);
  }

  @Override
  public CfTypeInstruction asTypeInstruction() {
    return this;
  }

  @Override
  public boolean isTypeInstruction() {
    return true;
  }

  @Override
  public DexType getType() {
    return reference.getType();
  }

  @Override
  public CfInstruction withType(DexType newType) {
    throw new Unimplemented();
  }

  @Override
  public void write(
      AppView<?> appView,
      ProgramMethod context,
      DexItemFactory dexItemFactory,
      GraphLens graphLens,
      GraphLens codeLens,
      InitClassLens initClassLens,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor) {
    DexMethodHandle rewrittenHandle =
        rewriter.rewriteDexMethodHandle(
            reference.getBootstrapMethod(), NOT_ARGUMENT_TO_LAMBDA_METAFACTORY, context);
    List<DexValue> rewrittenArguments =
        rewriter.rewriteBootstrapArguments(
            reference.getBootstrapMethodArguments(), NOT_ARGUMENT_TO_LAMBDA_METAFACTORY, context);
    Object[] bsmArgs = new Object[rewrittenArguments.size()];
    for (int i = 0; i < rewrittenArguments.size(); i++) {
      bsmArgs[i] =
          CfInvokeDynamic.decodeBootstrapArgument(
              rewrittenArguments.get(i), namingLens, dexItemFactory);
    }
    ConstantDynamic constantDynamic =
        new ConstantDynamic(
            reference.getName().toString(),
            getConstantTypeDescriptor(graphLens, codeLens, namingLens, dexItemFactory),
            rewrittenHandle.toAsmHandle(namingLens),
            bsmArgs);
    visitor.visitLdcInsn(constantDynamic);
  }

  private String getConstantTypeDescriptor(
      GraphLens graphLens, GraphLens codeLens, NamingLens namingLens, DexItemFactory factory) {
    DexType rewrittenType = graphLens.lookupType(reference.getType(), codeLens);
    DexType renamedType = namingLens.lookupType(rewrittenType, factory);
    return renamedType.toDescriptorString();
  }

  @Override
  public int bytecodeSizeUpperBound() {
    // ldc or ldc_w
    return 3;
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  @Override
  public boolean canThrow() {
    return true;
  }

  @Override
  void internalRegisterUse(
      UseRegistry<?> registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerTypeReference(reference.getType());
    registry.registerMethodHandle(
        reference.getBootstrapMethod(), NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
    assert reference.getBootstrapMethodArguments().isEmpty();
  }

  @Override
  public void buildIR(IRBuilder builder, CfState state, CfSourceCode code) {
    throw new CompilationError("Unsupported dynamic constant (not desugaring)");
  }

  @Override
  public CfFrameState evaluate(CfFrameState frame, AppView<?> appView, CfAnalysisConfig config) {
    // ... →
    // ..., value
    return frame.push(config, appView.dexItemFactory().classType);
  }
}

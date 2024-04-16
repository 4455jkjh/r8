// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.verticalclassmerging;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.classmerging.ClassMergerMode;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathMethod;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions.MutableMethodConversionOptions;
import com.android.tools.r8.lightir.LirBuilder;
import com.android.tools.r8.lightir.LirCode;
import com.android.tools.r8.lightir.LirEncodingStrategy;
import com.android.tools.r8.lightir.LirStrategy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.RetracerForCodePrinting;
import java.util.ArrayList;
import java.util.List;

/**
 * A short-lived piece of code that will be converted into {@link LirCode} using {@link
 * #toLirCode(AppView, VerticalClassMergerGraphLens, ClassMergerMode)}.
 */
public class IncompleteVerticalClassMergerBridgeCode extends Code {

  private DexMethod method;
  private DexMethod invocationTarget;
  private final InvokeType type;
  private final boolean isInterface;

  public IncompleteVerticalClassMergerBridgeCode(
      DexMethod method, InvokeType type, boolean isInterface) {
    this.method = method;
    this.invocationTarget = null;
    this.type = type;
    this.isInterface = isInterface;
  }

  @Override
  public Code getCodeAsInlining(
      DexMethod caller,
      boolean isCallerD8R8Synthesized,
      DexMethod callee,
      boolean isCalleeD8R8Synthesized,
      DexItemFactory factory) {
    // This code object is synthesized so "inlining" just "strips" the callee position.
    assert isCalleeD8R8Synthesized;
    return this;
  }

  public DexMethod getMethod() {
    return method;
  }

  public DexMethod getTarget() {
    return invocationTarget;
  }

  // By the time the synthesized code object is created, vertical class merging still has not
  // finished. Therefore it is possible that the method signatures `method` and `invocationTarget`
  // will change as a result of additional class merging operations. To deal with this, the
  // vertical class merger explicitly invokes this method to update `method` and `invocation-
  // Target` when vertical class merging has finished.
  //
  // Note that, without this step, these method signatures might refer to intermediate signatures
  // that are only present in the middle of vertical class merging, which means that the graph
  // lens will not work properly (since the graph lens generated by vertical class merging only
  // expects to be applied to method signatures from *before* vertical class merging or *after*
  // vertical class merging).
  public void updateMethodSignatures(VerticalClassMergerGraphLens lens) {
    invocationTarget = lens.getNextImplementationMethodSignature(method);
    method = lens.getNextBridgeMethodSignature(method);
  }

  public LirCode<?> toLirCode(AppView<AppInfoWithLiveness> appView) {
    boolean isD8R8Synthesized = true;
    LirEncodingStrategy<Value, Integer> strategy =
        LirStrategy.getDefaultStrategy().getEncodingStrategy();
    LirBuilder<Value, Integer> lirBuilder =
        LirCode.builder(method, isD8R8Synthesized, strategy, appView.options());

    // Add all arguments.
    List<Value> argumentValues = new ArrayList<>();
    int instructionIndex = 0;
    for (; instructionIndex < method.getNumberOfArgumentsForNonStaticMethod(); instructionIndex++) {
      DexType argumentType = method.getArgumentTypeForNonStaticMethod(instructionIndex);
      TypeElement argumentTypeElement =
          argumentType.toTypeElement(
              appView, instructionIndex == 0 ? definitelyNotNull() : maybeNull());
      Value argumentValue = Value.createNoDebugLocal(instructionIndex, argumentTypeElement);
      argumentValues.add(argumentValue);
      strategy.defineValue(argumentValue, argumentValue.getNumber());
      lirBuilder.addArgument(instructionIndex, argumentType.isBooleanType());
    }

    if (type.isStatic()) {
      lirBuilder.addInvokeStatic(invocationTarget, argumentValues, isInterface);
    } else if (isInterface) {
      lirBuilder.addInvokeInterface(invocationTarget, argumentValues);
    } else {
      lirBuilder.addInvokeVirtual(invocationTarget, argumentValues);
    }

    if (method.getReturnType().isVoidType()) {
      lirBuilder.addReturnVoid();
    } else {
      Value returnValue =
          Value.createNoDebugLocal(instructionIndex, method.getReturnType().toTypeElement(appView));
      strategy.defineValue(returnValue, returnValue.getNumber());
      lirBuilder.addReturn(returnValue);
    }

    return lirBuilder.build();
  }

  // Implement Code.

  @Override
  public IRCode buildIR(
      ProgramMethod method,
      AppView<?> appView,
      MutableMethodConversionOptions conversionOptions) {
    throw new Unreachable();
  }

  @Override
  protected boolean computeEquals(Object other) {
    throw new Unreachable();
  }

  @Override
  protected int computeHashCode() {
    throw new Unreachable();
  }

  @Override
  public int estimatedDexCodeSizeUpperBoundInBytes() {
    throw new Unreachable();
  }

  @Override
  public boolean isEmptyVoidMethod() {
    throw new Unreachable();
  }

  @Override
  public void registerCodeReferences(ProgramMethod method, UseRegistry registry) {
    throw new Unreachable();
  }

  @Override
  public void registerCodeReferencesForDesugaring(ClasspathMethod method, UseRegistry registry) {
    throw new Unreachable();
  }

  @Override
  public String toString() {
    return "IncompleteVerticalClassMergerBridgeCode";
  }

  @Override
  public String toString(DexEncodedMethod method, RetracerForCodePrinting retracer) {
    return toString();
  }
}

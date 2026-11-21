// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.utils.internal.BooleanBox;
import java.util.List;

public class MoveResultRewriter extends CodeRewriterPass<AppInfo> {

  public MoveResultRewriter(AppView<?> appView) {
    super(appView);
  }

  @Override
  protected String getRewriterId() {
    return "MoveResultRewriter";
  }

  @Override
  protected boolean shouldRewriteCode(IRCode code, MethodProcessor methodProcessor) {
    return options.isGeneratingDex() && code.metadata().mayHaveInvokeMethod();
  }

  // Replace result uses for methods where something is known about what is returned.
  @SuppressWarnings("ReferenceEquality")
  @Override
  protected CodeRewriterResult rewriteCode(IRCode code) {
    boolean changed = false;
    boolean mayHaveRemovedTrivialPhi = false;
    AffectedValues affectedValues = null;
    TypeAnalysis typeAnalysis = null;
    for (BasicBlock block : code.blocks) {
      InstructionListIterator iterator = block.listIterator();
      while (iterator.hasNext()) {
        InvokeMethod invoke = iterator.next().asInvokeMethod();
        if (invoke == null
            || !invoke.hasOutValue()
            || invoke.outValue().hasLocalInfo()
            || !hasMatchingArgumentType(invoke)) {
          continue;
        }

        // Check if the invoked method is known to return one of its arguments.
        DexClassAndMethod target = invoke.lookupSingleTarget(appView, code.context());
        if (target == null) {
          continue;
        }

        MethodOptimizationInfo optimizationInfo = target.getDefinition().getOptimizationInfo();
        if (!optimizationInfo.returnsArgument()) {
          continue;
        }

        int argumentIndex = optimizationInfo.getReturnedArgument();
        // Replace the out value of the invoke with the argument and ignore the out value.
        if (argumentIndex < 0 || !checkArgumentType(invoke, argumentIndex)) {
          continue;
        }

        Value argument = invoke.arguments().get(argumentIndex);
        Value outValue = invoke.outValue();
        assert outValue.verifyCompatible(argument.outType());

        // Make sure that we are only narrowing information here. Note, in cases where we cannot
        // find the definition of types, computing lessThanOrEqual will return false unless it is
        // object.
        if (!argument.getType().lessThanOrEqual(outValue.getType(), appView)) {
          continue;
        }

        mayHaveRemovedTrivialPhi |= outValue.hasPhiUsers();
        invoke.clearOutValue();
        changed = true;
        if (argument.getType().equals(outValue.getType())) {
          outValue.replaceUsers(argument);
        } else {
          affectedValues = getOrCreateAffectedValues(affectedValues);
          outValue.replaceUsers(argument, affectedValues);
          if (!affectedValues.isEmpty()) {
            BooleanBox removedAssumeInstructionInCurrentBlock = new BooleanBox();
            typeAnalysis = getOrCreateTypeAnalysis(typeAnalysis, code);
            typeAnalysis.narrowingWithAssumeRemoval(
                affectedValues,
                assume -> removedAssumeInstructionInCurrentBlock.or(assume.getBlock() == block));
            affectedValues.clear();
            if (removedAssumeInstructionInCurrentBlock.isTrue()) {
              // Workaround ConcurrentModificationException.
              iterator = block.listIterator();
            }
          }
        }
      }
    }
    if (changed) {
      if (mayHaveRemovedTrivialPhi) {
        affectedValues = getOrCreateAffectedValues(affectedValues);
        code.removeAllDeadAndTrivialPhis(affectedValues);
        if (!affectedValues.isEmpty()) {
          typeAnalysis = getOrCreateTypeAnalysis(typeAnalysis, code);
          typeAnalysis.narrowingWithAssumeRemoval(affectedValues);
        }
      }
      code.removeRedundantBlocks();
    }
    return CodeRewriterResult.hasChanged(changed);
  }

  private static AffectedValues getOrCreateAffectedValues(AffectedValues affectedValues) {
    return affectedValues != null ? affectedValues : new AffectedValues();
  }

  private TypeAnalysis getOrCreateTypeAnalysis(TypeAnalysis typeAnalysis, IRCode code) {
    return typeAnalysis != null
        ? typeAnalysis
        : new TypeAnalysis(appView, code).setKeepRedundantBlocksAfterAssumeRemoval(true);
  }

  // Prefilter: skip target lookup when no argument has a value type compatible with the return
  // type.
  private static boolean hasMatchingArgumentType(InvokeMethod invoke) {
    TypeElement outType = invoke.outValue().getType();
    List<Value> arguments = invoke.arguments();
    for (int i = 0; i < arguments.size(); i++) {
      if (arguments.get(i).getType().isValueTypeCompatible(outType)) {
        return true;
      }
    }
    return false;
  }

  private boolean checkArgumentType(InvokeMethod invoke, int argumentIndex) {
    // TODO(sgjesse): Insert cast if required.
    TypeElement returnType =
        TypeElement.fromDexType(invoke.getInvokedMethod().proto.returnType, maybeNull(), appView);
    TypeElement argumentType =
        TypeElement.fromDexType(getArgumentType(invoke, argumentIndex), maybeNull(), appView);
    return appView.enableWholeProgramOptimizations()
        ? argumentType.lessThanOrEqual(returnType, appView)
        : argumentType.equals(returnType);
  }

  private DexType getArgumentType(InvokeMethod invoke, int argumentIndex) {
    if (invoke.isInvokeStatic()) {
      return invoke.getInvokedMethod().proto.parameters.values[argumentIndex];
    }
    if (argumentIndex == 0) {
      return invoke.getInvokedMethod().holder;
    }
    return invoke.getInvokedMethod().proto.parameters.values[argumentIndex - 1];
  }
}

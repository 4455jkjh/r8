// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info.bridge;

import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.GOTO;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.code.AliasedValueConfiguration;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Goto;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.internal.BooleanUtils;
import com.android.tools.r8.utils.internal.SetUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import java.util.Set;

public class BridgeAnalyzer {

  /** Returns a {@link BridgeInfo} object describing this method if it is recognized as a bridge. */
  public static BridgeInfo analyzeMethod(AppView<?> appView, DexEncodedMethod method, IRCode code) {
    DexItemFactory factory = appView.dexItemFactory();
    // The main method of lambda classes forward all arguments except the receiver (i.e., the lambda
    // instance) to the synthetic javac lambda$ method using an invoke-static. If the receiver is
    // unused, we require that argument i is passed as argument i-1 to the invoke-static for i>0.
    int forwardArgumentOffset = method.isInstance() && code.getThis().isUnused() ? -1 : 0;
    // Scan through the instructions one-by-one. We expect a sequence of Argument instructions,
    // followed by a (possibly empty) sequence of CheckCast/Box/Unbox operations followed by a
    // single InvokeMethod instruction, followed by an optional CheckCast/Box/Unbox instruction,
    // followed by a Return instruction.
    InvokeMethod uniqueInvoke = null;
    // The unique cast, box, or unbox operation prior to the return instruction.
    Instruction uniqueReturnCheckCastBoxOrUnbox = null;
    InstructionListIterator instructionIterator = code.entryBlock().listIterator();
    Set<BasicBlock> seenBlocks = null;
    while (instructionIterator.hasNext()) {
      Instruction instruction = instructionIterator.next();
      switch (instruction.opcode()) {
        case ARGUMENT:
        case ASSUME:
          break;

        case CHECK_CAST:
          {
            if (!analyzeCheckCastBoxOrUnbox(
                method, instruction, uniqueInvoke, forwardArgumentOffset)) {
              return failure();
            }
            // If we have moved past the single invoke instruction, then record that this cast is
            // the cast instruction for the result value.
            if (uniqueInvoke != null) {
              if (uniqueReturnCheckCastBoxOrUnbox != null) {
                return failure();
              }
              uniqueReturnCheckCastBoxOrUnbox = instruction;
            }
            break;
          }

        case INVOKE_STATIC:
        case INVOKE_VIRTUAL:
          {
            InvokeMethod invoke = instruction.asInvokeMethod();
            if (isBoxingInvoke(invoke, factory) || isUnboxingInvoke(invoke, factory)) {
              if (!analyzeCheckCastBoxOrUnbox(
                  method, instruction, uniqueInvoke, forwardArgumentOffset)) {
                return failure();
              }
              // If we have moved past the single invoke instruction, then record that this boxing
              // is the boxing instruction for the result value.
              if (uniqueInvoke != null) {
                if (uniqueReturnCheckCastBoxOrUnbox != null) {
                  return failure();
                }
                uniqueReturnCheckCastBoxOrUnbox = invoke;
              }
              break;
            } else {
              // Intentionally fall through.
            }
          }

        // fall through
        case INVOKE_DIRECT:
        case INVOKE_SUPER:
          {
            if (uniqueInvoke != null) {
              return failure();
            }
            InvokeMethod invoke = instruction.asInvokeMethod();
            if (!analyzeInvoke(invoke, factory, forwardArgumentOffset)) {
              return failure();
            }
            // Record that we have seen the single invoke instruction.
            uniqueInvoke = invoke;
            break;
          }

        case GOTO:
          {
            Goto gotoInstruction = instruction.asGoto();
            BasicBlock targetBlock = gotoInstruction.getTarget();
            if (targetBlock.hasCatchHandlers()) {
              return failure();
            }
            if (seenBlocks == null) {
              assert gotoInstruction.getBlock().isEntry();
              seenBlocks = SetUtils.newIdentityHashSet(code.entryBlock());
            }
            if (!seenBlocks.add(targetBlock)) {
              return failure();
            }
            instructionIterator = targetBlock.listIterator();
            break;
          }

        case RETURN:
          if (!analyzeReturn(
              instruction.asReturn(), uniqueInvoke, uniqueReturnCheckCastBoxOrUnbox)) {
            return failure();
          }
          break;

        default:
          return failure();
      }
    }

    assert uniqueInvoke != null;
    assert uniqueInvoke.isInvokeDirect()
        || uniqueInvoke.isInvokeStatic()
        || uniqueInvoke.isInvokeSuper()
        || uniqueInvoke.isInvokeVirtual();
    switch (uniqueInvoke.getType()) {
      case DIRECT:
        if (forwardArgumentOffset != 0) {
          return null;
        }
        return new DirectBridgeInfo(uniqueInvoke.getInvokedMethod());
      case STATIC:
        return forwardArgumentOffset == 0
            ? new StaticBridgeInfo(uniqueInvoke.getInvokedMethod())
            : new StaticBridgeExcludingReceiverInfo(
                uniqueInvoke.getInvokedMethod(), uniqueInvoke.getInterfaceBit());
      case SUPER:
        if (forwardArgumentOffset != 0) {
          return null;
        }
        return new SuperBridgeInfo(uniqueInvoke.getInvokedMethod());
      case VIRTUAL:
        if (forwardArgumentOffset != 0) {
          return null;
        }
        return new VirtualBridgeInfo(uniqueInvoke.getInvokedMethod());
      default:
        throw new Unreachable();
    }
  }

  private static boolean analyzeCheckCastBoxOrUnbox(
      DexEncodedMethod method,
      Instruction checkCastBoxOrUnbox,
      InvokeMethod invoke,
      int forwardArgumentOffset) {
    return invoke == null
        ? analyzeCheckCastBoxOrUnboxBeforeInvoke(checkCastBoxOrUnbox, forwardArgumentOffset)
        : analyzeCheckCastBoxOrUnboxAfterInvoke(method, checkCastBoxOrUnbox, invoke);
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean analyzeCheckCastBoxOrUnboxBeforeInvoke(
      Instruction checkCastBoxOrUnbox, int forwardArgumentOffset) {
    Value object = checkCastBoxOrUnbox.getFirstOperand().getAliasedValue();
    // It must be processing one of the arguments.
    if (!object.isArgument()) {
      return false;
    }
    int argumentIndex = object.getDefinition().asArgument().getIndex();
    Value outValue = checkCastBoxOrUnbox.outValue();
    if (outValue == null) {
      return false;
    }
    // The out value should not have any phi users since we only allow linear control flow.
    if (outValue.hasPhiUsers()) {
      return false;
    }
    // It is not allowed to have any other users than the invoke instruction.
    if (outValue.hasDebugUsers() || !outValue.hasSingleUniqueUser()) {
      return false;
    }
    InvokeMethod invoke = outValue.singleUniqueUser().asInvokeMethod();
    if (invoke == null) {
      return false;
    }
    // The cast value must be used in the same argument position, unless the `this` value is unused.
    int expectedForwardArgumentIndex = argumentIndex + forwardArgumentOffset;
    if (invoke.arguments().size() <= expectedForwardArgumentIndex
        || invoke.getArgument(expectedForwardArgumentIndex) != outValue) {
      return false;
    }
    int forwardParameterIndex =
        expectedForwardArgumentIndex - BooleanUtils.intValue(invoke.isInvokeMethodWithReceiver());
    // It is not allowed to cast the receiver.
    if (forwardParameterIndex == -1) {
      return false;
    }
    // The type of the cast must match the type of the parameter.
    if (checkCastBoxOrUnbox.isCheckCast()) {
      if (checkCastBoxOrUnbox.asCheckCast().getType()
          != invoke.getInvokedMethod().getParameter(forwardParameterIndex)) {
        return false;
      }
    } else {
      InvokeMethod boxOrUnbox = checkCastBoxOrUnbox.asInvokeMethod();
      if (boxOrUnbox.getInvokedMethod().getReturnType()
          != invoke.getInvokedMethod().getParameter(forwardParameterIndex)) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean analyzeCheckCastBoxOrUnboxAfterInvoke(
      DexEncodedMethod method, Instruction checkCastBoxOrUnbox, InvokeMethod invoke) {
    Value returnValue = invoke.outValue();
    Value uncastValue = checkCastBoxOrUnbox.getFirstOperand().getAliasedValue();
    Value outValue = checkCastBoxOrUnbox.outValue();
    if (outValue == null) {
      return false;
    }
    // The out value should not have any phi users since we only allow linear control flow.
    if (outValue.hasPhiUsers()) {
      return false;
    }
    // It must cast/box/unbox the result to the return type of the enclosing method.
    if (checkCastBoxOrUnbox.isCheckCast()) {
      if (checkCastBoxOrUnbox.asCheckCast().getType() != method.getReturnType()) {
        return false;
      }
    } else {
      InvokeMethod boxOrUnbox = checkCastBoxOrUnbox.asInvokeMethod();
      if (boxOrUnbox.getInvokedMethod().getReturnType() != method.getReturnType()) {
        return false;
      }
    }
    // It must return the cast/box/unbox value.
    return uncastValue == returnValue
        && !outValue.hasDebugUsers()
        && outValue.hasSingleUniqueUser()
        && outValue.singleUniqueUser().isReturn();
  }

  private static boolean analyzeInvoke(
      InvokeMethod invoke, DexItemFactory factory, int forwardArgumentOffset) {
    // All of the forwarded arguments of the enclosing method must be in the same argument position.
    for (int argumentIndex = 0; argumentIndex < invoke.arguments().size(); argumentIndex++) {
      Value argument = invoke.getArgument(argumentIndex).getAliasedValue();
      if (argument.isPhi()) {
        return false;
      } else if (argument.isArgument()) {
        int incomingArgumentIndex = argument.getDefinition().asArgument().getIndex();
        if (argumentIndex != incomingArgumentIndex + forwardArgumentOffset) {
          return false;
        }
      } else if (isCheckCastBoxOrUnbox(argument.getDefinition(), factory)) {
        int incomingArgumentIndex =
            argument
                .getDefinition()
                .getFirstOperand()
                .getAliasedValue()
                .getDefinition()
                .asArgument()
                .getIndex();
        if (argumentIndex != incomingArgumentIndex + forwardArgumentOffset) {
          return false;
        }
      }
      // Validate that besides argument values only check-cast of argument values are allowed at
      // their argument position.
      assert argument.isArgument()
          || (isCheckCastBoxOrUnbox(argument.getDefinition(), factory)
              && invoke
                  .getArgument(argumentIndex)
                  .getAliasedValue(new BridgeAnalyzerAliasedValueConfiguration(factory))
                  .isArgument()
              && invoke
                          .getArgument(argumentIndex)
                          .getAliasedValue(new BridgeAnalyzerAliasedValueConfiguration(factory))
                          .getDefinition()
                          .asArgument()
                          .getIndex()
                      + forwardArgumentOffset
                  == argumentIndex);
    }
    return true;
  }

  private static boolean analyzeReturn(
      Return ret, InvokeMethod invoke, Instruction returnCheckCastBoxOrUnbox) {
    // If we haven't seen an invoke this is not a bridge.
    if (invoke == null) {
      return false;
    }
    // It must not return a value, or the return value must be the result value of the invoke.
    return ret.isReturnVoid()
        || ret.returnValue().getAliasedValue()
            == (returnCheckCastBoxOrUnbox != null ? returnCheckCastBoxOrUnbox : invoke).outValue();
  }

  private static BridgeInfo failure() {
    return null;
  }

  private static boolean isCheckCastBoxOrUnbox(Instruction instruction, DexItemFactory factory) {
    if (instruction.isCheckCast()) {
      return true;
    }
    if (instruction.isInvokeMethod()) {
      InvokeMethod invoke = instruction.asInvokeMethod();
      return isBoxingInvoke(invoke, factory) || isUnboxingInvoke(invoke, factory);
    }
    return false;
  }

  private static boolean isBoxingInvoke(InvokeMethod invoke, DexItemFactory factory) {
    return factory.boxPrimitiveMethods.contains(invoke.getInvokedMethod());
  }

  private static boolean isUnboxingInvoke(InvokeMethod invoke, DexItemFactory factory) {
    return factory.unboxPrimitiveMethods.contains(invoke.getInvokedMethod());
  }

  private static class BridgeAnalyzerAliasedValueConfiguration
      implements AliasedValueConfiguration {

    private final DexItemFactory factory;

    private BridgeAnalyzerAliasedValueConfiguration(DexItemFactory factory) {
      this.factory = factory;
    }

    @Override
    public boolean isIntroducingAnAlias(Instruction instruction) {
      if (instruction.isAssume() || instruction.isCheckCast()) {
        return true;
      }
      if (instruction.isInvokeMethod()) {
        InvokeMethod invoke = instruction.asInvokeMethod();
        return isBoxingInvoke(invoke, factory) || isUnboxingInvoke(invoke, factory);
      }
      return false;
    }

    @Override
    public Value getAliasForOutValue(Instruction instruction) {
      return instruction.getFirstOperand();
    }
  }
}

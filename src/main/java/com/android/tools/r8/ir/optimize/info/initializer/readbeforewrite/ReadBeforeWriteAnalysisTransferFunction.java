// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info.initializer.readbeforewrite;

import static com.android.tools.r8.graph.ProgramField.asProgramFieldOrNull;
import static com.android.tools.r8.ir.code.Opcodes.ADD;
import static com.android.tools.r8.ir.code.Opcodes.AND;
import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_GET;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_LENGTH;
import static com.android.tools.r8.ir.code.Opcodes.ARRAY_PUT;
import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.CMP;
import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.DIV;
import static com.android.tools.r8.ir.code.Opcodes.GOTO;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.INC;
import static com.android.tools.r8.ir.code.Opcodes.INIT_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_OF;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INT_SWITCH;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.MUL;
import static com.android.tools.r8.ir.code.Opcodes.NEG;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_EMPTY;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_FILLED;
import static com.android.tools.r8.ir.code.Opcodes.NEW_INSTANCE;
import static com.android.tools.r8.ir.code.Opcodes.NOT;
import static com.android.tools.r8.ir.code.Opcodes.NUMBER_CONVERSION;
import static com.android.tools.r8.ir.code.Opcodes.OR;
import static com.android.tools.r8.ir.code.Opcodes.REM;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.SHL;
import static com.android.tools.r8.ir.code.Opcodes.SHR;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;
import static com.android.tools.r8.ir.code.Opcodes.STRING_SWITCH;
import static com.android.tools.r8.ir.code.Opcodes.SUB;
import static com.android.tools.r8.ir.code.Opcodes.THROW;
import static com.android.tools.r8.ir.code.Opcodes.USHR;
import static com.android.tools.r8.ir.code.Opcodes.XOR;
import static com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo.getMethodOptimizationInfoOrDefault;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.AbstractFieldSet;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.AbstractTransferFunction;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.DataflowAnalysisResult.FailedDataflowAnalysisResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.FailedTransferFunctionResult;
import com.android.tools.r8.ir.analysis.framework.intraprocedural.TransferFunctionResult;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.info.initializer.InstanceInitializerInfo;
import com.android.tools.r8.utils.internal.SetUtils;
import com.android.tools.r8.utils.internal.TraversalContinuation;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

public class ReadBeforeWriteAnalysisTransferFunction
    implements AbstractTransferFunction<BasicBlock, Instruction, ReadBeforeWriteAnalysisState> {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final ProgramMethod context;
  private final DexItemFactory factory;
  private final ReadBeforeWriteAnalysisStateFactory stateFactory;
  private final Value thisValue;

  ReadBeforeWriteAnalysisTransferFunction(
      AppView<? extends AppInfoWithClassHierarchy> appView, IRCode code) {
    this.appView = appView;
    this.context = code.context();
    this.factory = appView.dexItemFactory();
    this.stateFactory = new ReadBeforeWriteAnalysisStateFactory(code);
    this.thisValue = code.entryBlock().entry().outValue();
  }

  @Override
  public TransferFunctionResult<ReadBeforeWriteAnalysisState> apply(
      Instruction instruction, ReadBeforeWriteAnalysisState state) {
    if (state.readBeforeWriteSet == null) {
      // The receiver has escaped and all of its instance fields may have been read before they are
      // written. We maintain the `writtenBeforeReadSet` if it is not empty, otherwise bail out.
      return preserveWrittenBeforeReadSetOrFail(state);
    }
    switch (instruction.opcode()) {
      case ARGUMENT:
      case RETURN:
        return state;

      // Arrays.
      case ARRAY_GET:
      case ARRAY_LENGTH:
      case NEW_ARRAY_EMPTY:
        return state;

      case ARRAY_PUT:
        {
          if (!state.isThisInitialized) {
            // It is not possible to read from the uninitialized `this`.
            assert !state.isThisEscaped;
            return state;
          }
          ArrayPut arrayPut = instruction.asArrayPut();
          if (state.isMaybeThis(thisValue, arrayPut.value())) {
            return state.withEscapedThis(stateFactory);
          }
          return state;
        }

      case NEW_ARRAY_FILLED:
        {
          if (!state.isThisInitialized) {
            // It is not possible to read from the uninitialized `this`.
            assert !state.isThisEscaped;
            return state;
          }
          NewArrayFilled newArrayFilled = instruction.asNewArrayFilled();
          for (Value argument : newArrayFilled.arguments()) {
            if (state.isMaybeThis(thisValue, argument)) {
              return state.withEscapedThis(stateFactory);
            }
          }
          return state;
        }

      // Constants.
      case CONST_CLASS:
      case CONST_NUMBER:
      case CONST_STRING:
        return state;

      // Unop.
      case INC:
      case NEG:
      case NOT:
      case NUMBER_CONVERSION:
        return state;

      // Arithmetic binop.
      case ADD:
      case DIV:
      case MUL:
      case REM:
      case SUB:
        return state;

      // Logical binop.
      case AND:
      case OR:
      case SHL:
      case SHR:
      case USHR:
      case XOR:
        return state;

      // Control flow.
      case GOTO:
      case IF:
      case INT_SWITCH:
      case STRING_SWITCH:
        return state;

      case THROW:
        // If this analysis is extended to catch handlers, we should mark the corresponding
        // move-exception out-value as an alias of `this` if the thrown value is in `thisAliases`.
        assert !instruction.getBlock().hasCatchHandlers();
        return state;

      // Aliasing.
      case ASSUME:
      case CHECK_CAST:
        {
          if (state.isMaybeThis(thisValue, instruction.getFirstOperand())) {
            return state.joinThisAlias(instruction.outValue(), stateFactory);
          }
          return state;
        }

      case INSTANCE_GET:
        {
          if (!state.isThisInitialized) {
            // It is not possible to read from the uninitialized `this`.
            assert !state.isThisEscaped;
            return state;
          }
          InstanceGet instanceGet = instruction.asInstanceGet();
          ProgramField resolvedField =
              asProgramFieldOrNull(instanceGet.resolveField(appView, context).getResolutionPair());
          if (resolvedField == null) {
            return state;
          }
          if (state.writtenBeforeReadSet.contains(resolvedField.getDefinition())) {
            // This field is definitely assigned at this point, so don't add it to the
            // read-before-write set.
            return state;
          }
          if (state.isMaybeThis(thisValue, instanceGet.object())) {
            return state.joinReadBeforeWrite(resolvedField.getDefinition(), stateFactory);
          }
          if (state.isThisEscaped
              && !instanceGet
                  .object()
                  .isDefinedByInstructionSatisfying(Instruction::isNewInstance)) {
            return state.joinReadBeforeWrite(resolvedField.getDefinition(), stateFactory);
          }
          return state;
        }

      case INSTANCE_PUT:
        {
          InstancePut instancePut = instruction.asInstancePut();
          if (state.isThisInitialized && state.isMaybeThis(thisValue, instancePut.value())) {
            return state.withEscapedThis(stateFactory);
          }
          // Check if this is a definite assignment to an instance field on this, which has not
          // previously been read.
          ProgramField resolvedField =
              instancePut.resolveField(appView, context).getSingleProgramField();
          if (resolvedField != null
              && state.isDefinitelyThis(thisValue, instancePut.object())
              && !state.readBeforeWriteSet.contains(resolvedField.getDefinition())) {
            return state.joinWrittenBeforeRead(resolvedField.getDefinition(), stateFactory);
          }
          return state;
        }

      case INVOKE_DIRECT:
      case INVOKE_INTERFACE:
      case INVOKE_STATIC:
      case INVOKE_SUPER:
      case INVOKE_VIRTUAL:
        {
          InvokeMethod invoke = instruction.asInvokeMethod();
          if (invoke.isInvokeConstructor(factory) && invoke.getFirstArgument().isThis()) {
            SingleResolutionResult<?> resolutionResult =
                invoke.resolveMethod(appView, context).asSingleResolution();
            if (resolutionResult == null) {
              return preserveWrittenBeforeReadSetOrFail(state);
            }
            DexClassAndMethod singleTarget =
                resolutionResult
                    .lookupDispatchTarget(appView, invoke, context)
                    .getSingleDispatchTarget();
            if (singleTarget == null) {
              return preserveWrittenBeforeReadSetOrFail(state);
            }
            if (singleTarget.isLibraryMethod()) {
              // In principle this library constructor could call into the app and the app could
              // read a field on a subclass of `this` before the field has been assigned in the
              // subclass. This is a contrived example so we assume this does not happen by
              // modeling the (currently empty) set of library classes where this does not hold.
              return state.withInitializedThis(stateFactory);
            }
            InstanceInitializerInfo instanceInitializerInfo =
                singleTarget
                    .getOptimizationInfo()
                    .getInstanceInitializerInfo(invoke.asInvokeDirect());
            AbstractFieldSet readBeforeWriteSet = instanceInitializerInfo.readBeforeWriteSet();
            if (readBeforeWriteSet.isTop()) {
              return preserveWrittenBeforeReadSetOrFail(state);
            }
            // The `this` cannot escape until it has been initialized.
            assert !state.isThisEscaped;
            assert !state.isThisInitialized;
            boolean newIsThisEscaped =
                invoke.hasNonReceiverArgumentThatMatches(arg -> state.isMaybeThis(thisValue, arg))
                    || instanceInitializerInfo.receiverMayEscapeOutsideConstructorChain();
            boolean newIsThisInitialized = true;
            Set<DexEncodedField> newReadBeforeWriteSet;
            if (readBeforeWriteSet.isConcreteFieldSet()) {
              newReadBeforeWriteSet =
                  ReadBeforeWriteAnalysisState.joinIdentitySets(
                      state.readBeforeWriteSet,
                      readBeforeWriteSet.asConcreteFieldSet().getFields());
              // Remove fields from the read-before-write set that are definitely written before the
              // constructor call.
              if (Iterables.any(state.writtenBeforeReadSet, newReadBeforeWriteSet::contains)) {
                newReadBeforeWriteSet = SetUtils.newIdentityHashSet(newReadBeforeWriteSet);
                newReadBeforeWriteSet.removeAll(state.writtenBeforeReadSet);
              }
            } else {
              assert readBeforeWriteSet.isEmpty();
              newReadBeforeWriteSet = state.readBeforeWriteSet;
            }
            return stateFactory.create(
                newIsThisEscaped,
                newIsThisInitialized,
                newReadBeforeWriteSet,
                state.writtenBeforeReadSet,
                state.thisAliases);
          }
          if (!state.isThisInitialized) {
            // The uninitialized `this` cannot escape.
            assert !state.isThisEscaped;
            return state;
          }
          boolean newIsThisEscaped =
              state.isThisEscaped
                  || invoke.hasArgumentThatMatches(arg -> state.isMaybeThis(thisValue, arg));
          if (!newIsThisEscaped) {
            // Since `this` has not escaped, the callee cannot read any fields on `this`.
            return state;
          }
          // The invoked method could do whatever. It is quite common to call simple constructors
          // that don't read any fields. Check for that here.
          boolean mayReadAnyField;
          if (invoke.isInvokeConstructor(factory)) {
            DexClassAndMethod resolvedMethod =
                invoke.resolveMethod(appView, context).getResolutionPair();
            InstanceInitializerInfo instanceInitializerInfo =
                getMethodOptimizationInfoOrDefault(resolvedMethod)
                    .getInstanceInitializerInfo(invoke.asInvokeDirect());
            mayReadAnyField = !instanceInitializerInfo.readSet().isEmpty();
          } else {
            mayReadAnyField = true;
          }
          if (mayReadAnyField) {
            return preserveWrittenBeforeReadSetOrFail(state);
          }
          return state.withEscapedThis(stateFactory, true);
        }

      // Instructions that trigger class initializers but don't have any operands + StaticPut.
      case INIT_CLASS:
      case NEW_INSTANCE:
      case STATIC_GET:
      case STATIC_PUT:
        {
          if (!state.isThisInitialized) {
            // The uninitialized `this` cannot escape.
            assert !state.isThisEscaped;
            return state;
          }
          boolean newIsThisEscaped =
              state.isThisEscaped
                  || (instruction.isStaticPut()
                      && state.isMaybeThis(thisValue, instruction.asStaticPut().value()));
          if (!state.isThisEscaped) {
            // Since `this` has not escaped, the <clinit> of the instantiated class cannot read
            // any fields on `this`.
            return state.withEscapedThis(stateFactory, newIsThisEscaped);
          }
          DexType initializedType;
          if (instruction.isInitClass()) {
            initializedType = instruction.asInitClass().getType();
          } else if (instruction.isNewInstance()) {
            initializedType = instruction.asNewInstance().getType();
          } else {
            assert instruction.isStaticFieldInstruction();
            initializedType = instruction.asFieldInstruction().getField().getHolderType();
          }
          if (!initializedType.isClassType()) {
            // Unexpected, should never happen?
            return preserveWrittenBeforeReadSetOrFail(state);
          }
          DexClass initializedClass = appView.definitionFor(initializedType);
          if (initializedClass == null) {
            // Ignore missing classes.
            return state.withEscapedThis(stateFactory, newIsThisEscaped);
          }
          if (initializedClass.isLibraryClass()) {
            // Assume that the class initialization of classes in the library do not read program
            // instance fields.
            return state.withEscapedThis(stateFactory, newIsThisEscaped);
          }
          // Look for a <clinit> method on this class or its program superclasses.
          TraversalContinuation<?, ?> traversalContinuation =
              appView
                  .appInfo()
                  .traverseSuperClasses(
                      initializedClass,
                      (supertype, superclass, subclass) ->
                          TraversalContinuation.breakIf(
                              superclass != null
                                  && superclass.isProgramClass()
                                  && superclass.hasClassInitializer()));
          if (initializedClass.hasClassInitializer() || traversalContinuation.isBreak()) {
            // The <clinit> of the instantiated class may in principle read any fields on `this`.
            return preserveWrittenBeforeReadSetOrFail(state);
          }
          return state.withEscapedThis(stateFactory, newIsThisEscaped);
        }

      case CMP:
      case INSTANCE_OF:
        return state;

      default:
        assert !instruction.isArithmeticBinop();
        assert !instruction.isLogicalBinop();
        assert !instruction.isJumpInstruction();
        assert !instruction.isUnop();
        return preserveWrittenBeforeReadSetOrFail(state);
    }
  }

  @Override
  public TransferFunctionResult<ReadBeforeWriteAnalysisState> applyBlock(
      BasicBlock block, ReadBeforeWriteAnalysisState state) {
    Collection<Value> newThisAliases = null;
    for (Phi phi : block.getPhis()) {
      if (phi.hasOperandThatMatches(operand -> state.isMaybeThis(thisValue, operand))) {
        if (newThisAliases == null) {
          newThisAliases = new ArrayList<>();
        }
        newThisAliases.add(phi);
      }
    }
    if (newThisAliases != null) {
      return state.joinThisAliases(newThisAliases, stateFactory);
    }
    return state;
  }

  private TransferFunctionResult<ReadBeforeWriteAnalysisState> preserveWrittenBeforeReadSetOrFail(
      ReadBeforeWriteAnalysisState state) {
    if (state.writtenBeforeReadSet.isEmpty()) {
      // No information to preserve, simply bail out.
      return new FailedTransferFunctionResult<>();
    }
    return state.setReadBeforeWriteSetToUnknown(stateFactory);
  }

  @Override
  public boolean shouldTransferExceptionalControlFlowFromInstruction(
      BasicBlock throwBlock, Instruction throwInstruction) {
    return AbstractTransferFunction.super.shouldTransferExceptionalControlFlowFromInstruction(
        throwBlock, throwInstruction);
  }

  @Override
  public ReadBeforeWriteAnalysisState computeExceptionalBlockEntryState(
      BasicBlock basicBlock,
      DexType guard,
      BasicBlock throwBlock,
      Instruction throwInstruction,
      ReadBeforeWriteAnalysisState throwState) {
    // We currently do not run this analysis when the code may have catch handlers, and therefore
    // do not need to consider this exceptional control flow edge here.
    throw new Unreachable();
  }

  @Override
  public FailedDataflowAnalysisResult createFailedAnalysisResult(
      Instruction instruction,
      TransferFunctionResult<ReadBeforeWriteAnalysisState> transferResult) {
    return AbstractTransferFunction.super.createFailedAnalysisResult(instruction, transferResult);
  }
}

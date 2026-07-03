// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.virtualmethodhoisting;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
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
import static com.android.tools.r8.ir.code.Opcodes.MONITOR;
import static com.android.tools.r8.ir.code.Opcodes.MOVE_EXCEPTION;
import static com.android.tools.r8.ir.code.Opcodes.MUL;
import static com.android.tools.r8.ir.code.Opcodes.NEG;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_EMPTY;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_FILLED;
import static com.android.tools.r8.ir.code.Opcodes.NEW_INSTANCE;
import static com.android.tools.r8.ir.code.Opcodes.NOT;
import static com.android.tools.r8.ir.code.Opcodes.NUMBER_CONVERSION;
import static com.android.tools.r8.ir.code.Opcodes.OR;
import static com.android.tools.r8.ir.code.Opcodes.REM;
import static com.android.tools.r8.ir.code.Opcodes.RESOURCE_CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.SHL;
import static com.android.tools.r8.ir.code.Opcodes.SHR;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;
import static com.android.tools.r8.ir.code.Opcodes.STORE_STORE_FENCE;
import static com.android.tools.r8.ir.code.Opcodes.STRING_SWITCH;
import static com.android.tools.r8.ir.code.Opcodes.SUB;
import static com.android.tools.r8.ir.code.Opcodes.THROW;
import static com.android.tools.r8.ir.code.Opcodes.USHR;
import static com.android.tools.r8.ir.code.Opcodes.XOR;
import static com.android.tools.r8.utils.internal.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldResolutionResult.SingleFieldResolutionResult;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.LirConverter;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.ThrowingAction;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Searches for non-abstract methods that override an abstract method, where the body of the
 * non-abstract method can safely be hoisted up to the declaration of the abstract method. This
 * helps reduce the method count.
 */
public class VirtualMethodHoister {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory factory;

  private final VirtualMethodHoisterLens.Builder lensBuilder =
      new VirtualMethodHoisterLens.Builder();

  public VirtualMethodHoister(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    if (shouldRun()) {
      try (Timing t0 = timing.begin("VirtualMethodHoister")) {
        internalRun(executorService, timing);
        appView.getTypeElementFactory().clearTypeElementsCache();
        appView.notifyOptimizationFinished();
      }
    }
  }

  private boolean shouldRun() {
    return appView.options().isOptimizing() && appView.options().isShrinking();
  }

  private void internalRun(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    Set<DexMethod> hoistedMethods = hoistMethodsConcurrently(executorService);
    if (hoistedMethods.isEmpty()) {
      return;
    }

    // Prune the methods that have been removed due to hoisting.
    pruneApp(hoistedMethods, executorService, timing);

    // Rewrite AppView and LirCode.
    VirtualMethodHoisterLens lens = lensBuilder.build(appView);
    appView.rewriteWithLens(lens, executorService, timing);
    LirConverter.rewriteLirWithLens(appView, timing, executorService, ThrowingAction.empty());
    appView.clearCodeRewritings(executorService, timing);
  }

  private Set<DexMethod> hoistMethodsConcurrently(ExecutorService executorService)
      throws ExecutionException {
    // Process the strongly connected program components concurrently.
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.createWithDeterministicOrder(appView);
    Set<DexMethod> hoistedMethods = ConcurrentHashMap.newKeySet();
    List<Set<DexProgramClass>> stronglyConnectedComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();
    ThreadUtils.processItems(
        stronglyConnectedComponents,
        stronglyConnectedComponent -> {
          VirtualMethodHoisterTraversal traversal =
              new VirtualMethodHoisterTraversal(appView, immediateSubtypingInfo);
          traversal.run(
              ListUtils.sort(stronglyConnectedComponent, Comparator.comparing(DexClass::getType)));
          hoistedMethods.addAll(traversal.removeAndGetHoistedMethods());
        },
        appView.options().getThreadingModule(),
        executorService);
    return hoistedMethods;
  }

  private void pruneApp(
      Set<DexMethod> hoistedMethods, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    PrunedItems prunedItems =
        PrunedItems.builder().setPrunedApp(appView.app()).addRemovedMethods(hoistedMethods).build();
    appView.pruneItems(prunedItems, executorService, timing);
  }

  private class VirtualMethodHoisterTraversal extends DepthFirstTopDownClassHierarchyTraversal {

    private final Map<DexProgramClass, Set<DexEncodedMethod>> hoistedMethods =
        new IdentityHashMap<>();

    private final Map<DexProgramClass, TraversalState> states = new IdentityHashMap<>();

    VirtualMethodHoisterTraversal(
        AppView<AppInfoWithLiveness> appView,
        ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
      super(appView, immediateSubtypingInfo);
    }

    private TraversalState getOrCreateState(DexProgramClass clazz) {
      return states.computeIfAbsent(clazz, ignoreKey(TraversalState::new));
    }

    public Set<DexMethod> removeAndGetHoistedMethods() {
      Set<DexMethod> hoistedMethodReferences = Sets.newIdentityHashSet();
      hoistedMethods.forEach(
          (sourceClass, sourceMethods) -> {
            sourceClass.getMethodCollection().removeMethods(sourceMethods);
            for (DexEncodedMethod sourceMethod : sourceMethods) {
              hoistedMethodReferences.add(sourceMethod.getReference());
            }
          });
      return hoistedMethodReferences;
    }

    @Override
    public void visit(DexProgramClass clazz) {
      if (clazz.isInterface()) {
        return;
      }

      TraversalState state = getOrCreateState(clazz);
      if (clazz.hasSuperType()) {
        DexProgramClass superClass =
            asProgramClassOrNull(appView.definitionFor(clazz.getSuperType()));
        if (superClass != null) {
          TraversalState superClassState = states.get(superClass);
          if (superClassState != null) {
            state.addAbstractMethods(superClassState);
          }
        }
      }

      clazz.forEachProgramVirtualMethod(
          method -> {
            if (method.getAccessFlags().isAbstract()) {
              state.abstractMethods.add(method);
            } else {
              state.abstractMethods.remove(method);
            }
          });
    }

    @Override
    public void prune(DexProgramClass clazz) {
      if (clazz.isInterface()) {
        assert !states.containsKey(clazz);
        return;
      }

      TraversalState state = getOrCreateState(clazz);
      clazz.forEachProgramVirtualMethod(
          method -> {
            ProgramMethod sourceMethod = findHoistCandidate(method, state);

            // Shadowing: Clear subclass candidates for this signature.
            state.clearCandidates(method);

            if (sourceMethod != null) {
              // Replace the abstract target method by the source method. In principle the
              // replacement may be subject to further hoisting if we add it to the state, but
              // we don't expect abstract overrides of abstract methods.
              applyHoist(sourceMethod, method);
            } else if (!method.getAccessFlags().isAbstract()) {
              // Add method as a candidate for hoisting.
              if (isHoistable(method, clazz)) {
                state.addCandidate(method);
              }
            }
          });

      // Propagate state to superclasses (only superclass, not interfaces).
      if (clazz.hasSuperType() && !state.isEmpty()) {
        DexProgramClass superClass =
            asProgramClassOrNull(appView.definitionFor(clazz.getSuperType()));
        if (superClass != null) {
          getOrCreateState(superClass).addAll(state);
        }
      }

      states.remove(clazz);
    }

    private boolean isHoistable(ProgramMethod method, DexProgramClass clazz) {
      if (!clazz.hasSuperType()) {
        return false;
      }
      DexProgramClass superClass =
          asProgramClassOrNull(appView.definitionFor(clazz.getSuperType()));
      if (superClass == null) {
        return false;
      }
      TraversalState superClassState = states.get(superClass);
      return superClassState != null && superClassState.containsAbstractMethod(method);
    }

    private ProgramMethod findHoistCandidate(ProgramMethod targetMethod, TraversalState state) {
      if (!targetMethod.getAccessFlags().isAbstract()) {
        return null;
      }
      KeepMethodInfo keepInfo = appView.getKeepInfo(targetMethod);
      if (!keepInfo.isAbstractToNonAbstractOptimizationAllowed(appView.options())) {
        return null;
      }
      for (ProgramMethod candidate : state.getCandidates(targetMethod)) {
        if (isSafeToHoist(candidate, targetMethod)) {
          return candidate;
        }
      }
      return null;
    }

    private boolean isSafeToHoist(ProgramMethod candidate, ProgramMethod targetMethod) {
      if (appView.getKeepInfo(candidate).isPinned(appView.options())) {
        return false;
      }
      if (candidate.getAccessFlags().getVisibilityOrdinal()
          != targetMethod.getAccessFlags().getVisibilityOrdinal()) {
        return false;
      }
      IRCode code = candidate.buildIR(appView);
      for (Instruction instruction : code.instructions()) {
        if (isInstructionContextIndependent(instruction)) {
          continue;
        }
        int opcode = instruction.opcode();
        switch (opcode) {
          case ARRAY_PUT:
            {
              ArrayPut arrayPut = instruction.asArrayPut();
              if (mightBeThis(arrayPut.value())) {
                // We would need to check the array type, which is not embedded in the array-put.
                // For now simply bail-out conservatively.
                return false;
              }

              // Safe.
              break;
            }

          case CHECK_CAST:
          case CONST_CLASS:
          case INIT_CLASS:
          case INSTANCE_OF:
          case NEW_ARRAY_EMPTY:
          case NEW_INSTANCE:
            {
              DexType type = instruction.asTypeInstruction().getType();
              if (isTypeInaccessibleInTargetClass(type, targetMethod)) {
                return false;
              }
              // Safe.
              break;
            }

          case NEW_ARRAY_FILLED:
            {
              NewArrayFilled newArrayFilled = instruction.asNewArrayFilled();
              DexType type = newArrayFilled.getArrayType();
              if (isTypeInaccessibleInTargetClass(type, targetMethod)) {
                return false;
              }
              if (!type.isArrayType()) {
                // Should never happen.
                return false;
              }
              // If any of the array elements are `this`, then check the code still type checks
              // after hoisting.
              DexType elementType = type.asArrayType().getArrayElementType();
              for (Value argument : newArrayFilled.arguments()) {
                if (mightBeThis(argument)) {
                  if (isInvalidUseOfThis(argument, elementType, targetMethod)) {
                    return false;
                  } else {
                    // The rest are also safe.
                    break;
                  }
                }
              }
              // Safe.
              break;
            }

          case INSTANCE_GET:
          case INSTANCE_PUT:
          case STATIC_GET:
          case STATIC_PUT:
            {
              FieldInstruction fieldInstruction = instruction.asFieldInstruction();
              SingleFieldResolutionResult<?> resolutionResult =
                  fieldInstruction.resolveField(appView, candidate).asSingleFieldResolutionResult();
              if (resolutionResult == null
                  || resolutionResult.isAccessibleFrom(targetMethod, appView).isPossiblyFalse()) {
                return false;
              }

              DexField field = fieldInstruction.getField();
              if (fieldInstruction.isInstanceFieldInstruction()) {
                Value object = fieldInstruction.asInstanceFieldInstruction().object();
                if (isInvalidUseOfThis(object, field.getHolderType(), targetMethod)) {
                  return false;
                }
              }

              if (fieldInstruction.isFieldPut()) {
                Value value = fieldInstruction.asFieldPut().value();
                if (isInvalidUseOfThis(value, field.getType(), targetMethod)) {
                  return false;
                }
              }

              // Safe.
              break;
            }

          case INVOKE_DIRECT:
          case INVOKE_INTERFACE:
          case INVOKE_VIRTUAL:
          case INVOKE_STATIC:
            {
              InvokeMethod invoke = instruction.asInvokeMethod();
              SingleResolutionResult<?> resolutionResult =
                  invoke.resolveMethod(appView, candidate).asSingleResolution();
              if (resolutionResult == null
                  || resolutionResult.isAccessibleFrom(targetMethod, appView).isPossiblyFalse()) {
                return false;
              }

              boolean isStatic = invoke.isInvokeStatic();
              DexMethod invokedMethod = invoke.getInvokedMethod();
              for (int argumentIndex = 0;
                  argumentIndex < invoke.arguments().size();
                  argumentIndex++) {
                Value argument = invoke.getArgument(argumentIndex);
                DexType argumentType = invokedMethod.getArgumentType(argumentIndex, isStatic);
                if (isInvalidUseOfThis(argument, argumentType, targetMethod)) {
                  return false;
                }
              }

              // Safe.
              break;
            }

          case INVOKE_SUPER:
            // Conservatively return false here. In principle this may be OK to hoist if this is an
            // invoke-super to a method higher up in the class hierarchy than the target class.
            return false;

          case RETURN:
            {
              // If this returns `this`, then check that the code still type checks after hoisting
              // the method.
              Return returnInstruction = instruction.asReturn();
              if (!returnInstruction.isReturnVoid()) {
                Value returnValue = returnInstruction.returnValue();
                if (isInvalidUseOfThis(returnValue, targetMethod.getReturnType(), targetMethod)) {
                  return false;
                }
              }
              // Safe.
              break;
            }

          default:
            return false;
        }
      }
      return true;
    }

    // Used to identify instructions that don't require any handling since the semantics of the
    // instructions do not depend on the context that they live in, i.e., these instructions
    // continue to behave the same when moved to another class.
    private boolean isInstructionContextIndependent(Instruction instruction) {
      int opcode = instruction.opcode();
      switch (opcode) {
        case ADD:
        case AND:
        case ARRAY_GET:
        case ARRAY_LENGTH:
        case ARGUMENT:
        case ASSUME:
        case CMP:
        case CONST_NUMBER:
        case CONST_STRING:
        case DIV:
        case GOTO:
        case IF:
        case INC:
        case INT_SWITCH:
        case MONITOR:
        case MOVE_EXCEPTION:
        case MUL:
        case NEG:
        case NOT:
        case NUMBER_CONVERSION:
        case OR:
        case REM:
        case RESOURCE_CONST_NUMBER:
        case SHL:
        case SHR:
        case STORE_STORE_FENCE:
        case STRING_SWITCH:
        case SUB:
        case THROW:
        case USHR:
        case XOR:
          // Safe.
          return true;
        default:
          return false;
      }
    }

    private boolean mightBeThis(Value value) {
      return value.getAliasedValue().isThis() || value.getAliasedValue().isPhi();
    }

    private boolean isInvalidUseOfThis(Value value, DexType type, ProgramMethod targetMethod) {
      return mightBeThis(value)
          && type.isClassType()
          && !appView.appInfo().isSubtype(targetMethod.getHolderType(), type);
    }

    private boolean isTypeInaccessibleInTargetClass(DexType type, ProgramMethod targetMethod) {
      DexType baseType = type.getBaseType();
      if (baseType.isClassType()) {
        DexClass clazz = appView.definitionFor(baseType);
        return clazz == null
            || AccessControl.isClassAccessible(clazz, targetMethod, appView).isPossiblyFalse();
      }
      return false;
    }

    private void applyHoist(ProgramMethod sourceMethod, ProgramMethod targetMethod) {
      // Replace the target method with the source method.
      DexProgramClass targetClass = targetMethod.getHolder();
      targetClass
          .getMethodCollection()
          .replaceVirtualMethod(
              targetMethod.getReference(),
              t ->
                  sourceMethod
                      .getDefinition()
                      .toTypeSubstitutedMethodAsInlining(
                          t.getReference(),
                          factory,
                          builder ->
                              builder.modifyAccessFlags(
                                  f -> f.demoteFromFinal().setAbstract().demoteFromAbstract())));

      // Record for lens and pruning.
      if (canRetargetInvokesToTargetMethod(sourceMethod, targetMethod)) {
        lensBuilder.map(sourceMethod, targetMethod);
      }

      hoistedMethods
          .computeIfAbsent(sourceMethod.getHolder(), ignoreKey(Sets::newIdentityHashSet))
          .add(sourceMethod.getDefinition());
    }

    private boolean canRetargetInvokesToTargetMethod(
        ProgramMethod sourceMethod, ProgramMethod targetMethod) {
      DexProgramClass sourceHolder = sourceMethod.getHolder();
      DexClass targetHolder = targetMethod.getHolder();
      if (!targetHolder.getAccessFlags().isPublic()) {
        if (sourceHolder.getAccessFlags().isPublic() || !sourceMethod.isSamePackage(targetMethod)) {
          return false;
        }
      }
      if (targetMethod.getAccessFlags().isPublic()) {
        return true;
      }
      MethodAccessFlags sourceAccessFlags = sourceMethod.getAccessFlags();
      MethodAccessFlags targetAccessFlags = targetMethod.getAccessFlags();
      if (sourceAccessFlags.isPackagePrivate()
          && !targetAccessFlags.isPrivate()
          && sourceMethod.isSamePackage(targetMethod)) {
        return true;
      }
      return sourceAccessFlags.isProtected()
          && targetAccessFlags.isProtected()
          && sourceMethod.isSamePackage(targetMethod);
    }
  }

  private static class TraversalState {

    private final DexMethodSignatureMap<List<ProgramMethod>> candidates =
        DexMethodSignatureMap.create();

    private final DexMethodSignatureSet abstractMethods = DexMethodSignatureSet.create();

    void addAbstractMethods(TraversalState other) {
      abstractMethods.addAll(other.abstractMethods);
    }

    void addCandidate(ProgramMethod method) {
      candidates.computeIfAbsent(method, ignoreKey(ArrayList::new)).add(method);
    }

    void addAll(TraversalState other) {
      other.candidates.forEach(
          (signature, methods) ->
              candidates.computeIfAbsent(signature, ignoreKey(ArrayList::new)).addAll(methods));
    }

    List<ProgramMethod> getCandidates(ProgramMethod method) {
      return candidates.getOrDefault(method, Collections.emptyList());
    }

    void clearCandidates(ProgramMethod method) {
      candidates.remove(method);
    }

    boolean containsAbstractMethod(ProgramMethod method) {
      return abstractMethods.contains(method);
    }

    boolean isEmpty() {
      return candidates.isEmpty();
    }
  }
}

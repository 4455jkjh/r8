// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.virtualmethodhoisting;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CONST_NUMBER;
import static com.android.tools.r8.ir.code.Opcodes.CONST_STRING;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.utils.internal.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.LirConverter;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
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
      if (targetMethod.getAccessFlags().isAbstract()
          && !appView.getKeepInfo(targetMethod).isPinned(appView.options())) {
        for (ProgramMethod candidate : state.getCandidates(targetMethod)) {
          if (isSafeToHoist(candidate, targetMethod)) {
            return candidate;
          }
        }
      }
      return null;
    }

    private boolean isSafeToHoist(ProgramMethod candidate, ProgramMethod targetMethod) {
      if (appView.getKeepInfo(candidate).isPinned(appView.options())) {
        return false;
      }
      IRCode code = candidate.buildIR(appView);
      for (Instruction instruction : code.instructions()) {
        int opcode = instruction.opcode();
        if (opcode == ARGUMENT
            || opcode == ASSUME
            || opcode == CONST_NUMBER
            || opcode == CONST_STRING) {
          // Safe
        } else if (opcode == RETURN) {
          Return returnInstruction = instruction.asReturn();
          if (!returnInstruction.isReturnVoid()) {
            Value returnValue = returnInstruction.returnValue();
            if (returnValue.getAliasedValue().isThis()) {
              DexType returnType = targetMethod.getReturnType();
              DexClass targetClass = targetMethod.getHolder();
              if (!appView.appInfo().isSubtype(targetClass.getType(), returnType)) {
                return false;
              }
            }
          }
        } else {
          return false;
        }
      }
      return true;
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
                          builder -> builder.modifyAccessFlags(AccessFlags::demoteFromFinal)));

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

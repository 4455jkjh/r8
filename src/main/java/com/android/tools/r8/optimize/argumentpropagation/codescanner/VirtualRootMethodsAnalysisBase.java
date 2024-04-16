// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize.argumentpropagation.codescanner;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.optimize.argumentpropagation.utils.DepthFirstTopDownClassHierarchyTraversal;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Computes the set of virtual methods for which we can use a monomorphic method state as well as
 * the mapping from virtual methods to their representative root methods.
 */
public class VirtualRootMethodsAnalysisBase extends DepthFirstTopDownClassHierarchyTraversal {

  protected static class VirtualRootMethod {

    private final VirtualRootMethod parent;
    private final ProgramMethod root;
    private final ProgramMethodSet overrides = ProgramMethodSet.create();

    VirtualRootMethod(ProgramMethod root) {
      this(root, null);
    }

    VirtualRootMethod(ProgramMethod root, VirtualRootMethod parent) {
      assert root != null;
      this.parent = parent;
      this.root = root;
    }

    void addOverride(ProgramMethod override) {
      assert override.getDefinition() != root.getDefinition();
      assert override.getMethodSignature().equals(root.getMethodSignature());
      overrides.add(override);
      if (hasParent()) {
        getParent().addOverride(override);
      }
    }

    boolean hasParent() {
      return parent != null;
    }

    VirtualRootMethod getParent() {
      return parent;
    }

    ProgramMethod getRoot() {
      return root;
    }

    ProgramMethod getSingleNonAbstractMethod() {
      ProgramMethod singleNonAbstractMethod = root.getAccessFlags().isAbstract() ? null : root;
      for (ProgramMethod override : overrides) {
        if (!override.getAccessFlags().isAbstract()) {
          if (singleNonAbstractMethod != null) {
            // Not a single non-abstract method.
            return null;
          }
          singleNonAbstractMethod = override;
        }
      }
      assert singleNonAbstractMethod == null
          || !singleNonAbstractMethod.getAccessFlags().isAbstract();
      return singleNonAbstractMethod;
    }

    void forEach(Consumer<ProgramMethod> consumer) {
      consumer.accept(root);
      overrides.forEach(consumer);
    }

    boolean hasOverrides() {
      return !overrides.isEmpty();
    }

    boolean isInterfaceMethodWithSiblings() {
      // TODO(b/190154391): Conservatively returns true for all interface methods, but should only
      //  return true for those with siblings.
      return root.getHolder().isInterface();
    }
  }

  private final Map<DexProgramClass, DexMethodSignatureMap<VirtualRootMethod>>
      virtualRootMethodsPerClass = new IdentityHashMap<>();

  protected final ProgramMethodSet monomorphicVirtualRootMethods = ProgramMethodSet.create();
  protected final ProgramMethodSet monomorphicVirtualNonRootMethods = ProgramMethodSet.create();

  protected final Map<DexMethod, DexMethod> virtualRootMethods = new IdentityHashMap<>();

  protected VirtualRootMethodsAnalysisBase(
      AppView<AppInfoWithLiveness> appView, ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    super(appView, immediateSubtypingInfo);
  }

  @Override
  public void visit(DexProgramClass clazz) {
    DexMethodSignatureMap<VirtualRootMethod> state = computeVirtualRootMethodsState(clazz);
    virtualRootMethodsPerClass.put(clazz, state);
  }

  private DexMethodSignatureMap<VirtualRootMethod> computeVirtualRootMethodsState(
      DexProgramClass clazz) {
    DexMethodSignatureMap<VirtualRootMethod> virtualRootMethodsForClass =
        DexMethodSignatureMap.create();
    immediateSubtypingInfo.forEachImmediateProgramSuperClass(
        clazz,
        superclass -> {
          DexMethodSignatureMap<VirtualRootMethod> virtualRootMethodsForSuperclass =
              virtualRootMethodsPerClass.get(superclass);
          virtualRootMethodsForSuperclass.forEach(
              (signature, info) ->
                  virtualRootMethodsForClass.computeIfAbsent(
                      signature, ignoreKey(() -> new VirtualRootMethod(info.getRoot(), info))));
        });
    clazz.forEachProgramVirtualMethod(
        method -> {
          if (virtualRootMethodsForClass.containsKey(method)) {
            virtualRootMethodsForClass.get(method).getParent().addOverride(method);
          } else {
            virtualRootMethodsForClass.put(method, new VirtualRootMethod(method));
          }
        });
    return virtualRootMethodsForClass;
  }

  @Override
  public void prune(DexProgramClass clazz) {
    // Record the overrides for each virtual method that is rooted at this class.
    DexMethodSignatureMap<VirtualRootMethod> virtualRootMethodsForClass =
        virtualRootMethodsPerClass.remove(clazz);
    clazz.forEachProgramVirtualMethod(
        rootCandidate -> {
          VirtualRootMethod virtualRootMethod =
              virtualRootMethodsForClass.remove(rootCandidate.getMethodSignature());
          acceptVirtualMethod(rootCandidate, virtualRootMethod);
          if (!rootCandidate.isStructurallyEqualTo(virtualRootMethod.getRoot())) {
            return;
          }
          boolean isMonomorphicVirtualMethod =
              !clazz.isInterface() && !virtualRootMethod.hasOverrides();
          if (isMonomorphicVirtualMethod) {
            monomorphicVirtualRootMethods.add(rootCandidate);
          } else {
            ProgramMethod singleNonAbstractMethod = virtualRootMethod.getSingleNonAbstractMethod();
            if (singleNonAbstractMethod != null
                && !virtualRootMethod.isInterfaceMethodWithSiblings()) {
              virtualRootMethod.forEach(
                  method -> {
                    // Interface methods can have siblings and can therefore not be mapped to their
                    // unique non-abstract implementation, unless the interface method does not have
                    // any siblings.
                    virtualRootMethods.put(
                        method.getReference(), singleNonAbstractMethod.getReference());
                  });
              if (!singleNonAbstractMethod.getHolder().isInterface()) {
                monomorphicVirtualNonRootMethods.add(singleNonAbstractMethod);
              }
            } else {
              virtualRootMethod.forEach(
                  method ->
                      virtualRootMethods.put(method.getReference(), rootCandidate.getReference()));
            }
          }
        });
  }

  protected void acceptVirtualMethod(ProgramMethod method, VirtualRootMethod virtualRootMethod) {
    // Intentionally empty.
  }
}

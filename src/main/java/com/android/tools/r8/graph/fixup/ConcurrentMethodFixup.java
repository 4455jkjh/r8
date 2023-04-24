// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.fixup;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClasspathOrLibraryClass;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class ConcurrentMethodFixup {

  private final AppView<AppInfoWithLiveness> appView;
  private final Map<ClasspathOrLibraryClass, DexMethodSignatureSet> nonProgramVirtualMethods =
      new ConcurrentHashMap<>();
  private final ProgramClassFixer programClassFixer;

  public ConcurrentMethodFixup(
      AppView<AppInfoWithLiveness> appView, ProgramClassFixer programClassFixer) {
    this.appView = appView;
    this.programClassFixer = programClassFixer;
  }

  public void fixupClassesConcurrentlyByConnectedProgramComponents(
      Timing timing, ExecutorService executorService) throws ExecutionException {
    timing.begin("Concurrent method fixup");
    timing.begin("Compute strongly connected components");
    ImmediateProgramSubtypingInfo immediateSubtypingInfo =
        ImmediateProgramSubtypingInfo.create(appView);
    List<Set<DexProgramClass>> connectedComponents =
        new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo)
            .computeStronglyConnectedComponents();
    timing.end();

    timing.begin("Process strongly connected components");
    ThreadUtils.processItems(
        connectedComponents, this::processConnectedProgramComponents, executorService);
    timing.end();
    timing.end();
  }

  public interface ProgramClassFixer {
    // When a class is fixed-up, it is guaranteed that its supertype and interfaces were processed
    // before. In addition, all interfaces are processed before any class is processed.
    void fixupProgramClass(DexProgramClass clazz, MethodNamingUtility namingUtility);
  }

  private void processConnectedProgramComponents(Set<DexProgramClass> classes) {
    List<DexProgramClass> sorted = new ArrayList<>(classes);
    sorted.sort(Comparator.comparing(DexClass::getType));
    BiMap<DexMethodSignature, DexMethodSignature> componentSignatures = HashBiMap.create();

    // 1) Reserve all library overrides and pinned virtual methods.
    reserveComponentPinnedAndInterfaceMethodSignatures(sorted, componentSignatures);

    // 2) Map all interfaces top-down updating the componentSignatures.
    Set<DexProgramClass> processedInterfaces = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : sorted) {
      if (clazz.isInterface()) {
        processInterface(clazz, processedInterfaces, componentSignatures);
      }
    }

    // 3) Map all classes top-down propagating the inherited signatures.
    // The componentSignatures are already fully computed and should not be updated anymore.
    BiMap<DexMethodSignature, DexMethodSignature> immutableComponentSignaturesForTesting =
        InternalOptions.assertionsEnabled()
            ? ImmutableBiMap.copyOf(componentSignatures)
            : componentSignatures;
    Map<DexProgramClass, BiMap<DexMethodSignature, DexMethodSignature>> processedClasses =
        new IdentityHashMap<>();
    for (DexProgramClass clazz : sorted) {
      if (!clazz.isInterface()) {
        processClass(clazz, processedClasses, immutableComponentSignaturesForTesting);
      }
    }
  }

  private void processClass(
      DexProgramClass clazz,
      Map<DexProgramClass, BiMap<DexMethodSignature, DexMethodSignature>> processedClasses,
      BiMap<DexMethodSignature, DexMethodSignature> componentSignatures) {
    assert !clazz.isInterface();
    if (processedClasses.containsKey(clazz)) {
      return;
    }
    // We need to process first the super-type for the top-down propagation of inherited signatures.
    DexClass superClass = appView.definitionFor(clazz.superType);
    BiMap<DexMethodSignature, DexMethodSignature> inheritedSignatures;
    if (superClass == null || !superClass.isProgramClass()) {
      inheritedSignatures = HashBiMap.create(componentSignatures);
    } else {
      DexProgramClass superProgramClass = superClass.asProgramClass();
      processClass(superProgramClass, processedClasses, componentSignatures);
      inheritedSignatures = HashBiMap.create(processedClasses.get(superProgramClass));
    }
    processedClasses.put(clazz, inheritedSignatures);
    MethodNamingUtility utility = createMethodNamingUtility(inheritedSignatures, clazz);
    programClassFixer.fixupProgramClass(clazz, utility);
  }

  private void processInterface(
      DexProgramClass clazz,
      Set<DexProgramClass> processedInterfaces,
      BiMap<DexMethodSignature, DexMethodSignature> componentSignatures) {
    assert clazz.isInterface();
    if (!processedInterfaces.add(clazz)) {
      return;
    }
    // We need to process first all super-interfaces to avoid generating collisions by renaming
    // private or static methods into inherited virtual method signatures.
    for (DexType superInterface : clazz.getInterfaces()) {
      DexProgramClass superInterfaceClass =
          asProgramClassOrNull(appView.definitionFor(superInterface));
      if (superInterfaceClass != null) {
        processInterface(superInterfaceClass, processedInterfaces, componentSignatures);
      }
    }
    MethodNamingUtility utility = createMethodNamingUtility(componentSignatures, clazz);
    programClassFixer.fixupProgramClass(clazz, utility);
  }

  private MethodNamingUtility createMethodNamingUtility(
      BiMap<DexMethodSignature, DexMethodSignature> inheritedSignatures, DexProgramClass clazz) {
    BiMap<DexMethod, DexMethod> localSignatures = HashBiMap.create();
    clazz.forEachProgramInstanceInitializer(
        method -> {
          KeepMethodInfo keepInfo = appView.getKeepInfo(method);
          if (!keepInfo.isOptimizationAllowed(appView.options())
              || !keepInfo.isShrinkingAllowed(appView.options())) {
            localSignatures.put(method.getReference(), method.getReference());
          }
        });
    return new MethodNamingUtility(appView.dexItemFactory(), inheritedSignatures, localSignatures);
  }

  private void reserveComponentPinnedAndInterfaceMethodSignatures(
      List<DexProgramClass> stronglyConnectedProgramClasses,
      BiMap<DexMethodSignature, DexMethodSignature> componentSignatures) {
    Set<ClasspathOrLibraryClass> seenNonProgramClasses = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : stronglyConnectedProgramClasses) {
      // If a private or static method is pinned, we need to reserve the mapping to avoid creating
      // a collision with a changed virtual method.
      clazz.forEachProgramMethodMatching(
          m -> !m.isInstanceInitializer(),
          method -> {
            KeepMethodInfo keepInfo = appView.getKeepInfo(method);
            if (!keepInfo.isOptimizationAllowed(appView.options())
                || !keepInfo.isShrinkingAllowed(appView.options())) {
              componentSignatures.put(method.getMethodSignature(), method.getMethodSignature());
            }
          });
      clazz.forEachImmediateSupertype(
          supertype -> {
            DexClass superclass = appView.definitionFor(supertype);
            if (superclass != null
                && !superclass.isProgramClass()
                && seenNonProgramClasses.add(superclass.asClasspathOrLibraryClass())) {
              for (DexMethodSignature vMethod :
                  getOrComputeNonProgramVirtualMethods(superclass.asClasspathOrLibraryClass())) {
                componentSignatures.put(vMethod, vMethod);
              }
            }
          });
    }
  }

  private DexMethodSignatureSet getOrComputeNonProgramVirtualMethods(
      ClasspathOrLibraryClass clazz) {
    DexMethodSignatureSet libraryMethodsOnClass = nonProgramVirtualMethods.get(clazz);
    if (libraryMethodsOnClass != null) {
      return libraryMethodsOnClass;
    }
    return computeNonProgramVirtualMethods(clazz);
  }

  private DexMethodSignatureSet computeNonProgramVirtualMethods(
      ClasspathOrLibraryClass classpathOrLibraryClass) {
    DexClass clazz = classpathOrLibraryClass.asDexClass();
    DexMethodSignatureSet libraryMethodsOnClass = DexMethodSignatureSet.create();
    clazz.forEachImmediateSupertype(
        supertype -> {
          DexClass superclass = appView.definitionFor(supertype);
          if (superclass != null) {
            assert !superclass.isProgramClass();
            libraryMethodsOnClass.addAll(
                getOrComputeNonProgramVirtualMethods(superclass.asClasspathOrLibraryClass()));
          }
        });
    clazz.forEachClassMethodMatching(
        DexEncodedMethod::belongsToVirtualPool,
        method -> libraryMethodsOnClass.add(method.getMethodSignature()));
    nonProgramVirtualMethods.put(classpathOrLibraryClass, libraryMethodsOnClass);
    return libraryMethodsOnClass;
  }
}

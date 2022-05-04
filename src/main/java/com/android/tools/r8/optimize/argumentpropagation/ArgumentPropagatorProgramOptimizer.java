// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.graph.ObjectAllocationInfoCollection;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RemovedArgumentInfo;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithUpperBound;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.optimize.argumentpropagation.ArgumentPropagatorGraphLens.Builder;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.utils.AccessUtils;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.ints.IntSets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.IntPredicate;

public class ArgumentPropagatorProgramOptimizer {

  static class AllowedPrototypeChanges {

    private static final AllowedPrototypeChanges EMPTY =
        new AllowedPrototypeChanges(false, IntSets.EMPTY_SET);

    boolean canRewriteToVoid;
    IntSet removableParameterIndices;

    AllowedPrototypeChanges(boolean canRewriteToVoid, IntSet removableParameterIndices) {
      this.canRewriteToVoid = canRewriteToVoid;
      this.removableParameterIndices = removableParameterIndices;
    }

    public static AllowedPrototypeChanges create(RewrittenPrototypeDescription prototypeChanges) {
      return prototypeChanges.isEmpty()
          ? empty()
          : new AllowedPrototypeChanges(
              prototypeChanges.hasBeenChangedToReturnVoid(),
              prototypeChanges.getArgumentInfoCollection().getKeys());
    }

    public static AllowedPrototypeChanges empty() {
      return EMPTY;
    }

    @Override
    public int hashCode() {
      return BooleanUtils.intValue(canRewriteToVoid) | (removableParameterIndices.hashCode() << 1);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      AllowedPrototypeChanges other = (AllowedPrototypeChanges) obj;
      return canRewriteToVoid == other.canRewriteToVoid
          && removableParameterIndices.equals(other.removableParameterIndices);
    }
  }

  private final AppView<AppInfoWithLiveness> appView;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;
  private final Map<Set<DexProgramClass>, DexMethodSignatureSet> interfaceDispatchOutsideProgram;

  private final Map<DexClass, DexMethodSignatureSet> libraryVirtualMethods =
      new ConcurrentHashMap<>();

  public ArgumentPropagatorProgramOptimizer(
      AppView<AppInfoWithLiveness> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo,
      Map<Set<DexProgramClass>, DexMethodSignatureSet> interfaceDispatchOutsideProgram) {
    this.appView = appView;
    this.immediateSubtypingInfo = immediateSubtypingInfo;
    this.interfaceDispatchOutsideProgram = interfaceDispatchOutsideProgram;
  }

  public ArgumentPropagatorGraphLens run(
      List<Set<DexProgramClass>> stronglyConnectedProgramComponents,
      Consumer<DexProgramClass> affectedClassConsumer,
      ExecutorService executorService,
      Timing timing)
      throws ExecutionException {
    timing.begin("Optimize components");
    Collection<Builder> partialGraphLensBuilders =
        ThreadUtils.processItemsWithResults(
            stronglyConnectedProgramComponents,
            classes ->
                new StronglyConnectedComponentOptimizer()
                    .optimize(
                        classes,
                        interfaceDispatchOutsideProgram.getOrDefault(
                            classes, DexMethodSignatureSet.empty()),
                        affectedClassConsumer),
            executorService);
    timing.end();

    // Merge all the partial, disjoint graph lens builders into a single graph lens.
    timing.begin("Build graph lens");
    ArgumentPropagatorGraphLens.Builder graphLensBuilder =
        ArgumentPropagatorGraphLens.builder(appView);
    partialGraphLensBuilders.forEach(graphLensBuilder::mergeDisjoint);
    ArgumentPropagatorGraphLens graphLens = graphLensBuilder.build();
    timing.end();

    return graphLens;
  }

  private DexMethodSignatureSet getOrComputeLibraryVirtualMethods(DexClass clazz) {
    DexMethodSignatureSet libraryMethodsOnClass = libraryVirtualMethods.get(clazz);
    if (libraryMethodsOnClass != null) {
      return libraryMethodsOnClass;
    }
    return computeLibraryVirtualMethods(clazz);
  }

  private DexMethodSignatureSet computeLibraryVirtualMethods(DexClass clazz) {
    DexMethodSignatureSet libraryMethodsOnClass = DexMethodSignatureSet.create();
    immediateSubtypingInfo.forEachImmediateSuperClassMatching(
        clazz,
        (supertype, superclass) -> superclass != null,
        (supertype, superclass) ->
            libraryMethodsOnClass.addAll(getOrComputeLibraryVirtualMethods(superclass)));
    clazz.forEachClassMethodMatching(
        DexEncodedMethod::belongsToVirtualPool,
        method -> libraryMethodsOnClass.add(method.getMethodSignature()));
    libraryVirtualMethods.put(clazz, libraryMethodsOnClass);
    return libraryMethodsOnClass;
  }

  public class StronglyConnectedComponentOptimizer {

    private final DexItemFactory dexItemFactory;
    private final InternalOptions options;

    private final Map<DexMethodSignature, AllowedPrototypeChanges>
        allowedPrototypeChangesForVirtualMethods = new HashMap<>();

    private final ProgramMethodMap<SingleValue> returnValuesForVirtualMethods =
        ProgramMethodMap.create();

    // Reserved names, i.e., mappings from pairs (old method signature, prototype changes) to the
    // new method signature for that method.
    private final Map<DexMethodSignature, Map<AllowedPrototypeChanges, DexMethodSignature>>
        newMethodSignatures = new HashMap<>();

    // The method name suffix to start from when searching for a fresh method signature. Used to
    // avoid searching from index 0 to a large number when searching for a fresh method signature.
    private final Map<DexMethodSignature, IntBox> newMethodSignatureSuffixes = new HashMap<>();

    // Occupied method signatures (inverse of reserved names). Used to effectively check if a given
    // method signature is already reserved.
    private final Map<DexMethodSignature, Pair<AllowedPrototypeChanges, DexMethodSignature>>
        occupiedMethodSignatures = new HashMap<>();

    public StronglyConnectedComponentOptimizer() {
      this.dexItemFactory = appView.dexItemFactory();
      this.options = appView.options();
    }

    // TODO(b/190154391): Strengthen the static type of parameters.
    // TODO(b/69963623): If we optimize a method to be unconditionally throwing (because it has a
    //  bottom parameter), then for each caller that becomes unconditionally throwing, we could
    //  also enqueue the caller's callers for reprocessing. This would propagate the throwing
    //  information to all call sites.
    // TODO(b/190154391): If we learn that a parameter of an instance initializer is constant, and
    //  this parameter is assigned to a field on the class, and this field does not have any other
    //  writes in the program, then we should replace all field reads by the constant value and
    // prune
    //  the field. Alternatively, we could consider building flow constraints for field assignments,
    //  similarly to the way we deal with call chains in argument propagation. If a field is only
    //  assigned the parameter of a given method, we would add the flow constraint "parameter p ->
    //  field f".
    private ArgumentPropagatorGraphLens.Builder optimize(
        Set<DexProgramClass> stronglyConnectedProgramClasses,
        DexMethodSignatureSet interfaceDispatchOutsideProgram,
        Consumer<DexProgramClass> affectedClassConsumer) {
      // First reserve pinned method signatures.
      reservePinnedMethodSignatures(stronglyConnectedProgramClasses);

      // To ensure that we preserve the overriding relationships between methods, we only remove a
      // constant or unused parameter from a virtual method when it can be removed from all other
      // virtual methods in the component with the same method signature.
      computePrototypeChangesForVirtualMethods(
          stronglyConnectedProgramClasses, interfaceDispatchOutsideProgram);

      // Build a graph lens while visiting the classes in the component.
      // TODO(b/190154391): Consider visiting the interfaces first, and then processing the
      //  (non-interface) classes in top-down order to reduce the amount of reserved names.
      ArgumentPropagatorGraphLens.Builder partialGraphLensBuilder =
          ArgumentPropagatorGraphLens.builder(appView);
      List<DexProgramClass> stronglyConnectedProgramClassesWithDeterministicOrder =
          new ArrayList<>(stronglyConnectedProgramClasses);
      stronglyConnectedProgramClassesWithDeterministicOrder.sort(
          Comparator.comparing(DexClass::getType));
      for (DexProgramClass clazz : stronglyConnectedProgramClassesWithDeterministicOrder) {
        if (visitClass(clazz, interfaceDispatchOutsideProgram, partialGraphLensBuilder)) {
          affectedClassConsumer.accept(clazz);
        }
      }
      return partialGraphLensBuilder;
    }

    private void reservePinnedMethodSignatures(
        Set<DexProgramClass> stronglyConnectedProgramClasses) {
      DexMethodSignatureSet pinnedMethodSignatures = DexMethodSignatureSet.create();
      Set<DexClass> seenLibraryClasses = Sets.newIdentityHashSet();
      for (DexProgramClass clazz : stronglyConnectedProgramClasses) {
        clazz.forEachProgramMethodMatching(
            method -> !method.isInstanceInitializer(),
            method -> {
              if (!appView.getKeepInfo(method).isShrinkingAllowed(options)) {
                pinnedMethodSignatures.add(method.getMethodSignature());
              }
            });
        immediateSubtypingInfo.forEachImmediateSuperClassMatching(
            clazz,
            (supertype, superclass) ->
                superclass != null
                    && !superclass.isProgramClass()
                    && seenLibraryClasses.add(superclass),
            (supertype, superclass) ->
                pinnedMethodSignatures.addAll(getOrComputeLibraryVirtualMethods(superclass)));
      }
      pinnedMethodSignatures.forEach(
          signature ->
              reserveMethodSignature(signature, signature, AllowedPrototypeChanges.empty()));
    }

    private void reserveMethodSignature(
        DexMethodSignature newMethodSignature,
        DexMethodSignature originalMethodSignature,
        AllowedPrototypeChanges allowedPrototypeChanges) {
      // Record that methods with the given signature and removed parameters should be mapped to the
      // new signature.
      newMethodSignatures
          .computeIfAbsent(originalMethodSignature, ignoreKey(HashMap::new))
          .put(allowedPrototypeChanges, newMethodSignature);

      // Record that the new method signature is used, by a method with the old signature that had
      // the
      // given removed parameters.
      occupiedMethodSignatures.put(
          newMethodSignature, new Pair<>(allowedPrototypeChanges, originalMethodSignature));
    }

    private void computePrototypeChangesForVirtualMethods(
        Set<DexProgramClass> stronglyConnectedProgramClasses,
        DexMethodSignatureSet interfaceDispatchOutsideProgram) {
      // Group the virtual methods in the component by their signatures.
      Map<DexMethodSignature, ProgramMethodSet> virtualMethodsBySignature =
          computeVirtualMethodsBySignature(stronglyConnectedProgramClasses);
      virtualMethodsBySignature.forEach(
          (signature, methods) -> {
            // Check that there are no keep rules that prohibit prototype changes from any of the
            // methods.
            if (Iterables.any(
                methods,
                method -> !isPrototypeChangesAllowed(method, interfaceDispatchOutsideProgram))) {
              return;
            }

            // Find the parameters that are constant or unused in all methods.
            IntSet removableVirtualMethodParametersInAllMethods = new IntArraySet();
            for (int parameterIndex = 1;
                parameterIndex < signature.getProto().getArity() + 1;
                parameterIndex++) {
              if (canRemoveParameterFromVirtualMethods(parameterIndex, methods)) {
                removableVirtualMethodParametersInAllMethods.add(parameterIndex);
              }
            }

            // If any prototype changes can be made, record it.
            SingleValue returnValueForVirtualMethods =
                getReturnValueForVirtualMethods(signature, methods);
            boolean canRewriteVirtualMethodsToVoid = returnValueForVirtualMethods != null;
            if (canRewriteVirtualMethodsToVoid
                || !removableVirtualMethodParametersInAllMethods.isEmpty()) {
              allowedPrototypeChangesForVirtualMethods.put(
                  signature,
                  new AllowedPrototypeChanges(
                      canRewriteVirtualMethodsToVoid,
                      removableVirtualMethodParametersInAllMethods));
            }

            // Also record the found return value for abstract virtual methods.
            if (canRewriteVirtualMethodsToVoid) {
              for (ProgramMethod method : methods) {
                if (method.getAccessFlags().isAbstract()) {
                  returnValuesForVirtualMethods.put(method, returnValueForVirtualMethods);
                } else {
                  AbstractValue returnValueForVirtualMethod =
                      method.getOptimizationInfo().getAbstractReturnValue();
                  assert returnValueForVirtualMethod.equals(returnValueForVirtualMethods);
                }
              }
            }
          });
    }

    private Map<DexMethodSignature, ProgramMethodSet> computeVirtualMethodsBySignature(
        Set<DexProgramClass> stronglyConnectedProgramClasses) {
      Map<DexMethodSignature, ProgramMethodSet> virtualMethodsBySignature = new HashMap<>();
      for (DexProgramClass clazz : stronglyConnectedProgramClasses) {
        clazz.forEachProgramVirtualMethod(
            method ->
                virtualMethodsBySignature
                    .computeIfAbsent(
                        method.getMethodSignature(), ignoreKey(ProgramMethodSet::create))
                    .add(method));
      }
      return virtualMethodsBySignature;
    }

    private boolean isPrototypeChangesAllowed(
        ProgramMethod method, DexMethodSignatureSet interfaceDispatchOutsideProgram) {
      return appView.getKeepInfo(method).isParameterRemovalAllowed(options)
          && !method.getDefinition().isLibraryMethodOverride().isPossiblyTrue()
          && !appView.appInfo().isBootstrapMethod(method)
          && !appView.appInfo().isMethodTargetedByInvokeDynamic(method)
          && !interfaceDispatchOutsideProgram.contains(method);
    }

    private SingleValue getReturnValueForVirtualMethods(
        DexMethodSignature signature, ProgramMethodSet methods) {
      if (signature.getReturnType().isVoidType()) {
        return null;
      }

      SingleValue returnValue = null;
      for (ProgramMethod method : methods) {
        if (method.getDefinition().isAbstract()) {
          DexProgramClass holder = method.getHolder();
          if (holder.isInterface()) {
            ObjectAllocationInfoCollection objectAllocationInfoCollection =
                appView.appInfo().getObjectAllocationInfoCollection();
            if (objectAllocationInfoCollection.isImmediateInterfaceOfInstantiatedLambda(holder)) {
              return null;
            }
          }
          // OK, this can be rewritten to have void return type.
          continue;
        }
        if (!appView.appInfo().mayPropagateValueFor(method)) {
          return null;
        }
        AbstractValue returnValueForMethod = method.getOptimizationInfo().getAbstractReturnValue();
        if (!returnValueForMethod.isSingleValue()
            || !returnValueForMethod.asSingleValue().isMaterializableInAllContexts(appView)
            || (returnValue != null && !returnValueForMethod.equals(returnValue))) {
          return null;
        }
        returnValue = returnValueForMethod.asSingleValue();
      }
      return returnValue;
    }

    private boolean canRemoveParameterFromVirtualMethods(
        int parameterIndex, ProgramMethodSet methods) {
      for (ProgramMethod method : methods) {
        if (method.getDefinition().isAbstract()) {
          DexProgramClass holder = method.getHolder();
          if (holder.isInterface()) {
            ObjectAllocationInfoCollection objectAllocationInfoCollection =
                appView.appInfo().getObjectAllocationInfoCollection();
            if (objectAllocationInfoCollection.isImmediateInterfaceOfInstantiatedLambda(holder)) {
              return false;
            }
          }
          // OK, this parameter can be removed.
          continue;
        }
        CallSiteOptimizationInfo optimizationInfo = method.getOptimizationInfo().getArgumentInfos();
        if (optimizationInfo.isConcreteCallSiteOptimizationInfo()) {
          ConcreteCallSiteOptimizationInfo concreteOptimizationInfo =
              optimizationInfo.asConcreteCallSiteOptimizationInfo();
          AbstractValue abstractValue =
              concreteOptimizationInfo.getAbstractArgumentValue(parameterIndex);
          if (abstractValue.isSingleValue()
              && abstractValue.asSingleValue().isMaterializableInContext(appView, method)) {
            // OK, this parameter has a constant value and can be removed.
            continue;
          }
        }
        return false;
      }
      return true;
    }

    // Returns true if the class was changed as a result of argument propagation.
    private boolean visitClass(
        DexProgramClass clazz,
        DexMethodSignatureSet interfaceDispatchOutsideProgram,
        ArgumentPropagatorGraphLens.Builder partialGraphLensBuilder) {
      BooleanBox affected = new BooleanBox();
      Set<DexField> newFieldSignatures = Sets.newIdentityHashSet();
      Map<DexField, DexType> newFieldTypes = new IdentityHashMap<>();
      clazz.forEachProgramFieldMatching(
          field -> field.getType().isClassType(),
          field -> {
            DexType newFieldType = getNewFieldType(field);
            if (newFieldType != field.getType()) {
              newFieldTypes.put(field.getReference(), newFieldType);
            } else {
              // Reserve field signature.
              newFieldSignatures.add(field.getReference());
            }
          });
      clazz.forEachProgramFieldMatching(
          field -> field.getType().isClassType(),
          field -> {
            DexField newFieldSignature =
                getNewFieldSignature(field, newFieldSignatures, newFieldTypes);
            if (newFieldSignature != field.getReference()) {
              partialGraphLensBuilder.recordMove(field.getReference(), newFieldSignature);
              affected.set();
            }
          });
      DexMethodSignatureSet instanceInitializerSignatures = DexMethodSignatureSet.create();
      clazz.forEachProgramInstanceInitializer(instanceInitializerSignatures::add);
      clazz.forEachProgramMethod(
          method -> {
            RewrittenPrototypeDescription prototypeChanges =
                method.getDefinition().belongsToDirectPool()
                    ? computePrototypeChangesForDirectMethod(
                        method, interfaceDispatchOutsideProgram, instanceInitializerSignatures)
                    : computePrototypeChangesForVirtualMethod(method);
            DexMethod newMethodSignature = getNewMethodSignature(method, prototypeChanges);
            if (newMethodSignature != method.getReference()) {
              partialGraphLensBuilder.recordMove(
                  method.getReference(), newMethodSignature, prototypeChanges);
              affected.set();
            }
          });
      return affected.get();
    }

    private DexType getNewFieldType(ProgramField field) {
      DynamicType dynamicType = field.getOptimizationInfo().getDynamicType();
      if (dynamicType.isUnknown()) {
        return field.getType();
      }

      KeepFieldInfo keepInfo = appView.getKeepInfo(field);

      // We don't have dynamic type information for fields that are kept.
      assert !keepInfo.isPinned(options);

      if (!keepInfo.isFieldTypeStrengtheningAllowed(options)) {
        return field.getType();
      }

      if (dynamicType.isNullType()) {
        // Don't optimize always null fields; these will be optimized anyway.
        return field.getType();
      }

      if (dynamicType.isNotNullType()) {
        // We don't have a more specific type.
        return field.getType();
      }

      DynamicTypeWithUpperBound dynamicTypeWithUpperBound =
          dynamicType.asDynamicTypeWithUpperBound();
      TypeElement dynamicUpperBoundType = dynamicTypeWithUpperBound.getDynamicUpperBoundType();
      assert dynamicUpperBoundType.isReferenceType();

      ClassTypeElement staticFieldType = field.getType().toTypeElement(appView).asClassType();
      if (dynamicUpperBoundType.equalUpToNullability(staticFieldType)) {
        // We don't have more precise type information.
        return field.getType();
      }

      if (!dynamicUpperBoundType.strictlyLessThan(staticFieldType, appView)) {
        assert options.testing.allowTypeErrors;
        return field.getType();
      }

      DexType newStaticFieldType;
      if (dynamicUpperBoundType.isClassType()) {
        ClassTypeElement dynamicUpperBoundClassType = dynamicUpperBoundType.asClassType();
        if (dynamicUpperBoundClassType.getClassType() == dexItemFactory.objectType) {
          if (dynamicUpperBoundClassType.getInterfaces().hasSingleKnownInterface()) {
            newStaticFieldType =
                dynamicUpperBoundClassType.getInterfaces().getSingleKnownInterface();
          } else {
            return field.getType();
          }
        } else {
          newStaticFieldType = dynamicUpperBoundClassType.getClassType();
        }
      } else {
        newStaticFieldType = dynamicUpperBoundType.asArrayType().toDexType(dexItemFactory);
      }

      if (!AccessUtils.isAccessibleInSameContextsAs(newStaticFieldType, field.getType(), appView)) {
        return field.getType();
      }

      return newStaticFieldType;
    }

    private DexField getNewFieldSignature(
        ProgramField field,
        Set<DexField> newFieldSignatures,
        Map<DexField, DexType> newFieldTypes) {
      DexType newFieldType = newFieldTypes.getOrDefault(field.getReference(), field.getType());
      if (newFieldType == field.getType()) {
        assert newFieldSignatures.contains(field.getReference());
        return field.getReference();
      }
      // Find a new name for this field if the signature is already occupied.
      return dexItemFactory.createFreshFieldNameWithoutHolder(
          field.getHolderType(), newFieldType, field.getName().toString(), newFieldSignatures::add);
    }

    private DexMethod getNewMethodSignature(
        ProgramMethod method, RewrittenPrototypeDescription prototypeChanges) {
      DexMethodSignature methodSignatureWithoutPrototypeChanges = method.getMethodSignature();
      AllowedPrototypeChanges allowedPrototypeChanges =
          AllowedPrototypeChanges.create(prototypeChanges);

      // Check if there is a reserved signature for this already.
      DexMethodSignature reservedSignature =
          newMethodSignatures
              .getOrDefault(methodSignatureWithoutPrototypeChanges, Collections.emptyMap())
              .get(allowedPrototypeChanges);
      if (reservedSignature != null) {
        return reservedSignature.withHolder(method.getHolderType(), dexItemFactory);
      }

      DexMethod methodReferenceWithParametersRemoved =
          prototypeChanges.rewriteMethod(method, dexItemFactory);
      DexMethodSignature methodSignatureWithParametersRemoved =
          methodReferenceWithParametersRemoved.getSignature();

      // Find a method signature. First check if the current signature is available.
      if (!occupiedMethodSignatures.containsKey(methodSignatureWithParametersRemoved)) {
        if (!method.getDefinition().isInstanceInitializer()) {
          reserveMethodSignature(
              methodSignatureWithParametersRemoved,
              methodSignatureWithoutPrototypeChanges,
              allowedPrototypeChanges);
        }
        return methodReferenceWithParametersRemoved;
      }

      Pair<AllowedPrototypeChanges, DexMethodSignature> occupant =
          occupiedMethodSignatures.get(methodSignatureWithParametersRemoved);
      // In this case we should have found a reserved method signature above.
      assert !(occupant.getFirst().equals(allowedPrototypeChanges)
          && occupant.getSecond().equals(methodSignatureWithoutPrototypeChanges));

      // We need to find a new name for this method, since the signature is already occupied.
      // TODO(b/190154391): Instead of generating a new name, we could also try permuting the order
      // of parameters.
      IntBox suffix =
          newMethodSignatureSuffixes.computeIfAbsent(
              methodSignatureWithParametersRemoved, ignoreKey(IntBox::new));
      DexMethod newMethod =
          dexItemFactory.createFreshMethodNameWithoutHolder(
              method.getName().toString(),
              methodReferenceWithParametersRemoved.getProto(),
              method.getHolderType(),
              candidate -> {
                suffix.increment();
                return isMethodSignatureFresh(
                    candidate.getSignature(),
                    methodSignatureWithoutPrototypeChanges,
                    allowedPrototypeChanges);
              },
              suffix.get());

      // Reserve the newly generated method signature.
      if (!method.getDefinition().isInstanceInitializer()) {
        reserveMethodSignature(
            newMethod.getSignature(),
            methodSignatureWithoutPrototypeChanges,
            allowedPrototypeChanges);
      }

      return newMethod;
    }

    private boolean isMethodSignatureFresh(
        DexMethodSignature signature,
        DexMethodSignature previous,
        AllowedPrototypeChanges allowedPrototypeChanges) {
      Pair<AllowedPrototypeChanges, DexMethodSignature> candidateOccupant =
          occupiedMethodSignatures.get(signature);
      if (candidateOccupant == null) {
        return true;
      }
      return candidateOccupant.getFirst().equals(allowedPrototypeChanges)
          && candidateOccupant.getSecond().equals(previous);
    }

    private RewrittenPrototypeDescription computePrototypeChangesForDirectMethod(
        ProgramMethod method,
        DexMethodSignatureSet interfaceDispatchOutsideProgram,
        DexMethodSignatureSet instanceInitializerSignatures) {
      assert method.getDefinition().belongsToDirectPool();
      if (!isPrototypeChangesAllowed(method, interfaceDispatchOutsideProgram)) {
        return RewrittenPrototypeDescription.none();
      }
      // TODO(b/199864962): Allow parameter removal from check-not-null classified methods.
      if (method
          .getOptimizationInfo()
          .getEnumUnboxerMethodClassification()
          .isCheckNotNullClassification()) {
        return RewrittenPrototypeDescription.none();
      }
      RewrittenPrototypeDescription prototypeChanges = computePrototypeChangesForMethod(method);
      if (prototypeChanges.isEmpty()) {
        return prototypeChanges;
      }
      if (method.getDefinition().isInstanceInitializer()) {
        DexMethod rewrittenMethod =
            prototypeChanges.getArgumentInfoCollection().rewriteMethod(method, dexItemFactory);
        assert rewrittenMethod != method.getReference();
        if (!instanceInitializerSignatures.add(rewrittenMethod)) {
          return RewrittenPrototypeDescription.none();
        }
      }
      return prototypeChanges;
    }

    private RewrittenPrototypeDescription computePrototypeChangesForVirtualMethod(
        ProgramMethod method) {
      AllowedPrototypeChanges allowedPrototypeChanges =
          allowedPrototypeChangesForVirtualMethods.get(method.getMethodSignature());
      if (allowedPrototypeChanges == null) {
        return RewrittenPrototypeDescription.none();
      }

      IntSet removableParameterIndices = allowedPrototypeChanges.removableParameterIndices;

      if (method.getAccessFlags().isAbstract()) {
        // Treat the parameters as unused.
        ArgumentInfoCollection.Builder removableParametersBuilder =
            ArgumentInfoCollection.builder();
        for (int removableParameterIndex : removableParameterIndices) {
          removableParametersBuilder.addArgumentInfo(
              removableParameterIndex,
              RemovedArgumentInfo.builder()
                  .setType(method.getArgumentType(removableParameterIndex))
                  .build());
        }
        return RewrittenPrototypeDescription.create(
            Collections.emptyList(),
            computeReturnChangesForMethod(method, allowedPrototypeChanges.canRewriteToVoid),
            removableParametersBuilder.build());
      }

      RewrittenPrototypeDescription prototypeChanges =
          computePrototypeChangesForMethod(
              method,
              allowedPrototypeChanges.canRewriteToVoid,
              removableParameterIndices::contains);
      assert prototypeChanges.getArgumentInfoCollection().size()
          == removableParameterIndices.size();
      return prototypeChanges;
    }

    private RewrittenPrototypeDescription computePrototypeChangesForMethod(ProgramMethod method) {
      return computePrototypeChangesForMethod(method, true, parameterIndex -> true);
    }

    private RewrittenPrototypeDescription computePrototypeChangesForMethod(
        ProgramMethod method,
        boolean allowToVoidRewriting,
        IntPredicate removableParameterIndices) {
      return RewrittenPrototypeDescription.create(
          Collections.emptyList(),
          computeReturnChangesForMethod(method, allowToVoidRewriting),
          computeParameterChangesForMethod(method, removableParameterIndices));
    }

    private ArgumentInfoCollection computeParameterChangesForMethod(
        ProgramMethod method, IntPredicate removableParameterIndices) {
      ConcreteCallSiteOptimizationInfo optimizationInfo =
          method.getOptimizationInfo().getArgumentInfos().asConcreteCallSiteOptimizationInfo();
      if (optimizationInfo == null) {
        return ArgumentInfoCollection.empty();
      }

      ArgumentInfoCollection.Builder removableParametersBuilder = ArgumentInfoCollection.builder();
      for (int argumentIndex = method.getDefinition().getFirstNonReceiverArgumentIndex();
          argumentIndex < method.getDefinition().getNumberOfArguments();
          argumentIndex++) {
        if (!removableParameterIndices.test(argumentIndex)) {
          continue;
        }
        AbstractValue abstractValue = optimizationInfo.getAbstractArgumentValue(argumentIndex);
        if (abstractValue.isSingleValue()
            && abstractValue.asSingleValue().isMaterializableInContext(appView, method)) {
          removableParametersBuilder.addArgumentInfo(
              argumentIndex,
              RemovedArgumentInfo.builder()
                  .setSingleValue(abstractValue.asSingleValue())
                  .setType(method.getArgumentType(argumentIndex))
                  .build());
        }
      }
      return removableParametersBuilder.build();
    }

    private RewrittenTypeInfo computeReturnChangesForMethod(
        ProgramMethod method, boolean allowToVoidRewriting) {
      if (!allowToVoidRewriting) {
        assert !returnValuesForVirtualMethods.containsKey(method);
        return null;
      }

      AbstractValue returnValue;
      if (method.getReturnType().isAlwaysNull(appView)) {
        returnValue = appView.abstractValueFactory().createNullValue();
      } else if (method.getDefinition().belongsToVirtualPool()
          && returnValuesForVirtualMethods.containsKey(method)) {
        assert method.getAccessFlags().isAbstract();
        returnValue = returnValuesForVirtualMethods.get(method);
      } else {
        returnValue = method.getOptimizationInfo().getAbstractReturnValue();
      }

      if (!returnValue.isSingleValue()
          || !returnValue.asSingleValue().isMaterializableInAllContexts(appView)) {
        return null;
      }

      SingleValue singleValue = returnValue.asSingleValue();
      return RewrittenTypeInfo.toVoid(method.getReturnType(), dexItemFactory, singleValue);
    }
  }
}

// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import static com.android.tools.r8.ir.optimize.info.OptimizationFeedback.getSimpleFeedback;
import static com.android.tools.r8.utils.internal.MapUtils.ignoreKey;

import com.android.tools.r8.contexts.CompilationContext.MainThreadContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.finalizer.IRFinalizer;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeAnalyzer;
import com.android.tools.r8.ir.optimize.info.bridge.BridgeInfo;
import com.android.tools.r8.ir.optimize.info.bridge.StaticBridgeExcludingReceiverInfo;
import com.android.tools.r8.ir.optimize.info.bridge.VirtualBridgeInfo;
import com.android.tools.r8.optimize.bridgehoisting.BridgeHoisting;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.OptionalBool;
import com.android.tools.r8.utils.internal.SetUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntListIterator;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;

public class BridgeHoistingToSharedSyntheticSuperClass {

  private final AppView<AppInfoWithLiveness> appView;
  private final BridgeAnalyzer bridgeAnalyzer;
  private final DexItemFactory factory;

  BridgeHoistingToSharedSyntheticSuperClass(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.bridgeAnalyzer = new BridgeAnalyzer(appView);
    this.factory = appView.dexItemFactory();
  }

  public static void run(
      AppView<AppInfoWithLiveness> appView, ExecutorService executorService, Timing timing)
      throws ExecutionException {
    InternalOptions options = appView.options();
    if (!options.isOptimizing() || !options.isShrinking()) {
      return;
    }
    if (!appView.options().canInitNewInstanceUsingSuperclassConstructor()) {
      // TODO(b/309575527): Extend to all runtimes.
      return;
    }
    TestingOptions testingOptions = options.getTestingOptions();
    if (!testingOptions.enableBridgeHoistingToSharedSyntheticSuperclass) {
      return;
    }
    timing.time(
        "BridgeHoistingToSharedSyntheticSuperClass",
        () ->
            new BridgeHoistingToSharedSyntheticSuperClass(appView)
                .internalRun(executorService, timing));
  }

  private void internalRun(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    Collection<Group> groups = createInitialGroups(appView);
    groups = refineGroups(groups);
    if (!groups.isEmpty()) {
      ProfileCollectionAdditions profileCollectionAdditions =
          ProfileCollectionAdditions.create(appView);
      rewriteApplication(groups, profileCollectionAdditions);
      commitPendingSyntheticClasses(timing);
      updateArtProfiles(groups, profileCollectionAdditions);
      new BridgeHoisting(appView).run(executorService, timing);
    }
    appView.getTypeElementFactory().clearTypeElementsCache();
  }

  /** Returns the set of (non-singleton) groups that have the same superclass. */
  private Collection<Group> createInitialGroups(AppView<AppInfoWithLiveness> appView) {
    Map<DexClass, Group> groups = new LinkedHashMap<>();
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      // Interfaces always have java.lang.Object as their supertype and cannot be given a class as
      // their supertype. Also skip classes without a supertype (i.e., java.lang.Object).
      if (clazz.isInterface() || !clazz.hasSuperType()) {
        continue;
      }
      DexClass superclass = appView.definitionFor(clazz.getSuperType());
      if (superclass != null) {
        groups.computeIfAbsent(superclass, ignoreKey(Group::new)).addClass(clazz);
      }
    }
    groups.values().removeIf(Group::isSingleton);
    return groups.values();
  }

  private Collection<Group> refineGroups(Collection<Group> groups) {
    Collection<Group> newGroups = new ArrayList<>();
    for (Group group : groups) {
      Iterables.addAll(newGroups, refineGroup(group));
    }
    return newGroups;
  }

  /**
   * Splits the group into a collection of smaller groups that should receive a shared superclass.
   *
   * <p>For each class, this creates a specification of the bridges (a mapping from bridge method
   * signatures to their bridge implementation). Two classes are selected for getting a shared
   * synthetic super class if the bridge specification of one is a subset of the other (i.e., a
   * subset of the bridges can be shared and there are no bridges with the same signature that have
   * different behavior).
   */
  private Iterable<Group> refineGroup(Group group) {
    List<Group> newGroups = new ArrayList<>();
    for (DexProgramClass clazz : group) {
      BridgeSpecification bridgeSpecification = getBridgeSpecification(clazz);
      if (bridgeSpecification.isEmpty()) {
        continue;
      }
      Group targetGroup = getGroupForClass(newGroups, clazz, bridgeSpecification);
      if (targetGroup == null) {
        newGroups.add(new Group(clazz, bridgeSpecification));
      }
    }
    // Only introduce a shared super class for non-singleton groups that do not already have a
    // shared superclass in the first place.
    return Iterables.filter(
        newGroups, newGroup -> !newGroup.isSingleton() && newGroup.size() < group.size());
  }

  // TODO(b/309575527): Avoid building IR for all methods.
  private BridgeSpecification getBridgeSpecification(DexProgramClass clazz) {
    BridgeSpecification bridgeSpecification = new BridgeSpecification();
    clazz.forEachProgramVirtualMethodMatching(
        DexEncodedMethod::hasCode,
        method -> {
          // Skip methods that are pinned or allow code replacement. BridgeHoisting cannot hoist
          // pinned bridge methods, and we cannot rely on the body of methods that may be replaced.
          KeepMethodInfo keepInfo = appView.getKeepInfo(method);
          if (keepInfo.isPinned(appView.options())
              || keepInfo.isCodeReplacementAllowed(appView.options())) {
            return;
          }

          // Require the bridge method to be public so that it remains accessible to callers in all
          // packages when hoisted to the shared synthetic superclass (which is placed in the
          // package of the group representative).
          if (!method.getAccessFlags().isPublic()) {
            return;
          }

          IRCode code = method.buildIR(appView, MethodConversionOptions.nonConverting());
          BridgeInfo bridgeInfo = bridgeAnalyzer.analyzeMethod(method.getDefinition(), code);
          if (bridgeInfo == null
              || bridgeInfo.getInvokedMethod().getProto().isIdenticalTo(method.getProto())) {
            return;
          }
          if (bridgeInfo.isStaticBridgeExcludingReceiverInfo()) {
            StaticBridgeExcludingReceiverInfo staticBridgeInfo =
                bridgeInfo.asStaticBridgeExcludingReceiverInfo();
            DexMethod bridgeMethodReference =
                getSpecializedMethodReferenceForLambdaClass(clazz, method, staticBridgeInfo);
            if (bridgeMethodReference != null) {
              bridgeSpecification.addBridge(method, bridgeMethodReference.getSignature());
              getSimpleFeedback().setBridgeInfo(method, staticBridgeInfo);
            }
          } else if (bridgeInfo.isVirtualBridgeInfo()) {
            VirtualBridgeInfo virtualBridgeInfo = bridgeInfo.asVirtualBridgeInfo();
            boolean isInvokedMethodPresentOnSuper =
                appView
                    .appInfo()
                    .resolveMethodOnClass(
                        clazz.getSuperType(), virtualBridgeInfo.getInvokedMethod())
                    .isSingleResolution();
            if (isInvokedMethodPresentOnSuper) {
              // No need to insert a method on a synthetic super class in this case.
              return;
            }
            bridgeSpecification.addBridge(method, virtualBridgeInfo);
            getSimpleFeedback().setBridgeInfo(method, virtualBridgeInfo);
          }
        });
    return bridgeSpecification;
  }

  private DexMethod getSpecializedMethodReferenceForLambdaClass(
      DexProgramClass clazz, ProgramMethod method, StaticBridgeExcludingReceiverInfo bridgeInfo) {
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    if (!syntheticItems.isSynthetic(clazz)
        || !syntheticItems.hasKindThatMatches(clazz, (kind, n) -> kind.equals(n.LAMBDA))) {
      return null;
    }

    // For a lambda main method such as `Object apply(Object)` that targets a method
    // `Integer lambda$0(Integer)`, we want to introduce a method `Integer apply(Integer)` on the
    // lambda class. First check that this method signature does not already exist in the hierarchy.
    DexMethod bridgeTarget = bridgeInfo.getInvokedMethod();
    DexMethod bridgeTargetWithoutCaptures;
    if (bridgeInfo.getCaptures() == 0) {
      bridgeTargetWithoutCaptures = bridgeTarget;
    } else {
      bridgeTargetWithoutCaptures =
          bridgeTarget.withParameters(
              bridgeTarget.getParameters().subParameters(bridgeInfo.getCaptures()), factory);
    }
    assert bridgeTargetWithoutCaptures.getArity() == method.getArity();

    DexMethod bridgeMethodReference =
        appView.testing().enableBridgeHoistingToSharedSyntheticSuperclassReturnSpecialization
            ? method.getReference().withProto(bridgeTargetWithoutCaptures.getProto(), factory)
            : method
                .getReference()
                .withParameters(bridgeTargetWithoutCaptures.getParameters(), factory);
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOn(clazz, bridgeMethodReference);
    if (resolutionResult.isSingleResolution()) {
      return null;
    }
    return bridgeMethodReference;
  }

  private void materializeSpecializedOverloadsOnLambdaClass(
      DexProgramClass clazz,
      BridgeSpecification bridgeSpecification,
      ProfileCollectionAdditions profileCollectionAdditions) {
    List<DexEncodedMethod> pendingMethods = new ArrayList<>();
    bridgeSpecification.forEach(
        (bridge, target) -> {
          ProgramMethod method = clazz.lookupProgramMethod(bridge.withHolder(clazz, factory));
          if (method == null) {
            return;
          }
          BridgeInfo bridgeInfo = method.getOptimizationInfo().getBridgeInfo();
          if (bridgeInfo == null || !bridgeInfo.isStaticBridgeExcludingReceiverInfo()) {
            return;
          }
          materializeSpecializedOverloadOnLambdaClass(
              clazz,
              method,
              bridgeInfo.asStaticBridgeExcludingReceiverInfo(),
              target.withHolder(clazz, factory),
              pendingMethods,
              profileCollectionAdditions);
        });
    clazz.addVirtualMethods(pendingMethods);
  }

  /**
   * For a lambda class with a main method `Object apply(Object)` that targets a static javac
   * synthetic method `Integer lambda$0(Integer)`:
   *
   * <p>1. Insert a `Integer apply(Integer)` method that calls the `Integer lambda$0(Integer)`
   * method.
   *
   * <p>2. Update the `Object apply(Object)` method to call the newly added overload.
   */
  private void materializeSpecializedOverloadOnLambdaClass(
      DexProgramClass clazz,
      ProgramMethod method,
      StaticBridgeExcludingReceiverInfo bridgeInfo,
      DexMethod bridgeMethodReference,
      List<DexEncodedMethod> pendingMethods,
      ProfileCollectionAdditions profileCollectionAdditions) {
    // Synthesize a bridge method on the lambda class with the target signature.
    ProgramMethod bridgeMethod =
        method
            .getDefinition()
            .toTypeSubstitutedMethodAsInlining(
                bridgeMethodReference,
                factory,
                builder ->
                    builder
                        .modifyAccessFlags(MethodAccessFlags::setSynthetic)
                        .setIsLibraryMethodOverride(OptionalBool.FALSE))
            .asProgramMethod(clazz);
    pendingMethods.add(bridgeMethod.getDefinition());
    profileCollectionAdditions.addMethodIfContextIsInProfile(bridgeMethod, method);

    // Fixup bridge.
    fixupBridgeMethodCode(bridgeMethod, method.getReference());

    // Update the current method to call the bridge instead.
    DexMethod bridgeTarget = bridgeInfo.getInvokedMethod();
    IRCode code = method.buildIR(appView);
    InvokeStatic invoke =
        code.<InvokeStatic>instructions(
                i ->
                    i.isInvokeStatic()
                        && i.asInvokeStatic().getInvokedMethod().isIdenticalTo(bridgeTarget))
            .iterator()
            .next();
    invoke.replace(
        InvokeVirtual.builder()
            .setArguments(
                ImmutableList.<Value>builder()
                    .add(code.getThis())
                    .addAll(
                        invoke
                            .arguments()
                            .subList(bridgeInfo.getCaptures(), invoke.arguments().size()))
                    .build())
            .setIsInterface(clazz.isInterface())
            .setMethod(bridgeMethodReference)
            .applyIf(
                !bridgeMethodReference.getReturnType().isVoidType(),
                builder ->
                    builder.setFreshOutValue(
                        code, bridgeMethodReference.getReturnType().toTypeElement(appView)))
            .setPosition(invoke)
            .build());

    // Commit the code.
    IRFinalizer<?> finalizer =
        code.getConversionOptions().getFinalizer(new DeadCodeRemover(appView), appView);
    Code newCode = finalizer.finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    method.setCode(newCode, appView);

    // Now the lambda main method is a virtual bridge to the newly synthesized method on the lambda.
    VirtualBridgeInfo virtualBridgeInfo = new VirtualBridgeInfo(bridgeMethodReference);
    getSimpleFeedback().setBridgeInfo(method, virtualBridgeInfo);
  }

  private void fixupBridgeMethodCode(ProgramMethod method, DexMethod originalMethod) {
    assert method.getArity() == originalMethod.getArity();
    IntList worklist = new IntArrayList(method.getParameters().size());
    for (int parameterIndex = 0; parameterIndex < method.getParameters().size(); parameterIndex++) {
      if (method.getParameter(parameterIndex).isPrimitiveType()
          != originalMethod.getParameter(parameterIndex).isPrimitiveType()) {
        worklist.add(method.getDefinition().getArgumentIndexFromParameterIndex(parameterIndex));
      }
    }
    if (worklist.isEmpty()) {
      return;
    }
    IRCode code = method.buildIR(appView);
    Iterator<Argument> arguments = code.argumentIterator();
    IntListIterator worklistIterator = worklist.iterator();
    while (worklistIterator.hasNext()) {
      int argumentIndex = worklistIterator.nextInt();
      Argument argument = arguments.next();
      while (argument.getIndex() != argumentIndex) {
        argument = arguments.next();
      }
      Value argumentValue = argument.outValue();
      if (argumentValue.getType().isPrimitiveType()) {
        // Remove optional cast and unbox.
        if (argumentValue.singleUniqueUser().isCheckCast()) {
          CheckCast checkCast = argumentValue.singleUniqueUser().asCheckCast();
          checkCast.outValue().replaceUsers(checkCast.object());
          checkCast.remove();
        }
        InvokeVirtual invoke = argumentValue.singleUniqueUser().asInvokeVirtual();
        assert appView.dexItemFactory().unboxPrimitiveMethods.contains(invoke.getInvokedMethod());
        invoke.outValue().replaceUsers(invoke.getFirstArgument());
        invoke.remove();
      } else {
        assert false;
      }
    }

    IRFinalizer<?> finalizer =
        code.getConversionOptions().getFinalizer(new DeadCodeRemover(appView), appView);
    for (Instruction i : code.instructions()) {
      if (i.isCheckCast() && i.getFirstOperand().getType().isPrimitiveType()) {
        assert false;
      }
    }
    Code newCode = finalizer.finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    method.setCode(newCode, appView);
  }

  private Group getGroupForClass(
      Collection<Group> groups, DexProgramClass clazz, BridgeSpecification bridgeSpecification) {
    for (Group group : groups) {
      // Maintain the group's BridgeSpecification as the intersection (subset) of the bridge
      // specifications of all classes in the group.
      if (bridgeSpecification.lessThanOrEquals(group.getBridgeSpecification())) {
        group.addClass(clazz);
        group.setBridgeSpecification(bridgeSpecification);
        return group;
      } else if (group.getBridgeSpecification().lessThanOrEquals(bridgeSpecification)) {
        group.addClass(clazz);
        return group;
      }
    }
    return null;
  }

  private void rewriteApplication(
      Collection<Group> groups, ProfileCollectionAdditions profileCollectionAdditions) {
    MainThreadContext mainThreadContext =
        appView.createProcessorContext().createMainThreadContext();
    for (Group group : groups) {
      DexProgramClass representative = ListUtils.first(group.getClasses());
      Set<DexType> interfaces = SetUtils.newIdentityHashSet(representative.getInterfaces());
      for (DexProgramClass clazz : Iterables.skip(group.getClasses(), 1)) {
        interfaces.removeIf(type -> !clazz.getInterfaces().contains(type));
      }
      DexProgramClass syntheticSuperclass =
          appView
              .getSyntheticItems()
              .createClass(
                  kinds -> kinds.SHARED_SUPER_CLASS,
                  mainThreadContext.createUniqueContext(representative),
                  appView,
                  classBuilder -> {
                    classBuilder
                        .setAbstract()
                        .setSuperType(representative.getSuperType())
                        .setInterfaces(ListUtils.sort(interfaces, Comparator.naturalOrder()));
                    group
                        .getBridgeSpecification()
                        .forEach(
                            (bridge, target) ->
                                classBuilder.addMethod(
                                    methodBuilder ->
                                        methodBuilder
                                            .setAccessFlags(
                                                MethodAccessFlags.builder()
                                                    .setAbstract()
                                                    .setPublic()
                                                    .build())
                                            // TODO(b/309575527): Set correct api level.
                                            .setApiLevelForDefinition(appView.computedMinApiLevel())
                                            // TODO(b/309575527): Set correct library override info.
                                            .setIsLibraryMethodOverride(OptionalBool.FALSE)
                                            .setName(target.getName())
                                            .setProto(target.getProto())));
                  });

      // Fixup class hierarchy.
      for (DexProgramClass clazz : group) {
        materializeSpecializedOverloadsOnLambdaClass(
            clazz, group.getBridgeSpecification(), profileCollectionAdditions);
        clazz.setSuperType(syntheticSuperclass.getType());
        clazz.setInterfaces(clazz.getInterfaces().removeIf(interfaces::contains));
      }

      // Fixup instantiated hierarchy.
      Map<DexType, Set<DexClass>> instantiatedHierarchy =
          appView.appInfo().getMutableObjectAllocationInfoCollection().getInstantiatedHierarchy();
      for (DexProgramClass clazz : group) {
        for (DexType oldSupertype : syntheticSuperclass.allImmediateSupertypes()) {
          Set<DexClass> instantiatedSubclasses = instantiatedHierarchy.get(oldSupertype);
          if (instantiatedSubclasses != null && instantiatedSubclasses.remove(clazz)) {
            instantiatedSubclasses.add(syntheticSuperclass);
            instantiatedHierarchy
                .computeIfAbsent(syntheticSuperclass.getType(), ignoreKey(Sets::newIdentityHashSet))
                .add(clazz);
          }
        }
      }
    }
  }

  private void commitPendingSyntheticClasses(Timing timing) {
    assert appView.getSyntheticItems().hasPendingSyntheticClasses();
    appView.rebuildAppInfo(timing);
  }

  private void updateArtProfiles(
      Collection<Group> groups, ProfileCollectionAdditions profileCollectionAdditions) {
    if (profileCollectionAdditions.isNop()) {
      return;
    }
    for (Group group : groups) {
      for (DexProgramClass clazz : group) {
        profileCollectionAdditions.applyIfContextIsInProfile(
            clazz, additionsBuilder -> additionsBuilder.addClassRule(clazz.getSuperType()));
        group
            .getBridgeSpecification()
            .forEach(
                (bridge, target) -> {
                  DexEncodedMethod targetMethod = clazz.getMethodCollection().getMethod(target);
                  if (targetMethod != null) {
                    profileCollectionAdditions.applyIfContextIsInProfile(
                        targetMethod.getReference(),
                        additionsBuilder ->
                            additionsBuilder.addMethodRule(
                                target.withHolder(clazz.getSuperType(), appView.dexItemFactory())));
                  }
                });
      }
    }
    profileCollectionAdditions.commit(appView);
  }

  private static class Group implements Iterable<DexProgramClass> {

    private final List<DexProgramClass> classes;
    private BridgeSpecification bridgeSpecification;

    public Group() {
      this.classes = new ArrayList<>();
      this.bridgeSpecification = null;
    }

    public Group(DexProgramClass clazz, BridgeSpecification bridgeSpecification) {
      this.classes = ListUtils.newArrayList(clazz);
      this.bridgeSpecification = bridgeSpecification;
    }

    void addClass(DexProgramClass clazz) {
      classes.add(clazz);
    }

    BridgeSpecification getBridgeSpecification() {
      return bridgeSpecification;
    }

    List<DexProgramClass> getClasses() {
      return classes;
    }

    void setBridgeSpecification(BridgeSpecification bridgeSpecification) {
      this.bridgeSpecification = bridgeSpecification;
    }

    boolean isSingleton() {
      return size() == 1;
    }

    @Override
    public Iterator<DexProgramClass> iterator() {
      return classes.iterator();
    }

    public int size() {
      return classes.size();
    }
  }

  private static class BridgeSpecification {

    private final DexMethodSignatureMap<DexMethodSignature> bridges =
        DexMethodSignatureMap.create();

    void addBridge(ProgramMethod method, VirtualBridgeInfo bridgeInfo) {
      addBridge(method, bridgeInfo.getInvokedMethod().getSignature());
    }

    void addBridge(ProgramMethod method, DexMethodSignature target) {
      bridges.put(method, target);
    }

    boolean containsBridgeWithTarget(DexMethodSignature method, DexMethodSignature target) {
      return target.equals(bridges.get(method));
    }

    void forEach(BiConsumer<? super DexMethodSignature, ? super DexMethodSignature> consumer) {
      bridges.forEach(consumer);
    }

    boolean isEmpty() {
      return bridges.isEmpty();
    }

    boolean lessThanOrEquals(BridgeSpecification bridgeSpecification) {
      if (size() > bridgeSpecification.size()) {
        return false;
      }
      for (Entry<DexMethodSignature, DexMethodSignature> entry : bridges.entrySet()) {
        DexMethodSignature method = entry.getKey();
        DexMethodSignature target = entry.getValue();
        if (!bridgeSpecification.containsBridgeWithTarget(method, target)) {
          return false;
        }
      }
      return true;
    }

    int size() {
      return bridges.size();
    }
  }
}

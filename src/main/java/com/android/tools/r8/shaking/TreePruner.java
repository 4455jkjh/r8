// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.google.common.base.Predicates.alwaysFalse;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultInstanceInitializerCode;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.NestMemberClassAttribute;
import com.android.tools.r8.graph.PermittedSubclassAttribute;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.RecordComponentInfo;
import com.android.tools.r8.ir.optimize.info.MutableFieldOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MutableMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback.OptimizationInfoFixer;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.ArrayUtils;
import com.android.tools.r8.utils.internal.IterableUtils;
import com.android.tools.r8.utils.internal.PredicateUtils;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import java.util.function.Predicate;

public abstract class TreePruner {

  protected final AppView<AppInfoWithLiveness> appView;
  private final UnusedItemsPrinter unusedItemsPrinter;

  protected TreePruner(
      AppView<AppInfoWithLiveness> appView, UnusedItemsPrinter unusedItemsPrinter) {
    this.appView = appView;
    this.unusedItemsPrinter = unusedItemsPrinter;
  }

  public abstract boolean isInstantiatedDirectly(DexProgramClass clazz);

  public abstract boolean isLiveMethod(DexMethod method);

  public abstract boolean isLiveProgramClass(DexProgramClass clazz);

  public abstract boolean isReachableOrReferencedField(DexEncodedField field);

  public abstract boolean isTargetedMethod(DexMethod method);

  public abstract boolean isTypeLive(DexType type);

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    run(executorService, timing, PrunedItems.builder());
  }

  public void run(
      ExecutorService executorService, Timing timing, PrunedItems.Builder prunedItemsBuilder)
      throws ExecutionException {
    timing.begin("Pruning application");
    DirectMappedDexApplication application = appView.appInfo().app().asDirect();
    DirectMappedDexApplication newApplication =
        removeUnused(application, prunedItemsBuilder, timing);
    PrunedItems prunedItems = prunedItemsBuilder.setPrunedApp(newApplication).build();
    fixupOptimizationInfo(newApplication, executorService, prunedItems.getRemovedClasses());
    appView.pruneItems(prunedItems, executorService, timing);
    appView.notifyOptimizationFinished();
    timing.end();
  }

  private DirectMappedDexApplication removeUnused(
      DirectMappedDexApplication application,
      PrunedItems.Builder prunedItemsBuilder,
      Timing timing) {
    Collection<DexProgramClass> classes = application.classesWithDeterministicOrder();
    List<DexProgramClass> newClasses = getNewProgramClasses(classes, prunedItemsBuilder);
    return newClasses.size() < classes.size()
        ? application.builder().replaceProgramClasses(newClasses).build(timing)
        : application;
  }

  private List<DexProgramClass> getNewProgramClasses(
      Collection<DexProgramClass> classes, PrunedItems.Builder prunedItemsBuilder) {
    InternalOptions options = appView.options();
    List<DexProgramClass> newClasses = new ArrayList<>();
    for (DexProgramClass clazz : classes) {
      if (isLiveProgramClass(clazz)) {
        newClasses.add(clazz);
        // The class is used and must be kept. Remove the unused fields and methods from the class.
        unusedItemsPrinter.visiting(clazz);
        if (options.isShrinking()) {
          fixupAccessFlags(clazz);
          pruneFields(clazz, prunedItemsBuilder);
          pruneMethods(clazz, prunedItemsBuilder);
          pruneAttributes(clazz);
          pruneUnusedInterfaces(clazz);
        } else {
          // Only attempt to remove synthetic methods inserted by desugaring.
          pruneMethods(clazz, prunedItemsBuilder);
        }
        unusedItemsPrinter.visited();
      } else {
        // The class is completely unused and we can remove it.
        prunedItemsBuilder.addRemovedClass(clazz.type);
        if (clazz.hasSourceFile()) {
          appView.addPrunedClassSourceFile(clazz.type, clazz.getSourceFile().toString());
        }
        if (!appView.getSyntheticItems().isSynthetic(clazz)) {
          unusedItemsPrinter.registerUnusedClass(clazz);
        }
      }
    }
    unusedItemsPrinter.finished();
    return newClasses;
  }

  private void fixupAccessFlags(DexProgramClass clazz) {
    InternalOptions options = appView.options();
    if (!isInstantiatedDirectly(clazz) && !options.forceProguardCompatibility) {
      // The class is only needed as a type but never instantiated. Make it abstract to reflect
      // this.
      if (clazz.isFinal()) {
        // We cannot mark this class abstract, as it is final (not supported on Android).
        // However, this might extend an abstract class and we might have removed the
        // corresponding methods in this class. This might happen if we only keep this
        // class around for its constants.
        // For now, we remove the final flag to still be able to mark it abstract.
        clazz.accessFlags.demoteFromFinal();
      }
      clazz.accessFlags.setAbstract();
    }
  }

  private void pruneUnusedInterfaces(DexProgramClass clazz) {
    Set<DexType> reachableInterfaces = new LinkedHashSet<>();
    for (DexType type : clazz.getInterfaces()) {
      retainReachableInterfacesFrom(type, reachableInterfaces);
    }
    if (!reachableInterfaces.isEmpty()) {
      boolean pinnedHolder = !appView.getKeepInfo(clazz).isOptimizationAllowed(appView.options());
      removeInterfacesImplementedDirectlyAndIndirectlyByClassFromSet(
          pinnedHolder, clazz.superType, reachableInterfaces, clazz);
    }
    if (reachableInterfaces.isEmpty()) {
      clazz.interfaces = DexTypeList.empty();
    } else {
      clazz.interfaces = new DexTypeList(reachableInterfaces.toArray(DexType.EMPTY_ARRAY));
    }
  }

  private void removeInterfacesImplementedDirectlyAndIndirectlyByClassFromSet(
      boolean pinnedRoot, DexType type, Set<DexType> interfaces, DexProgramClass context) {
    DexClass clazz = appView.definitionFor(type);
    if (clazz == null) {
      return;
    }
    if (clazz.isLibraryClass()) {
      // TODO(b/259204069): Mitigation of invalid interface removal.
      // It would be nice to integrate this with the api database.
      return;
    }
    for (DexType itf : clazz.interfaces) {
      if (pinnedRoot) {
        DexProgramClass itfClass = asProgramClassOrNull(appView.definitionFor(itf, context));
        if (itfClass == null
            || !appView.getKeepInfo(itfClass).isOptimizationAllowed(appView.options())) {
          // If root-holder and interface are pinned then retain the interface on the root-holder.
          continue;
        }
      }
      if (interfaces.remove(itf) && interfaces.isEmpty()) {
        return;
      }
    }
    if (clazz.superType != null) {
      assert !interfaces.isEmpty();
      removeInterfacesImplementedDirectlyAndIndirectlyByClassFromSet(
          pinnedRoot, clazz.superType, interfaces, context);
    }
  }

  private void retainReachableInterfacesFrom(DexType type, Set<DexType> reachableInterfaces) {
    if (isTypeLive(type)) {
      reachableInterfaces.add(type);
    } else {
      DexProgramClass unusedInterface = appView.definitionForProgramType(type);
      assert unusedInterface != null;
      assert unusedInterface.isInterface();
      for (DexType interfaceType : unusedInterface.interfaces.values) {
        retainReachableInterfacesFrom(interfaceType, reachableInterfaces);
      }
    }
  }

  private void pruneAttributes(DexProgramClass clazz) {
    clazz.removeAnnotations(this::isAnnotationReferencingPrunedType);
    clazz.forEachMember(m -> m.removeAllAnnotations(this::isAnnotationReferencingPrunedType));
    clazz.removeInnerClasses(this::isAttributeReferencingMissingOrPrunedType);
    clazz.removeEnclosingMethodAttribute(this::isAttributeReferencingPrunedItem);
    rewriteNestAttributes(
        clazz, this::isTypeLive, appView.appInfo()::definitionForWithoutExistenceAssert);
    clazz.removePermittedSubclassAttribute(this::isAttributeReferencingPrunedType);
  }

  private void pruneFields(DexProgramClass clazz, PrunedItems.Builder prunedItemsBuilder) {
    DexEncodedField[] reachableInstanceFields =
        reachableFields(clazz.instanceFields(), prunedItemsBuilder);
    if (reachableInstanceFields != null) {
      clazz.setInstanceFields(reachableInstanceFields);
    }
    DexEncodedField[] reachableStaticFields =
        reachableFields(clazz.staticFields(), prunedItemsBuilder);
    if (reachableStaticFields != null) {
      clazz.setStaticFields(reachableStaticFields);
    }
    // TODO(b/274888318): Check this.
    if (reachableInstanceFields != null) {
      if (!clazz.getRecordComponents().isEmpty()) {
        clazz.removeRecordComponents(
            PredicateUtils.not(isReachableInstanceField(reachableInstanceFields)));
      }
    }
    assert verifyNoDeadFields(clazz);
  }

  private void pruneMethods(DexProgramClass clazz, PrunedItems.Builder prunedItemsBuilder) {
    DexEncodedMethod[] reachableDirectMethods =
        reachableMethods(clazz.directMethods(), clazz, prunedItemsBuilder);
    if (reachableDirectMethods != null) {
      clazz.setDirectMethods(reachableDirectMethods);
    }
    DexEncodedMethod[] reachableVirtualMethods =
        reachableMethods(clazz.virtualMethods(), clazz, prunedItemsBuilder);
    if (reachableVirtualMethods != null) {
      clazz.setVirtualMethods(reachableVirtualMethods);
    }
  }

  public static void rewriteNestAttributes(
      DexProgramClass clazz, Predicate<DexType> isLive, Function<DexType, DexClass> definition) {
    if (!clazz.isInANest() || !isLive.test(clazz.type)) {
      return;
    }
    if (clazz.isNestHost()) {
      clearDeadNestMembers(clazz, isLive, definition);
    } else {
      assert clazz.isNestMember();
      if (!isLive.test(clazz.getNestHost())) {
        claimNestOwnership(clazz, isLive, definition);
      }
    }
  }

  public static Predicate<RecordComponentInfo> isReachableInstanceField(
      DexEncodedField[] reachableInstanceFields) {
    final int ARRAY_LOOKUP_THRESHOLD = 10;
    if (reachableInstanceFields.length == 0) {
      return alwaysFalse();
    } else if (reachableInstanceFields.length < ARRAY_LOOKUP_THRESHOLD) {
      return info ->
          ArrayUtils.contains(
              reachableInstanceFields, DexEncodedField::getReference, info.getField());
    } else {
      Set<DexField> reachableInstanceFieldSet = Sets.newIdentityHashSet();
      for (DexEncodedField reachableInstanceField : reachableInstanceFields) {
        reachableInstanceFieldSet.add(reachableInstanceField.getReference());
      }
      return info -> reachableInstanceFieldSet.contains(info.getField());
    }
  }

  private boolean isTypeMissing(DexType type) {
    return appView.appInfo().getMissingClasses().contains(type);
  }

  private static void clearDeadNestMembers(
      DexClass nestHost, Predicate<DexType> isLive, Function<DexType, DexClass> definition) {
    // null definition should raise a warning which is raised later on in Nest specific passes.
    nestHost.removeNestMemberAttributes(
        nestMemberAttr ->
            definition.apply(nestMemberAttr.getNestMember()) != null
                && !isLive.test(nestMemberAttr.getNestMember()));
  }

  @SuppressWarnings("ReferenceEquality")
  private static void claimNestOwnership(
      DexClass newHost, Predicate<DexType> isLive, Function<DexType, DexClass> definition) {
    DexClass previousHost = definition.apply(newHost.getNestHost());
    if (previousHost == null) {
      // Nest host will be cleared from all nest members in Nest specific passes.
      return;
    }
    newHost.clearNestHost();
    for (NestMemberClassAttribute attr : previousHost.getNestMembersClassAttributes()) {
      if (attr.getNestMember() != newHost.type && isLive.test(attr.getNestMember())) {
        DexClass nestMember = definition.apply(attr.getNestMember());
        if (nestMember != null) {
          nestMember.setNestHost(newHost.type);
        }
        // We still need to add it, even if the definition is null,
        // so the warning / error are correctly raised in Nest specific passes.
        newHost
            .getNestMembersClassAttributes()
            .add(new NestMemberClassAttribute(attr.getNestMember()));
      }
    }
  }

  private boolean isAnnotationReferencingPrunedType(DexAnnotation annotation) {
    return !isTypeLive(annotation.getAnnotationType());
  }

  private boolean isAttributeReferencingPrunedItem(EnclosingMethodAttribute attr) {
    return (attr.getEnclosingClass() != null && !isTypeLive(attr.getEnclosingClass()))
        || (attr.getEnclosingMethod() != null && !isLiveMethod(attr.getEnclosingMethod()));
  }

  private boolean isAttributeReferencingMissingOrPrunedType(InnerClassAttribute attr) {
    if (isTypeMissing(attr.getInner()) || !isTypeLive(attr.getInner())) {
      return true;
    }
    DexType context = attr.getLiveContext(appView, this::isLiveMethod);
    return context == null || isTypeMissing(context) || !isTypeLive(context);
  }

  private boolean isAttributeReferencingPrunedType(PermittedSubclassAttribute attr) {
    return !isTypeLive(attr.getPermittedSubclass());
  }

  private <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>> int firstUnreachableIndex(
      List<D> items, Predicate<D> live) {
    for (int i = 0; i < items.size(); i++) {
      if (!live.test(items.get(i))) {
        return i;
      }
    }
    return -1;
  }

  private DexEncodedMethod[] reachableMethods(
      Iterable<DexEncodedMethod> methods,
      DexProgramClass clazz,
      PrunedItems.Builder prunedItemsBuilder) {
    return reachableMethods(
        IterableUtils.ensureUnmodifiableList(methods), clazz, prunedItemsBuilder);
  }

  private DexEncodedMethod[] reachableMethods(
      List<DexEncodedMethod> methods,
      DexProgramClass clazz,
      PrunedItems.Builder prunedItemsBuilder) {
    int firstUnreachable =
        firstUnreachableIndex(methods, method -> isLiveMethod(method.getReference()));
    // Return the original array if all methods are used.
    if (firstUnreachable == -1) {
      for (DexEncodedMethod method : methods) {
        canonicalizeCode(method.asProgramMethod(clazz));
      }
      return null;
    }
    ArrayList<DexEncodedMethod> reachableMethods = new ArrayList<>(methods.size());
    for (int i = 0; i < methods.size(); i++) {
      DexEncodedMethod method = methods.get(i);
      if (isLiveMethod(method.getReference())) {
        canonicalizeCode(method.asProgramMethod(clazz));
        reachableMethods.add(method);
      } else if (isTargetedMethod(method.getReference())) {
        // If the method is already abstract, and doesn't have code, let it be.
        if (method.shouldNotHaveCode() && !method.hasCode()) {
          reachableMethods.add(method);
          continue;
        }
        // Private methods and static methods can only be targeted yet non-live as the result of
        // an invalid invoke. They will not actually be called at runtime but we have to keep them
        // as non-abstract (see above) to produce the same failure mode.
        new ProgramMethod(clazz, method).convertToAbstractOrThrowNullMethod(appView);
        method.markNotProcessed();
        reachableMethods.add(method);
      } else {
        unusedItemsPrinter.registerUnusedMethod(method);
        prunedItemsBuilder.addRemovedMethod(method.getReference());
      }
    }
    return reachableMethods.isEmpty()
        ? DexEncodedMethod.EMPTY_ARRAY
        : reachableMethods.toArray(DexEncodedMethod.EMPTY_ARRAY);
  }

  private void canonicalizeCode(ProgramMethod method) {
    if (method.getDefinition().hasCode()) {
      DefaultInstanceInitializerCode.canonicalizeCodeIfPossible(appView, method);
    }
  }

  private DexEncodedField[] reachableFields(
      List<DexEncodedField> fields, PrunedItems.Builder prunedItemsBuilder) {
    int firstUnreachable = firstUnreachableIndex(fields, this::isReachableOrReferencedField);
    // Return the original array if all fields are used.
    if (firstUnreachable == -1) {
      return null;
    }
    DexEncodedField firstUnreachableField = fields.get(firstUnreachable);
    unusedItemsPrinter.registerUnusedField(firstUnreachableField);
    prunedItemsBuilder.addRemovedField(firstUnreachableField.getReference());
    ArrayList<DexEncodedField> reachableOrReferencedFields = new ArrayList<>(fields.size());
    for (int i = 0; i < firstUnreachable; i++) {
      reachableOrReferencedFields.add(fields.get(i));
    }
    for (int i = firstUnreachable + 1; i < fields.size(); i++) {
      DexEncodedField field = fields.get(i);
      if (isReachableOrReferencedField(field)) {
        reachableOrReferencedFields.add(field);
      } else {
        unusedItemsPrinter.registerUnusedField(field);
        prunedItemsBuilder.addRemovedField(field.getReference());
      }
    }
    return reachableOrReferencedFields.isEmpty()
        ? DexEncodedField.EMPTY_ARRAY
        : reachableOrReferencedFields.toArray(DexEncodedField.EMPTY_ARRAY);
  }

  private void fixupOptimizationInfo(
      DirectMappedDexApplication application,
      ExecutorService executorService,
      Set<DexType> prunedTypes)
      throws ExecutionException {
    // Clear the type elements cache due to redundant interface removal.
    appView.getTypeElementFactory().clearTypeElementsCache();

    OptimizationFeedback feedback = OptimizationFeedbackSimple.getInstance();
    feedback.fixupOptimizationInfos(
        application.classes(),
        appView.options().getThreadingModule(),
        executorService,
        new OptimizationInfoFixer() {
          @Override
          public void fixup(DexEncodedField field, MutableFieldOptimizationInfo optimizationInfo) {
            optimizationInfo.fixupClassTypeReferences(appView, appView.graphLens(), prunedTypes);
          }

          @Override
          public void fixup(
              DexEncodedMethod method, MutableMethodOptimizationInfo optimizationInfo) {
            optimizationInfo.fixupClassTypeReferences(appView, appView.graphLens(), prunedTypes);
          }
        });

    // Verify that the fixup did not lead to the caching of any elements.
    assert appView.getTypeElementFactory().verifyNoCachedTypeElements();
  }

  private boolean verifyNoDeadFields(DexProgramClass clazz) {
    for (DexEncodedField field : clazz.fields()) {
      // Pinned field which type is never instantiated are always null, they are marked as dead
      // so their reads and writes are cleared, but they are still in the program.
      assert !field.getOptimizationInfo().isDead() || appView.appInfo().isPinned(field)
          : "Expected field `" + field.getReference().toSourceString() + "` to be absent";
    }
    return true;
  }
}
